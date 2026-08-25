package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.SecretRedactor
import com.example.data.database.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.ConnectionState
import com.example.data.model.ConnectionStatus
import com.example.data.model.RoutingMode
import com.example.data.model.VlessProfile
import com.example.data.repository.ServerRepository
import com.example.data.repository.SettingsRepository
import com.example.xray.XrayConfigBuilder
import com.example.xray.XrayEngine
import com.example.xray.XrayEngineImpl
import com.example.xray.XrayLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class RayVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.raytunnel.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.raytunnel.ACTION_DISCONNECT"
        const val ACTION_RECONNECT = "com.example.raytunnel.ACTION_RECONNECT"
        const val EXTRA_PROFILE_ID = "com.example.raytunnel.EXTRA_PROFILE_ID"

        const val NOTIFICATION_CHANNEL_ID = "raytunnel_vpn_channel"
        const val NOTIFICATION_ID = 1001

        private val _vpnState = MutableStateFlow(ConnectionState())
        val vpnState: StateFlow<ConnectionState> = _vpnState.asStateFlow()

        fun updateState(state: ConnectionState) {
            _vpnState.value = state
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var durationJob: Job? = null
    private var pingJob: Job? = null

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelManager: TunnelManager? = null
    private val xrayEngine: XrayEngine = XrayEngineImpl.instance

    private lateinit var serverRepository: ServerRepository
    private lateinit var settingsRepository: SettingsRepository
    private var activeProfile: VlessProfile? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(applicationContext)
        serverRepository = ServerRepository(db.serverProfileDao())
        settingsRepository = SettingsRepository(applicationContext)
        createNotificationChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)

        when (action) {
            ACTION_CONNECT -> {
                serviceScope.launch {
                    val profile = if (!profileId.isNullOrBlank()) {
                        serverRepository.getProfileById(profileId)
                    } else {
                        val settings = settingsRepository.getSettings()
                        settings.selectedProfileId?.let { serverRepository.getProfileById(it) }
                    }
                    if (profile != null) {
                        connect(profile)
                    } else {
                        XrayLogManager.appendLog("No valid server profile found to connect.", "VPN")
                        updateState(_vpnState.value.copy(
                            status = ConnectionStatus.FAILED,
                            errorMessage = "No server profile selected."
                        ))
                        stopSelf()
                    }
                }
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    disconnect()
                }
            }
            ACTION_RECONNECT -> {
                serviceScope.launch {
                    activeProfile?.let { connect(it) }
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun connect(profile: VlessProfile) = withContext(Dispatchers.IO) {
        try {
            activeProfile = profile
            updateState(ConnectionState(
                status = ConnectionStatus.PREPARING,
                activeProfile = profile
            ))
            showForegroundNotification("Preparing connection to ${profile.name}...")

            XrayLogManager.appendLog("Starting VPN connection procedure for server: ${profile.name} (${profile.address}:${profile.port})", "VPN")

            val settings = settingsRepository.getSettings()

            // 1. Build Xray JSON config
            val xrayConfigJson = XrayConfigBuilder.buildJson(profile, settings)
            XrayLogManager.appendLog("Generated Xray configuration. Redacted preview:\n${SecretRedactor.redact(xrayConfigJson)}", "CONFIG")

            updateState(_vpnState.value.copy(status = ConnectionStatus.CONNECTING))
            showForegroundNotification("Connecting to ${profile.name}...")

            // 2. Start Xray-core Engine
            val startResult = xrayEngine.start(xrayConfigJson) { socketFd ->
                protect(socketFd)
            }

            if (startResult.isError) {
                val error = startResult as com.example.core.AppResult.Error
                XrayLogManager.appendLog("Xray engine error: ${error.userFriendlyMessage}", "ERROR")
                updateState(_vpnState.value.copy(
                    status = ConnectionStatus.FAILED,
                    errorMessage = error.userFriendlyMessage
                ))
                disconnectResources()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@withContext
            }

            // 3. Configure and establish Android VpnService TUN interface
            val builder = Builder()
                .setSession("Maximus - ${profile.name}")
                .setMtu(settings.mtu)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)

            // DNS configuration
            val primaryDns = if (settings.dnsServer.isNotBlank()) settings.dnsServer else "1.1.1.1"
            builder.addDnsServer(primaryDns)
            if (settings.customDns.isNotBlank() && settings.customDns != primaryDns) {
                try { builder.addDnsServer(settings.customDns) } catch (_: Exception) {}
            }

            // IPv6 Handling
            if (settings.ipv6Enabled) {
                try {
                    builder.addAddress("fdfe:dcba:9876::1", 126)
                    builder.addRoute("::", 0)
                } catch (e: Exception) {
                    XrayLogManager.appendLog("IPv6 address configuration skipped: ${e.message}", "VPN")
                }
            }

            // Avoid routing loops: Disallow the RayTunnel app itself from being captured by TUN
            try {
                builder.addDisallowedApplication(packageName)
                XrayLogManager.appendLog("Excluded '$packageName' from VPN interface to prevent routing loops.", "TUNNEL")
            } catch (e: Exception) {
                XrayLogManager.appendLog("Disallowing package notice: ${e.message}", "TUNNEL")
            }

            // Establish TUN
            val pfd = builder.establish()
            if (pfd == null) {
                XrayLogManager.appendLog("VpnService.Builder.establish() returned null. Permission might be revoked.", "ERROR")
                updateState(_vpnState.value.copy(
                    status = ConnectionStatus.FAILED,
                    errorMessage = "Failed to establish VPN interface. Please check Android VPN permissions."
                ))
                disconnectResources()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@withContext
            }

            vpnInterface = pfd

            // 4. Start Tunnel Manager to read/write packets
            tunnelManager = TunnelManager(
                vpnInterface = pfd,
                profile = profile,
                settings = settings,
                protectSocket = { socket -> protect(socket) },
                protectDatagram = { datagramSocket -> protect(datagramSocket) }
            ) { sent, received ->
                (xrayEngine as? XrayEngineImpl)?.recordTraffic(sent, received)
            }
            tunnelManager?.start()

            // 5. Update Connection State to CONNECTED
            val startTime = System.currentTimeMillis()
            updateState(_vpnState.value.copy(
                status = ConnectionStatus.CONNECTED,
                activeProfile = profile,
                lastConnectedTime = startTime,
                vpnIp = "172.19.0.1",
                errorMessage = null
            ))

            showForegroundNotification("Connected to ${profile.name}")
            XrayLogManager.appendLog("VPN tunnel established and traffic routing active.", "VPN")

            startDurationAndPingWatchers(profile, startTime)

        } catch (e: Exception) {
            XrayLogManager.appendLog("Fatal error establishing VPN connection: ${e.message}", "ERROR")
            updateState(_vpnState.value.copy(
                status = ConnectionStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Unknown connection failure"
            ))
            disconnectResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startDurationAndPingWatchers(profile: VlessProfile, startTime: Long) {
        durationJob?.cancel()
        durationJob = serviceScope.launch {
            while (isActive && _vpnState.value.isConnected) {
                delay(1000)
                val durationSec = (System.currentTimeMillis() - startTime) / 1000
                val stats = xrayEngine.getStats()
                updateState(_vpnState.value.copy(
                    connectedDurationSeconds = durationSec,
                    uploadBytes = stats.txBytes,
                    downloadBytes = stats.rxBytes,
                    uploadSpeedBps = stats.txSpeedBps,
                    downloadSpeedBps = stats.rxSpeedBps
                ))
            }
        }

        pingJob?.cancel()
        pingJob = serviceScope.launch {
            while (isActive && _vpnState.value.isConnected) {
                try {
                    val socket = Socket()
                    protect(socket)
                    val sStart = System.currentTimeMillis()
                    socket.connect(InetSocketAddress(profile.address, profile.port), 3000)
                    val latency = System.currentTimeMillis() - sStart
                    socket.close()
                    updateState(_vpnState.value.copy(pingMs = latency))
                    serverRepository.updateLatency(profile.id, latency)
                } catch (_: Exception) {
                    // Ping failed
                }
                delay(10000)
            }
        }
    }

    private suspend fun disconnect() = withContext(Dispatchers.IO) {
        if (_vpnState.value.status == ConnectionStatus.DISCONNECTED) return@withContext

        XrayLogManager.appendLog("Initiating clean VPN disconnection...", "VPN")
        updateState(_vpnState.value.copy(status = ConnectionStatus.DISCONNECTING))

        disconnectResources()

        updateState(ConnectionState(
            status = ConnectionStatus.DISCONNECTED,
            activeProfile = null,
            connectedDurationSeconds = 0,
            uploadBytes = 0,
            downloadBytes = 0
        ))

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        XrayLogManager.appendLog("VPN successfully disconnected and interface closed.", "VPN")
    }

    private fun disconnectResources() {
        durationJob?.cancel()
        durationJob = null
        pingJob?.cancel()
        pingJob = null

        tunnelManager?.stop()
        tunnelManager = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        xrayEngine.stop()
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                XrayLogManager.appendLog("Underlying network available.", "NETWORK")
            }

            override fun onLost(network: Network) {
                XrayLogManager.appendLog("Underlying network connection lost.", "NETWORK")
                val settings = settingsRepository.getSettings()
                if (_vpnState.value.isConnected && settings.autoReconnect) {
                    XrayLogManager.appendLog("Auto-reconnect is enabled. Waiting for network recovery...", "VPN")
                    updateState(_vpnState.value.copy(status = ConnectionStatus.RECONNECTING))
                }
            }
        }

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification(statusText: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, RayVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val reconnectIntent = Intent(this, RayVpnService::class.java).apply {
            action = ACTION_RECONNECT
        }
        val reconnectPendingIntent = PendingIntent.getService(
            this,
            2,
            reconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val profileName = activeProfile?.name ?: "VLESS Tunnel"

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Maximus — $profileName")
            .setContentText(statusText)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
            .addAction(android.R.drawable.ic_menu_rotate, "Reconnect", reconnectPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        disconnectResources()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
