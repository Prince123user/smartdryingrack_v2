package com.example.smartdryingrack.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.smartdryingrack.BuildConfig
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.feature.device.DeviceViewModel
import com.example.smartdryingrack.domain.model.*

@Composable
fun SettingsRoute(onNavigateDevice: () -> Unit, onNavigateAutomation: () -> Unit, onNavigateNotifications: () -> Unit,
                  onNavigateAppearance: () -> Unit, onNavigateAbout: () -> Unit,
                  settingsViewModel: SettingsViewModel = hiltViewModel(), deviceViewModel: DeviceViewModel = hiltViewModel()) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val connection by deviceViewModel.connection.collectAsStateWithLifecycle()
    val rackState by deviceViewModel.rackState.collectAsStateWithLifecycle()
    SettingsScreen(settings, connection, rackState, onNavigateDevice = onNavigateDevice, onNavigateAutomation = onNavigateAutomation,
        onNavigateNotifications = onNavigateNotifications, onNavigateAppearance = onNavigateAppearance, onNavigateAbout = onNavigateAbout)
}

@Composable
fun SettingsScreen(settings: AutomationSettings = AutomationSettings(), connection: ConnectionState = ConnectionState.OFFLINE,
                   rackState: RackState = RackState.UNKNOWN,
                   firmware: String? = null, onNavigateDevice: () -> Unit, onNavigateAutomation: () -> Unit,
                   onNavigateNotifications: () -> Unit, onNavigateAppearance: () -> Unit, onNavigateAbout: () -> Unit) {
    ScreenColumn {
        com.example.smartdryingrack.core.designsystem.RackHero(
            rackState,
            showPosition = false,
            modifier = Modifier.heightIn(min = 170.dp, max = 225.dp)
        )
        SettingsSection("Device")
        NeonFeatureRow("ESP32 rack", settings.deviceName, Icons.Outlined.Memory, onNavigateDevice)
        DeviceConnectionCard(connection, onNavigateDevice)
        NeonFeatureRow("Rain protection", "Connected sensor protection and device-side safety", Icons.Outlined.Umbrella, onNavigateAutomation)
        NeonFeatureRow("Notifications", "In-app alerts and persistent event history", Icons.Outlined.Notifications, onNavigateNotifications)
        NeonFeatureRow("Temperature unit", "Celsius (°C)", Icons.Outlined.Thermostat)
        NeonFeatureRow("Appearance", if (settings.darkTheme) "Midnight cyan theme" else "Light theme", Icons.Outlined.Palette, onNavigateAppearance)
        NeonFeatureRow("Device Settings", "Address, Wi-Fi status and firmware details", Icons.Outlined.Settings, onNavigateDevice)
        NeonFeatureRow("Automation", "Manage rain protection and sensor sensitivity", Icons.Outlined.Tune, onNavigateAutomation)
        NeonFeatureRow("Notification Settings", "Customize alerts and events", Icons.Outlined.Notifications, onNavigateNotifications)
        NeonFeatureRow("About Smart Drying Rack", "Version ${BuildConfig.VERSION_NAME} · local Wi-Fi REST", Icons.Outlined.Info, onNavigateAbout)
    }
}

@Composable
private fun SettingsSection(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary) }
