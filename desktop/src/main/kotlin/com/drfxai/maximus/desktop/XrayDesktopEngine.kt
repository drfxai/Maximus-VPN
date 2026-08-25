package com.drfxai.maximus.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Connection lifecycle shared by the desktop UI. */
enum class DesktopConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, FAILED
}

data class DesktopConnectionState(
    val status: DesktopConnectionStatus = DesktopConnectionStatus.DISCONNECTED,
    val activeProfile: ServerProfile? = null,
    val connectedAtMs: Long? = null,
    val errorMessage: String? = null
) {
    val isConnected: Boolean get() = status == DesktopConnectionStatus.CONNECTED
    val isBusy: Boolean get() = status == DesktopConnectionStatus.CONNECTING ||
            status == DesktopConnectionStatus.DISCONNECTING
}

data class TrafficSnapshot(
    val upBps: Long = 0,
    val downBps: Long = 0,
    val totalUp: Long = 0,
    val totalDown: Long = 0
)

/**
 * Real Xray-core engine for Windows x64 / Linux x64.
 *
 * Spawns the bundled Xray-core executable in TUN mode with a generated config.
 * Captures stdout into a bounded log buffer and parses Xray's traffic statistics
 * from its debug output when available. Fail-closed: if Xray exits unexpectedly,
 * state moves to FAILED and the TUN dies with the process (no fallback path exists).
 */
class XrayDesktopEngine {

    companion object {
        const val XRAY_VERSION_PINNED = "Xray-core 26.7.28"
        private const val MAX_LOG_LINES = 1000
        val APP_DIR: Path = Path.of(System.getProperty("user.home"), ".maximus-vpn")
    }

    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var logPumpActive = false

    private val _state = MutableStateFlow(DesktopConnectionState())
    val state: StateFlow<DesktopConnectionState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot())
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    init { Files.createDirectories(APP_DIR) }

    fun connect(profile: ServerProfile) {
        stopInternal(clearState = false)
        _state.value = DesktopConnectionState(
            status = DesktopConnectionStatus.CONNECTING,
            activeProfile = profile
        )
        try {
            require(profile.isValid) { profile.validationError ?: "Invalid server profile" }

            val binary = ensureXrayBinary()
            val config = APP_DIR.resolve("config.json")
            Files.writeString(config, XrayConfigBuilder.buildTunConfig(profile))

            appendLog("INFO", "Starting $XRAY_VERSION_PINNED for '${profile.name}' (${profile.address}:${profile.port})")

            val pb = ProcessBuilder(binary.toString(), "run", "-c", config.toString())
                .directory(APP_DIR.toFile())
                .redirectErrorStream(true)
            // Exclude the Xray process's own traffic from the TUN to avoid routing loops;
            // on Linux this is handled via autoOutboundsInterface in the config.
            if (!isWindows()) {
                pb.environment()["XRAY_TUN_EXCLUDE_SELF"] = "1"
            }

            val proc = pb.start()
            process = proc
            logPumpActive = true
            pumpLogs(proc)
            watchExit(proc, profile)

            // Give Xray a moment; fail fast on bad configs
            Thread.sleep(800)
            if (!proc.isAlive) {
                throw IllegalStateException("Xray exited during startup — see Logs for details.")
            }

            _state.value = _state.value.copy(
                status = DesktopConnectionStatus.CONNECTED,
                connectedAtMs = System.currentTimeMillis(),
                errorMessage = null
            )
            appendLog("VPN", "Connected. System traffic is routed through Xray-core TUN.")
            startTrafficSampler()
        } catch (e: Exception) {
            appendLog("ERROR", "Connect failed: ${e.message}")
            stopInternal(clearState = false)
            _state.value = _state.value.copy(
                status = DesktopConnectionStatus.FAILED,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    fun disconnect() {
        _state.value = _state.value.copy(status = DesktopConnectionStatus.DISCONNECTING)
        stopInternal(clearState = false)
        _state.value = DesktopConnectionState(status = DesktopConnectionStatus.DISCONNECTED)
        appendLog("VPN", "Disconnected.")
    }

    /** True when the Xray process is alive. */
    fun isRunning(): Boolean = process?.isAlive == true

    fun clearLogs() { _logs.value = emptyList() }

    // ---------- internals ----------

    private fun stopInternal(clearState: Boolean) {
        logPumpActive = false
        try {
            process?.destroy()
            Thread.sleep(150)
            if (process?.isAlive == true) process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null
    }

    private fun pumpLogs(proc: Process) {
        scope.launch {
            proc.inputStream.bufferedReader().forEachLine { line ->
                appendLogRaw(line.take(500))
            }
        }
    }

    private fun watchExit(proc: Process, profile: ServerProfile) {
        scope.launch {
            proc.waitFor()
            if (logPumpActive && _state.value.status == DesktopConnectionStatus.CONNECTED) {
                // Unexpected death while connected — fail closed (TUN dies with process).
                appendLog("ERROR", "Xray-core exited unexpectedly (code ${proc.exitValue()}). Tunnel closed.")
                _state.value = _state.value.copy(
                    status = DesktopConnectionStatus.FAILED,
                    errorMessage = "Xray-core exited unexpectedly (code ${proc.exitValue()})"
                )
            }
        }
    }

    /**
     * Sample Xray's stats API output by parsing log lines of the form
     * "stats: outbound>>>proxy>>>traffic>>>uplink 1234" when loglevel includes info.
     * Falls back to zeros silently when not present.
     */
    private fun startTrafficSampler() {
        scope.launch {
            var lastUp = 0L; var lastDown = 0L; var lastAt = System.currentTimeMillis()
            while (isActive && isRunning()) {
                delay(1000)
                val now = System.currentTimeMillis()
                val dt = ((now - lastAt).coerceAtLeast(1)) / 1000.0
                val up = parseTrafficCounter("uplink")
                val down = parseTrafficCounter("downlink")
                _traffic.value = TrafficSnapshot(
                    upBps = if (up > lastUp) ((up - lastUp) / dt).toLong() else 0,
                    downBps = if (down > lastDown) ((down - lastDown) / dt).toLong() else 0,
                    totalUp = up, totalDown = down
                )
                lastUp = up; lastDown = down; lastAt = now
            }
        }
    }

    @Volatile private var latestUpLink = 0L
    @Volatile private var latestDownLink = 0L
    private fun parseTrafficCounter(kind: String): Long =
        if (kind == "uplink") latestUpLink else latestDownLink

    private fun appendLog(category: String, message: String) {
        val ts = java.time.LocalTime.now().toString().take(8)
        appendLogRaw("[$category] $message")
    }

    private fun appendLogRaw(line: String) {
        val list = _logs.value.toMutableList()
        list.add(line)
        // Parse Xray stats lines when they appear
        if (line.contains(">>>traffic>>>uplink")) {
            line.substringAfterLast(' ').toLongOrNull()?.let { latestUpLink = it }
        } else if (line.contains(">>>traffic>>>downlink")) {
            line.substringAfterLast(' ').toLongOrNull()?.let { latestDownLink = it }
        }
        if (list.size > MAX_LOG_LINES) {
            _logs.value = list.takeLast(MAX_LOG_LINES)
        } else {
            _logs.value = list
        }
    }

    internal fun ensureXrayBinary(): Path {
        val binaryName = if (isWindows()) "xray.exe" else "xray"
        val target = APP_DIR.resolve(binaryName)
        if (Files.exists(target)) {
            target.toFile().setExecutable(true)
            return target
        }
        val configured = System.getenv("MAXIMUS_XRAY_PATH")?.takeIf { it.isNotBlank() }?.let(Path::of)
        if (configured != null && Files.exists(configured)) {
            Files.copy(configured, target, StandardCopyOption.REPLACE_EXISTING)
        } else {
            val resource = "/xray/$binaryName"
            val stream = XrayDesktopEngine::class.java.getResourceAsStream(resource)
                ?: throw IllegalStateException(
                    "Xray binary is not bundled with this build. CI injects it at release time."
                )
            stream.use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
        }
        target.toFile().setExecutable(true)
        return target
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")
}

/** Immutable parsed representation of a vless:// URI. */
data class ParsedVless(
    val uuid: String,
    val host: String,
    val port: Int,
    val name: String,
    val type: String,
    val security: String,
    val sni: String,
    val hostHeader: String,
    val path: String,
    val serviceName: String,
    val flow: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val spiderX: String,
    val alpn: List<String>
)

object VlessParser {
    fun parseLegacy(raw: String): ParsedVless = parse(raw)

    fun parse(raw: String): ParsedVless {
        val uri = URI(raw.trim())
        require(uri.scheme.equals("vless", true)) { "URI must use vless://" }
        val userInfo = uri.userInfo ?: throw IllegalArgumentException("Missing UUID (user info)")
        val host = uri.host ?: throw IllegalArgumentException("Missing server host")
        val port = uri.port.takeIf { it > 0 } ?: throw IllegalArgumentException("Missing server port")
        val params = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.associate {
            val p = it.split('=', limit = 2)
            decode(p[0]) to decode(p.getOrElse(1) { "" })
        }
        val fragment = uri.rawFragment?.let(::decode).orEmpty().ifBlank { "Maximus Server" }
        val type = params["type"] ?: params["net"] ?: "tcp"
        val security = params["security"] ?: "none"
        return ParsedVless(
            uuid = userInfo,
            host = host,
            port = port,
            name = fragment,
            type = type.lowercase(),
            security = security.lowercase(),
            sni = params["sni"] ?: params["serverName"] ?: "",
            hostHeader = params["host"].orEmpty(),
            path = params["path"] ?: "/",
            serviceName = params["serviceName"] ?: params["service_name"] ?: "",
            flow = params["flow"].orEmpty(),
            fingerprint = params["fp"] ?: "",
            publicKey = params["pbk"] ?: "",
            shortId = params["sid"] ?: "",
            spiderX = params["spx"] ?: "",
            alpn = params["alpn"].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
        )
    }

    private fun decode(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8)
}
