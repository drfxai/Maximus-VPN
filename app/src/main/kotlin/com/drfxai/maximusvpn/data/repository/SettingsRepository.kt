package com.drfxai.maximusvpn.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.drfxai.maximusvpn.data.model.AppSettings
import com.drfxai.maximusvpn.data.model.ReconnectPolicy
import com.drfxai.maximusvpn.data.model.RoutingMode
import com.drfxai.maximusvpn.data.model.SplitTunnelMode
import com.drfxai.maximusvpn.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("maximusvpn_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): AppSettings {
        val routingModeName = prefs.getString("routing_mode", RoutingMode.RULE_BYPASS_LAN.name) ?: RoutingMode.RULE_BYPASS_LAN.name
        val routingMode = try {
            RoutingMode.valueOf(routingModeName)
        } catch (_: Exception) {
            RoutingMode.RULE_BYPASS_LAN
        }

        // Legacy keys → new model:
        //   dark_theme:Boolean → themeMode (true=DARK, false=LIGHT)
        //   auto_reconnect:Boolean → reconnectPolicy (true=BALANCED, false=OFF)
        val legacyDark = prefs.getBoolean("dark_theme", true)
        val legacyAutoReconnect = prefs.getBoolean("auto_reconnect", true)
        val themeMode = try {
            ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: if (legacyDark) ThemeMode.DARK.name else ThemeMode.LIGHT.name)
        } catch (_: Exception) { if (legacyDark) ThemeMode.DARK else ThemeMode.LIGHT }
        val reconnectPolicy = try {
            ReconnectPolicy.valueOf(
                prefs.getString("reconnect_policy", null) ?: if (legacyAutoReconnect) ReconnectPolicy.BALANCED.name else ReconnectPolicy.OFF.name
            )
        } catch (_: Exception) { if (legacyAutoReconnect) ReconnectPolicy.BALANCED else ReconnectPolicy.OFF }

        return AppSettings(
            themeMode = themeMode,
            routingMode = routingMode,
            dnsServer = prefs.getString("dns_server", "1.1.1.1") ?: "1.1.1.1",
            customDns = prefs.getString("custom_dns", "8.8.8.8") ?: "8.8.8.8",
            ipv6Enabled = prefs.getBoolean("ipv6_enabled", true),
            mtu = prefs.getInt("mtu", 1500),
            customBypassRules = prefs.getString("bypass_rules", "localhost,127.0.0.1,*.local,*.lan") ?: "",
            killSwitchEnabled = prefs.getBoolean("kill_switch", false),
            reconnectPolicy = reconnectPolicy,
            autoConnectOnBoot = prefs.getBoolean("auto_boot", false),
            splitTunnelMode = try {
                SplitTunnelMode.valueOf(
                    prefs.getString("split_tunnel_mode", SplitTunnelMode.DISABLED.name) ?: SplitTunnelMode.DISABLED.name
                )
            } catch (_: Exception) { SplitTunnelMode.DISABLED },
            // Split-tunnel allow/exclude lists live in SplitTunnelRepository (separate prefs)
            subscriptionAutoUpdateHours = prefs.getInt("sub_update_hours", 24),
            subscriptionUpdateOnWifiOnly = prefs.getBoolean("sub_wifi_only", true),
            logLevel = prefs.getString("log_level", "warning") ?: "warning",
            selectedProfileId = prefs.getString("selected_profile_id", null),
            onboardingCompleted = prefs.getBoolean("onboarding_done", false)
        )
    }

    fun getSettings(): AppSettings = _settingsFlow.value

    /** Convenience: transform current settings through [block] and persist. */
    fun update(block: (AppSettings) -> AppSettings) = updateSettings(block(_settingsFlow.value))

    fun updateSettings(settings: AppSettings) {
        prefs.edit()
            .putString("theme_mode", settings.themeMode.name)
            .putBoolean("dark_theme", settings.themeMode == ThemeMode.DARK || settings.themeMode == ThemeMode.AMOLED) // legacy read compat
            .putString("routing_mode", settings.routingMode.name)
            .putString("dns_server", settings.dnsServer)
            .putString("custom_dns", settings.customDns)
            .putBoolean("ipv6_enabled", settings.ipv6Enabled)
            .putInt("mtu", settings.mtu)
            .putString("bypass_rules", settings.customBypassRules)
            .putBoolean("kill_switch", settings.killSwitchEnabled)
            .putString("reconnect_policy", settings.reconnectPolicy.name)
            .putBoolean("auto_reconnect", settings.reconnectPolicy != ReconnectPolicy.OFF) // legacy read compat
            .putBoolean("auto_boot", settings.autoConnectOnBoot)
            .putString("split_tunnel_mode", settings.splitTunnelMode.name)
            // Split-tunnel package lists are persisted by SplitTunnelRepository
            .putInt("sub_update_hours", settings.subscriptionAutoUpdateHours)
            .putBoolean("sub_wifi_only", settings.subscriptionUpdateOnWifiOnly)
            .putString("log_level", settings.logLevel)
            .putString("selected_profile_id", settings.selectedProfileId)
            .putBoolean("onboarding_done", settings.onboardingCompleted)
            .apply()

        _settingsFlow.value = settings
    }

    fun setThemeMode(mode: ThemeMode) =
        updateSettings(_settingsFlow.value.copy(themeMode = mode))

    /** Legacy helper kept for callers still toggling dark mode. */
    fun setDarkTheme(darkTheme: Boolean) = setThemeMode(if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT)

    fun setSelectedProfileId(id: String?) =
        updateSettings(_settingsFlow.value.copy(selectedProfileId = id))

    fun setOnboardingCompleted() =
        updateSettings(_settingsFlow.value.copy(onboardingCompleted = true))
}
