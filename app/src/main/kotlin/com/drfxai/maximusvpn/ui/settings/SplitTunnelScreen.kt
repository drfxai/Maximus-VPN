package com.drfxai.maximusvpn.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drfxai.maximusvpn.data.model.SplitTunnelMode
import com.drfxai.maximusvpn.ui.viewmodel.SettingsViewModel

/**
 * Split tunneling editor: mode chips (Off / Only these apps / All except these apps)
 * plus a searchable launchable-app list with icons. Selections persist through the
 * ViewModel (SplitTunnelRepository) immediately on toggle.
 */
@Composable
fun SplitTunnelScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val allowList by viewModel.allowList.collectAsStateWithLifecycle()
    val excludeList by viewModel.excludeList.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val splitMode by viewModel.splitTunnelMode.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val selected = when (splitMode) {
        SplitTunnelMode.ALLOW_ONLY -> allowList
        SplitTunnelMode.EXCLUDE -> excludeList
        SplitTunnelMode.DISABLED -> emptySet()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Split Tunneling", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = splitMode == SplitTunnelMode.DISABLED,
                onClick = { viewModel.setSplitTunnelMode(SplitTunnelMode.DISABLED) },
                label = { Text("Off") }
            )
            FilterChip(
                selected = splitMode == SplitTunnelMode.ALLOW_ONLY,
                onClick = { viewModel.setSplitTunnelMode(SplitTunnelMode.ALLOW_ONLY) },
                label = { Text("Only these") }
            )
            FilterChip(
                selected = splitMode == SplitTunnelMode.EXCLUDE,
                onClick = { viewModel.setSplitTunnelMode(SplitTunnelMode.EXCLUDE) },
                label = { Text("All except") }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            when (splitMode) {
                SplitTunnelMode.DISABLED -> "All apps use the VPN."
                SplitTunnelMode.ALLOW_ONLY -> "ONLY the checked apps use the VPN."
                SplitTunnelMode.EXCLUDE -> "All apps EXCEPT the checked ones use the VPN."
            },
            fontSize = 12.sp, color = Color.Gray
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps…", fontSize = 13.sp) },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(installedApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }) { app ->
                val toAllow = splitMode == SplitTunnelMode.ALLOW_ONLY
                val checked = if (toAllow) app.packageName in allowList else app.packageName in excludeList
                Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        app.icon?.let { d ->
                            val bmp = android.graphics.Bitmap.createBitmap(
                                d.intrinsicWidth.coerceAtLeast(1),
                                d.intrinsicHeight.coerceAtLeast(1),
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bmp)
                            d.setBounds(0, 0, canvas.width, canvas.height)
                            d.draw(canvas)
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(app.packageName, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                        }
                        Checkbox(
                            checked = checked,
                            enabled = splitMode != SplitTunnelMode.DISABLED,
                            onCheckedChange = { viewModel.toggleApp(app.packageName, toAllow) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Done") }
            OutlinedButton(onClick = {
                viewModel.clearSplitLists()
            }) { Text("Clear all") }
        }
    }

    // Ensure the app list is loaded while this screen is visible.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadInstalledApps() }
}
