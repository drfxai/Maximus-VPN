package com.drfxai.maximus.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val engine = remember { XrayDesktopEngine() }
    Window(onCloseRequest = {
        engine.stop()
        exitApplication()
    }, title = "Maximus VPN Desktop") {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF00E5A8),
                secondary = Color(0xFF4F8CFF),
                background = Color(0xFF090D14),
                surface = Color(0xFF131A24)
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                var uri by remember { mutableStateOf("") }
                var status by remember { mutableStateOf("Disconnected") }
                var detail by remember { mutableStateOf("Paste a VLESS link to connect.") }
                var busy by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MAXIMUS VPN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Windows 64-bit • Ubuntu 64-bit", color = Color(0xFF93A4C3))
                        }
                        Text(status, color = if (status == "Connected") Color(0xFF00E5A8) else Color(0xFFFFC247))
                    }

                    Card {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("VLESS Configuration", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = uri,
                                onValueChange = { uri = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                placeholder = { Text("vless://...") }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(enabled = !busy, onClick = {
                                    busy = true
                                    try {
                                        engine.connect(uri)
                                        status = "Connected"
                                        detail = "Xray TUN is running. System traffic is routed through the tunnel."
                                    } catch (e: Exception) {
                                        status = "Error"
                                        detail = e.message ?: "Unable to start Xray."
                                    } finally {
                                        busy = false
                                    }
                                }) { Text("Connect") }
                                TextButton(onClick = {
                                    engine.stop()
                                    status = "Disconnected"
                                    detail = "Tunnel stopped."
                                }) { Text("Disconnect") }
                            }
                        }
                    }

                    Card {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Engine", style = MaterialTheme.typography.titleLarge)
                            Text("Xray-core • TUN mode", color = Color(0xFF00E5A8))
                            Text(detail, color = Color(0xFF93A4C3))
                            Text("Windows requires an elevated session for system routing/TUN. Ubuntu may require root or the appropriate network capabilities.", color = Color(0xFFFFC247))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Maximus VPN Desktop", color = Color(0xFF6D7C96))
                }
            }
        }
    }
}
