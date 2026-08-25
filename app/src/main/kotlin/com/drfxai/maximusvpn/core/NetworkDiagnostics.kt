package com.drfxai.maximusvpn.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Public-IP / connectivity checks used by Diagnostics.
 *
 * When the VPN is connected, these requests flow through the Xray tunnel (the app's own
 * process is excluded from the TUN, but its sockets are routed through Xray via the tun
 * stack only if the OS routes them so — on Android the app is disallowed from TUN, so we
 * instead report the exit IP as seen through a socket protected by VpnService::protect).
 *
 * In practice: this check tells the user which public IP their traffic exits from.
 * If it shows the ISP IP while "connected", that is a leak indicator surfaced to the user.
 */
object NetworkDiagnostics {

    data class IpCheckResult(
        val ip: String,
        val source: String,
        val latencyMs: Long
    )

    private val IP_SERVICES = listOf(
        "https://api.ipify.org",
        "https://ifconfig.me/ip",
        "https://icanhazip.com"
    )

    /** Fetch public egress IP. Never throws — returns null on failure. */
    suspend fun fetchPublicIp(): IpCheckResult? = withContext(Dispatchers.IO) {
        for (service in IP_SERVICES) {
            try {
                val started = System.currentTimeMillis()
                val conn = URL(service).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "MaximusVPN/1.0")
                if (conn.responseCode == 200) {
                    val ip = conn.inputStream.bufferedReader().readText().trim()
                    val latency = System.currentTimeMillis() - started
                    if (ip.isNotEmpty() && ip.length < 64) {
                        return@withContext IpCheckResult(ip, service, latency)
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Try next service
            }
        }
        null
    }

    /** Raw TCP ping (connect latency) toward host:port. Returns -1 on failure. */
    suspend fun tcpPing(host: String, port: Int): Long = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            val started = System.currentTimeMillis()
            socket.connect(InetSocketAddress(host, port), 4000)
            val elapsed = System.currentTimeMillis() - started
            socket.close()
            elapsed
        } catch (_: Exception) {
            -1L
        }
    }
}
