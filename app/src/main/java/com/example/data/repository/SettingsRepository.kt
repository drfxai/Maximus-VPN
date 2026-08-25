package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppSettings
import com.example.data.model.RoutingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("raytunnel_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): AppSettings {
        val routingModeName = prefs.getString("routing_mode", RoutingMode.RULE_BYPASS_LAN.name) ?: RoutingMode.RULE_BYPASS_LAN.name
        val routingMode = try {
            RoutingMode.valueOf(routingModeName)
        } catch (_: Exception) {
            RoutingMode.RULE_BYPASS_LAN
        }

        return AppSettings(
            darkTheme = prefs.getBoolean("dark_theme", true),
            routingMode = routingMode,
            dnsServer = prefs.getString("dns_server", "1.1.1.1") ?: "1.1.1.1",
            customDns = prefs.getString("custom_dns", "8.8.8.8") ?: "8.8.8.8",
            killSwitchEnabled = prefs.getBoolean("kill_switch", false),
            ipv6Enabled = prefs.getBoolean("ipv6_enabled", true),
            autoReconnect = prefs.getBoolean("auto_reconnect", true),
            autoConnectOnBoot = prefs.getBoolean("auto_boot", false),
            logLevel = prefs.getString("log_level", "warning") ?: "warning",
            selectedProfileId = prefs.getString("selected_profile_id", null),
            mtu = prefs.getInt("mtu", 1500),
            customBypassRules = prefs.getString("bypass_rules", "localhost,127.0.0.1,*.local,*.lan") ?: ""
        )
    }

    fun getSettings(): AppSettings = _settingsFlow.value

    fun updateSettings(settings: AppSettings) {
        prefs.edit()
            .putBoolean("dark_theme", settings.darkTheme)
            .putString("routing_mode", settings.routingMode.name)
            .putString("dns_server", settings.dnsServer)
            .putString("custom_dns", settings.customDns)
            .putBoolean("kill_switch", settings.killSwitchEnabled)
            .putBoolean("ipv6_enabled", settings.ipv6Enabled)
            .putBoolean("auto_reconnect", settings.autoReconnect)
            .putBoolean("auto_boot", settings.autoConnectOnBoot)
            .putString("log_level", settings.logLevel)
            .putString("selected_profile_id", settings.selectedProfileId)
            .putInt("mtu", settings.mtu)
            .putString("bypass_rules", settings.customBypassRules)
            .apply()

        _settingsFlow.value = settings
    }

    fun setDarkTheme(darkTheme: Boolean) {
        val current = _settingsFlow.value
        updateSettings(current.copy(darkTheme = darkTheme))
    }

    fun setSelectedProfileId(id: String?) {
        val current = _settingsFlow.value
        updateSettings(current.copy(selectedProfileId = id))
    }
}
