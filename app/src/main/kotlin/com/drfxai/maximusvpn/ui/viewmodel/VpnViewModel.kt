package com.drfxai.maximusvpn.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drfxai.maximusvpn.MaximusApplication
import com.drfxai.maximusvpn.data.model.ConnectionState
import com.drfxai.maximusvpn.data.model.ConnectionStatus
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.data.repository.ServerRepository
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.vpn.VpnController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(
    private val serverRepository: ServerRepository = MaximusApplication.instance.serverRepository,
    private val settingsRepository: SettingsRepository = MaximusApplication.instance.settingsRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = VpnController.connectionState

    val selectedProfile: StateFlow<VlessProfile?> = combine(
        serverRepository.allProfiles,
        settingsRepository.settingsFlow
    ) { profiles, settings ->
        if (profiles.isEmpty()) return@combine null
        val targetId = settings.selectedProfileId
        profiles.firstOrNull { it.id == targetId } ?: profiles.first()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun toggleConnection(context: Context) {
        val currentState = connectionState.value
        if (currentState.isConnected || currentState.isBusy) {
            VpnController.stopVpn(context)
        } else {
            selectedProfile.value?.let { profile ->
                VpnController.startVpn(context, profile)
            }
        }
    }

    fun selectServer(profile: VlessProfile) {
        settingsRepository.setSelectedProfileId(profile.id)
    }

    fun reconnect(context: Context) {
        VpnController.reconnectVpn(context)
    }
}
