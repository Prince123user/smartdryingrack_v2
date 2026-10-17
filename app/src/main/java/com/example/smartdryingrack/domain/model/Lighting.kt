package com.example.smartdryingrack.domain.model

/** Only these six IDs can appear in a manual lighting request. The status light is not a zone. */
enum class LightingZone(val id: String, val label: String) {
    FRONT("front", "Front"), LEFT("left", "Left side"), RIGHT("right", "Right side"),
    BACK("back", "Back"), CR("cr", "CR"), BALCONY("balcony", "Balcony");
    companion object {
        fun fromId(id: String): LightingZone = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown house-light zone: $id")
    }
}

data class Rgb(val r: Int, val g: Int, val b: Int) {
    init { require(listOf(r, g, b).all { it in 0..255 }) }
    val hex: String get() = String.format(java.util.Locale.ROOT, "#%02X%02X%02X", r, g, b)
}

/** Ordinary single-color house LEDs have no RGB setting. */
data class LightSettings(val power: Boolean, val brightness: Int) {
    init { require(brightness in 0..100) }
    val outputDescription: String get() = when {
        !power -> "Off · brightness retained"
        brightness == 0 -> "On · 0% brightness — no visible output"
        else -> "On · $brightness% brightness"
    }
}

/** RGB belongs only to the automatically controlled indicator. */
data class StatusLightSettings(val power: Boolean, val rgb: Rgb, val brightness: Int) {
    init { require(brightness in 0..100) }
    val outputDescription: String get() = when {
        !power -> "Off"
        brightness == 0 || rgb == Rgb(0, 0, 0) -> "No visible output"
        else -> "On · $brightness% brightness"
    }
}

data class ConfirmedZone(val settings: LightSettings, val revision: Long, val lastRequestId: String?, val available: Boolean = true)
data class SystemStatusLight(val settings: StatusLightSettings, val meaning: String, val description: String, val pattern: String)
data class LightingSnapshot(val deviceSession: String, val version: Long,
                            val zones: Map<LightingZone, ConfirmedZone>, val indicator: SystemStatusLight?)
data class LightingUpdate(val zone: LightingZone, val deviceSession: String, val expectedRevision: Long,
                          val requestId: String, val settings: LightSettings)
data class LightingAcknowledgement(val requestId: String, val zone: LightingZone, val deviceSession: String, val revision: Long)
