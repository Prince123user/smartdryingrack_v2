package com.example.smartdryingrack.data.hardware.rest

import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.SensorData
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/** Plain HTTP is restricted to a private IPv4 address on the user's home network. */
fun normalizeRackUrl(value: String): String {
    val uri = try { URI(value.trim()) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid rack URL.") }
    require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Use an http:// or https:// rack address." }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path in listOf("", "/")) {
        "Enter only the rack address and optional port, without a path or credentials."
    }
    require(uri.port == -1 || uri.port in 1..65535) { "Invalid port." }
    if (uri.scheme == "http") {
        val parts = uri.host.split(".").map { it.toIntOrNull() }
        require(parts.size == 4 && parts.all { it != null && it in 0..255 } &&
            (parts[0] == 10 || parts[0] == 192 && parts[1] == 168 || parts[0] == 172 && parts[1] in 16..31)) {
            "HTTP requires a private Wi-Fi IPv4 address, such as http://192.168.1.50. Use HTTPS elsewhere."
        }
    }
    return uri.toString().trimEnd('/')
}

data class RackSnapshot(val state: RackState, val sensors: SensorData, val firmware: String?,
                        val buzzerEnabled: Boolean? = null, val rainRaw: Int? = null, val motionReason: String? = null)

fun parseRackSnapshot(body: String, receivedAt: Long): RackSnapshot {
    val json = JSONObject(body)
    val state = RackState.valueOf(json.getString("state"))
    val sensors = json.getJSONObject("sensors")
    require(sensors.get("temperature") is Number && sensors.get("humidity") is Number && sensors.get("rainDetected") is Boolean) { "Invalid sensor value types." }
    val dryness = sensors.opt("clothesDryPercentage")
    require(dryness == null || dryness == JSONObject.NULL || (dryness is Number && dryness.toDouble() in 0.0..100.0 && dryness.toDouble() % 1.0 == 0.0)) { "Invalid dryness reading." }
    val data = SensorData(
        temperature = sensors.getDouble("temperature").toFloat(),
        humidity = sensors.getDouble("humidity").toFloat(),
        rainDetected = sensors.getBoolean("rainDetected"),
        clothesDryPercentage = if (sensors.has("clothesDryPercentage") && !sensors.isNull("clothesDryPercentage"))
            sensors.getInt("clothesDryPercentage") else null,
        timestamp = receivedAt
    )
    require(data.isValid) { "The rack returned invalid sensor readings." }
    val buzzer = json.opt("buzzerEnabled").takeUnless { it == JSONObject.NULL }
    require(buzzer == null || buzzer is Boolean) { "Invalid buzzer setting." }
    val raw = sensors.opt("rainRaw").takeUnless { it == JSONObject.NULL }
    require(raw == null || raw is Number && raw.toDouble() in 0.0..4095.0 && raw.toDouble() % 1.0 == 0.0) { "Invalid rain sensor reading." }
    return RackSnapshot(state, data, json.optString("firmwareVersion").takeIf { it.isNotBlank() },
        buzzer as Boolean?, (raw as Number?)?.toInt(), json.optString("motionReason").takeIf { it.isNotBlank() })
}

/** Never retries a motor command; firmware must deduplicate its requestId. */
class RackApi(private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    fun status(baseUrl: String): RackSnapshot =
        parseRackSnapshot(request(baseUrl, "/api/status"), System.currentTimeMillis())

    fun command(baseUrl: String, action: String) {
        require(action in setOf("EXTEND", "RETRACT", "STOP"))
        val body = JSONObject().put("action", action).put("requestId", UUID.randomUUID().toString()).toString()
        val response = JSONObject(request(baseUrl, "/api/commands", body))
        if (!response.getBoolean("accepted")) throw IOException("The rack rejected the command.")
    }

    fun setBuzzer(baseUrl: String, enabled: Boolean): Boolean {
        val body = JSONObject().put("enabled", enabled).put("requestId", UUID.randomUUID().toString()).toString()
        val response = JSONObject(request(baseUrl, "/api/buzzer", body))
        if (response.opt("accepted") != true || response.opt("buzzerEnabled") !is Boolean || response.getBoolean("buzzerEnabled") != enabled) {
            throw IOException("The rack did not confirm the requested buzzer setting. Update the ESP32 firmware.")
        }
        return response.getBoolean("buzzerEnabled")
    }

    private fun request(baseUrl: String, path: String, body: String? = null): String {
        val connection = connectionFactory(URI(normalizeRackUrl(baseUrl) + path).toURL())
        try {
            connection.connectTimeout = 2500
            connection.readTimeout = 2500
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Connection", "close")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            if (connection.responseCode !in 200..299) throw IOException("Rack returned HTTP ${connection.responseCode}.")
            return connection.inputStream.bufferedReader().use {
                val buffer = CharArray(65537)
                var count = 0
                while (count < buffer.size) {
                    val read = it.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                require(count <= 65536) { "Rack response is too large." }
                String(buffer, 0, count)
            }
        } finally {
            connection.disconnect()
        }
    }
}
