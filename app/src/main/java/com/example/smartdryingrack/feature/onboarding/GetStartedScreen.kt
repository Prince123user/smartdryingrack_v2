package com.example.smartdryingrack.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.RackState

@Composable
fun GetStartedScreen(onGetStarted: () -> Unit, onHaveDevice: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CircuitBackground()
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            ReferenceArtwork(com.example.smartdryingrack.R.drawable.reference_intro, 60, 370, Modifier.fillMaxWidth().height(130.dp))
            Text("SMART\nDRYING RACK", color = AppColors.TextPrimary, fontSize = 30.sp, lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("Smart. Safe. Automatic.", color = AppColors.Primary, modifier = Modifier.padding(top = 8.dp))
            Text("Automatically protects your clothes\nby retracting the rack when rain is detected.",
                color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            ReferenceArtwork(com.example.smartdryingrack.R.drawable.reference_intro, 720, 1450, Modifier.fillMaxWidth().weight(1f), "Illustration of a motorized drying rack with hanging laundry")
            PageDots(0, 3)
            Spacer(Modifier.height(18.dp))
            NeonAction("Get Started", Icons.AutoMirrored.Outlined.ArrowForward, onGetStarted, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onHaveDevice) { Text("I already have a device") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
