package com.example.smartdryingrack.data.hardware.rest

import com.example.smartdryingrack.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface LightingGateway {
    suspend fun fetch(address: String): LightingSnapshot
    suspend fun update(address: String, update: LightingUpdate): LightingAcknowledgement
}

private const val MAX_JSON_INTEGER = 9007199254740991L
private val lightingToken = Regex("[A-Za-z0-9_.-]{1,96}")
private fun JSONObject.token(key: String): String = text(key).also {
    require(lightingToken.matches(it)) { "Invalid lighting $key token." }
}
private fun JSONObject.integer(key: String, max: Long = MAX_JSON_INTEGER): Long {
    val value = get(key)
    require(value is Number && value.toDouble().isFinite() && value.toDouble() >= 0 &&
        value.toDouble() <= max.toDouble() && value.toDouble() % 1.0 == 0.0) { "Invalid lighting $key." }
    return value.toLong()
}
private fun JSONObject.text(key: String, max: Int = 96): String {
    val value = get(key)
    require(value is String && value.isNotBlank() && value.length <= max) { "Invalid lighting $key." }
    return value
}
private fun JSONObject.settings(): LightSettings {
    val power = get("power")
    require(power is Boolean) { "Lighting power must be a boolean." }
    require(!has("rgb")) { "House lights are single-color LEDs; RGB is not supported." }
    return LightSettings(power, integer("brightness", 100).toInt())
}

fun parseLightingSnapshot(body: String): LightingSnapshot {
    val json = JSONObject(body)
    require(json.integer("schemaVersion", 2) == 2L) { "Unsupported lighting schema." }
    val session = json.token("deviceSession")
    val version = json.integer("stateVersion")
    val array = json.getJSONArray("zones")
    require(array.length() == LightingZone.entries.size) { "The device must report all six house-light zones." }
    val zones = mutableMapOf<LightingZone, ConfirmedZone>()
    repeat(array.length()) { index ->
        val zone = array.getJSONObject(index)
        val id = LightingZone.fromId(zone.text("id"))
        require(id !in zones) { "Duplicate lighting zone." }
        val revision = zone.integer("revision")
        require(revision <= version) { "Zone revision exceeds snapshot version." }
        require(zone.has("lastRequestId")) { "Missing lighting lastRequestId." }
        val request = if (zone.isNull("lastRequestId")) null else zone.token("lastRequestId")
        zones[id] = ConfirmedZone(zone.settings(), revision, request)
    }
    require(json.has("indicator")) { "Missing system-indicator state." }
    val indicator = if (json.isNull("indicator")) null else {
        val status = json.getJSONObject("indicator")
        require(status.text("id") == "system_status" && status.text("mode") == "AUTOMATIC") {
            "System status light must be automatic."
        }
        val pattern = status.text("pattern")
        require(pattern in setOf("solid", "blink", "pulse", "off")) { "Unknown status-light pattern." }
        val power = status.get("power")
        require(power is Boolean) { "Indicator power must be a boolean." }
        val color = status.getJSONObject("rgb")
        val settings = StatusLightSettings(power, Rgb(color.integer("r", 255).toInt(),
            color.integer("g", 255).toInt(), color.integer("b", 255).toInt()), status.integer("brightness", 100).toInt())
        SystemStatusLight(settings, status.text("meaning"), status.text("description", 256), pattern)
    }
    return LightingSnapshot(session, version, zones, indicator)
}

/** Schema 2: ordinary dimmable house LEDs; RGB is reserved for the read-only indicator. */
class LightingApi(factory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) : LightingGateway {
    private val http = RackApi(factory)
    override suspend fun fetch(address: String): LightingSnapshot = withContext(Dispatchers.IO) {
        parseLightingSnapshot(http.request(address, "/api/lights"))
    }
    override suspend fun update(address: String, update: LightingUpdate): LightingAcknowledgement = withContext(Dispatchers.IO) {
        require(update.expectedRevision in 0 until MAX_JSON_INTEGER)
        require(lightingToken.matches(update.deviceSession))
        require(lightingToken.matches(update.requestId))
        val body = JSONObject().put("requestId", update.requestId).put("deviceSession", update.deviceSession)
            .put("expectedRevision", update.expectedRevision).put("power", update.settings.power)
            .put("brightness", update.settings.brightness)
        val response = JSONObject(http.request(address, "/api/lights/zones/${update.zone.id}", body.toString()))
        if (response.opt("accepted") != true) {
            val reason = response.optJSONObject("error")?.optString("message")?.take(256)
            throw IOException(reason?.takeIf { it.isNotBlank() } ?: "The device rejected this light change.")
        }
        val ack = LightingAcknowledgement(response.token("requestId"), LightingZone.fromId(response.text("zone")),
            response.token("deviceSession"), response.integer("revision"))
        require(ack.requestId == update.requestId && ack.zone == update.zone && ack.deviceSession == update.deviceSession &&
            ack.revision > update.expectedRevision) { "Lighting acknowledgement does not match this zone change." }
        ack
    }
}
