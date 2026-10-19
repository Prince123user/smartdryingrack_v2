package com.example.smartdryingrack.data.hardware.rest

import java.net.SocketTimeoutException
import java.net.ConnectException
import org.json.JSONException

/** Actionable, bounded messages; never expose response bodies or socket internals. */
internal fun connectionFailureMessage(error: Exception): String = when (error) {
    is RackWifiUnavailableException -> "Wi-Fi is unavailable. Join the same Wi-Fi network as your rack. Internet access is not required."
    is SocketTimeoutException, is ConnectException -> "The rack did not respond. Check its power and current IP address, and keep both devices on the same Wi-Fi."
    is RackHttpException -> when (error.statusCode) {
        503 -> "The ESP32 responded, but its status service is unavailable. Check the sensor diagnostics and firmware Serial Monitor."
        404 -> "The device responded, but its rack status endpoint was not found. Check the address and compatible firmware."
        else -> "The device responded with HTTP ${error.statusCode}. Check its firmware status service."
    }
    is JSONException, is IllegalArgumentException -> "The device responded with incompatible status data. Check that the ESP32 is running app-compatible firmware."
    else -> "The connection was interrupted. Check rack power, Wi-Fi and the saved address."
}
