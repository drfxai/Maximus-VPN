package com.drfxai.maximusvpn.ui.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drfxai.maximusvpn.R
import com.drfxai.maximusvpn.data.model.ReconnectPolicy
import com.drfxai.maximusvpn.data.model.RoutingMode
import com.drfxai.maximusvpn.ui.components.ThemeToggleSwitch
import com.drfxai.maximusvpn.ui.theme.AppTheme
import com.drfxai.maximusvpn.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showConfigPreview by remember { mutableStateOf(false) }
    var previewJson by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }
    var dnsDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Screen Header with Title & Theme Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Tunnel & Engine Settings", color = AppTheme.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Routing, DNS, and Security preferences", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
            }

            ThemeToggleSwitch(
                isDark = (settings.themeMode != com.drfxai.maximusvpn.data.model.ThemeMode.LIGHT),
                onThemeChange = { isDark ->
                    viewModel.setThemeMode(if (isDark) com.drfxai.maximusvpn.data.model.ThemeMode.DARK else com.drfxai.maximusvpn.data.model.ThemeMode.LIGHT)
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: APPEARANCE & THEME
        SectionHeader("APPEARANCE & THEME")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppTheme.colors.surfaceElevated)
                                .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if ((settings.themeMode != com.drfxai.maximusvpn.data.model.ThemeMode.LIGHT)) Icons.Default.DarkMode else Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = if ((settings.themeMode != com.drfxai.maximusvpn.data.model.ThemeMode.LIGHT)) AppTheme.colors.primary else Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if ((settings.themeMode != com.drfxai.maximusvpn.data.model.ThemeMode.LIGHT)) "Dark Theme Active" else "Light Theme Active",
                                color = AppTheme.colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Swipe or tap the Sun/Moon switch to change appearance",
                                color = AppTheme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    ThemeToggleSwitch(
                        isDark = (settings.themeMode != com.drfxai.maximusvpn.data.model.ThemeMode.LIGHT),
                        onThemeChange = { isDark ->
                            viewModel.setThemeMode(if (isDark) com.drfxai.maximusvpn.data.model.ThemeMode.DARK else com.drfxai.maximusvpn.data.model.ThemeMode.LIGHT)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: ROUTING MODE
        SectionHeader("ROUTING RULES")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                RoutingOptionItem(
                    title = "Bypass LAN & Direct Local",
                    subtitle = "Routes LAN & private IPs directly; tunnels everything else",
                    isSelected = settings.routingMode == RoutingMode.RULE_BYPASS_LAN,
                    onSelect = { viewModel.setRoutingMode(RoutingMode.RULE_BYPASS_LAN) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                RoutingOptionItem(
                    title = "Global Proxy (All Traffic)",
                    subtitle = "Routes all traffic strictly through the VLESS tunnel",
                    isSelected = settings.routingMode == RoutingMode.GLOBAL,
                    onSelect = { viewModel.setRoutingMode(RoutingMode.GLOBAL) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                RoutingOptionItem(
                    title = "Custom Bypass List",
                    subtitle = "Bypasses specified custom domain & IP patterns",
                    isSelected = settings.routingMode == RoutingMode.BYPASS_SELECTED,
                    onSelect = { viewModel.setRoutingMode(RoutingMode.BYPASS_SELECTED) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: DNS CONFIGURATION
        SectionHeader("DNS CONFIGURATION")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Upstream DNS Provider", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = dnsDropdownExpanded,
                    onExpandedChange = { dnsDropdownExpanded = !dnsDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = getDnsLabel(settings.dnsServer),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dnsDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AppTheme.colors.surfaceElevated,
                            unfocusedContainerColor = AppTheme.colors.surfaceElevated,
                            focusedBorderColor = AppTheme.colors.primary,
                            unfocusedBorderColor = AppTheme.colors.borderSubtle,
                            focusedTextColor = AppTheme.colors.textPrimary,
                            unfocusedTextColor = AppTheme.colors.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = dnsDropdownExpanded,
                        onDismissRequest = { dnsDropdownExpanded = false },
                        modifier = Modifier
                            .background(AppTheme.colors.surfaceCard)
                            .border(1.dp, AppTheme.colors.borderSubtle)
                    ) {
                        listOf(
                            "1.1.1.1" to "Cloudflare (1.1.1.1)",
                            "8.8.8.8" to "Google DNS (8.8.8.8)",
                            "9.9.9.9" to "Quad9 Secure (9.9.9.9)",
                            "https://1.1.1.1/dns-query" to "Cloudflare DoH"
                        ).forEach { (ip, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = AppTheme.colors.textPrimary) },
                                onClick = {
                                    viewModel.setDnsServer(ip)
                                    dnsDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: SECURITY & NETWORK TOGGLES
        SectionHeader("SECURITY & TUNNELING")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                SettingToggleItem(
                    icon = Icons.Default.Lock,
                    title = "Kill Switch (Block Non-VPN)",
                    subtitle = "Prevents packet leaks if VPN disconnects unexpectedly",
                    checked = settings.killSwitchEnabled,
                    onCheckedChange = { viewModel.setKillSwitch(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingToggleItem(
                    icon = Icons.Default.Route,
                    title = "IPv6 Tunnel Routing",
                    subtitle = "Assigns IPv6 TUN virtual address & routes IPv6 traffic",
                    checked = settings.ipv6Enabled,
                    onCheckedChange = { viewModel.setIpv6(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingToggleItem(
                    icon = Icons.Default.Refresh,
                    title = "Auto-Reconnect on Network Switch",
                    subtitle = "Automatically re-establishes tunnel on WiFi/Cellular transition",
                    checked = settings.reconnectPolicy != com.drfxai.maximusvpn.data.model.ReconnectPolicy.OFF,
                    onCheckedChange = { viewModel.setReconnectPolicy(if (it) ReconnectPolicy.BALANCED else ReconnectPolicy.OFF) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: ADVANCED & DIAGNOSTICS
        SectionHeader("ENGINE DIAGNOSTICS")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Xray JSON Preview Button
                Button(
                    onClick = {
                        scope.launch {
                            previewJson = viewModel.getPreviewConfigJson()
                            showConfigPreview = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("view_xray_config_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceElevated),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Raw Xray JSON Config", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Reset Settings Button
                Button(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (AppTheme.colors.isDark) Color(0xFF241416) else Color(0xFFFEF2F2)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = AppTheme.colors.statusError, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset All Settings to Defaults", color = AppTheme.colors.statusError, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: DEVELOPER & ABOUT
        SectionHeader("ABOUT & DEVELOPER")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF030818))
                                .border(1.dp, AppTheme.colors.borderMedium, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_maximus_logo),
                                contentDescription = "Maximus App Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Maximus VPN", color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Enterprise VLESS & Xray Client", color = AppTheme.colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppTheme.colors.primary.copy(alpha = 0.15f))
                            .border(1.dp, AppTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("v1.0.0", color = AppTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.borderSubtle))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Developer / Organization", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                    Text("DrFXAi", color = AppTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Core Proxy Engine", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                    Text("Xray-core 1.8.24", color = AppTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Protocols Supported", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "VLESS • REALITY • Vision • WS • gRPC",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showConfigPreview) {
        ConfigPreviewDialog(
            configJson = previewJson,
            onDismiss = { showConfigPreview = false }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Settings", color = AppTheme.colors.textPrimary) },
            text = { Text("Are you sure you want to reset all routing and engine settings to factory defaults?", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetToDefaults()
                        Toast.makeText(context, "Settings restored to defaults", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset", color = AppTheme.colors.statusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceCard
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = AppTheme.colors.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun RoutingOptionItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppTheme.colors.primary,
                unselectedColor = AppTheme.colors.textMuted
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppTheme.colors.textSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.colors.surfaceElevated)
                    .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AppTheme.colors.textSecondary, fontSize = 11.sp)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppTheme.colors.onPrimary,
                checkedTrackColor = AppTheme.colors.primary,
                uncheckedThumbColor = AppTheme.colors.textMuted,
                uncheckedTrackColor = AppTheme.colors.surfaceElevated
            )
        )
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Maximus VPN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    "Version 1.0.0 \u2022 by DrFXAi",
                    fontSize = 13.sp,
                    color = AppTheme.colors.textSecondary
                )
                Text(
                    "VLESS client powered by Xray-core",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textMuted
                )
            }
        }

    }
}

private fun getDnsLabel(dns: String): String {
    return when (dns) {
        "1.1.1.1" -> "Cloudflare (1.1.1.1)"
        "8.8.8.8" -> "Google DNS (8.8.8.8)"
        "9.9.9.9" -> "Quad9 Secure (9.9.9.9)"
        "https://1.1.1.1/dns-query" -> "Cloudflare DoH"
        else -> dns
    }
}
