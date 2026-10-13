package com.example.smartdryingrack.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartdryingrack.R
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.RackMode
import com.example.smartdryingrack.domain.model.RackState

@StringRes
fun ConnectionState.labelRes(): Int = when (this) {
    ConnectionState.ONLINE -> R.string.conn_online
    ConnectionState.OFFLINE -> R.string.conn_offline
    ConnectionState.CONNECTING -> R.string.conn_connecting
    ConnectionState.ERROR -> R.string.conn_error
}

@StringRes
fun RackState.labelRes(): Int = when (this) {
    RackState.EXTENDED -> R.string.rack_out_title
    RackState.RETRACTED -> R.string.rack_in_title
    RackState.EXTENDING -> R.string.rack_extending
    RackState.RETRACTING -> R.string.rack_retracting
    RackState.STOPPED -> R.string.rack_stopped
    RackState.ERROR -> R.string.rack_error
    RackState.UNKNOWN -> R.string.rack_unknown
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        CircuitBackground()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap), content = content)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = AppColors.Primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .shadow(if (LocalDarkTheme.current) 6.dp else 1.dp, shape,
                ambientColor = accent.copy(alpha = .12f), spotColor = accent.copy(alpha = .12f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.CardAlt.copy(alpha = .95f), AppColors.Card.copy(alpha = .94f))))
            .border(1.dp, accent.copy(alpha = if (LocalDarkTheme.current) .38f else .2f), shape)
            .padding(Dimens.CardPadding),
        content = content
    )
}

@Composable
fun SensorCard(label: String, value: String, modifier: Modifier = Modifier, accent: Color = AppColors.Primary, subtitle: String? = null) {
    GlassCard(modifier.semantics(mergeDescendants = true) {}, accent) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = AppColors.TextPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        }
    }
}

@Composable
fun ProgressRingCard(label: String, percentage: Int, subtitle: String, modifier: Modifier = Modifier, accent: Color = AppColors.Primary) {
    val ringTrack = AppColors.Border
    GlassCard(modifier.semantics(mergeDescendants = true) {}, accent) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text("$percentage%", style = MaterialTheme.typography.headlineMedium, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    drawArc(
                        color = ringTrack,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = (percentage / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Text("$percentage%", style = MaterialTheme.typography.labelMedium, color = AppColors.TextPrimary)
            }
        }
    }
}

@Composable
fun StatusIndicator(state: ConnectionState, modifier: Modifier = Modifier) {
    val color = when (state) {
        ConnectionState.ONLINE -> AppColors.Success
        ConnectionState.CONNECTING -> AppColors.Warning
        ConnectionState.OFFLINE -> AppColors.TextSecondary
        ConnectionState.ERROR -> AppColors.Danger
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val alpha = if (state == ConnectionState.ONLINE || state == ConnectionState.CONNECTING) pulse else 1f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(state.labelRes()), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun RackStatusCard(
    state: RackState,
    rainConfirmed: Boolean,
    condition: String?,
    modifier: Modifier = Modifier
) {
    val accent = if (rainConfirmed) AppColors.Warning else AppColors.Primary
    GlassCard(modifier.fillMaxWidth(), accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(if (rainConfirmed) Icons.Outlined.Umbrella else Icons.Outlined.DryCleaning, accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(state.labelRes()), style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(when {
                    rainConfirmed -> "Rain confirmed · protection requested"
                    state == RackState.EXTENDED -> "Ready for drying"
                    state == RackState.RETRACTED -> "Rack is sheltered"
                    state == RackState.EXTENDING || state == RackState.RETRACTING -> "Movement reported by the rack"
                    state == RackState.ERROR -> "Inspect your rack before reconnecting"
                    state == RackState.STOPPED -> "Movement stopped"
                    else -> "Waiting for live position telemetry"
                }, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, accent: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ModeSelector(mode: RackMode, onModeChange: (RackMode) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val outer = RoundedCornerShape(10.dp)
    val inner = RoundedCornerShape(8.dp)
    Row(
        modifier.clip(outer).background(AppColors.Card).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RackMode.entries.forEach { m ->
            val selected = m == mode
            val bg by animateColorAsState(
                if (selected) AppColors.Primary.copy(alpha = 0.3f) else Color.Transparent,
                label = "modeBg"
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.MinTouch)
                    .clip(inner)
                    .background(Brush.verticalGradient(if (selected) listOf(AppColors.Primary, AppColors.PrimaryVariant, AppColors.Card) else listOf(Color.Transparent, Color.Transparent)))
                    .border(1.dp, if (selected) AppColors.Primary else Color.Transparent, inner)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onModeChange(m)
                    }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(if (m == RackMode.AUTO) R.string.mode_auto else R.string.mode_manual),
                    color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun RackControlButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.Primary
) {
    NeonAction(label, when {
        label.contains("Extend", true) -> Icons.Outlined.KeyboardDoubleArrowUp
        label.contains("Retract", true) -> Icons.Outlined.KeyboardDoubleArrowDown
        else -> Icons.Outlined.StopCircle
    }, onClick, enabled, modifier, accent)
}

@Composable
fun EmergencyStopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.heightIn(min = Dimens.ButtonHeight),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Danger)
    ) {
        Text("EMERGENCY STOP", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FuturisticToggle(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(it)
            }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp), color = AppColors.TextPrimary)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        }
    }
}

@Composable
fun ErrorStateCard(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), accent = AppColors.Danger) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.Danger, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.Danger)
                Spacer(Modifier.height(4.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AppColors.Danger)
        ) {
            Text("RETRY", color = AppColors.Danger, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun EmptyStateCard(title: String, message: String, actionLabel: String? = null, modifier: Modifier = Modifier, onAction: (() -> Unit)? = null) {
    GlassCard(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
