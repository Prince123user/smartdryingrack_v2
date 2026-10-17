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
import kotlinx.coroutines.flow.collect
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class RackConnectionSession(val address: String, val generation: Long)

@Singleton
class WifiRackController internal constructor(
    private val preferences: DevicePreferences,
    private val api: RackApi,
    private val scope: CoroutineScope
) : RackController {
    @Inject constructor(preferences: DevicePreferences, transport: WifiTransport) : this(
        preferences, RackApi(transport::openConnection), CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ) {
        scope.launch { transport.changes.collect { resumeRequestedConnection(force = true) } }
    }
    private val state = MutableStateFlow(RackState.UNKNOWN)
    private val sensors = MutableStateFlow<SensorData?>(null)
    private val connection = MutableStateFlow(ConnectionState.OFFLINE)
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _firmware = MutableStateFlow<String?>(null)
    val firmware = _firmware.asStateFlow()
    private val settingsRequest = Mutex()
    private val _buzzerEnabled = MutableStateFlow<Boolean?>(null)
    val buzzerEnabled = _buzzerEnabled.asStateFlow()
    private val _rainRaw = MutableStateFlow<Int?>(null)
    val rainRaw = _rainRaw.asStateFlow()
    private val _motionReason = MutableStateFlow<String?>(null)
    val motionReason = _motionReason.asStateFlow()
    private val _requestedSession = MutableStateFlow<RackConnectionSession?>(null)
    val requestedSession = _requestedSession.asStateFlow()
    private var polling: Job? = null
    @Volatile private var generation = 0L
    @Volatile private var activeAddress = ""
    private val lifecycle = Mutex()

    override fun observeRackState() = state.asStateFlow()
    override fun observeSensorData() = sensors.asStateFlow()
    override fun observeConnection() = connection.asStateFlow()
    override suspend fun isConnected() = connection.value == ConnectionState.ONLINE

    private var reconnectEnabled = false

    override suspend fun connect() = lifecycle.withLock {
        reconnectEnabled = true
        startPolling()
    }

    /** Network/foreground events revive only a requested connection, never an explicit Disconnect. */
    suspend fun resumeRequestedConnection(force: Boolean = false) = lifecycle.withLock {
        if (reconnectEnabled && (force || connection.value != ConnectionState.ONLINE || polling?.isActive != true)) {
            startPolling()
        }
    }

    private fun startPolling() {
        reset()
        val address = preferences.address.value
        if (address.isBlank()) {
            _message.value = "Add your rack's Wi-Fi address to connect."
            return
        }
        connection.value = ConnectionState.CONNECTING
        activeAddress = address
        val session = generation
        _requestedSession.value = RackConnectionSession(address, session)
        polling = scope.launch {
            while (isActive && session == generation) {
                try {
                    settingsRequest.withLock {
                        val snapshot = api.status(address)
                        if (session != generation) return@launch
                        state.value = snapshot.state
                        sensors.value = snapshot.sensors
                        _firmware.value = snapshot.firmware
                        _buzzerEnabled.value = snapshot.buzzerEnabled
                        _rainRaw.value = snapshot.rainRaw
                        _motionReason.value = snapshot.motionReason
                        _message.value = null
                        connection.value = ConnectionState.ONLINE
                    }
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

    override suspend fun disconnect() = lifecycle.withLock { reconnectEnabled = false; reset() }

    private fun reset() {
        generation++
        activeAddress = ""
        _requestedSession.value = null
        polling?.cancel()
        polling = null
        connection.value = ConnectionState.OFFLINE
        state.value = RackState.UNKNOWN
        sensors.value = null
        _firmware.value = null
        _message.value = null
        _buzzerEnabled.value = null
        _rainRaw.value = null
        _motionReason.value = null
    }

    suspend fun setBuzzer(enabled: Boolean) = withContext(Dispatchers.IO) {
        check(connection.value == ConnectionState.ONLINE) { "Connect the rack before changing its buzzer." }
        check(_buzzerEnabled.value != null) { "Update the ESP32 firmware to enable buzzer control." }
        val session = generation
        val address = activeAddress
        settingsRequest.withLock {
            val confirmed = api.setBuzzer(address, enabled)
            if (session != generation) throw IOException("Connection changed; buzzer setting could not be confirmed.")
            _buzzerEnabled.value = confirmed
        }
    }

    private suspend fun send(action: String) = withContext(Dispatchers.IO) {
        check(connection.value == ConnectionState.ONLINE) { "Rack is offline." }
        val session = generation
        val address = activeAddress
        try {
            api.command(address, action)
            if (session != generation) throw IOException("Connection changed before the command could be confirmed. Check the rack before retrying.")
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
