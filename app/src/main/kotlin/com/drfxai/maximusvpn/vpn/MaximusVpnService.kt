package com.drfxai.maximusvpn.vpn

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
import com.drfxai.maximusvpn.MainActivity
import com.drfxai.maximusvpn.R
import com.drfxai.maximusvpn.core.SecretRedactor
import com.drfxai.maximusvpn.data.database.AppDatabase
import com.drfxai.maximusvpn.data.model.AppSettings
import com.drfxai.maximusvpn.data.model.ConnectionState
import com.drfxai.maximusvpn.data.model.ConnectionStatus
import com.drfxai.maximusvpn.data.model.RoutingMode
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.data.repository.ServerRepository
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.xray.XrayConfigBuilder
import com.drfxai.maximusvpn.xray.XrayCoreEngine
import com.drfxai.maximusvpn.xray.XrayLogManager
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

class MaximusVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.drfxai.maximusvpn.raytunnel.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.drfxai.maximusvpn.raytunnel.ACTION_DISCONNECT"
        const val ACTION_RECONNECT = "com.drfxai.maximusvpn.raytunnel.ACTION_RECONNECT"
        const val EXTRA_PROFILE_ID = "com.drfxai.maximusvpn.raytunnel.EXTRA_PROFILE_ID"

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
    private val xrayCore by lazy { XrayCoreEngine.getInstance(this) }

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

            // 1. Configure and establish the Android VpnService TUN interface FIRST.
            //    Fail-closed ordering: if anything later fails, we tear this down immediately,
            //    so traffic can never bypass the tunnel.
            val builder = Builder()
                .setSession("Maximus VPN - ${profile.name}")
                .setMtu(settings.mtu)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)

            val primaryDns = if (settings.dnsServer.isNotBlank()) settings.dnsServer else "1.1.1.1"
            builder.addDnsServer(primaryDns)
            if (settings.ipv6Enabled) {
                try {
                    builder.addAddress("fdfe:dcba:9876::1", 126)
                    builder.addRoute("::", 0)
                } catch (e: Exception) {
                    XrayLogManager.appendLog("IPv6 configuration skipped: ${e.message}", "VPN")
                }
            }

            // Prevent routing loops: exclude our own process from the TUN
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                XrayLogManager.appendLog("Disallow package notice: ${e.message}", "TUNNEL")
            }

            // Kill switch: when enabled, block ALL traffic while the tunnel is down
            // (Android enforces this at the system level via lockdown mode).
            val killSwitchRequested = settings.killSwitchEnabled
            if (killSwitchRequested) {
                try { builder.setBlocking(true) } catch (e: Exception) {
                    XrayLogManager.appendLog("setBlocking unavailable: ${e.message}", "SECURITY")
                }
            }

            val pfd = builder.establish()
            if (pfd == null) {
                XrayLogManager.appendLog("VpnService.Builder.establish() returned null. Permission revoked?", "ERROR")
                failConnection("Failed to establish VPN interface. Please re-grant VPN permission.")
                return@withContext
            }
            vpnInterface = pfd

            updateState(_vpnState.value.copy(status = ConnectionStatus.CONNECTING))
            showForegroundNotification("Connecting to ${profile.name}...")

            // 2. Build real Xray-core tun config and start the engine with our TUN fd.
            val ipv6 = settings.ipv6Enabled
            val xrayConfigJson = XrayConfigBuilder.buildTunConfig(
                profile, settings,
                tunFd = pfd.fd,
                ipv4Address = "172.19.0.1", inet4Prefix = 30,
                ipv6Address = "fdfe:dcba:9876::1", inet6Prefix = 126
            ).let { json -> json } // addresses above MUST mirror Builder.addAddress calls
            if (ipv6) {
                require(xrayConfigJson.contains("inet6_address")) { "IPv6 route configured but missing in Xray config" }
            }
            XrayLogManager.appendLog("Generated Xray tun config. Redacted preview:\n${SecretRedactor.redact(xrayConfigJson)}", "CONFIG")

            val startResult = xrayCore.start(xrayConfigJson, pfd.fd)
            if (startResult.isError) {
                val error = startResult as com.drfxai.maximusvpn.core.AppResult.Error
                XrayLogManager.appendLog("Xray engine error: ${error.userFriendlyMessage}", "ERROR")
                failConnection(error.userFriendlyMessage)
                return@withContext
            }

            // 3. Connected — Xray owns the TUN now (TCP/UDP/DNS all through the core).
            val startTime = System.currentTimeMillis()
            updateState(_vpnState.value.copy(
                status = ConnectionStatus.CONNECTED,
                activeProfile = profile,
                lastConnectedTime = startTime,
                vpnIp = "172.19.0.1",
                errorMessage = null
            ))
            showForegroundNotification("Connected to ${profile.name}")
            XrayLogManager.appendLog("VPN tunnel established — all traffic routed through Xray-core.", "VPN")

            startDurationAndPingWatchers(profile, startTime)

        } catch (e: Exception) {
            XrayLogManager.appendLog("Fatal error establishing VPN connection: ${e.message}", "ERROR")
            failConnection(e.localizedMessage ?: "Unknown connection failure")
        }
    }

    /** Fail-closed teardown: stop engine, close TUN, surface error, stop service. */
    private fun failConnection(message: String) {
        xrayCore.stop()
        disconnectResources()
        updateState(_vpnState.value.copy(
            status = ConnectionStatus.FAILED,
            errorMessage = message
        ))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startDurationAndPingWatchers(profile: VlessProfile, startTime: Long) {
        durationJob?.cancel()
        durationJob = serviceScope.launch {
            while (isActive && _vpnState.value.isConnected) {
                delay(1000)
                val durationSec = (System.currentTimeMillis() - startTime) / 1000
                val stats = xrayCore.statsFlow.value
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

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        xrayCore.stop()
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

        val disconnectIntent = Intent(this, MaximusVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val reconnectIntent = Intent(this, MaximusVpnService::class.java).apply {
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
