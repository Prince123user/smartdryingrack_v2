package com.example.smartdryingrack.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smartdryingrack.core.designsystem.*

@Composable
internal fun PageDots(active: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (index == active) AppColors.Primary else AppColors.Border))
        }
    }
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit, onConnect: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        CircuitBackground()
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(42.dp))
            Text(if (page == 0) "Smart Monitoring" else "Rain Protection", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text(if (page == 0) "Keep track of your drying environment\nin real time." else "Automatically retracts the rack\nwhen rain is detected.",
                color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            if (page == 0) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TutorialSensor("30°C", "Temperature", Icons.Outlined.Thermostat, Color(0xFFFF687C), Modifier.weight(1f))
                    TutorialSensor("65%", "Humidity", Icons.Outlined.WaterDrop, AppColors.Primary, Modifier.weight(1f))
                    TutorialSensor("No rain", "Rain status", Icons.Outlined.Cloud, Color(0xFFB7DDFF), Modifier.weight(1f))
                }
                Text("Illustrative readings", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            }
            ReferenceRack(Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp), rain = page == 1)
            PageDots(page + 1, 3)
            Spacer(Modifier.height(16.dp))
            if (page == 0) OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
            NeonAction(if (page == 0) "Next" else "Continue", Icons.AutoMirrored.Outlined.ArrowForward,
                { if (page == 0) page = 1 else onConnect() }, modifier = Modifier.fillMaxWidth())
            if (page == 1) TextButton(onClick = onConnect) { Text("Skip") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TutorialSensor(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                           color: Color, modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
            Text(value, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        }
    }
}
