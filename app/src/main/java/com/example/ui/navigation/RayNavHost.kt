package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.addserver.AddServerScreen
import com.example.ui.diagnostics.DiagnosticsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.servers.ServersScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.DiagnosticsViewModel
import com.example.ui.viewmodel.ServerViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.VpnViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val unselectedIcon: ImageVector) {
    data object Home : Screen("home", "Tunnel", Icons.Filled.Shield, Icons.Outlined.Shield)
    data object Servers : Screen("servers", "Servers", Icons.Filled.Dns, Icons.Outlined.Dns)
    data object Diagnostics : Screen("diagnostics", "Logs", Icons.Filled.Analytics, Icons.Outlined.Analytics)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object AddServer : Screen("add_server", "Add Server", Icons.Filled.Dns, Icons.Outlined.Dns)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Servers,
    Screen.Diagnostics,
    Screen.Settings
)

@Composable
fun MainApp(
    vpnViewModel: VpnViewModel,
    serverViewModel: ServerViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestVpnPermission: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        containerColor = AppTheme.colors.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = AppTheme.colors.surfaceCard,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = AppTheme.colors.borderSubtle
                    )
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.icon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp
                                )
                            },
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppTheme.colors.primary,
                                selectedTextColor = AppTheme.colors.primary,
                                indicatorColor = AppTheme.colors.surfaceElevated,
                                unselectedIconColor = AppTheme.colors.textSecondary,
                                unselectedTextColor = AppTheme.colors.textSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    vpnViewModel = vpnViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateToServers = { navController.navigate(Screen.Servers.route) },
                    onRequestVpnPermission = onRequestVpnPermission
                )
            }
            composable(Screen.Servers.route) {
                ServersScreen(
                    serverViewModel = serverViewModel,
                    vpnViewModel = vpnViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateToAddServer = { navController.navigate(Screen.AddServer.route) }
                )
            }
            composable(Screen.AddServer.route) {
                AddServerScreen(
                    serverViewModel = serverViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(
                    viewModel = diagnosticsViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel
                )
            }
        }
    }
}
