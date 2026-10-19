package com.example.smartdryingrack.lighting

import com.example.smartdryingrack.data.hardware.rest.*
import com.example.smartdryingrack.domain.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class LightingApiTest {
    @Test fun parsesActualZonesAndReadOnlyIndicator() {
        val snapshot = parseLightingSnapshot(snapshotJson().toString())
        assertEquals(LightingZone.entries.toSet(), snapshot.zones.keys)
        assertEquals("test_normal", snapshot.indicator!!.meaning)
        assertEquals("Simulated normal operation", snapshot.indicator!!.description)
        assertEquals(70, snapshot.zones.getValue(LightingZone.CR).settings.brightness)
    }
    @Test fun rejectsMissingDuplicateUnknownOrStatusManualZones() {
        listOf<(JSONObject) -> Unit>(
            { it.getJSONArray("zones").remove(0) },
            { it.getJSONArray("zones").getJSONObject(1).put("id", "front") },
            { it.getJSONArray("zones").getJSONObject(0).put("id", "attic") },
            { it.getJSONArray("zones").getJSONObject(0).put("id", "system_status") },
            { it.getJSONObject("indicator").put("mode", "MANUAL") },
            { it.getJSONObject("indicator").put("pattern", "unknown") },
            { it.put("schemaVersion", 1) },
            { it.put("deviceSession", "") },
            { it.getJSONArray("zones").getJSONObject(0).put("revision", 1) }
        ).forEach { mutate ->
            val json = snapshotJson(); mutate(json)
            assertThrows(Exception::class.java) { parseLightingSnapshot(json.toString()) }
        }
    }
    @Test fun rejectsCoercedFractionalAndOutOfRangeValues() {
        listOf("brightness" to -1, "brightness" to 101, "brightness" to 0.5,
            "brightness" to "40", "power" to "true", "power" to 1).forEach { (field, value) ->
            val json = snapshotJson(); json.getJSONArray("zones").getJSONObject(0).put(field, value)
            assertThrows(Exception::class.java) { parseLightingSnapshot(json.toString()) }
        }
        val houseRgb = snapshotJson()
        houseRgb.getJSONArray("zones").getJSONObject(0).put("rgb", JSONObject().put("r", 255).put("g", 0).put("b", 0))
        assertThrows(Exception::class.java) { parseLightingSnapshot(houseRgb.toString()) }
        listOf(-1, 256, 1.5, "20").forEach { value ->
            val json = snapshotJson(); json.getJSONObject("indicator").getJSONObject("rgb").put("r", value)
            assertThrows(Exception::class.java) { parseLightingSnapshot(json.toString()) }
        }
    }
    @Test fun unreportedIndicatorIsNotInvented() {
        val json = snapshotJson().put("indicator", JSONObject.NULL)
        assertNull(parseLightingSnapshot(json.toString()).indicator)
    }
    @Test fun deviceReportsZoneAvailabilityWithoutInventingSuccess() {
        val json = snapshotJson()
        json.getJSONArray("zones").getJSONObject(0).put("available", false)
        assertFalse(parseLightingSnapshot(json.toString()).zones.getValue(LightingZone.FRONT).available)
        json.getJSONArray("zones").getJSONObject(0).put("available", "false")
        assertThrows(Exception::class.java) { parseLightingSnapshot(json.toString()) }
    }
    @Test fun parsesSnapshotGeneratedByTheMergedFirmwareHostTest() {
        val body = javaClass.getResource("/firmware-lights-v1.1.2.json")!!.readText()
        val snapshot = parseLightingSnapshot(body)
        assertEquals(6, snapshot.zones.size)
        assertTrue(snapshot.zones.values.all { it.available && !it.settings.power && it.settings.brightness == 100 })
        assertNull(snapshot.indicator) // Logical colors with disabled hardware are not actual RGB state.
    }
    private val update = LightingUpdate(LightingZone.BALCONY, "test-boot", 0, "test-request",
        LightSettings(false, 0))
    private val ack = """{"accepted":true,"requestId":"test-request","zone":"balcony","deviceSession":"test-boot","revision":1}"""
    private fun server(test: suspend (MockWebServer, LightingApi, String) -> Unit) = runBlocking {
        val server = MockWebServer(); server.start(InetAddress.getByName("127.0.0.1"), 0)
        val api = LightingApi { url -> URL("http://127.0.0.1:${url.port}${url.path}").openConnection() as HttpURLConnection }
        try { test(server, api, "http://192.168.1.50:${server.port}") } finally { server.shutdown() }
    }
    @Test fun fetchAndUpdateUseOnlyTheSpecifiedHouseZone() = server { server, api, address ->
        server.enqueue(MockResponse().setBody(snapshotJson().toString()))
        server.enqueue(MockResponse().setResponseCode(202).setBody(ack))
        assertEquals(6, api.fetch(address).zones.size)
        assertEquals("/api/lights", server.takeRequest().path)
        assertEquals(1L, api.update(address, update).revision)
        val request = server.takeRequest()
        assertEquals("POST", request.method); assertEquals("/api/lights/zones/balcony", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(setOf("requestId", "deviceSession", "expectedRevision", "power", "brightness"), body.keySet())
        assertFalse(body.getBoolean("power")); assertEquals(0, body.getInt("brightness"))
        assertFalse(body.has("rgb"))
        assertEquals("no-cache", request.getHeader("Cache-Control"))
    }
    @Test fun wrongZoneSessionRequestRevisionOrAcceptanceCannotConfirmACommand() = server { server, api, address ->
        listOf(ack.replace("balcony", "front"), ack.replace("test-boot", "wrong-boot"),
            ack.replace("test-request", "other-request"), ack.replace("\"revision\":1", "\"revision\":0"),
            ack.replace("true", "\"true\""), """{"accepted":false,"error":{"code":"CONFLICT","message":"Stale revision"}}""")
            .forEach { body ->
                server.enqueue(MockResponse().setBody(body))
                var failed = false
                try { api.update(address, update) } catch (e: Exception) { failed = true }
                assertTrue("Mismatched/rejected acknowledgement must fail", failed)
            }
    }
    @Test fun legacyFirmwareAndHttpRejectionRemainErrors() = server { server, api, address ->
        server.enqueue(MockResponse().setResponseCode(404))
        try { api.fetch(address); fail("Must reject unavailable endpoint") } catch (e: RackHttpException) { assertEquals(404, e.statusCode) }
        server.enqueue(MockResponse().setResponseCode(409))
        try { api.update(address, update); fail("Must reject conflict") } catch (e: RackHttpException) { assertEquals(409, e.statusCode) }
    }
}
