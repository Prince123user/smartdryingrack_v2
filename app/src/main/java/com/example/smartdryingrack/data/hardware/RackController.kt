package com.example.smartdryingrack.data.hardware

import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.SensorData
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app knows about the physical rack goes through this interface.
 * Implementations: MockRackController now; Wi-Fi (REST/MQTT) and BLE later.
 */
interface RackController {
    suspend fun extend()
    suspend fun retract()
    suspend fun stop()
    fun observeRackState(): Flow<RackState>
    fun observeSensorData(): Flow<SensorData?>
    fun observeConnection(): Flow<ConnectionState>
    suspend fun connect()
    suspend fun disconnect()
    suspend fun isConnected(): Boolean
}

/** Demo-mode hooks. Only the mock controller implements this. */
interface DeviceSimulator {
    fun setRain(on: Boolean)
    fun setClothesDry(percent: Int)
    fun setSensorError(on: Boolean)
    suspend fun setOffline(offline: Boolean)
}

/** Optional device-authoritative settings; legacy/mock controllers keep local phone mode. */
interface DeviceRackSettings {
    val canAttemptStop: Boolean get() = false
    val deviceMode: kotlinx.coroutines.flow.StateFlow<com.example.smartdryingrack.domain.model.RackMode?>
    val modeSupported: kotlinx.coroutines.flow.StateFlow<Boolean>
    val stopLatched: kotlinx.coroutines.flow.StateFlow<Boolean?>
    suspend fun setDeviceMode(mode: com.example.smartdryingrack.domain.model.RackMode)
}
