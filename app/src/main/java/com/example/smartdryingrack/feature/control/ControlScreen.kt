package com.example.smartdryingrack.feature.control

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import com.example.smartdryingrack.core.designsystem.MovementAction
import com.example.smartdryingrack.core.designsystem.NeonFeatureRow
import com.example.smartdryingrack.core.designsystem.NeonToggleRow
import com.example.smartdryingrack.core.designsystem.RackHero
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.R
import com.example.smartdryingrack.core.designsystem.AppColors
import com.example.smartdryingrack.core.designsystem.Dimens
import com.example.smartdryingrack.core.designsystem.EmergencyStopButton
import com.example.smartdryingrack.core.designsystem.FuturisticToggle
import com.example.smartdryingrack.core.designsystem.GlassCard
import com.example.smartdryingrack.core.designsystem.ModeSelector
import com.example.smartdryingrack.core.designsystem.RackControlButton
import com.example.smartdryingrack.core.designsystem.ScreenColumn
import com.example.smartdryingrack.domain.model.RackAction
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.RackMode
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.RejectReason
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ControlRoute(onHouseLights: () -> Unit = {}, onConnectionSettings: () -> Unit = {}, viewModel: ControlViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ControlScreen(state, viewModel, onHouseLights, onConnectionSettings)
}

@StringRes
private fun RejectReason.res(): Int = when (this) {
    RejectReason.OFFLINE -> R.string.reject_offline
    RejectReason.ALREADY_THERE -> R.string.reject_already
    RejectReason.BUSY -> R.string.reject_busy
    RejectReason.ERROR -> R.string.reject_error
    RejectReason.EMERGENCY_HOLD -> R.string.reject_hold
    RejectReason.NOT_A_COMMAND -> R.string.reject_none
}

@Composable
fun ControlScreen(state: ControlUiState, actions: ControlActions, onHouseLights: () -> Unit = {}, onConnectionSettings: () -> Unit = {}) {
    val online = state.connection == ConnectionState.ONLINE
    ScreenColumn {
        ModeSelector(state.mode, actions::setMode, Modifier.fillMaxWidth())
        NeonFeatureRow("House Lights", "Control six independent dimmable LEDs", Icons.Outlined.Lightbulb, onHouseLights)

        if (!online) {
            GlassCard(Modifier.fillMaxWidth(), AppColors.Warning) {
                Text(if (state.pendingMovement != null) "Connection lost · movement outcome unknown" else "Rack connection unavailable",
                    color = AppColors.Warning, fontWeight = FontWeight.Bold)
                Text("Current movement and position cannot be verified. Check the rack before retrying a command.",
                    color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onConnectionSettings) { Text("Connection settings") }
            }
        }
        RackHero(if (online) state.rackState else RackState.UNKNOWN)
        if (online && state.pendingMovement != null) {
            Text("Movement accepted. Waiting for updated device state.", color = AppColors.Primary)
        } else if (online && state.rackState in setOf(RackState.UNKNOWN, RackState.ERROR)) {
            Text("Position is unknown or faulted. Inspect your rack and reconnect before moving it.", color = AppColors.Warning)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MovementAction("Extend", Icons.Outlined.KeyboardDoubleArrowUp,
                state.availability.extend == null, { actions.send(RackAction.EXTEND) }, Modifier.weight(1f))
            MovementAction("Retract", Icons.Outlined.KeyboardDoubleArrowDown,
                state.availability.retract == null, { actions.send(RackAction.RETRACT) }, Modifier.weight(1f))
        }
        NeonToggleRow("Buzzer", when {
            !online -> "Connection unavailable · reconnect to confirm sound setting"
            state.buzzerAvailable == false -> "Buzzer output unavailable · check device diagnostics"
            state.mode == RackMode.AUTO && state.buzzerAutoRequired == true -> "AUTO · movement sound always on. Switch to MANUAL to choose sound."
            state.mode == RackMode.AUTO -> "Update firmware to 1.1.6+ for mandatory AUTO movement sound."
            state.buzzerUpdating -> "Waiting for device confirmation…"
            state.buzzerEnabled == null -> "Requires updated ESP32 firmware (1.1.0+)"
            state.buzzerEnabled -> "Sound enabled · toggle on to test a short beep"
            else -> "Sound disabled"
        }, Icons.AutoMirrored.Outlined.VolumeUp, checked = state.buzzerAvailable != false &&
                ((state.mode == RackMode.AUTO && state.buzzerAutoRequired == true) || state.buzzerEnabled == true),
            onChange = actions::setBuzzerEnabled,
            enabled = state.mode == RackMode.MANUAL && state.connection == ConnectionState.ONLINE && state.buzzerEnabled != null && state.buzzerAvailable != false && !state.buzzerUpdating)
        state.deviceMessage?.let { Text(it, color = AppColors.Warning, style = MaterialTheme.typography.bodySmall) }
        state.rainRaw?.let { Text("${if (online) "Rain sensor raw" else "Last rain sensor raw (stale)"}: $it · check dry/wet calibration",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary) }
        state.motionReason?.let { Text("Device movement reason: " + it.lowercase().replace('_', ' '),
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary) }
        RackControlButton("Stop", { actions.send(RackAction.STOP) },
            enabled = state.availability.stop == null, modifier = Modifier.fillMaxWidth(), accent = AppColors.Warning)

        EmergencyStopButton(onClick = actions::emergencyStop, modifier = Modifier.fillMaxWidth())

        state.rejection?.let {
            Text(stringResource(it.res()), color = AppColors.Warning, style = MaterialTheme.typography.bodyMedium)
        }

        NeonFeatureRow("Rain protection enabled",
            "Confirmed rain requests retraction. Firmware safety remains independent.", Icons.Outlined.Umbrella)
        ScheduleCard(state, actions)
        Text("The emergency stop sends a Wi-Fi request. Use the physical stop if communication fails.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(state: ControlUiState, actions: ControlActions) {
    var editing by remember { mutableStateOf<Boolean?>(null) }
    val schedule = state.schedule
    val formatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    GlassCard(Modifier.fillMaxWidth()) {
        Text("Schedule", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FuturisticToggle("Schedule Enabled", schedule.enabled) { actions.setScheduleEnabled(it) }
        TimeRow("Rack Out", formatter.format(schedule.extendTime)) { editing = true }
        TimeRow("Rack In", formatter.format(schedule.retractTime)) { editing = false }
        Spacer(Modifier.height(8.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { day ->
                val full = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                FilterChip(
                    selected = schedule.isDayEnabled(day),
                    onClick = { actions.setDay(day, !schedule.isDayEnabled(day)) },
                    label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                    modifier = Modifier.semantics { contentDescription = full }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!schedule.extendTime.isBefore(schedule.retractTime)) {
            Text("Rack-out time must be earlier than rack-in time.", color = AppColors.Danger)
        }
        Text("Schedules run while the app process is active. Configure firmware schedules for protection when your phone is unavailable.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }

    editing?.let { isExtend ->
        TimeDialog(
            initial = if (isExtend) schedule.extendTime else schedule.retractTime,
            onDismiss = { editing = null },
            onConfirm = {
                if (isExtend) actions.setExtendTime(it) else actions.setRetractTime(it)
                editing = null
            }
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.TextPrimary)
        Text(value, color = AppColors.Primary, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = picker) }
    )
}
