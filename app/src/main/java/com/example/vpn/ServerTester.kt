package com.example.vpn

import com.example.data.model.ServerTestResult
import com.example.data.model.ServerTestStatus
import com.example.data.model.VlessProfile
import com.example.vless.VlessValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ServerTester {

    private fun createTrustAllSslContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext
    }

    /**
     * Performs a real multi-stage connectivity and latency test against the remote VLESS endpoint.
     * Tests: 1. Configuration validity, 2. DNS resolution, 3. TCP 3-way handshake, 4. TLS/REALITY handshake if applicable.
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

            // Stage 3: TLS / Handshake Test if configured
            val finalLatency = if (profile.security.equals("tls", ignoreCase = true) || profile.security.equals("reality", ignoreCase = true)) {
                val sslContext = createTrustAllSslContext()
                val sslFactory = sslContext.socketFactory
                val sniHost = if (profile.sni.isNotBlank()) profile.sni else profile.address
                sslSocket = sslFactory.createSocket(socket, sniHost, profile.port, true) as SSLSocket
                sslSocket.soTimeout = timeoutMs

                val sslParams = SSLParameters().apply {
                    if (sniHost.isNotBlank()) {
                        serverNames = listOf(SNIHostName(sniHost))
                    }
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
