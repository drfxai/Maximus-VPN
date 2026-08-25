package com.example.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ThemeToggleSwitch
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.ServerSortOption
import com.example.ui.viewmodel.ServerViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.VpnViewModel

@Composable
fun ServersScreen(
    serverViewModel: ServerViewModel,
    vpnViewModel: VpnViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToAddServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val serverList by serverViewModel.serverList.collectAsStateWithLifecycle()
    val searchQuery by serverViewModel.searchQuery.collectAsStateWithLifecycle()
    val onlyFavorites by serverViewModel.onlyFavorites.collectAsStateWithLifecycle()
    val sortOption by serverViewModel.sortOption.collectAsStateWithLifecycle()
    val isTestingAll by serverViewModel.isTestingAll.collectAsStateWithLifecycle()
    val testingStates by serverViewModel.serverTestingStates.collectAsStateWithLifecycle()

    val selectedProfileId by serverViewModel.selectedProfileId.collectAsStateWithLifecycle()
    val connectionState by vpnViewModel.connectionState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Screen Title & Action Bar with Theme Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Server Profiles",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${serverList.size} servers configured",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                // Theme Toggle Switch at Top
                ThemeToggleSwitch(
                    isDark = settings.darkTheme,
                    onThemeChange = { isDark ->
                        settingsViewModel.setDarkTheme(isDark)
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Ping All Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VLESS NODES",
                    color = AppTheme.colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Button(
                    onClick = { serverViewModel.testAllServers() },
                    enabled = !isTestingAll && serverList.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("test_all_servers_button")
                ) {
                    if (isTestingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AppTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pinging...", color = AppTheme.colors.primary, fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.NetworkPing,
                            contentDescription = "Ping All",
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ping All", color = AppTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar & Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { serverViewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("server_search_input"),
                    placeholder = { Text("Search servers, host, protocol...", color = AppTheme.colors.textMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textMuted)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { serverViewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTheme.colors.textMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppTheme.colors.surfaceCard,
                        unfocusedContainerColor = AppTheme.colors.surfaceCard,
                        focusedBorderColor = AppTheme.colors.primary,
                        unfocusedBorderColor = AppTheme.colors.borderSubtle,
                        focusedTextColor = AppTheme.colors.textPrimary,
                        unfocusedTextColor = AppTheme.colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Favorite Filter Toggle
                IconButton(
                    onClick = { serverViewModel.toggleFavoritesFilter() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (onlyFavorites) {
                                if (AppTheme.colors.isDark) Color(0xFF2E2410) else Color(0xFFFEF3C7)
                            } else AppTheme.colors.surfaceCard
                        )
                        .border(
                            1.dp,
                            if (onlyFavorites) AppTheme.colors.statusWarning.copy(alpha = 0.5f) else AppTheme.colors.borderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (onlyFavorites) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Filter Favorites",
                        tint = if (onlyFavorites) AppTheme.colors.statusWarning else AppTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Sort Menu
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surfaceCard)
                            .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Sort Options",
                            tint = AppTheme.colors.textSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier
                            .background(AppTheme.colors.surfaceCard)
                            .border(1.dp, AppTheme.colors.borderSubtle)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Default Order", color = AppTheme.colors.textPrimary) },
                            onClick = {
                                serverViewModel.setSortOption(ServerSortOption.DEFAULT)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Latency (Fastest)", color = AppTheme.colors.textPrimary) },
                            onClick = {
                                serverViewModel.setSortOption(ServerSortOption.LATENCY)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Name", color = AppTheme.colors.textPrimary) },
                            onClick = {
                                serverViewModel.setSortOption(ServerSortOption.NAME)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Server List or Empty State
            if (serverList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Dns,
                            contentDescription = null,
                            tint = AppTheme.colors.textMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || onlyFavorites) "No matching servers found" else "No VLESS servers configured",
                            color = AppTheme.colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the + button to add or import a VLESS configuration",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(serverList, key = { it.id }) { profile ->
                        ServerItemCard(
                            profile = profile,
                            isSelected = profile.id == selectedProfileId,
                            isConnected = profile.id == selectedProfileId && connectionState.isConnected,
                            testingStatus = testingStates[profile.id],
                            onSelect = {
                                serverViewModel.selectServer(profile)
                            },
                            onConnectDirect = {
                                serverViewModel.selectServer(profile)
                            },
                            onToggleFavorite = {
                                serverViewModel.toggleFavorite(profile)
                            },
                            onTestPing = {
                                serverViewModel.testServer(profile)
                            },
                            onDuplicate = {
                                serverViewModel.duplicateServer(profile)
                            },
                            onDelete = {
                                serverViewModel.deleteServer(profile.id)
                            },
                            onExportUri = {
                                serverViewModel.exportUri(it)
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button to Add Server
        FloatingActionButton(
            onClick = onNavigateToAddServer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_server_fab"),
            containerColor = AppTheme.colors.primary,
            contentColor = AppTheme.colors.onPrimary,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add VLESS Server",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
