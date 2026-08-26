package com.drfxai.maximusvpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drfxai.maximusvpn.data.database.AppDatabase
import com.drfxai.maximusvpn.data.repository.ServerRepository
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.xray.XrayLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Connects the last selected profile after boot when autoConnectOnBoot is enabled.
 * VPN consent must already have been granted by the user (establish() fails silently
 * otherwise); we never bypass that.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(appContext).getSettings()
                if (!settings.autoConnectOnBoot) return@launch

                val profileId = settings.selectedProfileId ?: return@launch
                val profile = ServerRepository(AppDatabase.getInstance(appContext).serverProfileDao())
                    .getProfileById(profileId) ?: return@launch

                XrayLogManager.appendLog(
                    "Boot completed — auto-connecting '${profile.name}' per settings.", "VPN"
                )
                val serviceIntent = Intent(appContext, MaximusVpnService::class.java).apply {
                    setAction(MaximusVpnService.ACTION_CONNECT)
                    putExtra(MaximusVpnService.EXTRA_PROFILE_ID, profile.id)
                }
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                XrayLogManager.appendLog("Boot auto-connect failed: ${e.message}", "ERROR")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
