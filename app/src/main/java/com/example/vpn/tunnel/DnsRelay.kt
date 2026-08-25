package com.example.vpn.tunnel

import com.example.vpn.packet.IPv4Header
import com.example.vpn.packet.PacketBuilder
import com.example.vpn.packet.UdpHeader
import com.example.xray.XrayLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DnsRelay(
    private val scope: CoroutineScope,
    private val defaultDnsServer: String,
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val sendToTun: (ByteArray) -> Unit,
    private val onTraffic: (sent: Long, received: Long) -> Unit
) {

    private val dnsQueryCounter = AtomicInteger(0)

    fun handleDnsPacket(
        ipHeader: IPv4Header,
        udpHeader: UdpHeader,
        packetData: ByteArray
    ) {
        val payloadLen = udpHeader.payloadLength
        if (payloadLen <= 0) return

        val dnsQueryData = ByteArray(payloadLen)
        System.arraycopy(packetData, udpHeader.payloadOffset, dnsQueryData, 0, payloadLen)

        val clientIp = ipHeader.srcIp
        val clientPort = udpHeader.srcPort
        val serverIp = ipHeader.dstIp
        val serverPort = udpHeader.dstPort

        val targetDns = if (defaultDnsServer.isNotBlank()) defaultDnsServer else ipHeader.dstIpStr

        scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protectSocket(socket)
                socket.soTimeout = 4000

                val dnsInetAddress = InetAddress.getByName(targetDns)
                val outPacket = DatagramPacket(dnsQueryData, dnsQueryData.size, dnsInetAddress, 53)

                socket.send(outPacket)
                onTraffic(dnsQueryData.size.toLong(), 0L)

                val responseBuffer = ByteArray(2048)
                val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(inPacket)

                val responseLen = inPacket.length
                if (responseLen > 0) {
                    val responseData = ByteArray(responseLen)
                    System.arraycopy(responseBuffer, 0, responseData, 0, responseLen)
                    onTraffic(0L, responseLen.toLong())

                    val responseIpPacket = PacketBuilder.buildUdpPacket(
                        srcIp = serverIp,
                        dstIp = clientIp,
                        srcPort = serverPort,
                        dstPort = clientPort,
                        payload = responseData
                    )

                    sendToTun(responseIpPacket)

                    val count = dnsQueryCounter.incrementAndGet()
                    if (count % 20 == 1) {
                        XrayLogManager.appendLog(
                            "DNS query resolved successfully via upstream $targetDns (Query #$count)",
                            "DNS"
                        )
                    }
                }
            } catch (e: Exception) {
                // If standard query fails, try fallback to 8.8.8.8 or 1.1.1.1
                tryFallbackDns(clientIp, clientPort, serverIp, serverPort, dnsQueryData)
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun tryFallbackDns(
        clientIp: ByteArray,
        clientPort: Int,
        serverIp: ByteArray,
        serverPort: Int,
        dnsQueryData: ByteArray
    ) {
        val fallbackServers = listOf("8.8.8.8", "1.1.1.1")
        for (fallback in fallbackServers) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protectSocket(socket)
                socket.soTimeout = 2500

                val dnsInetAddress = InetAddress.getByName(fallback)
                val outPacket = DatagramPacket(dnsQueryData, dnsQueryData.size, dnsInetAddress, 53)
                socket.send(outPacket)

                val responseBuffer = ByteArray(2048)
                val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(inPacket)

                val responseLen = inPacket.length
                if (responseLen > 0) {
                    val responseData = ByteArray(responseLen)
                    System.arraycopy(responseBuffer, 0, responseData, 0, responseLen)
                    val responseIpPacket = PacketBuilder.buildUdpPacket(
                        srcIp = serverIp,
                        dstIp = clientIp,
                        srcPort = serverPort,
                        dstPort = clientPort,
                        payload = responseData
                    )
                    sendToTun(responseIpPacket)
                    return
                }
            } catch (_: Exception) {
                // Next fallback
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }
}
