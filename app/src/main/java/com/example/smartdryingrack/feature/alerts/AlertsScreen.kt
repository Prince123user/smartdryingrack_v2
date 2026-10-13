package com.example.smartdryingrack.feature.alerts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.AlertCategory

@Composable
fun AlertsRoute(viewModel: AlertsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlertsScreen(state, viewModel)
}

@Composable
fun AlertsScreen(state: AlertsUiState, viewModel: AlertsViewModel) {
    var clearRequested by remember { mutableStateOf(false) }
    val active = state.alerts.filterNot { it.read }
    val read = state.alerts.filter { it.read }
    ScreenColumn {
        Text("Active monitoring and alerts", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        RackHero(com.example.smartdryingrack.domain.model.RackState.UNKNOWN, showPosition = false)
        Text("Alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Active alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.filter == null, onClick = { viewModel.setFilter(null) }, label = { Text("All") })
            AlertCategory.entries.forEach { category -> FilterChip(selected = state.filter == category,
                onClick = { viewModel.setFilter(category) }, label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }
        if (active.isEmpty()) EmptyStateCard("No new alerts", "Rain, sensor and connection events will appear here when recorded.", Icons.Outlined.CheckCircle)
        active.forEach { event -> key(event.id) { EventCard(event, viewModel::markRead) } }
        if (read.isNotEmpty()) {
            Text("Read alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Read means acknowledged, not physically resolved.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            read.forEach { event -> key(event.id) { EventCard(event, viewModel::markRead) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = viewModel::markAllRead, enabled = active.isNotEmpty()) { Text("Mark all read") }
            TextButton(onClick = { clearRequested = true }, enabled = state.alerts.isNotEmpty()) { Text("Clear history") }
        }
    }
    if (clearRequested) AlertDialog(onDismissRequest = { clearRequested = false }, title = { Text("Clear local history?") },
        text = { Text("This removes saved events from both Alerts and History on this phone.") },
        confirmButton = { TextButton(onClick = { viewModel.clearAll(); clearRequested = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearRequested = false }) { Text("Cancel") } })
}
