package com.example.smartdryingrack.feature.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdryingrack.data.repository.RackRepository
import com.example.smartdryingrack.data.repository.ScheduleRepository
import com.example.smartdryingrack.data.repository.SettingsRepository
import com.example.smartdryingrack.domain.model.AutomationSettings
import com.example.smartdryingrack.domain.model.CommandResult
import com.example.smartdryingrack.domain.model.CommandSource
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.ControlAvailability
import com.example.smartdryingrack.domain.model.LastCommand
import com.example.smartdryingrack.domain.model.RackAction
import com.example.smartdryingrack.domain.model.RackMode
import com.example.smartdryingrack.domain.model.RackSchedule
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.RejectReason
import com.example.smartdryingrack.domain.model.computeAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

data class ControlUiState(
    val mode: RackMode = RackMode.MANUAL,
    val rackState: RackState = RackState.UNKNOWN,
    val connection: ConnectionState = ConnectionState.OFFLINE,
    val lastCommand: LastCommand? = null,
    val lastUpdated: Long? = null,
    val availability: ControlAvailability = computeAvailability(RackState.UNKNOWN, RackMode.MANUAL, ConnectionState.OFFLINE),
    val schedule: RackSchedule = RackSchedule(),
    val settings: AutomationSettings = AutomationSettings(),
    val pendingMovement: RackState? = null,
    val buzzerEnabled: Boolean? = null,
    val buzzerUpdating: Boolean = false,
    val buzzerAutoRequired: Boolean? = null,
    val deviceMessage: String? = null,
    val rainRaw: Int? = null,
    val motionReason: String? = null,
    val rejection: RejectReason? = null
)

interface ControlActions {
    fun setBuzzerEnabled(enabled: Boolean)
    fun setMode(mode: RackMode)
    fun send(action: RackAction)
    fun emergencyStop()
    fun setScheduleEnabled(enabled: Boolean)
    fun setExtendTime(time: LocalTime)
    fun setRetractTime(time: LocalTime)
    fun setDay(day: DayOfWeek, enabled: Boolean)
    fun setUseWeather(enabled: Boolean)
}

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val rack: RackRepository,
    private val schedules: ScheduleRepository,
    private val settings: SettingsRepository,
    private val controller: com.example.smartdryingrack.data.hardware.rest.WifiRackController
) : ViewModel(), ControlActions {

    private val rejection = MutableStateFlow<RejectReason?>(null)

    private val base = combine(
        rack.mode, rack.rackState, rack.connection, rack.lastCommand, rack.sensorData
    ) { mode, state, conn, last, sensor ->
        ControlUiState(
            mode = mode,
            rackState = state,
            connection = conn,
            lastCommand = last,
            lastUpdated = sensor?.timestamp,
            availability = computeAvailability(state, mode, conn)
        )
    }

    private val buzzerUpdating = MutableStateFlow(false)
    private val deviceMessage = MutableStateFlow<String?>(null)
    private data class DeviceControls(val buzzer: Boolean?, val updating: Boolean, val message: String?, val raw: Int?, val reason: String?, val autoRequired: Boolean?)
    private val settingsMessage = combine(deviceMessage, rack.modeMessage, controller.message) { local, mode, connection -> connection ?: mode ?: local }
    private val buzzerPolicy = combine(controller.buzzerEnabled, controller.buzzerAutoRequired) { enabled, auto -> enabled to auto }
    private val deviceControls = combine(buzzerPolicy, buzzerUpdating, settingsMessage, controller.rainRaw, controller.motionReason) { buzzer, updating, message, raw, reason ->
        DeviceControls(buzzer.first, updating, message, raw, reason, buzzer.second)
    }
    private val controls =
        combine(base, schedules.schedule, settings.settings, rejection, rack.pendingMovement) { b, sch, set, rej, pending ->
            b.copy(schedule = sch, settings = set, rejection = rej, pendingMovement = pending,
                availability = if (pending == null) b.availability else b.availability.copy(
                    extend = com.example.smartdryingrack.domain.model.Unavailable.MOVING,
                    retract = com.example.smartdryingrack.domain.model.Unavailable.MOVING))
        }
    val uiState: StateFlow<ControlUiState> = combine(controls, deviceControls) { state, device ->
        state.copy(buzzerEnabled = device.buzzer, buzzerAutoRequired = device.autoRequired, buzzerUpdating = device.updating, deviceMessage = device.message,
            rainRaw = device.raw, motionReason = device.reason)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ControlUiState())

    override fun setBuzzerEnabled(enabled: Boolean) {
        if (buzzerUpdating.value || rack.mode.value == RackMode.AUTO) return
        buzzerUpdating.value = true
        deviceMessage.value = null
        viewModelScope.launch {
            try { controller.setBuzzer(enabled) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                deviceMessage.value = "Buzzer setting not confirmed. " + com.example.smartdryingrack.data.hardware.rest.connectionFailureMessage(e)
            } finally { buzzerUpdating.value = false }
        }
    }

    override fun setMode(mode: RackMode) {
        rejection.value = null
        rack.setMode(mode)
    }

    override fun send(action: RackAction) = dispatch(action, CommandSource.MANUAL)

    override fun emergencyStop() = dispatch(RackAction.STOP, CommandSource.EMERGENCY)

    private fun dispatch(action: RackAction, source: CommandSource) {
        viewModelScope.launch {
            rejection.value = (rack.send(action, source) as? CommandResult.Rejected)?.reason
        }
    }

    override fun setScheduleEnabled(enabled: Boolean) = schedules.update { it.copy(enabled = enabled) }
    override fun setExtendTime(time: LocalTime) = schedules.update { it.copy(extendTime = time) }
    override fun setRetractTime(time: LocalTime) = schedules.update { it.copy(retractTime = time) }
    override fun setDay(day: DayOfWeek, enabled: Boolean) = schedules.update { it.withDay(day, enabled) }
    override fun setUseWeather(enabled: Boolean) = settings.update { it.copy(useWeather = enabled) }
}
