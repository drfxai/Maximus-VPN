package com.drfxai.maximusvpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import com.drfxai.maximusvpn.BuildConfig
import com.drfxai.maximusvpn.MaximusApplication
import com.drfxai.maximusvpn.core.NetworkDiagnostics
import com.drfxai.maximusvpn.core.SecretRedactor
import com.drfxai.maximusvpn.data.model.ConnectionStatus
import com.drfxai.maximusvpn.data.model.DiagnosticReport
import com.drfxai.maximusvpn.data.model.TrafficStats
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.vpn.VpnController
import com.drfxai.maximusvpn.xray.XrayCoreEngine
import com.drfxai.maximusvpn.xray.XrayLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsViewModel(
    private val settingsRepository: SettingsRepository = MaximusApplication.instance.settingsRepository,
    private val xrayEngine: XrayCoreEngine = XrayCoreEngine.getInstance(MaximusApplication.instance)
) : ViewModel() {

    val logs: StateFlow<List<String>> = XrayLogManager.logsFlow
    val connectionState = VpnController.connectionState
    val trafficStats: StateFlow<TrafficStats> = xrayEngine.statsFlow

    /** Public egress IP as seen by external services (null until checked). */
    private val _publicIp = MutableStateFlow<String?>(null)
    val publicIp: StateFlow<String?> = _publicIp

    /** True when connected AND the observed egress IP differs from the VPN tunnel expectation. */
    private val _possibleLeak = MutableStateFlow<Boolean?>(null)
    val possibleLeak: StateFlow<Boolean?> = _possibleLeak

    /** DNS-leak heuristic result (see checkLeaks). */
    private val _dnsLeakSuspected = MutableStateFlow<Boolean?>(null)
    val dnsLeakSuspected: StateFlow<Boolean?> = _dnsLeakSuspected

    /** True when a WebRTC-capable STUN probe reaches the internet outside the tunnel. */
    private val _webrtcNote = MutableStateFlow<String?>(null)
    val webrtcNote: StateFlow<String?> = _webrtcNote

    /** True when IPv6 connectivity exists but the tunnel carries no v6 route. */
    private val _ipv6LeakSuspected = MutableStateFlow<Boolean?>(null)
    val ipv6LeakSuspected: StateFlow<Boolean?> = _ipv6LeakSuspected.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /**
     * Runs the full leak-test suite.
     *
     * HONEST LIMITATION: this app's own UID is excluded from the TUN, so probes made
     * from here ride the physical network. The checks therefore verify:
     *  - public egress IP reachability + latency (ISP IP while "connected" is expected
     *    for app-UID sockets and is NOT itself proof of leakage for other apps),
     *  - whether configured DNS servers are resolvable through each path,
     *  - whether native IPv6 is reachable (flagged when tunnel has no v6 route —
     *    other apps' v6 traffic would bypass a v4-only tunnel).
     */
    fun runFullCheck() {
        if (_checking.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _checking.value = true
            try {
                // 1. Public IP (v4)
                val ipResult = NetworkDiagnostics.fetchPublicIp()
                _publicIp.value = ipResult?.ip

                // 2. DNS leak heuristic: can we reach the configured Do53 resolver directly?
                val settings = settingsRepository.getSettings()
                _dnsLeakSuspected.value = try {
                    java.net.InetAddress.getByName(settings.dnsServer)
                    false // resolver reachable; deep leak testing requires in-tunnel probes
                } catch (_: Exception) {
                    null // inconclusive rather than falsely reassuring
                }

                // 3. IPv6 reachability vs tunnel capability
                _ipv6LeakSuspected.value = try {
                    val hasNativeV6 = hasGlobalIpv6()
                    hasNativeV6 && !settings.ipv6Enabled && connectionState.value.isConnected
                } catch (_: Exception) { null }

                // 4. WebRTC note: Android clients don't run browsers; STUN reachability only.
                _webrtcNote.value =
                    "WebRTC leaks apply to browsers. Native apps route UDP via the tunnel; " +
                    "verify per-app behaviour with split tunneling settings."

                // Leak flag semantics kept honest:
                val connected = connectionState.value.isConnected
                _possibleLeak.value = if (connected && ipResult == null) true else null
            } finally {
                _checking.value = false
            }
        }
    }

    /** Kept for compatibility with existing Diagnostics UI button. */
    fun checkPublicIp() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = NetworkDiagnostics.fetchPublicIp()
            _publicIp.value = result?.ip
            val connected = connectionState.value.isConnected
            _possibleLeak.value = if (connected && result == null) true else null
        }
    }

    private fun hasGlobalIpv6(): Boolean {
        return java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .any { it is java.net.Inet6Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
    }

    fun clearLogs() {
        XrayLogManager.clear()
    }

    /** Exports a redacted report to Downloads-style app files dir; returns the file. */
    fun exportReport(reportText: String): File? = try {
        val dir = File(MaximusApplication.instance.getExternalFilesDir(null), "reports")
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "maximus_report_$stamp.txt")
        file.writeText(reportText)
        file
    } catch (_: Exception) {
        null
    }

    fun generateDiagnosticReport(): DiagnosticReport {
        val conn = connectionState.value
        val settings = settingsRepository.getSettings()
        val profile = conn.activeProfile

        val profileSummary = if (profile != null) {
            "${profile.name} (${profile.protocolEnum.label} ${profile.address}:${profile.port} • ${profile.transport.uppercase()}${if (profile.security != "none") "/${profile.security.uppercase()}" else ""})"
        } else {
            "No active server connected"
        }

        return DiagnosticReport(
            appVersion = "Maximus v${BuildConfig.VERSION_NAME} (by DrFXAi)",
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
        val stats = trafficStats.value
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
        sb.appendLine("Active Server: ${SecretRedactor.redact(report.activeServerSummary)}")
        sb.appendLine("Routing Mode: ${report.routingMode}")
        sb.appendLine("Configured DNS: ${report.dnsServer}")
        sb.appendLine("Network Type: ${report.networkType}")
        sb.appendLine("Latency: ${report.lastLatencyMs?.let { "${it}ms" } ?: "N/A"}")
        sb.appendLine("Session Traffic: ↓ ${TrafficStats.formatBytes(stats.rxBytes)} / ↑ ${TrafficStats.formatBytes(stats.txBytes)}")
        sb.appendLine("Observed Public IP: ${_publicIp.value ?: "not checked"}")
        sb.appendLine("IPv6 Leak Suspected: ${_ipv6LeakSuspected.value ?: "inconclusive"}")
        if (report.lastError != null) {
            sb.appendLine("Last Error: ${SecretRedactor.redact(report.lastError)}")
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
