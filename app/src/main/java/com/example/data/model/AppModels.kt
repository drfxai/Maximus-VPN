package com.example.data.model

data class TrafficStats(
    val txBytes: Long = 0,
    val rxBytes: Long = 0,
    val txSpeedBps: Long = 0,
    val rxSpeedBps: Long = 0
) {
    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
            return String.format(
                java.util.Locale.US,
                "%.1f %sB",
                bytes.toDouble() / (1L shl (z * 10)),
                " KMGTPE"[z]
            )
        }

        fun formatSpeed(bytesPerSec: Long): String {
            return "${formatBytes(bytesPerSec)}/s"
        }
    }
}

sealed class ServerTestStatus {
    data class Available(val latencyMs: Long) : ServerTestStatus()
    data class Slow(val latencyMs: Long) : ServerTestStatus()
    data class Unavailable(val reason: String) : ServerTestStatus()
    data class InvalidConfig(val error: String) : ServerTestStatus()
    object Testing : ServerTestStatus()
    object Idle : ServerTestStatus()
}

data class ServerTestResult(
    val serverId: String,
    val status: ServerTestStatus,
    val checkedAt: Long = System.currentTimeMillis()
)

enum class RoutingMode(val title: String, val description: String) {
    GLOBAL("Global Proxy", "Route all device network traffic through the VLESS tunnel"),
    RULE_BYPASS_LAN("Bypass LAN & Direct Local", "Bypass local private subnets (192.168.x, 10.x) and route international traffic"),
    BYPASS_SELECTED("Custom Bypass List", "Bypass traffic matching specific user-defined domain and IP lists")
}

data class AppSettings(
    val darkTheme: Boolean = true,
    val routingMode: RoutingMode = RoutingMode.RULE_BYPASS_LAN,
    val dnsServer: String = "1.1.1.1",
    val customDns: String = "8.8.8.8",
    val killSwitchEnabled: Boolean = false,
    val ipv6Enabled: Boolean = true,
    val autoReconnect: Boolean = true,
    val autoConnectOnBoot: Boolean = false,
    val logLevel: String = "warning",
    val selectedProfileId: String? = null,
    val mtu: Int = 1500,
    val customBypassRules: String = "localhost,127.0.0.1,*.local,*.lan"
)

data class DiagnosticReport(
    val generatedAt: Long = System.currentTimeMillis(),
    val appVersion: String,
    val vpnServiceRunning: Boolean,
    val xrayCoreRunning: Boolean,
    val activeServerSummary: String,
    val routingMode: String,
    val dnsServer: String,
    val networkType: String,
    val lastLatencyMs: Long?,
    val connectionState: String,
    val sanitizedLogs: List<String>,
    val lastError: String?
)
