package com.drfxai.maximus.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ---------- Maximus dark FinTech palette (matches the Android app) ----------
private val Bg = Color(0xFF090D14)
private val Surface = Color(0xFF111827)
private val CardBg = Color(0xFF131A24)
private val Accent = Color(0xFF00E5A8)
private val AccentBlue = Color(0xFF4F8CFF)
private val TextPrimary = Color(0xFFE6ECF5)
private val TextSecondary = Color(0xFF93A4C3)
private val TextMuted = Color(0xFF6D7C96)
private val Warn = Color(0xFFFFC247)
private val ErrorRed = Color(0xFFFF5A76)

@Composable
fun MaximusDesktopApp(engine: XrayDesktopEngine, store: ServerStore, onExit: () -> Unit) {
    var screen by remember { mutableStateOf("Dashboard") }
    val state by engine.state.collectAsState()
    val logs by engine.logs.collectAsState()
    val traffic by engine.traffic.collectAsState()
    val scope = rememberCoroutineScope()

    val screens = listOf("Dashboard", "Servers", "Connection", "Diagnostics", "Logs", "Settings")

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = AccentBlue,
            background = Bg,
            surface = CardBg,
            error = ErrorRed
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Row(Modifier.fillMaxSize()) {
                // Sidebar navigation
                Column(
                    Modifier.width(190.dp).fillMaxHeight().background(Surface).padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("MAXIMUS", color = Accent, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 3.sp)
                    Text("VPN", color = TextSecondary, fontWeight = FontWeight.Light, fontSize = 12.sp, letterSpacing = 6.sp)
                    Spacer(Modifier.height(26.dp))
                    screens.forEach { s ->
                        val selected = screen == s
                        Card(
                            onClick = { screen = s },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) Accent.copy(alpha = 0.14f) else Color.Transparent
                            )
                        ) {
                            Text(
                                s,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                                color = if (selected) Accent else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    StatusPill(state.status.name)
                    Spacer(Modifier.height(10.dp))
                }

                // Content area
                Box(Modifier.weight(1f).fillMaxHeight().padding(22.dp)) {
                    when (screen) {
                        "Dashboard" -> DashboardScreen(engine, store, state, traffic)
                        "Servers" -> ServersScreen(store, engine, state) { screen = "Dashboard" }
                        "Connection" -> ConnectionScreen(store, engine, state)
                        "Diagnostics" -> DiagnosticsScreen(engine, state)
                        "Logs" -> LogsScreen(logs, engine)
                        "Settings" -> SettingsScreen(onExit)
                    }
                }
            }
        }
    }
}

// ---------- Dashboard ----------

@Composable
private fun DashboardScreen(
    engine: XrayDesktopEngine,
    store: ServerStore,
    state: DesktopConnectionState,
    traffic: TrafficSnapshot
) {
    var publicIp by remember { mutableStateOf("…") }
    LaunchedEffect(state.isConnected) {
        publicIp = "checking…"
        // Runs through current network path: shows tunnel exit IP when connected
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            publicIp = fetchPublicIp() ?: "unavailable"
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dashboard", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(systemLabel(), color = TextMuted, fontSize = 12.sp)
            }
            BigConnectButton(engine, state)
        }

        // Current server card
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("CURRENT SERVER")
                val p = state.activeProfile
                if (p != null) {
                    Text(p.name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(p.subtitle, color = Accent, fontSize = 13.sp)
                } else {
                    Text(
                        if (store.all().isNotEmpty()) "No server connected — pick one in Servers"
                        else "No servers yet — import a vless:// link in Servers",
                        color = TextSecondary, fontSize = 14.sp
                    )
                }
            }
        }

        // Metrics grid
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("UPLOAD", formatBps(traffic.upBps), formatBytes(traffic.totalUp), Accent, Modifier.weight(1f))
            MetricCard("DOWNLOAD", formatBps(traffic.downBps), formatBytes(traffic.totalDown), AccentBlue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("PUBLIC IP", publicIp, "egress address", Color(0xFF9B7BFF), Modifier.weight(1f))
            MetricCard(
                "CONNECTION TIME",
                state.connectedAtMs?.let { elapsed(it) } ?: "—",
                state.errorMessage ?: "stable",
                if (state.errorMessage != null) Warn else Color(0xFF39D98A),
                Modifier.weight(1f)
            )
        }

        if (state.status == DesktopConnectionStatus.FAILED && state.errorMessage != null) {
            GlassCard(border = ErrorRed.copy(alpha = 0.5f)) {
                Text("LAST ERROR", color = ErrorRed, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(state.errorMessage ?: "", color = ErrorRed, fontSize = 13.sp)
            }
        }

        Text(
            "TUN mode routes all system traffic through Xray-core. Windows may require an elevated session; Ubuntu may require root or suitable capabilities.",
            color = TextMuted, fontSize = 11.sp
        )
    }
}

@Composable
private fun BigConnectButton(engine: XrayDesktopEngine, state: DesktopConnectionState) {
    val store = remember { ServerStore() }
    Button(
        onClick = {
            if (state.isConnected || state.isBusy) {
                engine.disconnect()
            } else {
                val target = state.activeProfile ?: store.all().firstOrNull { it.favorite } ?: store.all().firstOrNull()
                if (target != null) {
                    CoroutineScope(Dispatchers.IO).launch { engine.connect(target) }
                }
            }
        },
        enabled = !state.isBusy,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                state.isConnected -> ErrorRed
                state.isBusy -> TextMuted
                else -> Accent
            },
            contentColor = if (state.isConnected) Color.White else Color(0xFF06251B)
        )
    ) {
        Text(
            when {
                state.isBusy -> "WORKING…"
                state.isConnected -> "DISCONNECT"
                else -> "CONNECT"
            },
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp
        )
    }
}

// ---------- Servers ----------

@Composable
private fun ServersScreen(
    store: ServerStore,
    engine: XrayDesktopEngine,
    state: DesktopConnectionState,
    onConnected: () -> Unit
) {
    var servers by remember { mutableStateOf(store.all()) }
    var importText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() { servers = store.all() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Servers", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("IMPORT vless:// LINK")
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("vless://uuid@host:443?security=reality&…#MyServer", color = TextMuted, fontSize = 12.sp) },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF2A3648),
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = Accent
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        try {
                            val profile = ServerProfile.fromVlessUri(importText.trim())
                            val err = profile.validationError
                            if (err != null) {
                                message = "Rejected: $err"
                            } else {
                                store.upsert(profile); refresh(); importText = ""; message = "Imported '${profile.name}'."
                            }
                        } catch (e: Exception) {
                            message = "Rejected: ${e.message}"
                        }
                    }) { Text("Import") }
                    if (message != null) {
                        Text(message ?: "", color = if ((message ?: "").startsWith("Rejected")) ErrorRed else Accent,
                            fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (servers.isEmpty()) {
            GlassCard { Text("No servers configured yet.", color = TextSecondary) }
        }

        servers.sortedByDescending { it.favorite }.forEach { server ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (server.favorite) Text("★  ", color = Warn, fontSize = 14.sp)
                            Text(server.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        Text(server.subtitle, color = TextSecondary, fontSize = 12.sp)
                        server.lastLatencyMs?.let { Text("$it ms", color = Accent, fontSize = 11.sp) }
                    }
                    TextButton(onClick = { store.setFavorite(server.id, !server.favorite); refresh() }) {
                        Text(if (server.favorite) "Unfavorite" else "Favorite", color = Warn, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { CoroutineScope(Dispatchers.IO).launch { engine.connect(server) }; onConnected() },
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF06251B)),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { store.delete(server.id); refresh() }) {
                        Text("Delete", color = ErrorRed, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------- Connection (manual URI quick-connect) ----------

@Composable
private fun ConnectionScreen(store: ServerStore, engine: XrayDesktopEngine, state: DesktopConnectionState) {
    var uri by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("Paste a vless:// link for a one-off connection.") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Connection", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("QUICK CONNECT")
                OutlinedTextField(
                    value = uri, onValueChange = { uri = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("vless://…", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF2A3648),
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !state.isBusy && uri.isNotBlank(),
                        onClick = {
                            try {
                                val profile = ServerProfile.fromVlessUri(uri.trim())
                                val err = profile.validationError
                                if (err != null) { detail = "Invalid config: $err" }
                                else CoroutineScope(Dispatchers.IO).launch { engine.connect(profile) }
                            } catch (e: Exception) { detail = "Parse error: ${e.message}" }
                        }
                    ) { Text("Connect") }
                    OutlinedButton(onClick = { engine.disconnect() }) { Text("Disconnect") }
                }
                Text(detail, color = TextSecondary, fontSize = 13.sp)
                Text(
                    "Quick-connect profiles are not saved. Use Servers to persist them.",
                    color = TextMuted, fontSize = 11.sp
                )
            }
        }

        StateCard(state)
    }
}

// ---------- Diagnostics ----------

@Composable
private fun DiagnosticsScreen(engine: XrayDesktopEngine, state: DesktopConnectionState) {
    var dnsOk by remember { mutableStateOf<Boolean?>(null) }
    var publicIp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            dnsOk = try {
                java.net.InetAddress.getByName("cloudflare.com").hostAddress != null
            } catch (_: Exception) { false }
            publicIp = fetchPublicIp()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnostics", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        DiagRow("Tunnel status", state.status.name,
            healthy = state.isConnected, warnStates = setOf(DesktopConnectionStatus.CONNECTING, DesktopConnectionStatus.DISCONNECTING))
        DiagRow("Xray-core process", if (engine.isRunning()) "running" else "stopped", healthy = engine.isRunning())
        DiagRow("DNS resolution", if (dnsOk == true) "working" else if (dnsOk == false) "FAILED" else "checking…", healthy = dnsOk == true)
        DiagRow("Public egress IP", publicIp ?: "checking…", healthy = publicIp != null)
        DiagRow("Active server", state.activeProfile?.name ?: "none", healthy = state.activeProfile != null)
        val binaryStatus = try {
            engine.ensureXrayBinary().fileName.toString() + " present"
        } catch (e: Exception) { "MISSING - " + (e.message ?: "error") }
        DiagRow("Binary", binaryStatus, healthy = !binaryStatus.startsWith("MISSING"))

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("SAFE EXPORT")
                Text(
                    "Diagnostic exports contain connection states and log lines only. Credentials (UUIDs, keys) are redacted before export.",
                    color = TextSecondary, fontSize = 12.sp
                )
                Button(onClick = {
                    val out = XrayDesktopEngine.APP_DIR.resolve("diagnostics-export.txt")
                    val body = buildString {
                        appendLine("Maximus VPN diagnostics — ${java.time.LocalDateTime.now()}")
                        appendLine("Status: ${state.status}")
                        appendLine("Server: ${state.activeProfile?.subtitle ?: "none"}")
                        appendLine("Public IP: $publicIp")
                        appendLine()
                        engine.logs.value.forEach { appendLine(redactLine(it)) }
                    }
                    java.nio.file.Files.createDirectories(out.parent)
                    java.nio.file.Files.writeString(out, body)
                }) { Text("Export to ~/.maximus-vpn/diagnostics-export.txt") }
            }
        }
    }
}

/** Strip UUID-like tokens and long base64 blobs from a log line before export/display. */
internal fun redactLine(line: String): String = line
    .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "<uuid>")
    .replace(Regex("[A-Za-z0-9_-]{43,}"), "<key>")

@Composable
private fun DiagRow(label: String, value: String, healthy: Boolean, warnStates: Set<Any> = emptySet()) {
    val color = when {
        warnStates.any { it.toString() == value } -> Warn
        healthy -> Accent
        else -> if (value.contains("stop", true) || value == "none" || value.startsWith("MISSING") || value == "FAILED") ErrorRed else TextSecondary
    }
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, fontSize = 13.sp)
            Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Logs ----------

@Composable
private fun LogsScreen(logs: List<String>, engine: XrayDesktopEngine) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Logs", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { engine.clearLogs() }) { Text("Clear", color = TextSecondary) }
        }
        GlassCard(modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().padding(10.dp)) {
                items(logs.takeLast(400)) { line ->
                    Text(
                        redactLine(line),
                        color = when {
                            line.contains("ERROR", true) -> ErrorRed
                            line.contains("WARN", true) -> Warn
                            else -> Color(0xFF9FB3CC)
                        },
                        fontSize = 11.sp, lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

// ---------- Settings ----------

@Composable
private fun SettingsScreen(onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("ABOUT")
                Text("Maximus VPN for Desktop", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Version 1.0.0 • by DrFXAi", color = TextSecondary, fontSize = 13.sp)
                Text("VLESS client powered by ${XrayDesktopEngine.XRAY_VERSION_PINNED}", color = TextMuted, fontSize = 12.sp)
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("ENGINE")
                Text("Data directory: ${XrayDesktopEngine.APP_DIR}", color = TextSecondary, fontSize = 12.sp)
                Text("Override binary path via MAXIMUS_XRAY_PATH environment variable.", color = TextMuted, fontSize = 11.sp)
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("PRIVACY")
                Text("No telemetry is collected. Server credentials are stored only in your local profile file.", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) {
            Text("Quit Maximus VPN")
        }
    }
}

// ---------- Shared composables ----------

@Composable
private fun GlassCard(modifier: Modifier = Modifier, border: Color = Color(0xFF223048), content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun MetricCard(title: String, value: String, sub: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.08f), Color.Transparent)))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 10.sp, color = TextMuted, letterSpacing = 2.sp)
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(sub, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
}

@Composable
private fun StatusPill(statusName: String) {
    val (color, label) = when (statusName) {
        "CONNECTED" -> Accent to "CONNECTED"
        "CONNECTING", "DISCONNECTING" -> Warn to statusName
        "FAILED" -> ErrorRed to "ERROR"
        else -> TextMuted to "OFFLINE"
    }
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f))) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun StateCard(state: DesktopConnectionState) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("STATE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(state.status.name)
                Spacer(Modifier.width(10.dp))
                state.activeProfile?.let { Text(it.subtitle, color = TextSecondary, fontSize = 12.sp) }
            }
            state.connectedAtMs?.let {
                Text("Uptime: ${elapsed(it)}", color = TextSecondary, fontSize = 12.sp)
            }
            state.errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
        }
    }
}

// ---------- helpers ----------

private fun systemLabel(): String {
    val os = System.getProperty("os.name").orEmpty()
    return when {
        os.contains("win", true) -> "Windows x64 • Xray TUN mode"
        os.contains("linux", true) -> "Linux x86_64 • Xray TUN mode"
        os.contains("mac", true) -> "macOS (unsupported target — running anyway)"
        else -> os
    }
}

private fun elapsed(fromMs: Long): String {
    val s = (System.currentTimeMillis() - fromMs) / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

internal fun formatBps(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%.0f KB/s".format(bps / 1_000.0)
    else -> "$bps B/s"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

internal fun fetchPublicIp(): String? = try {
    val conn = java.net.URL("https://api.ipify.org").openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 5000; conn.readTimeout = 5000
    conn.setRequestProperty("User-Agent", "MaximusVPN/1.0")
    if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText().trim() else null
} catch (_: Exception) { null }

fun main() {
    val engine = XrayDesktopEngine()
    val store = ServerStore()
    application {
        Window(
            onCloseRequest = { engine.disconnect(); exitApplication() },
            title = "Maximus VPN — DrFXAi",
            state = rememberWindowState(width = 1100.dp, height = 760.dp)
        ) {
            MaximusDesktopApp(engine, store, onExit = { exitApplication() })
        }
    }
}
