package com.drfxai.maximusvpn.data.model

/**
 * Unified protocol taxonomy for the profile model (v2.0).
 *
 * All protocols are parsed into [com.drfxai.maximusvpn.data.model.VlessProfile]
 * (the historical name was kept so existing Room rows, ViewModels and screens stay
 * source-compatible); the [VlessProfile.protocol] field discriminates the wire protocol.
 */
enum class VpnProtocol(val scheme: String, val label: String) {
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    TROJAN("trojan", "Trojan"),
    SHADOWSOCKS("shadowsocks", "Shadowsocks"),
    HYSTERIA2("hysteria2", "Hysteria2");

    companion object {
        /** Accepts scheme spellings seen in the wild (ss, hy2, hysteria2, trojan-go...). */
        fun fromScheme(raw: String): VpnProtocol? = when (raw.lowercase().trim()) {
            "vless" -> VLESS
            "vmess" -> VMESS
            "trojan", "trojan-go" -> TROJAN
            "ss", "shadowsocks", "shadowsock" -> SHADOWSOCKS
            "hysteria2", "hy2", "hya" -> HYSTERIA2
            else -> null
        }

        fun fromName(name: String): VpnProtocol? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Auto-reconnect behaviour on underlying-network changes.
 * Legacy boolean `autoReconnect=true` maps to [BALANCED].
 */
enum class ReconnectPolicy(val label: String, val description: String) {
    OFF("Off", "Never auto-reconnect; user must reconnect manually"),
    BALANCED("Balanced", "Reconnect when the active network is lost or replaced (default)"),
    AGGRESSIVE("Aggressive", "Reconnect immediately on ANY network change, including Wi-Fi ↔ mobile switches")
}

/** Per-app split tunneling mode applied through VpnService.Builder. */
enum class SplitTunnelMode(val label: String) {
    DISABLED("All apps use VPN"),
    ALLOW_ONLY("Only selected apps use VPN"),
    EXCLUDE("Selected apps bypass VPN")
}

/**
 * Theme selection. Replaces the legacy `darkTheme: Boolean`
 * (legacy true maps to DARK, false to LIGHT).
 */
enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("Pure Black (AMOLED)")
}
