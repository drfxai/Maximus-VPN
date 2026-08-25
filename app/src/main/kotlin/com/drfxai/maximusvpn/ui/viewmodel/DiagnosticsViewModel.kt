package com.drfxai.maximusvpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drfxai.maximusvpn.MaximusApplication
import com.drfxai.maximusvpn.core.SecretRedactor
import com.drfxai.maximusvpn.data.model.ConnectionStatus
import com.drfxai.maximusvpn.data.model.DiagnosticReport
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.vpn.VpnController
import com.drfxai.maximusvpn.xray.XrayCoreEngine
import com.drfxai.maximusvpn.xray.XrayLogManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsViewModel(
    private val settingsRepository: SettingsRepository = MaximusApplication.instance.settingsRepository,
    private val xrayEngine: XrayCoreEngine = XrayCoreEngine.getInstance(MaximusApplication.instance)
) : ViewModel() {

    val logs: StateFlow<List<String>> = XrayLogManager.logsFlow
    val connectionState = VpnController.connectionState

    /** Public egress IP as seen by external services (null until checked). */
    private val _publicIp = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val publicIp: kotlinx.coroutines.flow.StateFlow<String?> = _publicIp

    /** True when connected AND the observed egress IP differs from the VPN tunnel expectation. */
    private val _possibleLeak = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    val possibleLeak: kotlinx.coroutines.flow.StateFlow<Boolean?> = _possibleLeak

    /** Run a public-IP check through the current network path. */
    fun checkPublicIp() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = com.drfxai.maximusvpn.core.NetworkDiagnostics.fetchPublicIp()
            _publicIp.value = result?.ip
        }
    }

    fun clearLogs() {
        XrayLogManager.clear()
    }

    fun generateDiagnosticReport(): DiagnosticReport {
        val conn = connectionState.value
        val settings = settingsRepository.getSettings()
        val profile = conn.activeProfile

        val profileSummary = if (profile != null) {
            "${profile.name} (${profile.address}:${profile.port} • ${profile.transport.uppercase()}/${profile.security.uppercase()})"
        } else {
            "No active server connected"
        }

        return DiagnosticReport(
            appVersion = "Maximus v1.0.0 (by DrFXAi)",
            vpnServiceRunning = conn.status == ConnectionStatus.CONNECTED,
            xrayCoreRunning = xrayEngine.isRunning(),
            activeServerSummary = profileSummary,
            routingMode = settings.routingMode.title,
            dnsServer = settings.dnsServer,
            networkType = if (conn.isConnected) "Encrypted Tunnel (172.19.0.1)" else "Direct Interface",
            lastLatencyMs = conn.pingMs,
            connectionState = conn.status.name,
            sanitizedLogs = XrayLogManager.getLogs(),
            lastError = conn.errorMessage
        )
    }

    fun formatReportText(report: DiagnosticReport): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.generatedAt))
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("        MAXIMUS VPN DIAGNOSTIC REPORT     ")
        sb.appendLine("               (By DrFXAi)                ")
        sb.appendLine("==========================================")
        sb.appendLine("Timestamp: $dateStr")
        sb.appendLine("App Version: ${report.appVersion}")
        sb.appendLine("VPN Service Status: ${if (report.vpnServiceRunning) "ACTIVE (TUN ESTABLISHED)" else "INACTIVE"}")
        sb.appendLine("Xray Engine Status: ${if (report.xrayCoreRunning) "RUNNING" else "STOPPED"}")
        sb.appendLine("Engine Binary: ${xrayEngine.getVersion()}")
        sb.appendLine("Connection State: ${report.connectionState}")
        sb.appendLine("Active Server: ${report.activeServerSummary}")
        sb.appendLine("Routing Mode: ${report.routingMode}")
        sb.appendLine("Configured DNS: ${report.dnsServer}")
        sb.appendLine("Network Type: ${report.networkType}")
        sb.appendLine("Latency: ${report.lastLatencyMs?.let { "${it}ms" } ?: "N/A"}")
        if (report.lastError != null) {
            sb.appendLine("Last Error: ${report.lastError}")
        }
        sb.appendLine("\n--- SANITIZED LOG TRACE (UUIDs & KEYS REDACTED) ---")
        if (report.sanitizedLogs.isEmpty()) {
            sb.appendLine("[No log entries available]")
        } else {
            report.sanitizedLogs.takeLast(100).forEach { logLine ->
                sb.appendLine(SecretRedactor.redact(logLine))
            }
        }
        sb.appendLine("==========================================")
        return sb.toString()
    }
}
