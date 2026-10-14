package com.example.smartdryingrack.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.RackState
import kotlin.math.roundToInt

@Composable
fun AutomationSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel(),
                             controls: com.example.smartdryingrack.feature.control.ControlViewModel = hiltViewModel()) {
    val controlState by controls.uiState.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(false) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var sensitivity by remember(settings.rainSensitivity) { mutableStateOf(settings.rainSensitivity) }
    var threshold by remember(settings.dryThresholdPercent) { mutableStateOf(settings.dryThresholdPercent.toFloat()) }
    ScreenColumn {
        SettingsHeading("Automation", onBack)
        ModeSelector(controlState.mode, controls::setMode, Modifier.fillMaxWidth())
        ReferenceRack(Modifier.fillMaxWidth().height(250.dp), rain = true)
        NeonToggleRow("Rain Protection", "Automatically retracts the rack when rain is confirmed",
            androidx.compose.material.icons.Icons.Outlined.Umbrella, checked = true, enabled = false)
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Rain sensor calibration", style = MaterialTheme.typography.titleMedium)
            Text(controlState.rainRaw?.let { "Current raw reading: $it" } ?: "Raw reading requires updated ESP32 firmware.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Text("Record dry and wet readings, then set the sensor threshold and polarity in the firmware. Rain protection stays enabled.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
            Text(if (advanced) "Hide advanced settings" else "Advanced settings")
        }
        if (advanced) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Forecast rain sensitivity", style = MaterialTheme.typography.titleMedium)
                Text("Applies only to a configured forecast provider; it does not change the physical rain sensor.",
                    style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                Slider(value = sensitivity, onValueChange = { sensitivity = it },
                    onValueChangeFinished = { viewModel.update { it.copy(rainSensitivity = sensitivity) } })
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Clothes dryness target · ${threshold.roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 50f..100f,
                    onValueChangeFinished = { viewModel.update { it.copy(dryThresholdPercent = threshold.roundToInt()) } })
                Text("Requires a calibrated dryness sensor. The app never estimates readiness from invented readings.", color = AppColors.TextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text("Choose Auto or Manual and configure schedules in Controls. Phone automation runs only while this app process is alive. Rain safety, limit switches, motor timeout and emergency stop must also be enforced by firmware.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
    }
}
