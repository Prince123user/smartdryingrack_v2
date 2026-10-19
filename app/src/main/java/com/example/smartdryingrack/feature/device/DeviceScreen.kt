package com.example.smartdryingrack.feature.device

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.ConnectionState
import kotlinx.coroutines.delay

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
    val attempts by viewModel.failedAttempts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DeviceConnectionContent(connection, savedAddress, sensors?.timestamp, firmware, message, validation, attempts,
        onConnect = viewModel::connect, onDisconnect = viewModel::disconnect,
        onWifiSettings = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) })
}

@Composable
fun DeviceConnectionContent(connection: ConnectionState, savedAddress: String, lastReading: Long?,
                            firmware: String?, message: String?, validation: String?, failedAttempts: Int,
                            onConnect: (String) -> Unit, onDisconnect: () -> Unit, onWifiSettings: () -> Unit) {
    val focus = LocalFocusManager.current
    var address by rememberSaveable(savedAddress) { mutableStateOf(savedAddress) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastReading) {
        if (lastReading != null) while (true) { now = System.currentTimeMillis(); delay(5000) }
    }
    val connecting = connection == ConnectionState.CONNECTING
    val online = connection == ConnectionState.ONLINE
    val edited = address.trim() != savedAddress
    val connect = { focus.clearFocus(); onConnect(address) }
    val accent = when (connection) {
        ConnectionState.ONLINE -> AppColors.Success
        ConnectionState.ERROR -> AppColors.Warning
        else -> AppColors.Primary
    }
    Text("Rack connection", style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
    Text("Stay connected to your rack over local Wi-Fi.", color = AppColors.TextSecondary)
    GlassCard(Modifier.fillMaxWidth(), accent) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonIcon(if (online) Icons.Outlined.Wifi else Icons.Outlined.Router, accent)
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(when (connection) {
                    ConnectionState.ONLINE -> "Rack connected"
                    ConnectionState.CONNECTING -> "Connecting to your rack"
                    ConnectionState.ERROR -> "Connection needs attention"
                    ConnectionState.OFFLINE -> "Ready to connect"
                }, style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(when (connection) {
                    ConnectionState.ONLINE -> "Receiving device readings"
                    ConnectionState.CONNECTING -> "Waiting for the ESP32 to respond"
                    ConnectionState.ERROR -> if (failedAttempts > 0) "Retrying automatically · attempt $failedAttempts failed" else "Checking connection automatically"
                    ConnectionState.OFFLINE -> "Automatic reconnect is paused"
                }, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
        if (connecting) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppColors.Primary)
        }
        if (connection == ConnectionState.ERROR) {
            Spacer(Modifier.height(12.dp))
            Text(message ?: "Check rack power, Wi-Fi and the saved address.", color = AppColors.Warning,
                style = MaterialTheme.typography.bodyMedium)
            Text("Read-only checks retry after a 5-second pause. Failed commands are not replayed.",
                color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (lastReading != null) {
            Text("${if (online) "Last reading" else "Last reading (stale)"}: " +
                DateUtils.getRelativeTimeSpanString(lastReading, now, DateUtils.SECOND_IN_MILLIS),
                color = if (online) AppColors.TextSecondary else AppColors.Warning,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Connection settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Rack address") },
            placeholder = { Text("http://192.168.1.50") }, singleLine = true, enabled = !connecting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (address.isNotBlank() && !connecting) connect() }),
            isError = validation != null, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(validation ?: "Use the current IP shown in the ESP32 Serial Monitor.") })
        if (edited) Text("New address · connect to save and verify it", color = AppColors.Warning,
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        NeonAction(when {
            connecting -> "Connecting…"
            edited -> "Save address & connect"
            online -> "Check connection"
            connection == ConnectionState.ERROR -> "Retry now"
            else -> "Connect rack"
        }, Icons.Outlined.Wifi, onClick = connect, enabled = address.isNotBlank() && !connecting,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onWifiSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Open Wi-Fi settings")
        }
        if (connection != ConnectionState.OFFLINE) {
            TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Disconnect & pause retries", color = AppColors.TextSecondary)
            }
        }
        if (connection == ConnectionState.OFFLINE && message != null) {
            Text(message, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Connection guide", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("1. Power on the ESP32.\n2. Join the same Wi-Fi network on your phone.\n3. Enter the rack’s current address and connect.",
            color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text("Local Wi-Fi works without internet. Keep the phone connected if Android warns that this network has no internet.",
            color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = { showHelp = !showHelp }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (showHelp) "Hide device details" else "Device details & troubleshooting")
        }
        if (showHelp) {
            Text("Firmware: ${firmware ?: "Not reported"}\nSaved address: ${savedAddress.ifBlank { "Not set" }}\nTransport: local Wi-Fi REST",
                color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text("If the IP changes after a router or ESP32 restart, update it above. Guest Wi-Fi may block communication between devices. A connected rack does not guarantee that every lighting or sensor service is available.",
                color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
