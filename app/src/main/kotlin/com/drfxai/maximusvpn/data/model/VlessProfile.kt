package com.drfxai.maximusvpn.data.model

import java.util.UUID

/**
 * Unified server profile model (v2.0).
 *
 * Historically this represented a single VLESS endpoint; it now carries every
 * Xray-supported outbound protocol via [protocol]. Field reuse across protocols:
 *  - VLESS:       `uuid` = user id
 *  - VMess:       `uuid` = user id, `alterId` = alterId (usually 0), `encryption` = cipher
 *  - Trojan:      `uuid` = password (opaque string, NOT validated as a UUID)
 *  - Shadowsocks: `uuid` = password, `encryption` = method (e.g. aes-256-gcm)
 *  - Hysteria2:   `uuid` = auth password, `obfsPassword` = salamander obfs password
 *
 * NOTE: Hysteria2 imports are accepted for storage/sharing, but Xray-core has no
 * Hysteria2 outbound — attempting to connect surfaces a clear, actionable error
 * instead of silently producing a broken tunnel (fail-closed).
 */
data class VlessProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val encryption: String = "none",
    val transport: String = "tcp", // tcp, ws, grpc, http, h2, quic
    val security: String = "none", // none, tls, reality
    val sni: String = "",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    val flow: String = "",         // xtls-rprx-vision (VLESS only)
    val fingerprint: String = "",  // chrome, firefox, safari, randomized
    val publicKey: String = "",    // REALITY pbk
    val shortId: String = "",      // REALITY sid
    val spiderX: String = "",      // REALITY spx
    val alpn: String = "",         // h2,http/1.1
    val headerType: String = "",   // http, none | salamander (Hy2 obfs marker)
    // --- v2.0 unified-model fields ---
    val protocol: String = VpnProtocol.VLESS.name,
    val alterId: Int = 0,              // VMess only
    val allowInsecure: Boolean = false,// TLS allowInsecure (user opt-in, never default)
    val obfsPassword: String = "",     // Hysteria2 salamander obfs password
    val subscriptionId: String? = null,// owning subscription, null = manually added
    val isFavorite: Boolean = false,
    val lastLatencyMs: Long? = null,
    val lastTestedTimestamp: Long? = null,
    val countryCode: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val protocolEnum: VpnProtocol
        get() = VpnProtocol.fromName(protocol) ?: VpnProtocol.VLESS

    val displaySubtitle: String
        get() = "$address:$port • ${transport.uppercase()}${if (security != "none") "/${security.uppercase()}" else ""}"

    val securityBadge: String
        get() = when {
            security.equals("reality", ignoreCase = true) -> "REALITY"
            security.equals("tls", ignoreCase = true) -> "TLS"
            else -> "PLAIN"
        }

    /**
     * Stable identity used for de-duplication across manual imports and
     * subscription updates: same endpoint + credential + transport = same server.
     */
    val dedupeKey: String
        get() = "${protocolEnum.name.lowercase()}|$address|$port|${uuid.trim()}|" +
                "${transport.lowercase()}|${security.lowercase()}"

    companion object {
        fun protocolBadgeOf(profile: VlessProfile): String = profile.protocolEnum.label
    }
}
