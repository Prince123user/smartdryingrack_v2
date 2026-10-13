package com.example.smartdryingrack.feature.settings

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.feature.device.DeviceConnectionPanel

@Composable
fun DeviceSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var name by rememberSaveable(settings.deviceName) { mutableStateOf(settings.deviceName) }
    ScreenColumn {
        SettingsHeading("Device Settings", onBack)
        RackHero(RackState.UNKNOWN, showPosition = false, modifier = Modifier.heightIn(min = 170.dp, max = 225.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Device name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
                TextButton(onClick = { viewModel.update { it.copy(deviceName = name.trim()) } }, enabled = name.isNotBlank()) { Text("Save") }
            })
        DeviceConnectionPanel()
    }
}
