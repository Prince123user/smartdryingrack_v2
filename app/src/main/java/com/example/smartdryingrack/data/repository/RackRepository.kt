package com.example.smartdryingrack.data.repository

import com.example.smartdryingrack.data.hardware.RackController
import com.example.smartdryingrack.data.hardware.DeviceRackSettings
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import com.example.smartdryingrack.domain.model.CommandResult
import com.example.smartdryingrack.domain.model.CommandSource
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.EventType
import com.example.smartdryingrack.domain.model.LastCommand
import com.example.smartdryingrack.domain.model.RackAction
import com.example.smartdryingrack.domain.model.RackMode
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.RejectReason
import com.example.smartdryingrack.domain.model.SensorData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single gateway for rack commands. Enforces: one command at a time, no repeated or
 * conflicting commands, priority between sources, movement timeout, and emergency hold.
 */
@Singleton
class RackRepository @Inject constructor(
    private val controller: RackController,
    private val events: EventLog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val fault = MutableStateFlow(false)
    private var emergencyHold = false
    private var movementWatch: Job? = null
    private val _pendingMovement = MutableStateFlow<RackState?>(null)
    val pendingMovement = _pendingMovement.asStateFlow()
    private var pendingTarget: RackState?
        get() = _pendingMovement.value
        set(value) { _pendingMovement.value = value }
    private var movementVersion = 0L

    private val _mode = MutableStateFlow(RackMode.MANUAL)
    val mode: StateFlow<RackMode> = _mode.asStateFlow()
    private val deviceSettings = controller as? DeviceRackSettings
    private val modeUpdating = MutableStateFlow(false)
    private val _modeMessage = MutableStateFlow<String?>(null)
    val modeMessage = _modeMessage.asStateFlow()
    init {
        deviceSettings?.let { settings -> scope.launch {
            settings.deviceMode.filterNotNull().collect { _mode.value = it }
        } }
    }


    private val _lastCommand = MutableStateFlow<LastCommand?>(null)
    val lastCommand: StateFlow<LastCommand?> = _lastCommand.asStateFlow()

    private val _rainConfirmed = MutableStateFlow(false)
    val rainConfirmed: StateFlow<Boolean> = _rainConfirmed.asStateFlow()

    val rackState: StateFlow<RackState> =
        combine(controller.observeRackState(), fault) { state, faulted ->
            if (faulted) RackState.ERROR else state
        }.stateIn(scope, SharingStarted.Eagerly, RackState.UNKNOWN)

    val sensorData: StateFlow<SensorData?> =
        controller.observeSensorData().stateIn(scope, SharingStarted.Eagerly, null)

    val connection: StateFlow<ConnectionState> =
        controller.observeConnection().stateIn(scope, SharingStarted.Eagerly, ConnectionState.OFFLINE)

    suspend fun connect() = mutex.withLock {
        movementWatch?.cancel()
        movementVersion++
        pendingTarget = null
        fault.value = false
        controller.connect()
    }

    fun setMode(mode: RackMode) {
        val settings = deviceSettings
        if (settings == null || !settings.modeSupported.value) {
            _mode.value = mode // Legacy controllers only support phone automation.
            return
        }
        if (!modeUpdating.compareAndSet(false, true)) return
        _modeMessage.value = "Updating ESP32 mode…"
        scope.launch {
            try {
                settings.setDeviceMode(mode)
                _modeMessage.value = null // Mode itself changes only through confirmed device state.
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _modeMessage.value = "ESP32 mode was not confirmed. Check the connection and try again."
            } finally { modeUpdating.value = false }
        }
    }

    fun setRainConfirmed(value: Boolean) { _rainConfirmed.value = value }

    suspend fun send(action: RackAction, source: CommandSource): CommandResult =
        mutex.withLock { sendLocked(action, source) }

    private suspend fun sendLocked(action: RackAction, source: CommandSource): CommandResult {
        if (action != RackAction.EXTEND && action != RackAction.RETRACT && action != RackAction.STOP) {
            return CommandResult.Rejected(RejectReason.NOT_A_COMMAND)
        }
        if (action == RackAction.STOP && source == CommandSource.EMERGENCY) emergencyHold = true
        if (connection.value != ConnectionState.ONLINE && !(action == RackAction.STOP && deviceSettings?.canAttemptStop == true)) {
            return CommandResult.Rejected(RejectReason.OFFLINE)
        }

        if (action == RackAction.STOP) {
            try {
                controller.stop()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                events.record(EventType.DEVICE_ERROR)
                return CommandResult.Rejected(RejectReason.ERROR)
            }
            movementWatch?.cancel()
            movementVersion++
            pendingTarget = null
            if (source == CommandSource.EMERGENCY) {
                emergencyHold = true
                events.record(EventType.EMERGENCY_STOP)
            }
            _lastCommand.value = LastCommand(action, source, System.currentTimeMillis())
            return CommandResult.Accepted
        }

        if (modeUpdating.value) return CommandResult.Rejected(RejectReason.BUSY)
        if (source != CommandSource.MANUAL && deviceSettings?.stopLatched?.value == true) {
            return CommandResult.Rejected(RejectReason.EMERGENCY_HOLD)
        }
        // Firmware owns rain protection when it exposes runtime mode/config settings.
        // Avoid a phone rain command stopping/restarting its own automatic movement.
        if (source == CommandSource.RAIN && deviceSettings?.modeSupported?.value == true) {
            return CommandResult.Rejected(RejectReason.ALREADY_THERE)
        }
        val sensor = sensorData.value
        if (action == RackAction.RETRACT && source == CommandSource.RAIN &&
            (sensor == null || !sensor.isValid || System.currentTimeMillis() - sensor.timestamp !in 0..6000 ||
                !sensor.rainDetected || !rainConfirmed.value)) {
            return CommandResult.Rejected(RejectReason.ERROR)
        }
        if (action == RackAction.EXTEND && (sensor == null || !sensor.isValid ||
                System.currentTimeMillis() - sensor.timestamp !in 0..6000 || sensor.rainDetected || rainConfirmed.value)) {
            return CommandResult.Rejected(RejectReason.ERROR)
        }
        if (rackState.value in setOf(RackState.UNKNOWN, RackState.ERROR)) return CommandResult.Rejected(RejectReason.ERROR)
        if (source == CommandSource.MANUAL && (deviceSettings?.deviceMode?.value ?: mode.value) != RackMode.MANUAL) return CommandResult.Rejected(RejectReason.BUSY)
        if (pendingTarget != null && source.ordinal > CommandSource.RAIN.ordinal) return CommandResult.Rejected(RejectReason.BUSY)

        if (emergencyHold && source != CommandSource.MANUAL) {
            return CommandResult.Rejected(RejectReason.EMERGENCY_HOLD)
        }

        val state = rackState.value
        val target = if (action == RackAction.EXTEND) RackState.EXTENDED else RackState.RETRACTED
        val sameMotion = (action == RackAction.EXTEND && state == RackState.EXTENDING) ||
            (action == RackAction.RETRACT && state == RackState.RETRACTING)
        if ((state == target && pendingTarget == null) || sameMotion && pendingTarget == target) return CommandResult.Rejected(RejectReason.ALREADY_THERE)

        val moving = pendingTarget != null || state == RackState.EXTENDING || state == RackState.RETRACTING
        try {
            if (moving) {
                if (source.ordinal <= CommandSource.RAIN.ordinal) controller.stop()
                else return CommandResult.Rejected(RejectReason.BUSY)
            }
            fault.value = false
            if (action == RackAction.EXTEND) controller.extend() else controller.retract()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            fault.value = true
            emergencyHold = true
            events.record(EventType.DEVICE_ERROR)
            return CommandResult.Rejected(RejectReason.ERROR)
        }

        if (source == CommandSource.MANUAL) emergencyHold = false
        _lastCommand.value = LastCommand(action, source, System.currentTimeMillis())
        eventFor(action, source)?.let { events.record(it) }
        watchMovement(target, System.currentTimeMillis())
        return CommandResult.Accepted
    }

    private fun watchMovement(target: RackState, acceptedAt: Long) {
        movementWatch?.cancel()
        pendingTarget = target
        val version = ++movementVersion
        movementWatch = scope.launch {
            val reached = withTimeoutOrNull(MOVE_TIMEOUT_MS) {
                combine(controller.observeRackState(), controller.observeSensorData()) { state, sample ->
                    state == target && sample != null && sample.timestamp > acceptedAt
                }.first { it }
            }
            mutex.withLock {
                if (version != movementVersion) return@withLock
                pendingTarget = null
                if (reached == null) {
                    try { controller.stop() } catch (e: Exception) { if (e is CancellationException) throw e }
                    fault.value = true
                    events.record(EventType.DEVICE_ERROR)
                }
            }
        }
    }

    private fun eventFor(action: RackAction, source: CommandSource): EventType? = when (source) {
        CommandSource.MANUAL ->
            if (action == RackAction.EXTEND) EventType.MANUAL_EXTEND else EventType.MANUAL_RETRACT
        CommandSource.RAIN -> EventType.RAIN_RETRACT
        CommandSource.WEATHER -> if (action == RackAction.RETRACT) EventType.FORECAST_RETRACT else null
        CommandSource.SCHEDULE ->
            if (action == RackAction.EXTEND) EventType.SCHEDULE_EXTEND else EventType.SCHEDULE_RETRACT
        else -> null
    }

    private companion object {
        const val MOVE_TIMEOUT_MS = 10_000L
    }
}
