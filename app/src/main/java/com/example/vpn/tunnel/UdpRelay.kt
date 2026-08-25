package com.example.vpn.tunnel

import com.example.data.model.AppSettings
import com.example.data.model.VlessProfile
import com.example.vpn.packet.IPv4Header
import com.example.vpn.packet.PacketBuilder
import com.example.vpn.packet.UdpHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class UdpRelay(
    private val scope: CoroutineScope,
    private val profile: VlessProfile?,
    private val settings: AppSettings,
    private val protectDatagram: (DatagramSocket) -> Boolean,
    private val sendToTun: (ByteArray) -> Unit,
    private val onTraffic: (sent: Long, received: Long) -> Unit
) {

    private class UdpSession(
        val key: String,
        val socket: DatagramSocket,
        val clientIp: ByteArray,
        val serverIp: ByteArray,
        val clientPort: Int,
        val serverPort: Int,
        var lastActiveTime: Long = System.currentTimeMillis(),
        var listenJob: Job? = null
    )

    private val sessions = ConcurrentHashMap<String, UdpSession>()
    private var cleanupJob: Job? = null

    init {
        cleanupJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(20000)
                val now = System.currentTimeMillis()
                val iterator = sessions.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value.lastActiveTime > 30000) {
                        entry.value.listenJob?.cancel()
                        try { entry.value.socket.close() } catch (_: Exception) {}
                        iterator.remove()
                    }
                }
            }
        }
    }

    fun handleUdpPacket(
        ipHeader: IPv4Header,
        udpHeader: UdpHeader,
        packetData: ByteArray
    ) {
        val payloadLen = udpHeader.payloadLength
        if (payloadLen <= 0) return

        val payload = ByteArray(payloadLen)
        System.arraycopy(packetData, udpHeader.payloadOffset, payload, 0, payloadLen)

        val key = "${ipHeader.srcIpStr}:${udpHeader.srcPort}->${ipHeader.dstIpStr}:${udpHeader.dstPort}"

        var session = sessions[key]
        if (session == null) {
            try {
                val socket = DatagramSocket()
                protectDatagram(socket)
                socket.soTimeout = 10000

                session = UdpSession(
                    key = key,
                    socket = socket,
                    clientIp = ipHeader.srcIp,
                    serverIp = ipHeader.dstIp,
                    clientPort = udpHeader.srcPort,
                    serverPort = udpHeader.dstPort
                )
                sessions[key] = session
                startListening(session)
            } catch (_: Exception) {
                return
            }
        }

        session.lastActiveTime = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            try {
                val targetAddr = InetAddress.getByName(ipHeader.dstIpStr)
                val datagram = DatagramPacket(payload, payload.size, targetAddr, udpHeader.dstPort)
                session.socket.send(datagram)
                onTraffic(payload.size.toLong(), 0L)
            } catch (_: Exception) {
                // Ignore send failures
            }
        }
    }

    private fun startListening(session: UdpSession) {
        session.listenJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            try {
                while (isActive) {
                    session.socket.receive(packet)
                    val len = packet.length
                    if (len > 0) {
                        session.lastActiveTime = System.currentTimeMillis()
                        onTraffic(0L, len.toLong())

                        val responseData = ByteArray(len)
                        System.arraycopy(buffer, 0, responseData, 0, len)

                        val respIpPacket = PacketBuilder.buildUdpPacket(
                            srcIp = session.serverIp,
                            dstIp = session.clientIp,
                            srcPort = session.serverPort,
                            dstPort = session.clientPort,
                            payload = responseData
                        )
                        sendToTun(respIpPacket)
                    }
                }
            } catch (_: Exception) {
                // Timeout or socket closed
            }
        }
    }

    fun closeAll() {
        cleanupJob?.cancel()
        val keys = sessions.keys().toList()
        for (k in keys) {
            val s = sessions.remove(k)
            s?.listenJob?.cancel()
            try { s?.socket?.close() } catch (_: Exception) {}
        }
    }
}
