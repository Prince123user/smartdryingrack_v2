package com.example.smartdryingrack.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.data.repository.TemperaturePoint
import com.example.smartdryingrack.feature.alerts.*
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state)
}

@Composable
fun HistoryScreen(state: HistoryUiState) {
    val context = LocalContext.current
    var week by rememberSaveable { mutableStateOf(false) }
    val start = if (week) System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        else LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val points = state.temperatures.filter { it.timestamp >= start }
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonAction("Today", Icons.Outlined.Today, { week = false }, modifier = Modifier.weight(1f),
                accent = if (!week) AppColors.Primary else AppColors.TextSecondary)
            NeonAction("Week", Icons.Outlined.DateRange, { week = true }, modifier = Modifier.weight(1f),
                accent = if (week) AppColors.Primary else AppColors.TextSecondary)
        }
        RackHero(com.example.smartdryingrack.domain.model.RackState.UNKNOWN, showPosition = false)
        Text("Temperature (°C)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TemperatureChart(points)
        Text("Measurements are saved every five minutes while the app is connected.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        Text("Rack activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state.recent.isEmpty()) NeonFeatureRow("No activity yet", "Connect your rack to start its local timeline.", Icons.Outlined.History)
        state.recent.take(100).forEach { event ->
            key(event.id) {
                NeonFeatureRow(stringResource(event.type.titleRes()), eventTime(context, event.timestamp), event.type.eventIcon(),
                    accent = event.type.severity.color())
            }
        }
    }
}

@Composable
fun TemperatureChart(points: List<TemperaturePoint>) {
    val cyan = AppColors.Primary
    val grid = AppColors.Border
    GlassCard(Modifier.fillMaxWidth()) {
        if (points.size < 2) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonIcon(Icons.AutoMirrored.Outlined.ShowChart, modifier = Modifier.size(54.dp))
                Text("Waiting for temperature history", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Connect your rack. The trend appears after two measurements, recorded five minutes apart.",
                    color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            val low = points.minOf { it.celsius } - 1f
            val high = points.maxOf { it.celsius } + 1f
            Text(String.format(java.util.Locale.getDefault(), "%.1f°C · latest reading", points.last().celsius), color = cyan, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().height(170.dp)) {
                Column(Modifier.width(36.dp).fillMaxHeight().padding(vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(5) { index ->
                        Text(String.format(java.util.Locale.getDefault(), "%.0f", high - (high - low) * index / 4f),
                            style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                    }
                }
                Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 12.dp)) {
                    repeat(5) { row ->
                        val y = size.height * row / 4f
                        drawLine(grid.copy(alpha = .55f), Offset(0f, y), Offset(size.width, y), .6.dp.toPx())
                    }
                    val duration = (points.last().timestamp - points.first().timestamp).coerceAtLeast(1).toFloat()
                    val line = Path()
                    val positions = points.map { Offset((it.timestamp - points.first().timestamp) / duration * size.width,
                        size.height * (1f - (it.celsius - low) / (high - low))) }
                    positions.forEachIndexed { index, position -> if (index == 0) line.moveTo(position.x, position.y) else line.lineTo(position.x, position.y) }
                    val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(cyan.copy(alpha = .28f), Color.Transparent)))
                    drawPath(line, cyan.copy(alpha = .14f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(line, cyan, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    val last = positions.last()
                    drawCircle(cyan.copy(alpha = .16f), 8.dp.toPx(), last)
                    drawCircle(cyan, 3.dp.toPx(), last)
                }
            }
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d HH:mm")
            fun time(timestamp: Long) = formatter.format(java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time(points.first().timestamp), style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                Text(time(points.last().timestamp), style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            }
        }
    }
}
