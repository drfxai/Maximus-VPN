package com.example.vpn.tunnel

import com.example.data.model.AppSettings
import com.example.data.model.RoutingMode
import com.example.data.model.VlessProfile
import com.example.vless.VlessHeader
import com.example.vpn.packet.IPv4Header
import com.example.vpn.packet.PacketBuilder
import com.example.vpn.packet.TcpHeader
import com.example.xray.XrayLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class TcpVlessTunnel(
    private val scope: CoroutineScope,
    private val profile: VlessProfile?,
    private val settings: AppSettings,
    private val protectSocket: (Socket) -> Boolean,
    private val sendToTun: (ByteArray) -> Unit,
    private val onTraffic: (sent: Long, received: Long) -> Unit
) {

    private class TcpSession(
        val key: String,
        val clientIp: ByteArray,
        val serverIp: ByteArray,
        val clientPort: Int,
        val serverPort: Int,
        var clientSeq: Long,
        var ourSeq: Long,
        var isVless: Boolean = false,
        var socket: Socket? = null,
        var outStream: OutputStream? = null,
        var inStream: InputStream? = null,
        @Volatile var isConnected: Boolean = false,
        @Volatile var isClosed: Boolean = false,
        @Volatile var lastActiveTime: Long = System.currentTimeMillis(),
        val outgoingChannel: Channel<ByteArray> = Channel(Channel.UNLIMITED),
        var writerJob: Job? = null,
        var readerJob: Job? = null
    )

    private val sessions = ConcurrentHashMap<String, TcpSession>()
    private val isnGenerator = AtomicLong(100000L)
    private var cleanupJob: Job? = null

    init {
        startSessionCleanupWatcher()
    }

    fun handleTcpPacket(
        ipHeader: IPv4Header,
        tcpHeader: TcpHeader,
        packetData: ByteArray
    ) {
        val key = "${ipHeader.srcIpStr}:${tcpHeader.srcPort}->${ipHeader.dstIpStr}:${tcpHeader.dstPort}"

        if (tcpHeader.isRst) {
            closeSession(key)
            return
        }

        if (tcpHeader.isSyn && !tcpHeader.isAck) {
            // New TCP Connection Request
            handleSyn(key, ipHeader, tcpHeader)
            return
        }

        val session = sessions[key]
        if (session == null) {
            if (!tcpHeader.isRst && !tcpHeader.isFin) {
                // Send RST if no active session
                sendRst(ipHeader, tcpHeader)
            }
            return
        }

        session.lastActiveTime = System.currentTimeMillis()

        if (tcpHeader.isFin) {
            handleFin(session, tcpHeader)
            return
        }

        // Handle incoming data or ACK from client
        val payloadLen = tcpHeader.payloadLength
        if (payloadLen > 0) {
            handleData(session, tcpHeader, packetData)
        }
    }

    private fun handleSyn(
        key: String,
        ipHeader: IPv4Header,
        tcpHeader: TcpHeader
    ) {
        val clientInitialSeq = tcpHeader.sequenceNumber
        val ourInitialSeq = isnGenerator.addAndGet(20000L)

        val session = TcpSession(
            key = key,
            clientIp = ipHeader.srcIp,
            serverIp = ipHeader.dstIp,
            clientPort = tcpHeader.srcPort,
            serverPort = tcpHeader.dstPort,
            clientSeq = clientInitialSeq + 1,
            ourSeq = ourInitialSeq + 1
        )
        sessions[key] = session

        // 1. Respond with SYN + ACK
        val synAckPacket = PacketBuilder.buildTcpPacket(
            srcIp = session.serverIp,
            dstIp = session.clientIp,
            srcPort = session.serverPort,
            dstPort = session.clientPort,
            seq = ourInitialSeq,
            ack = session.clientSeq,
            flags = 0x12, // SYN | ACK
            windowSize = 65535
        )
        sendToTun(synAckPacket)

        // 2. Establish Upstream Connection asynchronously
        scope.launch(Dispatchers.IO) {
            establishUpstream(session, ipHeader.dstIpStr, tcpHeader.dstPort)
        }
    }

    private fun handleData(
        session: TcpSession,
        tcpHeader: TcpHeader,
        packetData: ByteArray
    ) {
        val payloadLen = tcpHeader.payloadLength
        if (payloadLen <= 0) return

        val payload = ByteArray(payloadLen)
        System.arraycopy(packetData, tcpHeader.payloadOffset, payload, 0, payloadLen)

        // Monotonically advance client sequence number to match expected TCP stream
        val nextExpectedSeq = tcpHeader.sequenceNumber + payloadLen
        if (nextExpectedSeq > session.clientSeq) {
            session.clientSeq = nextExpectedSeq
        }

        // 1. Send ACK back to client immediately so sender window progresses smoothly
        val ackPacket = PacketBuilder.buildTcpPacket(
            srcIp = session.serverIp,
            dstIp = session.clientIp,
            srcPort = session.serverPort,
            dstPort = session.clientPort,
            seq = session.ourSeq,
            ack = session.clientSeq,
            flags = 0x10, // ACK
            windowSize = 65535
        )
        sendToTun(ackPacket)

        // 2. Queue payload for upstream delivery (guaranteed delivery without race condition)
        session.outgoingChannel.trySend(payload)
    }

    private fun handleFin(session: TcpSession, tcpHeader: TcpHeader) {
        val nextExpectedSeq = tcpHeader.sequenceNumber + 1
        if (nextExpectedSeq > session.clientSeq) {
            session.clientSeq = nextExpectedSeq
        }

        // Send FIN + ACK
        val finAckPacket = PacketBuilder.buildTcpPacket(
            srcIp = session.serverIp,
            dstIp = session.clientIp,
            srcPort = session.serverPort,
            dstPort = session.clientPort,
            seq = session.ourSeq,
            ack = session.clientSeq,
            flags = 0x11, // FIN | ACK
            windowSize = 65535
        )
        sendToTun(finAckPacket)

        closeSession(session.key)
    }

    private fun sendRst(ipHeader: IPv4Header, tcpHeader: TcpHeader) {
        val rstPacket = PacketBuilder.buildTcpPacket(
            srcIp = ipHeader.dstIp,
            dstIp = ipHeader.srcIp,
            srcPort = tcpHeader.dstPort,
            dstPort = tcpHeader.srcPort,
            seq = if (tcpHeader.isAck) tcpHeader.ackNumber else 0L,
            ack = tcpHeader.sequenceNumber + 1,
            flags = 0x14, // RST | ACK
            windowSize = 0
        )
        sendToTun(rstPacket)
    }

    private suspend fun establishUpstream(
        session: TcpSession,
        destIpStr: String,
        destPort: Int
    ) {
        var rawSocket: Socket? = null
        try {
            val isDirectRouting = (settings.routingMode == RoutingMode.RULE_BYPASS_LAN && isLanIp(destIpStr))

            if (profile != null && !isDirectRouting) {
                // Route through VLESS Server
                rawSocket = Socket()
                protectSocket(rawSocket)
                rawSocket.tcpNoDelay = true
                rawSocket.soTimeout = 0
                rawSocket.connect(InetSocketAddress(profile.address, profile.port), 6000)

                var activeSocket = rawSocket
                val isTls = profile.security.equals("tls", ignoreCase = true) ||
                        profile.security.equals("reality", ignoreCase = true)

                if (isTls) {
                    val sslContext = createTrustAllSslContext()
                    val sniHost = if (profile.sni.isNotBlank()) profile.sni else profile.address
                    val sslSocket = sslContext.socketFactory.createSocket(
                        rawSocket,
                        sniHost,
                        profile.port,
                        true
                    ) as SSLSocket
                    try {
                        sslSocket.sslParameters = javax.net.ssl.SSLParameters().apply {
                            if (sniHost.isNotBlank()) {
                                serverNames = listOf(javax.net.ssl.SNIHostName(sniHost))
                            }
                        }
                    } catch (_: Exception) {}
                    sslSocket.startHandshake()
                    activeSocket = sslSocket
                }

                val outStream = activeSocket.getOutputStream()
                val inStream = activeSocket.getInputStream()

                // Send VLESS Request Header immediately
                val uuidBytes = VlessHeader.uuidToBytes(profile.uuid)
                val vlessReq = VlessHeader.encodeRequest(
                    uuidBytes = uuidBytes,
                    command = VlessHeader.COMMAND_TCP,
                    destPort = destPort,
                    destAddress = destIpStr
                )
                outStream.write(vlessReq)
                outStream.flush()

                session.isVless = true
                session.socket = activeSocket
                session.outStream = outStream
                session.inStream = inStream
                session.isConnected = true

                startPumping(session)
            } else {
                // Direct Outbound
                rawSocket = Socket()
                protectSocket(rawSocket)
                rawSocket.tcpNoDelay = true
                rawSocket.soTimeout = 0
                rawSocket.connect(InetSocketAddress(destIpStr, destPort), 5000)

                session.isVless = false
                session.socket = rawSocket
                session.outStream = rawSocket.getOutputStream()
                session.inStream = rawSocket.getInputStream()
                session.isConnected = true

                startPumping(session)
            }
        } catch (e: Exception) {
            // If VLESS connection failed, try transparent direct fallback
            tryDirectFallback(session, destIpStr, destPort)
        }
    }

    private fun tryDirectFallback(session: TcpSession, destIpStr: String, destPort: Int) {
        try {
            val fallbackSocket = Socket()
            protectSocket(fallbackSocket)
            fallbackSocket.tcpNoDelay = true
            fallbackSocket.soTimeout = 0
            fallbackSocket.connect(InetSocketAddress(destIpStr, destPort), 4000)

            session.isVless = false
            session.socket = fallbackSocket
            session.outStream = fallbackSocket.getOutputStream()
            session.inStream = fallbackSocket.getInputStream()
            session.isConnected = true

            startPumping(session)
        } catch (_: Exception) {
            closeSession(session.key)
        }
    }

    private fun startPumping(session: TcpSession) {
        val outStream = session.outStream ?: return
        val inStream = session.inStream ?: return

        // 1. Upstream Writer (Device -> Server)
        session.writerJob = scope.launch(Dispatchers.IO) {
            try {
                for (chunk in session.outgoingChannel) {
                    if (session.isClosed || !session.isConnected) break
                    outStream.write(chunk)
                    outStream.flush()
                    session.lastActiveTime = System.currentTimeMillis()
                    onTraffic(chunk.size.toLong(), 0L)
                }
            } catch (_: Exception) {
                closeSession(session.key)
            }
        }

        // 2. Upstream Reader (Server -> Device / Download stream)
        session.readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(16384)

            try {
                // If VLESS, strip the initial 2-byte server response header (Version + Addons length)
                if (session.isVless) {
                    val version = inStream.read()
                    if (version == -1) return@launch
                    val addonLen = inStream.read()
                    if (addonLen == -1) return@launch
                    if (addonLen > 0) {
                        var skipped = 0L
                        while (skipped < addonLen && isActive) {
                            val s = inStream.skip(addonLen.toLong() - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                    }
                }

                // Stream downloaded data continuously into MTU-safe TCP packets
                while (isActive && session.isConnected && !session.isClosed) {
                    val bytesRead = inStream.read(buffer)
                    if (bytesRead <= 0) break

                    session.lastActiveTime = System.currentTimeMillis()
                    onTraffic(0L, bytesRead.toLong())

                    var offset = 0
                    while (offset < bytesRead && isActive && !session.isClosed) {
                        val chunkSize = (bytesRead - offset).coerceAtMost(1360)
                        val chunk = ByteArray(chunkSize)
                        System.arraycopy(buffer, offset, chunk, 0, chunkSize)

                        val dataPacket = PacketBuilder.buildTcpPacket(
                            srcIp = session.serverIp,
                            dstIp = session.clientIp,
                            srcPort = session.serverPort,
                            dstPort = session.clientPort,
                            seq = session.ourSeq,
                            ack = session.clientSeq,
                            flags = 0x18, // PSH | ACK
                            windowSize = 65535,
                            payload = chunk
                        )

                        session.ourSeq += chunkSize
                        offset += chunkSize
                        sendToTun(dataPacket)
                    }
                }
            } catch (_: Exception) {
                // Connection closed or EOF
            } finally {
                if (!session.isClosed) {
                    // Send FIN to client to signal clean completion of download/stream
                    val finPacket = PacketBuilder.buildTcpPacket(
                        srcIp = session.serverIp,
                        dstIp = session.clientIp,
                        srcPort = session.serverPort,
                        dstPort = session.clientPort,
                        seq = session.ourSeq,
                        ack = session.clientSeq,
                        flags = 0x11, // FIN | ACK
                        windowSize = 65535
                    )
                    sendToTun(finPacket)
                }
                closeSession(session.key)
            }
        }
    }

    private fun closeSession(key: String) {
        val session = sessions.remove(key) ?: return
        session.isClosed = true
        session.isConnected = false
        session.outgoingChannel.close()
        session.writerJob?.cancel()
        session.readerJob?.cancel()
        try { session.outStream?.close() } catch (_: Exception) {}
        try { session.inStream?.close() } catch (_: Exception) {}
        try { session.socket?.close() } catch (_: Exception) {}
    }

    private fun startSessionCleanupWatcher() {
        cleanupJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30000)
                val now = System.currentTimeMillis()
                val iterator = sessions.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value.lastActiveTime > 60000) {
                        entry.value.isClosed = true
                        entry.value.isConnected = false
                        entry.value.outgoingChannel.close()
                        entry.value.writerJob?.cancel()
                        entry.value.readerJob?.cancel()
                        try { entry.value.socket?.close() } catch (_: Exception) {}
                        iterator.remove()
                    }
                }
            }
        }
    }

    fun closeAll() {
        cleanupJob?.cancel()
        val keys = sessions.keys().toList()
        for (k in keys) {
            closeSession(k)
        }
    }

    private fun isLanIp(ip: String): Boolean {
        return ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.startsWith("172.16.") ||
                ip.startsWith("172.17.") ||
                ip.startsWith("172.18.") ||
                ip.startsWith("172.19.") ||
                ip.startsWith("127.")
    }

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
}

