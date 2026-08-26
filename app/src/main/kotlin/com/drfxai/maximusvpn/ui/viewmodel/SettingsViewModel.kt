package com.drfxai.maximusvpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drfxai.maximusvpn.MaximusApplication
import com.drfxai.maximusvpn.core.SecretRedactor
import com.drfxai.maximusvpn.data.model.AppSettings
import com.drfxai.maximusvpn.data.model.ReconnectPolicy
import com.drfxai.maximusvpn.data.model.RoutingMode
import com.drfxai.maximusvpn.data.model.SplitTunnelMode
import com.drfxai.maximusvpn.data.model.ThemeMode
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.data.repository.ServerRepository
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.data.repository.SplitTunnelRepository
import com.drfxai.maximusvpn.xray.XrayConfigBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository = MaximusApplication.instance.settingsRepository,
    private val serverRepository: ServerRepository = MaximusApplication.instance.serverRepository,
    private val splitTunnelRepository: SplitTunnelRepository =
        SplitTunnelRepository(MaximusApplication.instance)
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow

    private val _installedApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val installedApps: StateFlow<List<AppEntry>> = _installedApps.asStateFlow()

    private val _allowList = MutableStateFlow<Set<String>>(splitTunnelRepository.getAllowList())
    val allowList: StateFlow<Set<String>> = _allowList.asStateFlow()

    private val _excludeList = MutableStateFlow<Set<String>>(splitTunnelRepository.getExcludeList())
    val excludeList: StateFlow<Set<String>> = _excludeList.asStateFlow()

    val splitTunnelMode: StateFlow<SplitTunnelMode> =
        settingsRepository.settingsFlow.map { it.splitTunnelMode }.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            settingsRepository.getSettings().splitTunnelMode
        )

    data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?
    )

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
    }

    /** Back-compat for existing toggle UI. */
    fun toggleDarkTheme() {
        val current = settingsRepository.getSettings()
        settingsRepository.setThemeMode(
            if (current.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        )
    }

    fun setRoutingMode(mode: RoutingMode) {
        settingsRepository.update { it.copy(routingMode = mode) }
    }

    fun setDnsServer(dns: String) {
        settingsRepository.update { it.copy(dnsServer = dns) }
    }

    fun setCustomDns(dns: String) {
        settingsRepository.update { it.copy(customDns = dns) }
    }

    fun setKillSwitch(enabled: Boolean) {
        settingsRepository.update { it.copy(killSwitchEnabled = enabled) }
    }

    fun setIpv6(enabled: Boolean) {
        settingsRepository.update { it.copy(ipv6Enabled = enabled) }
    }

    fun setReconnectPolicy(policy: ReconnectPolicy) {
        settingsRepository.update { it.copy(reconnectPolicy = policy) }
    }

    fun setAutoConnectOnBoot(enabled: Boolean) {
        settingsRepository.update { it.copy(autoConnectOnBoot = enabled) }
    }

    fun setSplitTunnelMode(mode: SplitTunnelMode) {
        settingsRepository.setSplitTunnelMode(mode)
    }

    fun setSubscriptionAutoUpdate(hours: Int) {
        settingsRepository.update { it.copy(subscriptionAutoUpdateHours = hours) }
    }

    fun setSubscriptionWifiOnly(wifiOnly: Boolean) {
        settingsRepository.update { it.copy(subscriptionUpdateOnWifiOnly = wifiOnly) }
    }

    fun completeOnboarding() {
        settingsRepository.setOnboardingCompleted()
    }

    // --- Split tunneling ---

    /** Loads the launchable-app list (Android 11+ requires QUERY_ALL_PACKAGES). */
    fun loadInstalledApps() {
        viewModelScope.launch {
            val pm = MaximusApplication.instance.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val entries = pm.queryIntentActivities(intent, 0)
                .asSequence()
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != MaximusApplication.instance.packageName } // never list ourselves
                .map { info ->
                    AppEntry(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info)?.toString() ?: info.packageName,
                        icon = try { pm.getApplicationIcon(info.packageName) } catch (_: Exception) { null }
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
            _installedApps.value = entries
        }
    }

    fun toggleApp(packageName: String, toAllowList: Boolean) {
        splitTunnelRepository.togglePackage(packageName, toAllowList)
        _allowList.value = splitTunnelRepository.getAllowList()
        _excludeList.value = splitTunnelRepository.getExcludeList()
    }

    fun clearSplitLists() {
        splitTunnelRepository.setAllowList(emptySet())
        splitTunnelRepository.setExcludeList(emptySet())
        _allowList.value = emptySet()
        _excludeList.value = emptySet()
    }

    fun setLogLevel(level: String) {
        settingsRepository.update { it.copy(logLevel = level) }
    }

    fun setCustomBypassRules(rules: String) {
        settingsRepository.update { it.copy(customBypassRules = rules) }
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
        val rawJson = XrayConfigBuilder.buildTunConfig(profile, currentSettings, tunFd = 0)
        return SecretRedactor.redact(rawJson)
    }

    fun resetToDefaults() {
        clearSplitLists()
        settingsRepository.updateSettings(AppSettings())
    }
}
