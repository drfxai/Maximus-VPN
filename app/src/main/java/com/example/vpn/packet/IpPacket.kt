package com.example.vpn.packet

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

enum class IpProtocol(val value: Int) {
    ICMP(1),
    TCP(6),
    UDP(17),
    UNKNOWN(-1);

    companion object {
        fun fromInt(value: Int): IpProtocol = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

data class IPv4Header(
    val version: Int = 4,
    val ihl: Int = 5,
    val tos: Int = 0,
    val totalLength: Int,
    val identification: Int = 0,
    val flags: Int = 0x4000, // Don't Fragment
    val ttl: Int = 64,
    val protocol: IpProtocol,
    val srcIp: ByteArray,
    val dstIp: ByteArray
) {
    val srcIpStr: String get() = formatIp(srcIp)
    val dstIpStr: String get() = formatIp(dstIp)

    companion object {
        fun parse(buffer: ByteArray, offset: Int = 0): IPv4Header? {
            if (buffer.size - offset < 20) return null
            val versionAndIhl = buffer[offset].toInt() and 0xFF
            val version = versionAndIhl shr 4
            if (version != 4) return null
            val ihl = versionAndIhl and 0x0F
            if (ihl < 5) return null

            val tos = buffer[offset + 1].toInt() and 0xFF
            val totalLength = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
            val identification = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or (buffer[offset + 5].toInt() and 0xFF)
            val flagsAndFrag = ((buffer[offset + 6].toInt() and 0xFF) shl 8) or (buffer[offset + 7].toInt() and 0xFF)
            val ttl = buffer[offset + 8].toInt() and 0xFF
            val protocolNum = buffer[offset + 9].toInt() and 0xFF
            val protocol = IpProtocol.fromInt(protocolNum)

            val srcIp = ByteArray(4)
            System.arraycopy(buffer, offset + 12, srcIp, 0, 4)
            val dstIp = ByteArray(4)
            System.arraycopy(buffer, offset + 16, dstIp, 0, 4)

            return IPv4Header(
                version = version,
                ihl = ihl,
                tos = tos,
                totalLength = totalLength,
                identification = identification,
                flags = flagsAndFrag,
                ttl = ttl,
                protocol = protocol,
                srcIp = srcIp,
                dstIp = dstIp
            )
        }

        fun formatIp(ip: ByteArray): String {
            if (ip.size != 4) return "0.0.0.0"
            return "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"
        }

        fun parseIp(ipStr: String): ByteArray {
            val parts = ipStr.split('.')
            if (parts.size != 4) return byteArrayOf(0, 0, 0, 0)
            val bytes = ByteArray(4)
            for (i in 0..3) {
                bytes[i] = (parts[i].toIntOrNull() ?: 0).toByte()
            }
            return bytes
        }
    }
}

data class UdpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val length: Int,
    val checksum: Int,
    val payloadOffset: Int,
    val payloadLength: Int
) {
    companion object {
        fun parse(buffer: ByteArray, offset: Int, totalPacketLength: Int): UdpHeader? {
            if (buffer.size - offset < 8) return null
            val srcPort = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            val dstPort = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
            val length = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or (buffer[offset + 5].toInt() and 0xFF)
            val checksum = ((buffer[offset + 6].toInt() and 0xFF) shl 8) or (buffer[offset + 7].toInt() and 0xFF)

            val payloadOffset = offset + 8
            val payloadLength = (length - 8).coerceAtLeast(0)

            return UdpHeader(srcPort, dstPort, length, checksum, payloadOffset, payloadLength)
        }
    }
}

data class TcpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val sequenceNumber: Long,
    val ackNumber: Long,
    val dataOffset: Int, // in 32-bit words
    val flags: Int,
    val windowSize: Int,
    val checksum: Int,
    val urgentPointer: Int,
    val payloadOffset: Int,
    val payloadLength: Int
) {
    val isSyn: Boolean get() = (flags and 0x02) != 0
    val isAck: Boolean get() = (flags and 0x10) != 0
    val isFin: Boolean get() = (flags and 0x01) != 0
    val isRst: Boolean get() = (flags and 0x04) != 0
    val isPsh: Boolean get() = (flags and 0x08) != 0

    companion object {
        fun parse(buffer: ByteArray, ipHeaderOffset: Int, ipHeaderLen: Int, totalIpLength: Int): TcpHeader? {
            val offset = ipHeaderOffset + ipHeaderLen
            if (buffer.size - offset < 20) return null

            val srcPort = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            val dstPort = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)

            val seq = ((buffer[offset + 4].toLong() and 0xFF) shl 24) or
                    ((buffer[offset + 5].toLong() and 0xFF) shl 16) or
                    ((buffer[offset + 6].toLong() and 0xFF) shl 8) or
                    (buffer[offset + 7].toLong() and 0xFF)

            val ack = ((buffer[offset + 8].toLong() and 0xFF) shl 24) or
                    ((buffer[offset + 9].toLong() and 0xFF) shl 16) or
                    ((buffer[offset + 10].toLong() and 0xFF) shl 8) or
                    (buffer[offset + 11].toLong() and 0xFF)

            val dataOffset = (buffer[offset + 12].toInt() and 0xF0) shr 4
            val flags = buffer[offset + 13].toInt() and 0x3F
            val windowSize = ((buffer[offset + 14].toInt() and 0xFF) shl 8) or (buffer[offset + 15].toInt() and 0xFF)
            val checksum = ((buffer[offset + 16].toInt() and 0xFF) shl 8) or (buffer[offset + 17].toInt() and 0xFF)
            val urgentPointer = ((buffer[offset + 18].toInt() and 0xFF) shl 8) or (buffer[offset + 19].toInt() and 0xFF)

            val tcpHeaderByteLen = dataOffset * 4
            val payloadOffset = offset + tcpHeaderByteLen
            val payloadLength = (totalIpLength - ipHeaderLen - tcpHeaderByteLen).coerceAtLeast(0)

            return TcpHeader(
                srcPort = srcPort,
                dstPort = dstPort,
                sequenceNumber = seq,
                ackNumber = ack,
                dataOffset = dataOffset,
                flags = flags,
                windowSize = windowSize,
                checksum = checksum,
                urgentPointer = urgentPointer,
                payloadOffset = payloadOffset,
                payloadLength = payloadLength
            )
        }
    }
}

object PacketBuilder {
    private val ipIdGenerator = AtomicInteger(1000)

    /**
     * Builds a complete IPv4 + UDP packet.
     */
    fun buildUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLength = ipHeaderLen + udpHeaderLen + payload.size
        val packet = ByteArray(totalLength)

        // 1. Fill IPv4 Header
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00.toByte() // TOS
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()

        val id = ipIdGenerator.incrementAndGet() and 0xFFFF
        packet[4] = ((id shr 8) and 0xFF).toByte()
        packet[5] = (id and 0xFF).toByte()

        packet[6] = 0x40.toByte() // Don't fragment
        packet[7] = 0x00.toByte()

        packet[8] = 64.toByte() // TTL
        packet[9] = IpProtocol.UDP.value.toByte()

        packet[10] = 0 // Checksum placeholder
        packet[11] = 0

        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipChecksum = computeIpChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // 2. Fill UDP Header
        val udpOffset = 20
        packet[udpOffset] = ((srcPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 1] = (srcPort and 0xFF).toByte()
        packet[udpOffset + 2] = ((dstPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 3] = (dstPort and 0xFF).toByte()

        val udpLen = udpHeaderLen + payload.size
        packet[udpOffset + 4] = ((udpLen shr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLen and 0xFF).toByte()
        packet[udpOffset + 6] = 0 // UDP checksum placeholder
        packet[udpOffset + 7] = 0

        // 3. Copy Payload
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, udpOffset + udpHeaderLen, payload.size)
        }

        // 4. Compute UDP Checksum with Pseudo-header
        val udpChecksum = computeUdpChecksum(srcIp, dstIp, packet, udpOffset, udpLen)
        packet[udpOffset + 6] = ((udpChecksum shr 8) and 0xFF).toByte()
        packet[udpOffset + 7] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    /**
     * Builds a complete IPv4 + ICMP Echo Reply packet.
     */
    fun buildIcmpReply(
        requestPacket: ByteArray,
        length: Int
    ): ByteArray? {
        if (length < 28) return null
        val ipHeader = IPv4Header.parse(requestPacket, 0) ?: return null
        if (ipHeader.protocol != IpProtocol.ICMP) return null

        val ipHeaderLen = ipHeader.ihl * 4
        val icmpOffset = ipHeaderLen
        val icmpType = requestPacket[icmpOffset].toInt() and 0xFF
        if (icmpType != 8) return null // Only respond to Echo Request (8)

        val reply = ByteArray(length)
        System.arraycopy(requestPacket, 0, reply, 0, length)

        // Swap IP addresses
        System.arraycopy(ipHeader.dstIp, 0, reply, 12, 4)
        System.arraycopy(ipHeader.srcIp, 0, reply, 16, 4)

        // Recompute IP Checksum
        reply[10] = 0
        reply[11] = 0
        val ipChecksum = computeIpChecksum(reply, 0, ipHeaderLen)
        reply[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        reply[11] = (ipChecksum and 0xFF).toByte()

        // Set ICMP Type to 0 (Echo Reply)
        reply[icmpOffset] = 0 // Type: Echo Reply
        reply[icmpOffset + 1] = 0 // Code: 0
        reply[icmpOffset + 2] = 0 // Checksum reset
        reply[icmpOffset + 3] = 0

        val icmpLen = length - icmpOffset
        val icmpChecksum = computeIpChecksum(reply, icmpOffset, icmpLen)
        reply[icmpOffset + 2] = ((icmpChecksum shr 8) and 0xFF).toByte()
        reply[icmpOffset + 3] = (icmpChecksum and 0xFF).toByte()

        return reply
    }

    /**
     * Builds a complete IPv4 + TCP packet.
     */
    fun buildTcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        windowSize: Int = 65535,
        payload: ByteArray = ByteArray(0)
    ): ByteArray {
        val ipHeaderLen = 20
        val tcpHeaderLen = 20
        val totalLength = ipHeaderLen + tcpHeaderLen + payload.size
        val packet = ByteArray(totalLength)

        // 1. IP Header
        packet[0] = 0x45.toByte()
        packet[1] = 0x00.toByte()
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()

        val id = ipIdGenerator.incrementAndGet() and 0xFFFF
        packet[4] = ((id shr 8) and 0xFF).toByte()
        packet[5] = (id and 0xFF).toByte()
        packet[6] = 0x40.toByte() // DF
        packet[7] = 0x00.toByte()
        packet[8] = 64.toByte() // TTL
        packet[9] = IpProtocol.TCP.value.toByte()
        packet[10] = 0
        packet[11] = 0

        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipChecksum = computeIpChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // 2. TCP Header
        val tcpOffset = 20
        packet[tcpOffset] = ((srcPort shr 8) and 0xFF).toByte()
        packet[tcpOffset + 1] = (srcPort and 0xFF).toByte()
        packet[tcpOffset + 2] = ((dstPort shr 8) and 0xFF).toByte()
        packet[tcpOffset + 3] = (dstPort and 0xFF).toByte()

        // Seq (4 bytes)
        packet[tcpOffset + 4] = ((seq shr 24) and 0xFF).toByte()
        packet[tcpOffset + 5] = ((seq shr 16) and 0xFF).toByte()
        packet[tcpOffset + 6] = ((seq shr 8) and 0xFF).toByte()
        packet[tcpOffset + 7] = (seq and 0xFF).toByte()

        // Ack (4 bytes)
        packet[tcpOffset + 8] = ((ack shr 24) and 0xFF).toByte()
        packet[tcpOffset + 9] = ((ack shr 16) and 0xFF).toByte()
        packet[tcpOffset + 10] = ((ack shr 8) and 0xFF).toByte()
        packet[tcpOffset + 11] = (ack and 0xFF).toByte()

        packet[tcpOffset + 12] = 0x50.toByte() // Header len: 5 * 4 = 20 bytes
        packet[tcpOffset + 13] = (flags and 0xFF).toByte()

        packet[tcpOffset + 14] = ((windowSize shr 8) and 0xFF).toByte()
        packet[tcpOffset + 15] = (windowSize and 0xFF).toByte()

        packet[tcpOffset + 16] = 0 // Checksum reset
        packet[tcpOffset + 17] = 0
        packet[tcpOffset + 18] = 0 // Urgent pointer
        packet[tcpOffset + 19] = 0

        // 3. Payload
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, tcpOffset + tcpHeaderLen, payload.size)
        }

        // 4. Compute TCP Checksum
        val tcpLen = tcpHeaderLen + payload.size
        val tcpChecksum = computeTcpChecksum(srcIp, dstIp, packet, tcpOffset, tcpLen)
        packet[tcpOffset + 16] = ((tcpChecksum shr 8) and 0xFF).toByte()
        packet[tcpOffset + 17] = (tcpChecksum and 0xFF).toByte()

        return packet
    }

    private fun computeIpChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val word = ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < offset + length) {
            sum += (buffer[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun computeUdpChecksum(srcIp: ByteArray, dstIp: ByteArray, buffer: ByteArray, udpOffset: Int, udpLen: Int): Int {
        var sum = 0
        // Pseudo-header
        for (i in 0 until 4 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += IpProtocol.UDP.value
        sum += udpLen

        // UDP Header + Data
        var i = udpOffset
        while (i < udpOffset + udpLen - 1) {
            sum += ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < udpOffset + udpLen) {
            sum += (buffer[i].toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = sum.inv() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }

    private fun computeTcpChecksum(srcIp: ByteArray, dstIp: ByteArray, buffer: ByteArray, tcpOffset: Int, tcpLen: Int): Int {
        var sum = 0
        // Pseudo-header
        for (i in 0 until 4 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += IpProtocol.TCP.value
        sum += tcpLen

        // TCP Header + Data
        var i = tcpOffset
        while (i < tcpOffset + tcpLen - 1) {
            sum += ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < tcpOffset + tcpLen) {
            sum += (buffer[i].toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = sum.inv() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }
}
