package com.drfxai.maximusvpn.vpn

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * One-tap connect/disconnect from Quick Settings.
 * Connects the currently selected profile; disconnects if already connected.
 * Tile state mirrors MaximusVpnService.vpnState while the tile is visible.
 */
class MaximusTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        val s = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = s
        s.launch {
            MaximusVpnService.vpnState.collect { updateTile() }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val state = MaximusVpnService.vpnState.value
        val intent = when (state.status) {
            com.drfxai.maximusvpn.data.model.ConnectionStatus.CONNECTED,
            com.drfxai.maximusvpn.data.model.ConnectionStatus.CONNECTING ->
                Intent(this, MaximusVpnService::class.java).apply {
                    setAction(MaximusVpnService.ACTION_DISCONNECT)
                }
            else -> Intent(this, MaximusVpnService::class.java).apply {
                setAction(MaximusVpnService.ACTION_CONNECT)
            }
        }
        startService(intent)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val status = MaximusVpnService.vpnState.value.status
        val (state, label) = when (status) {
            com.drfxai.maximusvpn.data.model.ConnectionStatus.CONNECTED -> Tile.STATE_ACTIVE to "Maximus — On"
            com.drfxai.maximusvpn.data.model.ConnectionStatus.CONNECTING,
            com.drfxai.maximusvpn.data.model.ConnectionStatus.RECONNECTING,
            com.drfxai.maximusvpn.data.model.ConnectionStatus.DISCONNECTING -> Tile.STATE_ACTIVE to "Maximus…"
            else -> Tile.STATE_INACTIVE to "Maximus VPN"
        }
        tile.state = state
        tile.label = label
        tile.updateTile()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
