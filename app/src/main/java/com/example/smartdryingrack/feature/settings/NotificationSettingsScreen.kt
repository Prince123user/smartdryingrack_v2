package com.example.smartdryingrack.feature.settings

import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.RackState

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    ScreenColumn {
        SettingsHeading("Notification Settings", onBack)
        RackHero(RackState.EXTENDED, showPosition = false, modifier = Modifier.heightIn(min = 170.dp, max = 225.dp))
        NeonToggleRow("Rain alerts", "Recorded in your local alert history", androidx.compose.material.icons.Icons.Outlined.Umbrella,
            checked = true, enabled = false)
        NeonToggleRow("Connection alerts", "Recorded when the device connection changes", androidx.compose.material.icons.Icons.Outlined.Wifi,
            checked = true, enabled = false)
        NeonToggleRow("Rack movement alerts", "Accepted commands appear in rack history", androidx.compose.material.icons.Icons.Outlined.Notifications,
            checked = true, enabled = false)
        NeonToggleRow("Clothes-dry alerts", "Notify when the measured dryness target is reached", androidx.compose.material.icons.Icons.Outlined.DryCleaning,
            checked = settings.clothesDryAlert, onChange = { enabled -> viewModel.update { it.copy(clothesDryAlert = enabled) } })
        NeonToggleRow("Notification sound", "Sound notifications are unavailable", androidx.compose.material.icons.Icons.AutoMirrored.Outlined.VolumeUp,
            checked = false, enabled = false)
    }
}
