package com.example.smartdryingrack.data.hardware.rest

import com.example.smartdryingrack.data.hardware.RackController
import com.example.smartdryingrack.data.repository.DevicePreferences
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.SensorData
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiRackController internal constructor(
    private val preferences: DevicePreferences,
    private val api: RackApi,
    private val scope: CoroutineScope
) : RackController {
    @Inject constructor(preferences: DevicePreferences) : this(
        preferences, RackApi(), CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )
    private val state = MutableStateFlow(RackState.UNKNOWN)
    private val sensors = MutableStateFlow<SensorData?>(null)
    private val connection = MutableStateFlow(ConnectionState.OFFLINE)
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _firmware = MutableStateFlow<String?>(null)
    val firmware = _firmware.asStateFlow()
    private var polling: Job? = null
    @Volatile private var generation = 0L
    @Volatile private var activeAddress = ""
    private val lifecycle = Mutex()

    override fun observeRackState() = state.asStateFlow()
    override fun observeSensorData() = sensors.asStateFlow()
    override fun observeConnection() = connection.asStateFlow()
    override suspend fun isConnected() = connection.value == ConnectionState.ONLINE

    override suspend fun connect() = lifecycle.withLock {
        reset()
        val address = preferences.address.value
        if (address.isBlank()) {
            _message.value = "Add your rack's Wi-Fi address to connect."
            return@withLock
        }
        connection.value = ConnectionState.CONNECTING
        activeAddress = address
        val session = generation
        polling = scope.launch {
            while (isActive && session == generation) {
                try {
                    val snapshot = api.status(address)
                    if (session != generation) return@launch
                    state.value = snapshot.state
                    sensors.value = snapshot.sensors
                    _firmware.value = snapshot.firmware
                    _message.value = null
                    connection.value = ConnectionState.ONLINE
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (session != generation) return@launch
                    state.value = RackState.UNKNOWN
                    connection.value = ConnectionState.ERROR
                    _message.value = "Unable to read the rack. Check Wi-Fi, the address and firmware API."
                }
                delay(if (connection.value == ConnectionState.ONLINE) 2000L else 5000L)
            }
        }
    }

    override suspend fun disconnect() = lifecycle.withLock { reset() }

    private fun reset() {
        generation++
        activeAddress = ""
        polling?.cancel()
        polling = null
        connection.value = ConnectionState.OFFLINE
        state.value = RackState.UNKNOWN
        sensors.value = null
        _firmware.value = null
    }

    private suspend fun send(action: String) = withContext(Dispatchers.IO) {
        check(connection.value == ConnectionState.ONLINE) { "Rack is offline." }
        val session = generation
        val address = activeAddress
        try {
            api.command(address, action)
            // Acceptance is not completion: physical position always comes from telemetry.
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (session == generation) {
                connection.value = ConnectionState.ERROR
                _message.value = "Command could not be confirmed. Check the rack before retrying."
            }
            throw e
        }
    }

    override suspend fun extend() = send("EXTEND")
    override suspend fun retract() = send("RETRACT")
    override suspend fun stop() = send("STOP")
}
