package com.example.smartdryingrack.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdryingrack.R
import com.example.smartdryingrack.core.designsystem.*
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onHaveDevice: (() -> Unit)? = null) {
    LaunchedEffect(Unit) { delay(900); onGetStarted() }
    Box(Modifier.fillMaxSize()) {
        CircuitBackground()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            ReferenceArtwork(R.drawable.reference_welcome, 60, 1070, Modifier.fillMaxWidth().weight(1f))
            Text("SMART\nDRYING RACK", fontSize = 32.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, color = AppColors.TextPrimary)
            Text("Smart. Safe. Automatic.", color = AppColors.Primary, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(26.dp))
            CircularProgressIndicator(Modifier.size(54.dp), color = AppColors.Primary, strokeWidth = 3.dp)
            Spacer(Modifier.height(64.dp))
        }
    }
}
