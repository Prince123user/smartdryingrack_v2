package com.example.smartdryingrack.lighting

import com.example.smartdryingrack.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

// Simulated device data used only in tests; never a production fallback.
fun lightingFixture() = LightingSnapshot("test-boot", 0,
    LightingZone.entries.associateWith { ConfirmedZone(LightSettings(true, Rgb(32, 160, 220), 70), 0, null) },
    SystemStatusLight(LightSettings(true, Rgb(0, 255, 0), 30), "test_normal", "Simulated normal operation", "solid"))
fun snapshotJson(): JSONObject {
    fun settings(value: LightSettings) = JSONObject().put("power", value.power).put("brightness", value.brightness)
        .put("rgb", JSONObject().put("r", value.rgb.r).put("g", value.rgb.g).put("b", value.rgb.b))
    val fixture = lightingFixture()
    return JSONObject().put("schemaVersion", 1).put("deviceSession", fixture.deviceSession).put("stateVersion", 0)
        .put("zones", JSONArray(LightingZone.entries.map { settings(fixture.zones.getValue(it).settings)
            .put("id", it.id).put("revision", 0).put("lastRequestId", JSONObject.NULL) }))
        .put("indicator", settings(fixture.indicator.settings).put("id", "system_status").put("mode", "AUTOMATIC")
            .put("meaning", "test_normal").put("description", "Simulated normal operation").put("pattern", "solid"))
}
