package com.example.smartdryingrack.feature.onboarding

import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.AppColors
import com.example.smartdryingrack.core.designsystem.ScreenColumn
import com.example.smartdryingrack.feature.device.DeviceConnectionPanel
import com.example.smartdryingrack.feature.device.DeviceViewModel
import com.example.smartdryingrack.domain.model.ConnectionState

@Composable
fun DeviceSetupScreen(onFinishSetup: () -> Unit, viewModel: DeviceViewModel = hiltViewModel()) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    if (connection == ConnectionState.ONLINE) {
        val address by viewModel.address.collectAsStateWithLifecycle()
        ScreenColumn {
            Text("Device Connected", style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.CheckCircle, null,
                tint = AppColors.Primary, modifier = Modifier.fillMaxWidth().height(72.dp))
            com.example.smartdryingrack.core.designsystem.RackHero(com.example.smartdryingrack.domain.model.RackState.UNKNOWN, showPosition = false)
            Text("ESP32 Rack is now online!", style = MaterialTheme.typography.titleMedium)
            com.example.smartdryingrack.core.designsystem.NeonFeatureRow("Wi-Fi REST connected", "Validated device telemetry received", androidx.compose.material.icons.Icons.Outlined.Wifi)
            com.example.smartdryingrack.core.designsystem.NeonFeatureRow("Local IP address", address, androidx.compose.material.icons.Icons.Outlined.Lan)
            com.example.smartdryingrack.core.designsystem.NeonAction("Open Home", androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowForward,
                onFinishSetup, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    ScreenColumn {
        Text("Connect Device", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Connect your phone and rack to the same Wi-Fi network.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        com.example.smartdryingrack.core.designsystem.RackHero(com.example.smartdryingrack.domain.model.RackState.UNKNOWN, showPosition = false)
        DeviceConnectionPanel(viewModel)
        TextButton(onClick = onFinishSetup, modifier = Modifier.fillMaxWidth()) { Text("Explore offline") }
        Text("Offline mode shows no live readings and cannot control your rack.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun WifiIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.75f
        val stroke = 3f

        drawArc(color, 210f, 120f, false, topLeft = Offset(w * 0.1f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.8f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, 210f, 120f, false, topLeft = Offset(w * 0.25f, h * 0.35f), size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.5f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(color, radius = 3.5f, center = Offset(cx, cy))
    }
}

@Composable
fun BluetoothIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val stroke = 3.5f

        drawLine(color, Offset(cx, h * 0.15f), Offset(cx, h * 0.85f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.3f, h * 0.35f), Offset(w * 0.7f, h * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.7f, h * 0.65f), Offset(cx, h * 0.85f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.3f, h * 0.65f), Offset(w * 0.7f, h * 0.35f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.7f, h * 0.35f), Offset(cx, h * 0.15f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 3.5f
        drawLine(color, Offset(w * 0.3f, h * 0.2f), Offset(w * 0.7f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.7f, h * 0.5f), Offset(w * 0.3f, h * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
