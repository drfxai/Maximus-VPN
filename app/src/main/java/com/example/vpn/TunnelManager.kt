package com.example.vpn

import android.os.ParcelFileDescriptor
import com.example.data.model.AppSettings
import com.example.data.model.VlessProfile
import com.example.vpn.packet.IPv4Header
import com.example.vpn.packet.IpProtocol
import com.example.vpn.packet.TcpHeader
import com.example.vpn.packet.UdpHeader
import com.example.vpn.tunnel.DnsRelay
import com.example.vpn.tunnel.IcmpHandler
import com.example.vpn.tunnel.TcpVlessTunnel
import com.example.vpn.tunnel.UdpRelay
import com.example.xray.XrayLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TunnelManager(
    private val vpnInterface: ParcelFileDescriptor,
    private val profile: VlessProfile?,
    private val settings: AppSettings,
    private val protectSocket: (Socket) -> Boolean,
    private val protectDatagram: (DatagramSocket) -> Boolean,
    private val onTraffic: (sent: Long, received: Long) -> Unit
) {

    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tunnelJob: Job? = null

    private var outputStream: FileOutputStream? = null
    private val outputLock = Any()

    private var dnsRelay: DnsRelay? = null
    private var icmpHandler: IcmpHandler? = null
    private var tcpTunnel: TcpVlessTunnel? = null
    private var udpRelay: UdpRelay? = null

    fun start() {
        if (isRunning.getAndSet(true)) return

        XrayLogManager.appendLog("Starting TUN device I/O handler with MTU ${settings.mtu}...", "TUNNEL")

        val fileDescriptor = vpnInterface.fileDescriptor
        val inputStream = FileInputStream(fileDescriptor)
        val outStream = FileOutputStream(fileDescriptor)
        outputStream = outStream

        val sendToTunFunc: (ByteArray) -> Unit = { packet ->
            try {
                synchronized(outputLock) {
                    outputStream?.write(packet)
                    outputStream?.flush()
                }
            } catch (_: Exception) {}
        }

        dnsRelay = DnsRelay(
            scope = scope,
            defaultDnsServer = if (settings.dnsServer.isNotBlank()) settings.dnsServer else "8.8.8.8",
            protectSocket = protectDatagram,
            sendToTun = sendToTunFunc,
            onTraffic = onTraffic
        )

        icmpHandler = IcmpHandler(
            sendToTun = sendToTunFunc,
            onTraffic = onTraffic
        )

        tcpTunnel = TcpVlessTunnel(
            scope = scope,
            profile = profile,
            settings = settings,
            protectSocket = protectSocket,
            sendToTun = sendToTunFunc,
            onTraffic = onTraffic
        )

        udpRelay = UdpRelay(
            scope = scope,
            profile = profile,
            settings = settings,
            protectDatagram = protectDatagram,
            sendToTun = sendToTunFunc,
            onTraffic = onTraffic
        )

        XrayLogManager.appendLog("TUN transparent router active: DNS, ICMP, and TCP/UDP VLESS bridges initialized.", "TUNNEL")

        tunnelJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(settings.mtu + 500)

            try {
                while (isActive && isRunning.get()) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        processPacket(buffer, length)
                    } else if (length < 0) {
                        break
                    } else {
                        kotlinx.coroutines.delay(5)
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    XrayLogManager.appendLog("TUN interface I/O loop notice: ${e.message}", "TUNNEL")
                }
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
                try {
                    synchronized(outputLock) {
                        outputStream?.close()
                        outputStream = null
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun processPacket(buffer: ByteArray, length: Int) {
        val ipHeader = IPv4Header.parse(buffer, 0) ?: return

        when (ipHeader.protocol) {
            IpProtocol.ICMP -> {
                icmpHandler?.handleIcmpPacket(ipHeader, buffer, length)
            }
            IpProtocol.UDP -> {
                val ipHeaderLen = ipHeader.ihl * 4
                val udpHeader = UdpHeader.parse(buffer, ipHeaderLen, length) ?: return
                if (udpHeader.dstPort == 53) {
                    dnsRelay?.handleDnsPacket(ipHeader, udpHeader, buffer)
                } else {
                    udpRelay?.handleUdpPacket(ipHeader, udpHeader, buffer)
                }
            }
            IpProtocol.TCP -> {
                val ipHeaderLen = ipHeader.ihl * 4
                val tcpHeader = TcpHeader.parse(buffer, 0, ipHeaderLen, length) ?: return
                tcpTunnel?.handleTcpPacket(ipHeader, tcpHeader, buffer)
            }
            else -> {
                // Ignore unhandled protocol
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        XrayLogManager.appendLog("Stopping TUN device I/O handler and closing file descriptor...", "TUNNEL")
        tunnelJob?.cancel()
        tunnelJob = null

        tcpTunnel?.closeAll()
        udpRelay?.closeAll()

        try {
            synchronized(outputLock) {
                outputStream?.close()
                outputStream = null
            }
        } catch (_: Exception) {}

        try {
            vpnInterface.close()
        } catch (e: Exception) {
            XrayLogManager.appendLog("Error closing VPN ParcelFileDescriptor: ${e.message}", "TUNNEL")
        }
    }
}
