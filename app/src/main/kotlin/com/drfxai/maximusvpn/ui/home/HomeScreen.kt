package com.drfxai.maximusvpn.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drfxai.maximusvpn.R
import com.drfxai.maximusvpn.data.model.ConnectionStatus
import com.drfxai.maximusvpn.data.model.TrafficStats
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.ui.components.ConnectionButton
import com.drfxai.maximusvpn.ui.components.LatencyPill
import com.drfxai.maximusvpn.ui.components.StatusBadge
import com.drfxai.maximusvpn.ui.components.ThemeToggleSwitch
import com.drfxai.maximusvpn.ui.theme.AppTheme
import com.drfxai.maximusvpn.ui.viewmodel.SettingsViewModel
import com.drfxai.maximusvpn.ui.viewmodel.VpnViewModel
import java.util.Locale

@Composable
fun HomeScreen(
    vpnViewModel: VpnViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToServers: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by vpnViewModel.connectionState.collectAsStateWithLifecycle()
    val activeProfile by vpnViewModel.selectedProfile.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Top Bar: Branding on Left, Theme Toggle Switch on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0E15))
                        .border(1.dp, AppTheme.colors.borderMedium, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_maximus_logo),
                        contentDescription = "Maximus Spartan App Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Maximus",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "DrFXAi • VLESS Tunnel",
                        color = AppTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Top Sun/Moon Interactive Theme Toggle
            ThemeToggleSwitch(
                isDark = settings.darkTheme,
                onThemeChange = { isDark ->
                    settingsViewModel.setDarkTheme(isDark)
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Connection State Pill / Badge Sub-header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(status = connectionState.status)
            LatencyPill(latencyMs = connectionState.pingMs ?: activeProfile?.lastLatencyMs)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error message banner if failed
        AnimatedVisibility(
            visible = connectionState.status == ConnectionStatus.FAILED && connectionState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (AppTheme.colors.isDark) Color(0xFF241416) else Color(0xFFFEF2F2)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.statusError.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = AppTheme.colors.statusError,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = connectionState.errorMessage ?: "Connection failed",
                        color = AppTheme.colors.statusError,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Connection Timer / Uptime Display
        val durationFormatted = formatDuration(connectionState.connectedDurationSeconds)
        Text(
            text = if (connectionState.isConnected) durationFormatted else "00:00:00",
            color = if (connectionState.isConnected) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Text(
            text = if (connectionState.isConnected) "SECURE TUNNEL ACTIVE" else "TAP TO CONNECT",
            color = if (connectionState.isConnected) AppTheme.colors.statusConnected else AppTheme.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Central Power Button
        ConnectionButton(
            status = connectionState.status,
            onClick = {
                val prepareIntent = com.drfxai.maximusvpn.vpn.VpnController.prepareVpn(context)
                if (prepareIntent != null) {
                    onRequestVpnPermission()
                } else {
                    vpnViewModel.toggleConnection(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Active Server Card
        ServerSelectorCard(
            profile = activeProfile,
            pingMs = connectionState.pingMs,
            onClick = onNavigateToServers
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Real-Time Traffic Dashboard Grid
        TrafficDashboardCard(
            uploadBytes = connectionState.uploadBytes,
            downloadBytes = connectionState.downloadBytes,
            uploadSpeedBps = connectionState.uploadSpeedBps,
            downloadSpeedBps = connectionState.downloadSpeedBps,
            vpnIp = connectionState.vpnIp ?: "172.19.0.1",
            isConnected = connectionState.isConnected
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ServerSelectorCard(
    profile: VlessProfile?,
    pingMs: Long?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (AppTheme.colors.isDark) 2.dp else 4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("selected_server_card"),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.surfaceElevated)
                        .border(1.dp, AppTheme.colors.borderMedium, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Server",
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.name ?: "No Server Selected",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = profile?.displaySubtitle ?: "Tap to choose a VLESS server",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LatencyPill(latencyMs = pingMs ?: profile?.lastLatencyMs)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Change Server",
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TrafficDashboardCard(
    uploadBytes: Long,
    downloadBytes: Long,
    uploadSpeedBps: Long,
    downloadSpeedBps: Long,
    vpnIp: String,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (AppTheme.colors.isDark) 2.dp else 4.dp, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "NETWORK TELEMETRY",
                color = AppTheme.colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Download Metric
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.metricDownload)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download",
                            tint = AppTheme.colors.metricDownload,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DOWNLOAD", color = AppTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isConnected) TrafficStats.formatSpeed(downloadSpeedBps) else "0.0 B/s",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Total: ${TrafficStats.formatBytes(downloadBytes)}",
                        color = AppTheme.colors.textMuted,
                        fontSize = 11.sp
                    )
                }

                // Upload Metric
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.metricUpload)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Upload",
                            tint = AppTheme.colors.metricUpload,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("UPLOAD", color = AppTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isConnected) TrafficStats.formatSpeed(uploadSpeedBps) else "0.0 B/s",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Total: ${TrafficStats.formatBytes(uploadBytes)}",
                        color = AppTheme.colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.colors.borderSubtle)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Tunnel IP and Protocol details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Protocol",
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Protocol: VLESS (Xray)",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "IP",
                        tint = AppTheme.colors.metricDownload,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tunnel: $vpnIp",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}
