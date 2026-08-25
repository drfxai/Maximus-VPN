package com.drfxai.maximusvpn.data.model

import java.util.UUID

/**
 * Strongly typed representation of a VLESS server configuration.
 */
data class VlessProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val encryption: String = "none",
    val transport: String = "tcp", // tcp, ws, grpc, http, h2, quic
    val security: String = "none",  // none, tls, reality
    val sni: String = "",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    val flow: String = "",         // xtls-rprx-vision
    val fingerprint: String = "",  // chrome, firefox, safari, randomized
    val publicKey: String = "",    // REALITY pbk
    val shortId: String = "",      // REALITY sid
    val spiderX: String = "",      // REALITY spx
    val alpn: String = "",         // h2,http/1.1
    val headerType: String = "",   // http, none
    val isFavorite: Boolean = false,
    val lastLatencyMs: Long? = null,
    val lastTestedTimestamp: Long? = null,
    val countryCode: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val displaySubtitle: String
        get() = "$address:$port • ${transport.uppercase()}${if (security != "none") "/${security.uppercase()}" else ""}"

    val securityBadge: String
        get() = when {
            security.equals("reality", ignoreCase = true) -> "REALITY"
            security.equals("tls", ignoreCase = true) -> "TLS"
            else -> "PLAIN"
        }
}
