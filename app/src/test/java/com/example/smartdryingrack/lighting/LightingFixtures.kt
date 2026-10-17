package com.example.smartdryingrack.lighting

import com.example.smartdryingrack.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

// Simulated device data used only in tests; never a production fallback.
fun lightingFixture() = LightingSnapshot("test-boot", 0,
    LightingZone.entries.associateWith { ConfirmedZone(LightSettings(true, 70), 0, null) },
    SystemStatusLight(StatusLightSettings(true, Rgb(0, 255, 0), 30), "test_normal", "Simulated normal operation", "solid"))
fun snapshotJson(): JSONObject {
    fun settings(value: LightSettings) = JSONObject().put("power", value.power).put("brightness", value.brightness)

    val fixture = lightingFixture()
    return JSONObject().put("schemaVersion", 2).put("deviceSession", fixture.deviceSession).put("stateVersion", 0)
        .put("zones", JSONArray(LightingZone.entries.map { settings(fixture.zones.getValue(it).settings)
            .put("id", it.id).put("revision", 0).put("lastRequestId", JSONObject.NULL) }))
        .put("indicator", JSONObject().put("power", true).put("brightness", 30)
            .put("rgb", JSONObject().put("r", 0).put("g", 255).put("b", 0)).put("id", "system_status").put("mode", "AUTOMATIC")
            .put("meaning", "test_normal").put("description", "Simulated normal operation").put("pattern", "solid"))
}
