package com.example.vpn.tunnel

import com.example.vpn.packet.IPv4Header
import com.example.vpn.packet.PacketBuilder
import com.example.xray.XrayLogManager

class IcmpHandler(
    private val sendToTun: (ByteArray) -> Unit,
    private val onTraffic: (sent: Long, received: Long) -> Unit
) {

    fun handleIcmpPacket(
        ipHeader: IPv4Header,
        packetData: ByteArray,
        length: Int
    ) {
        val reply = PacketBuilder.buildIcmpReply(packetData, length)
        if (reply != null) {
            onTraffic(length.toLong(), reply.size.toLong())
            sendToTun(reply)
        }
    }
}
