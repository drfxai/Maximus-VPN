package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.MainApp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DiagnosticsViewModel
import com.example.ui.viewmodel.ServerViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.VpnViewModel
import com.example.vpn.VpnController

class MainActivity : ComponentActivity() {

    private val vpnViewModel: VpnViewModel by viewModels()
    private val serverViewModel: ServerViewModel by viewModels()
    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnViewModel.toggleConnection(this)
        } else {
            Toast.makeText(this, "VPN permission is required to establish secure tunnel", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = settings.darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppTheme.colors.background
                ) {
                    MainApp(
                        vpnViewModel = vpnViewModel,
                        serverViewModel = serverViewModel,
                        diagnosticsViewModel = diagnosticsViewModel,
                        settingsViewModel = settingsViewModel,
                        onRequestVpnPermission = {
                            val prepareIntent = VpnController.prepareVpn(this)
                            if (prepareIntent != null) {
                                vpnPermissionLauncher.launch(prepareIntent)
                            }
                        }
                    )
                }
            }
        }
    }
}
