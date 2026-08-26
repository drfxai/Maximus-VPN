package com.drfxai.maximusvpn.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.drfxai.maximusvpn.data.model.ConnectionState
import com.drfxai.maximusvpn.data.model.ConnectionStatus
import com.drfxai.maximusvpn.data.model.VlessProfile
import kotlinx.coroutines.flow.StateFlow

object VpnController {

    val connectionState: StateFlow<ConnectionState> = MaximusVpnService.vpnState

    /**
     * Checks if Android VPN permission has been granted by the user.
     * Returns an Intent if permission needs to be requested via ActivityResultLauncher, or null if already granted.
     */
    fun prepareVpn(context: Context): Intent? {
        return VpnService.prepare(context)
    }

    /**
     * Initiates VPN connection with a specific VLESS profile.
     */
    fun startVpn(context: Context, profile: VlessProfile) {
        val intent = Intent(context, MaximusVpnService::class.java).apply {
            action = MaximusVpnService.ACTION_CONNECT
            putExtra(MaximusVpnService.EXTRA_PROFILE_ID, profile.id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /** Starts the service to connect the last selected profile. */
    fun startVpnFromSelected(context: Context) {
        val intent = Intent(context, MaximusVpnService::class.java).apply {
            action = ACTION_CONNECT
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Stops the running VPN connection and tears down the tunnel cleanly.
     */
    fun stopVpn(context: Context) {
        val intent = Intent(context, MaximusVpnService::class.java).apply {
            action = MaximusVpnService.ACTION_DISCONNECT
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Reconnects the active VPN tunnel.
     */
    fun reconnectVpn(context: Context) {
        val intent = Intent(context, MaximusVpnService::class.java).apply {
            action = MaximusVpnService.ACTION_RECONNECT
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
