package com.example.smartdryingrack.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartdryingrack.R
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.RackState

@Composable
fun CircuitBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(AppColors.Background)) {
        if (LocalDarkTheme.current) {
            ReferenceArtwork(R.drawable.reference_welcome, 1610, 1810, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun NeonIcon(icon: ImageVector, color: Color = AppColors.Primary, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp)
        .shadow(if (LocalDarkTheme.current) 6.dp else 1.dp, RoundedCornerShape(12.dp), clip = false,
            ambientColor = color.copy(alpha = .12f), spotColor = color.copy(alpha = .18f))
        .clip(RoundedCornerShape(12.dp))
        .background(Brush.radialGradient(listOf(color.copy(alpha = .28f), color.copy(alpha = .04f))))
        .border(1.dp, color.copy(alpha = .22f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(25.dp))
    }
}

@Composable
fun DeviceConnectionCard(connection: ConnectionState, onClick: () -> Unit) {
    val accent = when (connection) {
        ConnectionState.ONLINE -> AppColors.Primary
        ConnectionState.ERROR -> AppColors.Danger
        ConnectionState.CONNECTING -> AppColors.Warning
        else -> AppColors.TextSecondary
    }
    GlassCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(Icons.Outlined.Wifi, accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(connection.labelRes()), fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Text(when (connection) {
                    ConnectionState.ONLINE -> "Receiving readings · connection details"
                    ConnectionState.CONNECTING -> "Checking device telemetry…"
                    ConnectionState.ERROR -> "Connection needs attention · tap for help"
                    ConnectionState.OFFLINE -> "Set up your rack’s Wi-Fi connection"
                }, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = accent)
        }
    }
}

@Composable
fun RackHero(state: RackState, modifier: Modifier = Modifier, showPosition: Boolean = true) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = maxWidth * .77f
        Box(Modifier.fillMaxWidth().height(height + if (showPosition) 32.dp else 0.dp)) {
            val shape = RoundedCornerShape(16.dp)
            Box(Modifier.fillMaxWidth().height(height)
                .shadow(if (LocalDarkTheme.current) 10.dp else 4.dp, shape, clip = false)
                .clip(shape).border(1.dp, AppColors.BorderSubtle.copy(alpha = .6f), shape)) {
                ReferenceRack(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,
                    Color.Transparent, AppColors.Background.copy(alpha = .45f)))))
            }
            if (showPosition) {
                Text(stringResource(state.labelRes()), modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            }
        }
    }
}

@Composable
fun NeonAction(label: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true,
               modifier: Modifier = Modifier, accent: Color = AppColors.Primary) {
    val shape = RoundedCornerShape(12.dp)
    val activeColor = if (enabled) accent else AppColors.Border
    Box(modifier.heightIn(min = Dimens.ButtonHeight)
        .shadow(if (enabled) 10.dp else 0.dp, shape, clip = false,
            ambientColor = activeColor.copy(alpha = .20f), spotColor = activeColor.copy(alpha = .35f))
        .clip(shape)
        .background(Brush.verticalGradient(if (enabled) listOf(lerp(activeColor, Color.White, .12f), lerp(activeColor, Color.Black, .35f), lerp(activeColor, Color.Black, .58f)) else listOf(AppColors.Card, AppColors.Card)))
        .border(1.dp, if (enabled) lerp(activeColor, Color.White, .30f) else AppColors.Border.copy(alpha = .6f), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val trailing = icon == Icons.AutoMirrored.Outlined.ArrowForward
            if (!trailing) Icon(icon, contentDescription = null, tint = if (enabled) Color.White else AppColors.TextTertiary)
            Text(label, color = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary, fontWeight = FontWeight.Bold)
            if (trailing) Icon(icon, contentDescription = null, tint = if (enabled) Color.White else AppColors.TextTertiary)
        }
    }
}

@Composable
fun NeonFeatureRow(title: String, description: String, icon: ImageVector, onClick: (() -> Unit)? = null,
                   accent: Color = AppColors.Primary) {
    GlassCard(if (onClick != null) {
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
    } else Modifier.fillMaxWidth(), accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(icon, accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (onClick != null) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
        }
    }
}

@Composable
fun ScreenHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@Composable
fun EmptyStateCard(title: String, message: String, icon: ImageVector,
                   actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonIcon(icon, modifier = Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun MovementAction(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (enabled) AppColors.Primary else AppColors.Border
    Column(modifier.heightIn(min = 88.dp)
        .shadow(if (enabled) 8.dp else 0.dp, shape, clip = false,
            ambientColor = accent.copy(alpha = .16f), spotColor = accent.copy(alpha = .28f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(accent.copy(alpha = .26f), AppColors.Card)))
        .border(1.dp, accent.copy(alpha = if (enabled) .8f else .6f), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = if (enabled) AppColors.Primary else AppColors.TextTertiary, modifier = Modifier.size(30.dp))
        Text(label, color = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary,
            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun NeonToggleRow(title: String, description: String, icon: ImageVector, checked: Boolean,
                  onChange: ((Boolean) -> Unit)? = null, enabled: Boolean = true) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(icon)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Text(description, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = title },
                colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Primary,
                    checkedThumbColor = Color.White, uncheckedTrackColor = AppColors.Card,
                    uncheckedBorderColor = AppColors.Border,
                    disabledCheckedTrackColor = AppColors.Primary.copy(alpha = .55f),
                    disabledCheckedThumbColor = Color.White.copy(alpha = .85f)))
        }
    }
}
