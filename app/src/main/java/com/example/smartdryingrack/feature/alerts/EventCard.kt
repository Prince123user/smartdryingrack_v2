package com.example.smartdryingrack.feature.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.RackEvent

@Composable
fun EventCard(event: RackEvent, onMarkRead: (Long) -> Unit, showBadge: Boolean = true) {
    val context = LocalContext.current
    val color = if (event.read) AppColors.Primary else event.type.severity.color()
    GlassCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onMarkRead(event.id) }), color) {
        Row(verticalAlignment = Alignment.Top) {
            if (!showBadge) {
                androidx.compose.foundation.layout.Box(Modifier.width(76.dp).height(72.dp)) {
                    ReferenceRack(Modifier.fillMaxSize())
                    androidx.compose.material3.Icon(event.type.eventIcon(), null, tint = color,
                        modifier = Modifier.align(Alignment.BottomEnd).size(24.dp))
                }
            } else NeonIcon(event.type.eventIcon(), color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(event.type.titleRes()), color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(event.type.descRes()), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(7.dp))
                Text(eventTime(context, event.timestamp), style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            }
            if (showBadge) {
                Spacer(Modifier.width(8.dp))
                StatusBadge(if (event.read) "Read" else "New", color)
            }
        }
    }
}
