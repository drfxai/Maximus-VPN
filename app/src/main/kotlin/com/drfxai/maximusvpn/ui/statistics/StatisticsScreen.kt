package com.drfxai.maximusvpn.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drfxai.maximusvpn.MaximusApplication
import com.drfxai.maximusvpn.data.model.TrafficStats
import com.drfxai.maximusvpn.ui.theme.AppTheme
import com.drfxai.maximusvpn.vpn.VpnController
import com.drfxai.maximusvpn.xray.XrayCoreEngine

class StatisticsViewModel : ViewModel() {
    val connectionState = VpnController.connectionState
    val stats = XrayCoreEngine.getInstance(MaximusApplication.instance).statsFlow
}

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.connectionState.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val colors = AppTheme.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Statistics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

        // Session summary hero card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SESSION", fontSize = 11.sp, color = colors.textMuted, letterSpacing = 2.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = formatDuration(state.connectedDurationSeconds),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                    Text(
                        text = if (state.isConnected) "Connected via ${state.activeProfile?.name ?: "—"}" else "No active session",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    state.activeProfile?.let {
                        Text(
                            "${it.transport.uppercase()} / ${it.security.uppercase()} • ${it.address}:${it.port}",
                            fontSize = 12.sp, color = colors.primary
                        )
                    }
                }
            }
        }

        // Traffic totals
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "DOWNLOAD",
                    value = TrafficStats.formatBytes(stats.rxBytes),
                    sub = TrafficStats.formatSpeed(stats.rxSpeedBps),
                    icon = { Icon(Icons.Filled.ArrowDownward, null, tint = colors.metricDownload) },
                    accent = colors.metricDownload
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "UPLOAD",
                    value = TrafficStats.formatBytes(stats.txBytes),
                    sub = TrafficStats.formatSpeed(stats.txSpeedBps),
                    icon = { Icon(Icons.Filled.ArrowUpward, null, tint = colors.metricUpload) },
                    accent = colors.metricUpload
                )
            }
        }

        // Live throughput + latency
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "LATENCY",
                    value = state.pingMs?.let { "$it ms" } ?: "—",
                    sub = "last measured",
                    icon = { Icon(Icons.Filled.Speed, null, tint = colors.primary) },
                    accent = colors.primary
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "TUNNEL IP",
                    value = state.vpnIp ?: "—",
                    sub = "virtual interface",
                    icon = { Icon(Icons.Filled.Public, null, tint = colors.primaryLight) },
                    accent = colors.primaryLight
                )
            }
        }

        // Errors card when present
        if (state.errorMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.statusError.copy(alpha = 0.08f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("LAST ERROR", fontSize = 11.sp, color = colors.statusError, letterSpacing = 2.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(state.errorMessage ?: "", fontSize = 13.sp, color = colors.statusError)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) } // bottom-bar clearance
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    sub: String,
    icon: @Composable () -> Unit,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.06f), Color.Transparent)))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon()
                    Spacer(Modifier.size(6.dp))
                    Text(title, fontSize = 10.sp, color = AppTheme.colors.textMuted, letterSpacing = 1.5.sp)
                }
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                Text(sub, fontSize = 12.sp, color = AppTheme.colors.textSecondary)
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
