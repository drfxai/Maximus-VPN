package com.example

import com.example.vless.VlessHeader
import com.example.vpn.packet.IPv4Header
import com.example.vpn.packet.IpProtocol
import com.example.vpn.packet.PacketBuilder
import com.example.vpn.packet.TcpHeader
import com.example.vpn.packet.UdpHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketAndVlessTunnelTest {

    @Test
    fun testUdpPacketBuildingAndParsing() {
        val srcIp = byteArrayOf(172.toByte(), 19.toByte(), 0.toByte(), 1.toByte())
        val dstIp = byteArrayOf(8.toByte(), 8.toByte(), 8.toByte(), 8.toByte())
        val srcPort = 54321
        val dstPort = 53
        val payload = "TEST_DNS_QUERY".toByteArray()

        val packet = PacketBuilder.buildUdpPacket(srcIp, dstIp, srcPort, dstPort, payload)

        val ipHeader = IPv4Header.parse(packet, 0)
        assertNotNull(ipHeader)
        assertEquals(IpProtocol.UDP, ipHeader!!.protocol)
        assertEquals("172.19.0.1", ipHeader.srcIpStr)
        assertEquals("8.8.8.8", ipHeader.dstIpStr)

        val udpHeader = UdpHeader.parse(packet, ipHeader.ihl * 4, packet.size)
        assertNotNull(udpHeader)
        assertEquals(54321, udpHeader!!.srcPort)
        assertEquals(53, udpHeader.dstPort)
        assertEquals(payload.size, udpHeader.payloadLength)
    }

    @Test
    fun testTcpPacketBuildingAndParsing() {
        val srcIp = byteArrayOf(8.toByte(), 8.toByte(), 8.toByte(), 8.toByte())
        val dstIp = byteArrayOf(172.toByte(), 19.toByte(), 0.toByte(), 1.toByte())
        val srcPort = 443
        val dstPort = 51234
        val seq = 100500L
        val ack = 200500L
        val payload = "HTTP/1.1 200 OK\r\n\r\n".toByteArray()

        val packet = PacketBuilder.buildTcpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            seq = seq,
            ack = ack,
            flags = 0x18, // PSH | ACK
            windowSize = 65535,
            payload = payload
        )

        val ipHeader = IPv4Header.parse(packet, 0)
        assertNotNull(ipHeader)
        assertEquals(IpProtocol.TCP, ipHeader!!.protocol)
        assertEquals("8.8.8.8", ipHeader.srcIpStr)
        assertEquals("172.19.0.1", ipHeader.dstIpStr)

        val tcpHeader = TcpHeader.parse(packet, 0, ipHeader.ihl * 4, packet.size)
        assertNotNull(tcpHeader)
        assertEquals(443, tcpHeader!!.srcPort)
        assertEquals(51234, tcpHeader.dstPort)
        assertEquals(seq, tcpHeader.sequenceNumber)
        assertEquals(ack, tcpHeader.ackNumber)
        assertTrue(tcpHeader.isPsh)
        assertTrue(tcpHeader.isAck)
        assertEquals(payload.size, tcpHeader.payloadLength)
    }

    @Test
    fun testVlessHeaderEncoding() {
        val uuidStr = "e91c7a8b-4321-4def-9876-123456789abc"
        val uuidBytes = VlessHeader.uuidToBytes(uuidStr)
        assertEquals(16, uuidBytes.size)

        val reqHeader = VlessHeader.encodeRequest(
            uuidBytes = uuidBytes,
            command = VlessHeader.COMMAND_TCP,
            destPort = 443,
            destAddress = "1.1.1.1"
        )

        assertTrue(reqHeader.isNotEmpty())
        assertEquals(0x00.toByte(), reqHeader[0]) // Version 0
        assertEquals(0x01.toByte(), reqHeader[18]) // Command TCP
        assertEquals(0x01.toByte(), reqHeader[19]) // Port high (443 = 0x01BB)
        assertEquals(0xBB.toByte(), reqHeader[20]) // Port low
        assertEquals(VlessHeader.ATYPE_IPV4, reqHeader[21]) // Address type IPv4
        assertEquals(1.toByte(), reqHeader[22])
        assertEquals(1.toByte(), reqHeader[23])
        assertEquals(1.toByte(), reqHeader[24])
        assertEquals(1.toByte(), reqHeader[25])
    }
}
