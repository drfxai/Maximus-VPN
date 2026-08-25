package com.drfxai.maximusvpn.ui.diagnostics

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drfxai.maximusvpn.ui.components.ThemeToggleSwitch
import com.drfxai.maximusvpn.ui.theme.AppTheme
import com.drfxai.maximusvpn.ui.viewmodel.DiagnosticsViewModel
import com.drfxai.maximusvpn.ui.viewmodel.SettingsViewModel

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val connState by viewModel.connectionState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var logSearchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, logSearchQuery) {
        if (logSearchQuery.isBlank()) logs
        else logs.filter { it.contains(logSearchQuery, ignoreCase = true) }
    }

    LaunchedEffect(logs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Screen Top Bar with Title, Export, and Theme Toggle Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Diagnostics & Logs", color = AppTheme.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Real-time engine telemetry", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
            }

            ThemeToggleSwitch(
                isDark = settings.darkTheme,
                onThemeChange = { isDark ->
                    settingsViewModel.setDarkTheme(isDark)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Export Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENGINE TELEMETRY",
                color = AppTheme.colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Button(
                onClick = {
                    val report = viewModel.generateDiagnosticReport()
                    val text = viewModel.formatReportText(report)
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "Sanitized report copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("export_diagnostics_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Report", color = AppTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // System Status Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DiagnosticItem("VpnService", if (connState.isConnected) "ACTIVE" else "IDLE", if (connState.isConnected) AppTheme.colors.statusConnected else AppTheme.colors.textMuted)
                    DiagnosticItem("Xray Engine", if (connState.isConnected) "RUNNING" else "STANDBY", if (connState.isConnected) AppTheme.colors.primary else AppTheme.colors.textMuted)
                    DiagnosticItem("Tunnel IP", connState.vpnIp ?: "None", AppTheme.colors.textPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DiagnosticItem("Active Server", connState.activeProfile?.name ?: "Disconnected", AppTheme.colors.textPrimary)
                    DiagnosticItem("Latency", connState.pingMs?.let { "${it}ms" } ?: "N/A", AppTheme.colors.statusWarning)
                    DiagnosticItem("Redaction", "ACTIVE (STRICT)", AppTheme.colors.statusConnected)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Log Controls Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedTextField(
                value = logSearchQuery,
                onValueChange = { logSearchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                placeholder = { Text("Filter logs...", color = AppTheme.colors.textMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (logSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { logSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTheme.colors.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppTheme.colors.surfaceCard,
                    unfocusedContainerColor = AppTheme.colors.surfaceCard,
                    focusedBorderColor = AppTheme.colors.primary,
                    unfocusedBorderColor = AppTheme.colors.borderSubtle,
                    focusedTextColor = AppTheme.colors.textPrimary,
                    unfocusedTextColor = AppTheme.colors.textPrimary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    val allLogsText = logs.joinToString("\n")
                    clipboardManager.setText(AnnotatedString(allLogsText))
                    Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.colors.surfaceCard)
                    .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = AppTheme.colors.primary, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = { viewModel.clearLogs() },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.colors.surfaceCard)
                    .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = AppTheme.colors.statusError, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Log Console Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.consoleBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (logSearchQuery.isNotBlank()) "No matching logs found" else "No log entries captured yet.\nConnect to a server to see live Xray trace.",
                        color = AppTheme.colors.textMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs) { logLine ->
                        val textColor = when {
                            logLine.contains("[ERROR]") -> AppTheme.colors.statusError
                            logLine.contains("[WARN]") -> AppTheme.colors.statusWarning
                            logLine.contains("[CONFIG]") -> AppTheme.colors.metricDownload
                            logLine.contains("[TUNNEL]") -> AppTheme.colors.primary
                            else -> if (AppTheme.colors.isDark) AppTheme.colors.textSecondary else Color(0xFF94A3B8)
                        }
                        Text(
                            text = logLine,
                            color = textColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticItem(title: String, value: String, color: Color) {
    Column {
        Text(title, color = AppTheme.colors.textMuted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
