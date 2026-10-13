package com.example.smartdryingrack.feature.device

import android.text.format.DateUtils
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.ConnectionState

@Composable
fun DeviceRoute(viewModel: DeviceViewModel = hiltViewModel()) {
    ScreenColumn { DeviceConnectionPanel(viewModel) }
}

@Composable
fun DeviceConnectionPanel(viewModel: DeviceViewModel = hiltViewModel()) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val savedAddress by viewModel.address.collectAsStateWithLifecycle()
    val sensors by viewModel.sensorData.collectAsStateWithLifecycle()
    val firmware by viewModel.firmware.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val validation by viewModel.validation.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var address by rememberSaveable(savedAddress) { mutableStateOf(savedAddress) }
    Text("Your rack", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
    Text("Connect your phone and rack to the same Wi-Fi network.", color = AppColors.TextSecondary)
    GlassCard(Modifier.fillMaxWidth()) {
        StatusIndicator(connection)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = address, onValueChange = { address = it }, label = { Text("Rack address") },
            placeholder = { Text("http://192.168.1.50") }, singleLine = true,
            enabled = connection != ConnectionState.CONNECTING,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (address.isNotBlank() && connection != ConnectionState.CONNECTING) {
                    focus.clearFocus(); viewModel.connect(address)
                }
            }),
            isError = validation != null, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(validation ?: "Find the IP address in your router or firmware serial output.") }
        )
        message?.let { Text(it, color = if (connection == ConnectionState.ERROR) AppColors.Danger else AppColors.TextSecondary) }
        Spacer(Modifier.height(12.dp))
        NeonAction(if (connection == ConnectionState.CONNECTING) "Connecting…" else "Connect", androidx.compose.material.icons.Icons.Outlined.Wifi,
            onClick = { focus.clearFocus(); viewModel.connect(address) }, enabled = address.isNotBlank() && connection != ConnectionState.CONNECTING,
            modifier = Modifier.fillMaxWidth())
        /* Connection progress is announced by StatusIndicator above. */
        if (connection == ConnectionState.CONNECTING) {
            if (connection == ConnectionState.CONNECTING) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
        }
        if (connection != ConnectionState.OFFLINE) {
            OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Device details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Protocol: Wi-Fi REST", color = AppColors.TextSecondary)
        Text("Firmware: ${firmware ?: "Not reported"}", color = AppColors.TextSecondary)
        Text(sensors?.let { "Last reading: " + DateUtils.getRelativeTimeSpanString(it.timestamp, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS) }
            ?: "No sensor readings received", color = AppColors.TextSecondary)
        if (connection != ConnectionState.ONLINE && sensors != null) Text("Readings are stale", color = AppColors.Warning)
    }
}
