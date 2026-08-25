package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.RayApplication
import com.example.data.model.ConnectionState
import com.example.data.model.ConnectionStatus
import com.example.data.model.VlessProfile
import com.example.data.repository.ServerRepository
import com.example.data.repository.SettingsRepository
import com.example.vpn.VpnController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(
    private val serverRepository: ServerRepository = RayApplication.instance.serverRepository,
    private val settingsRepository: SettingsRepository = RayApplication.instance.settingsRepository
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
