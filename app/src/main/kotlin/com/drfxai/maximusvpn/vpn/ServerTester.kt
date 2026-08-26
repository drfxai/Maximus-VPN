package com.drfxai.maximusvpn.vpn

import com.drfxai.maximusvpn.data.model.ServerTestResult
import com.drfxai.maximusvpn.data.model.ServerTestStatus
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.vless.VlessValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket

object ServerTester {

    /**
     * Performs a connectivity and latency test against the remote endpoint.
     * Tests: 1. Configuration validity, 2. DNS resolution, 3. TCP handshake, and
     * 4. normal TLS handshake when security=tls. REALITY is intentionally TCP-only:
     * an ordinary X509 TLS handshake is not a valid REALITY compatibility test.
     */
    suspend fun testServer(profile: VlessProfile, timeoutMs: Int = 4000): ServerTestResult = withContext(Dispatchers.IO) {
        try {
            VlessValidator.validate(profile)
        } catch (e: Exception) {
            return@withContext ServerTestResult(
                serverId = profile.id,
                status = ServerTestStatus.InvalidConfig(e.localizedMessage ?: "Invalid configuration")
            )
        }

        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        var sslSocket: SSLSocket? = null

        try {
            // Stage 1: DNS Resolution
            val inetAddress = InetAddress.getByName(profile.address)

            // Stage 2: TCP Handshake
            socket = Socket()
            socket.soTimeout = timeoutMs
            val socketAddress = InetSocketAddress(inetAddress, profile.port)
            socket.connect(socketAddress, timeoutMs)

            val tcpLatency = System.currentTimeMillis() - startTime

            // Stage 3: normal TLS handshake only. REALITY uses Xray's own handshake
            // and cannot be validated by a stock SSLSocket.
            val finalLatency = if (profile.security.equals("tls", ignoreCase = true)) {
                val sslFactory = SSLContext.getDefault().socketFactory
                val sniHost = if (profile.sni.isNotBlank()) profile.sni else profile.address
                sslSocket = sslFactory.createSocket(socket, sniHost, profile.port, true) as SSLSocket
                sslSocket.soTimeout = timeoutMs

                val sslParams = SSLParameters().apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    if (sniHost.isNotBlank()) serverNames = listOf(SNIHostName(sniHost))
                }
                sslSocket.sslParameters = sslParams
                sslSocket.startHandshake()
                System.currentTimeMillis() - startTime
            } else {
                tcpLatency
            }

            val status = if (finalLatency < 350) {
                ServerTestStatus.Available(finalLatency)
            } else {
                ServerTestStatus.Slow(finalLatency)
            }

            ServerTestResult(serverId = profile.id, status = status)
        } catch (e: java.net.SocketTimeoutException) {
            ServerTestResult(
                serverId = profile.id,
                status = ServerTestStatus.Unavailable("Connection timed out (${timeoutMs}ms)")
            )
        } catch (e: java.net.UnknownHostException) {
            ServerTestResult(
                serverId = profile.id,
                status = ServerTestStatus.Unavailable("DNS resolution failed for '${profile.address}'")
            )
        } catch (e: Exception) {
            ServerTestResult(
                serverId = profile.id,
                status = ServerTestStatus.Unavailable(e.localizedMessage ?: "Connection refused")
            )
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
