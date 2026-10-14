package com.example.smartdryingrack.automation

/** Confirms (or clears) rain only after [required] consecutive matching readings. */
class RainDebouncer(private val required: Int = 3) {
    private var rainCount = 0
    private var dryCount = 0
    var confirmed = false
        private set

    fun permitsRainRetraction(currentWet: Boolean): Boolean = confirmed && currentWet

    fun reset() { rainCount = 0; dryCount = 0; confirmed = false }

    fun onReading(rain: Boolean): Boolean {
        if (rain) {
            rainCount++
            dryCount = 0
            if (rainCount >= required) confirmed = true
        } else {
            dryCount++
            rainCount = 0
            if (dryCount >= required) confirmed = false
        }
        return confirmed
    }
}
