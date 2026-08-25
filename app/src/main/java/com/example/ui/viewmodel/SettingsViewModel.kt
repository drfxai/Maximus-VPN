package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.RayApplication
import com.example.core.SecretRedactor
import com.example.data.model.AppSettings
import com.example.data.model.RoutingMode
import com.example.data.model.VlessProfile
import com.example.data.repository.ServerRepository
import com.example.data.repository.SettingsRepository
import com.example.xray.XrayConfigBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val settingsRepository: SettingsRepository = RayApplication.instance.settingsRepository,
    private val serverRepository: ServerRepository = RayApplication.instance.serverRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow

    val activeProfile: StateFlow<VlessProfile?> = combine(
        serverRepository.allProfiles,
        settingsRepository.settingsFlow
    ) { profiles, settings ->
        profiles.firstOrNull { it.id == settings.selectedProfileId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setDarkTheme(darkTheme: Boolean) {
        settingsRepository.setDarkTheme(darkTheme)
    }

    fun toggleDarkTheme() {
        val current = settingsRepository.getSettings()
        settingsRepository.setDarkTheme(!current.darkTheme)
    }

    fun setRoutingMode(mode: RoutingMode) {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(routingMode = mode))
    }

    fun setDnsServer(dns: String) {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(dnsServer = dns))
    }

    fun setCustomDns(dns: String) {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(customDns = dns))
    }

    fun setKillSwitch(enabled: Boolean) {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(killSwitchEnabled = enabled))
    }

    fun setIpv6(enabled: Boolean) {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(ipv6Enabled = enabled))
    }

    fun setAutoReconnect(enabled: Boolean) {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(autoReconnect = enabled))
    }

    fun setLogLevel(level: String) {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(logLevel = level))
    }

    fun setCustomBypassRules(rules: String) {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(customBypassRules = rules))
    }

    suspend fun getPreviewConfigJson(): String {
        val currentSettings = settingsRepository.getSettings()
        val profile = currentSettings.selectedProfileId?.let { serverRepository.getProfileById(it) }
            ?: VlessProfile(
                name = "Preview Server",
                address = "example.com",
                port = 443,
                uuid = "00000000-0000-0000-0000-000000000000",
                transport = "tcp",
                security = "reality",
                sni = "example.com",
                publicKey = "SAMPLE_KEY",
                flow = "xtls-rprx-vision"
            )
        val rawJson = XrayConfigBuilder.buildJson(profile, currentSettings)
        return SecretRedactor.redact(rawJson)
    }

    fun resetToDefaults() {
        settingsRepository.updateSettings(AppSettings())
    }
}
