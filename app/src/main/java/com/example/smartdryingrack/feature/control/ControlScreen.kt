package com.example.smartdryingrack.feature.control

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.example.smartdryingrack.core.designsystem.MovementAction
import com.example.smartdryingrack.core.designsystem.ScreenHeading
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.example.smartdryingrack.core.designsystem.RackStatusCard
import com.example.smartdryingrack.core.designsystem.ScreenColumn
import com.example.smartdryingrack.core.designsystem.labelRes
import com.example.smartdryingrack.domain.model.CommandSource
import com.example.smartdryingrack.domain.model.RackAction
import com.example.smartdryingrack.domain.model.RejectReason
import com.example.smartdryingrack.domain.model.Unavailable
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ControlRoute(onHouseLights: () -> Unit = {}, viewModel: ControlViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ControlScreen(state, viewModel, onHouseLights)
}

@StringRes
private fun Unavailable.res(): Int = when (this) {
    Unavailable.OFFLINE -> R.string.unavailable_offline
    Unavailable.AUTO_MODE -> R.string.unavailable_auto
    Unavailable.ALREADY_EXTENDED -> R.string.unavailable_extended
    Unavailable.ALREADY_RETRACTED -> R.string.unavailable_retracted
    Unavailable.MOVING -> R.string.unavailable_moving
    Unavailable.UNSAFE -> R.string.reject_error
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(state: ControlUiState, actions: ControlActions, onHouseLights: () -> Unit = {}) {
    ScreenColumn {
        ModeSelector(state.mode, actions::setMode, Modifier.fillMaxWidth())
        com.example.smartdryingrack.core.designsystem.NeonFeatureRow("House Lights", "Control six independent RGB zones", Icons.Outlined.Lightbulb, onHouseLights)

        com.example.smartdryingrack.core.designsystem.RackHero(state.rackState)
        if (state.pendingMovement != null) {
            Text("Movement accepted. Waiting for position confirmation.", color = AppColors.Primary)
        } else if (state.connection != com.example.smartdryingrack.domain.model.ConnectionState.ONLINE) {
            Text("Connect your rack before using movement controls.", color = AppColors.TextSecondary)
        } else if (state.rackState in setOf(com.example.smartdryingrack.domain.model.RackState.UNKNOWN, com.example.smartdryingrack.domain.model.RackState.ERROR)) {
            Text("Position is unknown or faulted. Inspect your rack and reconnect before moving it.", color = AppColors.Warning)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MovementAction("Extend", Icons.Outlined.KeyboardDoubleArrowUp,
                state.availability.extend == null, { actions.send(RackAction.EXTEND) }, Modifier.weight(1f))
            MovementAction("Retract", Icons.Outlined.KeyboardDoubleArrowDown,
                state.availability.retract == null, { actions.send(RackAction.RETRACT) }, Modifier.weight(1f))
        }
        com.example.smartdryingrack.core.designsystem.NeonToggleRow("Buzzer", when {
            state.buzzerUpdating -> "Waiting for device confirmation…"
            state.buzzerEnabled == null -> "Requires updated ESP32 firmware (1.1.0+)"
            state.buzzerEnabled -> "Sound enabled · toggle on to test a short beep"
            else -> "Sound disabled"
        }, Icons.Outlined.VolumeUp, checked = state.buzzerEnabled == true,
            onChange = actions::setBuzzerEnabled,
            enabled = state.connection == com.example.smartdryingrack.domain.model.ConnectionState.ONLINE && state.buzzerEnabled != null && !state.buzzerUpdating)
        state.deviceMessage?.let { Text(it, color = AppColors.Warning, style = MaterialTheme.typography.bodySmall) }
        state.rainRaw?.let { Text("Rain sensor raw: $it · check dry/wet calibration",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary) }
        state.motionReason?.let { Text("Device movement reason: " + it.lowercase().replace('_', ' '),
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary) }
        RackControlButton("Stop", { actions.send(RackAction.STOP) },
            enabled = state.availability.stop == null, modifier = Modifier.fillMaxWidth(), accent = AppColors.Warning)

        EmergencyStopButton(onClick = actions::emergencyStop, modifier = Modifier.fillMaxWidth())

        state.rejection?.let {
            Text(stringResource(it.res()), color = AppColors.Warning, style = MaterialTheme.typography.bodyMedium)
        }

        com.example.smartdryingrack.core.designsystem.NeonFeatureRow("Rain protection enabled",
            "Confirmed rain requests retraction. Firmware safety remains independent.", androidx.compose.material.icons.Icons.Outlined.Umbrella)
        ScheduleCard(state, actions)
        Text("The emergency stop sends a Wi-Fi request. Use the physical stop if communication fails.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
