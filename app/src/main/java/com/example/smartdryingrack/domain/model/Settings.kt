package com.example.smartdryingrack.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

data class AutomationSettings(
    val rainSensitivity: Float = 0.4f,
    val dryThresholdPercent: Int = 90,
    val useWeather: Boolean = false,
    val buzzer: Boolean = true,
    val clothesDryAlert: Boolean = true,
    val pushNotifications: Boolean = true,
    val rainAlerts: Boolean = true,
    val forecastAlerts: Boolean = true,
    val scheduleAlerts: Boolean = true,
    val darkTheme: Boolean = true,
    val deviceName: String = "Smart Drying Rack Pro",
    val firmwareVersion: String = "Unknown"
)

data class RackSchedule(
    val enabled: Boolean = false,
    val extendTime: LocalTime = LocalTime.of(8, 0),
    val retractTime: LocalTime = LocalTime.of(17, 0),
    val monday: Boolean = true,
    val tuesday: Boolean = true,
    val wednesday: Boolean = true,
    val thursday: Boolean = true,
    val friday: Boolean = true,
    val saturday: Boolean = false,
    val sunday: Boolean = false
) {
    fun isDayEnabled(day: DayOfWeek): Boolean = when (day) {
        DayOfWeek.MONDAY -> monday
        DayOfWeek.TUESDAY -> tuesday
        DayOfWeek.WEDNESDAY -> wednesday
        DayOfWeek.THURSDAY -> thursday
        DayOfWeek.FRIDAY -> friday
        DayOfWeek.SATURDAY -> saturday
        DayOfWeek.SUNDAY -> sunday
    }

    fun withDay(day: DayOfWeek, value: Boolean): RackSchedule = when (day) {
        DayOfWeek.MONDAY -> copy(monday = value)
        DayOfWeek.TUESDAY -> copy(tuesday = value)
        DayOfWeek.WEDNESDAY -> copy(wednesday = value)
        DayOfWeek.THURSDAY -> copy(thursday = value)
        DayOfWeek.FRIDAY -> copy(friday = value)
        DayOfWeek.SATURDAY -> copy(saturday = value)
        DayOfWeek.SUNDAY -> copy(sunday = value)
    }

    /** true = rack should be out, false = should be in, null = schedule has no opinion. */
    fun wantsRackOut(now: LocalDateTime): Boolean? {
        if (!enabled || !isDayEnabled(now.dayOfWeek) || !extendTime.isBefore(retractTime)) return null
        val t = now.toLocalTime()
        return !t.isBefore(extendTime) && t.isBefore(retractTime)
    }
}
