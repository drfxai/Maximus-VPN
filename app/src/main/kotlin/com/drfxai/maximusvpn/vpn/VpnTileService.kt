package com.drfxai.maximusvpn.vpn

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.drfxai.maximusvpn.data.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * One-tap connect/disconnect Quick Settings tile.
 *
 * Tap behaviour:
 *  - DISCONNECTED/FAILED → start foreground service CONNECT with the remembered profile.
 *    If VPN consent was never granted the service fails fast; the user must grant it
 *    from the app once (Android does not allow showing consent UI from a Tile).
 *  - CONNECTED → disconnect.
 *  - busy states → no-op (tile shows as "unavailable" while transitioning).
 */
class VpnTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render() }
    }

    override fun onClick() {
        super.onClick()
        val status = MaximusVpnService.vpnState.value.status
        when (status) {
            ConnectionStatus.CONNECTED -> VpnController.stopVpn(applicationContext)
            ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED ->
                VpnController.startVpnFromSelected(applicationContext)
            else -> Unit
        }
        scope.launch { render() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun render() {
        val tile = qsTile ?: return
        val state = MaximusVpnService.vpnState.value
        try {
            when (state.status) {
                ConnectionStatus.CONNECTED -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = state.activeProfile?.name ?: "Maximus VPN"
                    tile.subtitle = "Connected"
                }
                ConnectionStatus.PREPARING, ConnectionStatus.CONNECTING,
                ConnectionStatus.RECONNECTING, ConnectionStatus.DISCONNECTING -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.subtitle = "Working…"
                }
                ConnectionStatus.DISCONNECTED -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Maximus VPN"
                    tile.subtitle = "Tap to connect"
                }
                ConnectionStatus.FAILED -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.subtitle = "Failed — tap to retry"
                }
            }
        } catch (_: Exception) {}
        tile.updateTile()
    }
}
