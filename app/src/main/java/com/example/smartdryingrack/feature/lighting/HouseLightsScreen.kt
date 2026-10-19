package com.example.smartdryingrack.feature.lighting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.data.repository.*
import com.example.smartdryingrack.domain.model.*
import kotlinx.coroutines.awaitCancellation
import kotlin.math.roundToInt

interface LightingActions {
    fun refresh()
    fun power(zone: LightingZone, enabled: Boolean)
    fun brightness(zone: LightingZone, percent: Int)
    fun retry(zone: LightingZone)
    fun discard(zone: LightingZone)
}

@Composable
fun HouseLightsRoute(onConnectionSettings: () -> Unit = {}, viewModel: HouseLightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.activate()
            try { awaitCancellation() } finally { viewModel.deactivate() }
        }
    }
    HouseLightsScreen(state, viewModel, onConnectionSettings)
}

@Composable
fun HouseLightsScreen(state: HouseLightingState, actions: LightingActions, onConnectionSettings: () -> Unit = {}) {
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Six independent dimmable LEDs", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary)
            TextButton(onClick = actions::refresh, enabled = state.connection !in setOf(LightingConnection.DISCONNECTED, LightingConnection.LOADING) &&
                state.zones.values.none { it.busy }) { Text("Refresh lights") }
        }
        Text(when (state.connection) {
            LightingConnection.DISCONNECTED -> "Connect your rack to load lighting state."
            LightingConnection.LOADING -> "Reading lighting state from the device…"
            LightingConnection.READY -> "Lighting connected · actual device readings available"
            LightingConnection.ERROR -> when (state.problem) {
                LightingProblem.WIFI_UNAVAILABLE -> "Wi-Fi unavailable · waiting to reconnect"
                LightingProblem.UNREACHABLE -> "Rack unreachable · retrying automatically"
                else -> "Lighting service unavailable · retrying automatically"
            }
            LightingConnection.UNSUPPORTED -> "Lighting-enabled firmware required"
        }, color = if (state.connection == LightingConnection.ERROR) AppColors.Warning else AppColors.Primary,
            style = MaterialTheme.typography.bodyMedium)
        if (state.connection == LightingConnection.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary) }
        if (state.connection == LightingConnection.ERROR && state.lastSyncedAt != null) {
            Text("Last confirmed readings may be stale.", style = MaterialTheme.typography.bodySmall, color = AppColors.Warning)
        }
        if (state.connection in setOf(LightingConnection.DISCONNECTED, LightingConnection.ERROR, LightingConnection.UNSUPPORTED)) {
            OutlinedButton(onClick = onConnectionSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Connection settings")
            }
        }
        StatusLightCard(state.indicator, hardwareEnabled = state.indicatorHardwareEnabled,
            stale = state.connection != LightingConnection.READY)
        Text("House zones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        LightingZone.entries.forEach { id ->
            key(id) {
                val zone = state.zones.getValue(id)
                ZoneLightCard(id, zone, state.connection, actions)
            }
        }
        Text("OFF retains the chosen brightness. ON at 0% brightness produces no visible light.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@Composable
private fun StatusLightCard(indicator: SystemStatusLight?, hardwareEnabled: Boolean?, stale: Boolean) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonIcon(Icons.Outlined.Info)
            Column {
                Text("System Status Light", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text("Automatic RGB · ESP32 controlled · read-only", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (indicator == null) {
            Text(when (hardwareEnabled) {
                false -> "RGB output disabled in firmware"
                true -> "RGB output enabled · actual state not reported"
                null -> "Device state and meaning not reported"
            }, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            if (hardwareEnabled == false) Text("Confirm the RGB LED’s common-anode/common-cathode type and pinout before enabling its firmware output. House lights work independently.",
                color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
            if (stale && hardwareEnabled != null) Text("Last reported configuration · reconnect to verify",
                color = AppColors.Warning, style = MaterialTheme.typography.bodySmall)
        } else {
            ColorSwatch(indicator.settings.rgb, Modifier.fillMaxWidth().height(44.dp), "Device-reported status color")
            Spacer(Modifier.height(8.dp))
            Text(indicator.description, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("${indicator.meaning} · pattern: ${indicator.pattern}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Text("${indicator.settings.rgb.hex} · ${indicator.settings.outputDescription}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            if (stale) Text("Last confirmed status · may be stale", color = AppColors.Warning, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ZoneLightCard(id: LightingZone, zone: ZoneLightingState, connection: LightingConnection,
                          actions: LightingActions) {
    val selected = zone.selected
    val editable = connection == LightingConnection.READY && zone.confirmed?.available == true && !zone.busy
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonIcon(Icons.Outlined.Lightbulb)
            Column(Modifier.weight(1f)) {
                Text(id.label, style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(when (connection) {
                    LightingConnection.READY -> if (zone.confirmed?.available == false) "PWM output unavailable" else "Device connected"
                    LightingConnection.LOADING -> "Checking connection"
                    LightingConnection.UNSUPPORTED -> "Firmware support unavailable"
                    LightingConnection.DISCONNECTED -> "Disconnected"
                    LightingConnection.ERROR -> "Connection error"
                }, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
            Switch(checked = selected?.power == true, onCheckedChange = { actions.power(id, it) }, enabled = editable,
                modifier = Modifier.semantics {
                    contentDescription = "${id.label} power"
                    stateDescription = when {
                        selected == null -> "Device state not reported"
                        zone.pending != null -> "Selected ${if (selected.power) "on" else "off"}, not yet confirmed"
                        else -> "Device-confirmed ${if (selected.power) "on" else "off"}"
                    }
                })
        }
        Spacer(Modifier.height(12.dp))
        Text(if (zone.confirmed?.available == false) "Output unavailable · check device driver" else selected?.outputDescription ?: "Power and output not reported", color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall)
        Text("Brightness: ${selected?.brightness?.let { "$it%" } ?: "—%"}", color = AppColors.TextPrimary,
            modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(value = selected?.brightness?.toFloat() ?: 0f, onValueChange = { actions.brightness(id, it.roundToInt()) },
            valueRange = 0f..100f, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = "${id.label} brightness"
                stateDescription = selected?.let { "${it.brightness} percent${if (zone.pending != null) ", pending" else ", confirmed"}" } ?: "Not reported"
            })
        zone.confirmed?.takeIf { it.available }?.settings?.let {
            Text("${if (connection == LightingConnection.READY) "Device-confirmed" else "Last confirmed (stale)"}: ${if (it.power) "ON" else "OFF"} · ${it.brightness}%",
                style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        zone.pending?.let {
            Text(when (it.phase) {
                LightCommandPhase.DEBOUNCING -> "Pending selection · waiting for changes to settle"
                LightCommandPhase.SENDING -> "Sending / queued · awaiting acceptance"
                LightCommandPhase.CONFIRMING -> "Accepted · waiting for confirmed device state"
                LightCommandPhase.FAILED -> "Selection is unconfirmed · review device state"
            }, style = MaterialTheme.typography.bodySmall, color = AppColors.Warning, modifier = Modifier.padding(top = 6.dp))
        }
        zone.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Danger, modifier = Modifier.padding(top = 6.dp)) }
        if (zone.pending?.phase == LightCommandPhase.FAILED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { actions.retry(id) }, enabled = editable) { Text("Retry ${id.label}") }
                TextButton(onClick = { actions.discard(id) }) { Text("Discard selection") }
            }
        }
    }
}

@Composable
private fun ColorSwatch(rgb: Rgb, modifier: Modifier, label: String) {
    Box(modifier.background(Color(rgb.r, rgb.g, rgb.b), RoundedCornerShape(8.dp))
        .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp)).semantics {
            contentDescription = "$label ${rgb.hex}, red ${rgb.r}, green ${rgb.g}, blue ${rgb.b}"
        })
}
