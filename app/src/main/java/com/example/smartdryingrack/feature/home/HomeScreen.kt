package com.example.smartdryingrack.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.*
import kotlin.math.roundToInt

@Composable
fun HomeRoute(onConnect: () -> Unit = {}, onControl: () -> Unit = {}, onHouseLights: () -> Unit = {}, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    HomeScreen(state, onConnect, onControl, viewModel::retract, message, viewModel::setAutoMode, onHouseLights)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: HomeUiState, onConnect: () -> Unit = {}, onControl: () -> Unit = {},
               onRetract: () -> Unit = {}, message: String? = null, onAutoModeChange: ((Boolean) -> Unit)? = null, onHouseLights: () -> Unit = {}) {
    val sensors = state.sensorData?.takeIf { it.isValid }
    val online = state.connection == ConnectionState.ONLINE
    ScreenColumn {
        DeviceConnectionCard(state.connection, onConnect)
        NeonFeatureRow("House Lights", "Six dimmable LEDs · automatic RGB status", Icons.Outlined.Lightbulb, onHouseLights)
        RackHero(state.rackState, showPosition = false)
        RackStatusCard(state.rackState, state.rainConfirmed, null)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = if (androidx.compose.ui.platform.LocalConfiguration.current.fontScale > 1.3f) 1 else 3) {
            MiniSensor("Temperature", sensors?.let { "${it.temperature.roundToInt()}°C" } ?: "—", Icons.Outlined.Thermostat,
                Color(0xFFFF687C), Modifier.weight(1f))
            MiniSensor("Humidity", sensors?.let { "${it.humidity.roundToInt()}%" } ?: "—", Icons.Outlined.WaterDrop,
                Color(0xFF47B8FF), Modifier.weight(1f))
            MiniSensor("Rain status", when { sensors == null -> "—"; sensors.rainDetected -> "Wet"; else -> "No rain" },
                Icons.Outlined.Cloud, if (sensors?.rainDetected == true) AppColors.Warning else AppColors.Primary, Modifier.weight(1f))
        }
        NeonToggleRow(if (state.rackMode == RackMode.AUTO) "Auto mode ON" else "Auto mode OFF",
            if (state.rackMode == RackMode.AUTO) "Rack operates automatically" else "Manual control is selected",
            Icons.Outlined.Settings, checked = state.rackMode == RackMode.AUTO, onChange = onAutoModeChange ?: { onControl() })
        val manual = state.rackMode == RackMode.MANUAL
        NeonAction(if (manual) "Retract rack" else "Open controls", Icons.Outlined.KeyboardDoubleArrowDown,
            if (manual) onRetract else onControl,
            enabled = !manual || online && state.rackState in setOf(RackState.EXTENDED, RackState.STOPPED),
            modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, color = AppColors.Primary, style = MaterialTheme.typography.bodySmall) }
        if (!online) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (sensors != null) "Offline · readings are stale" else "Offline · no readings yet",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                TextButton(onClick = onConnect) { Text("Connect rack") }
            }
        }
        if (sensors?.clothesDryPercentage != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Clothes dryness · ${sensors.clothesDryPercentage}%", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { sensors.clothesDryPercentage / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Measured target: ${state.dryThreshold}%", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun MiniSensor(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    GlassCard(modifier, color) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        }
    }
}

@Preview(name = "Home • offline", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() { SmartDryingRackTheme { HomeScreen(HomeUiState()) } }
