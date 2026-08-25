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
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

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
 * Connect sequence (each step logged, any failure => FAILED):
 *   1. validate profile + runtime (elevated on Windows)
 *   2. extract bundled xray binary (+ wintun.dll on Windows) and verify PE arch
 *   3. write config.json, validate it via `xray run -test`
 *   4. start Xray, capture stderr separately, fail fast on startup errors
 *   5. wait until the TUN adapter actually exists ("MaximusVPN" on Windows)
 *   6. probe the SOCKS/HTTP inbound Xray exposes locally to prove routing readiness
 *   7. only then report CONNECTED
 *
 * Fail-closed everywhere: if Xray dies mid-session the state moves to FAILED and
 * the TUN adapter disappears with the process — no direct fallback path exists.
 */
class XrayDesktopEngine {

    companion object {
        const val XRAY_VERSION_PINNED = "Xray-core 26.7.28"
        const val WINTUN_VERSION_PINNED = "Wintun 0.14.1"
        private const val MAX_LOG_LINES = 1000

        /** Seconds to wait for the TUN adapter + local inbound to come up. */
        private const val TUN_WAIT_SECONDS = 20

        /** Local SOCKS inbound Xray listens on for self-traffic and our probes. */
        const val LOCAL_PROBE_PORT = 10808

        /** Stale Xray processes from previous runs (matched by command line). */
        private const val STALE_PROCESS_MARKER = "maximus-vpn"

        val APP_DIR: Path = Path.of(System.getProperty("user.home"), ".maximus-vpn")
        val BIN_DIR: Path = APP_DIR.resolve("bin")
    }

    @Volatile private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var logPumpActive = false
    @Volatile private var lastStartupError: String? = null
    private val stderrBuffer = object {
        val lines = ArrayDeque<String>()
        fun add(line: String) {
            synchronized(lines) {
                lines.addLast(line.take(500))
                if (lines.size > 50) lines.removeFirst()
            }
        }
        fun snapshot(): List<String> = synchronized(lines) { lines.toList() }
    }

    private val _state = MutableStateFlow(DesktopConnectionState())
    val state: StateFlow<DesktopConnectionState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot())
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    init { Files.createDirectories(APP_DIR); Files.createDirectories(BIN_DIR) }

    fun connect(profile: ServerProfile) {
        stopInternal()
        _state.value = DesktopConnectionState(
            status = DesktopConnectionStatus.CONNECTING,
            activeProfile = profile
        )
        try {
            // ---- 1. profile + runtime validation ----------------------------------
            log("[START] Validating connection request")
            require(profile.isValid) { profile.validationError ?: "Invalid server profile" }

            if (isWindows()) {
                log("[START] Validating Windows runtime")
                if (!isElevatedWindows()) {
                    throw IllegalStateException(
                        "Administrator privileges are required to create the TUN adapter. " +
                        "Right-click Maximus VPN and choose \"Run as administrator\", or reinstall " +
                        "via the MSI which configures elevation automatically."
                    )
                }
            }

            // ---- 2. extract runtime files -----------------------------------------
            val binary = ensureXrayBinary()
            log("[OK] xray${if (isWindows()) ".exe" else ""} found (${binary.fileName})")

            val wintunPath = ensureWintunRuntime()
            if (isWindows()) log("[OK] wintun.dll found (${wintunPath.fileName})")

            ensureGeoData()

            // ---- 3. config generation + validation ---------------------------------
            log("[START] Validating Xray configuration")
            val config = APP_DIR.resolve("config.json")
            Files.writeString(config, XrayConfigBuilder.buildTunConfig(profile))
            validateConfig(binary, config)
            log("[OK] Xray configuration valid")

            // ---- 4. process start ---------------------------------------------------
            log("[START] Starting Xray")
            lastStartupError = null
            stderrBuffer.snapshot().forEach { /* clear */ }
            synchronized(stderrBuffer.lines) { stderrBuffer.lines.clear() }

            killStaleProcesses()

            val pb = ProcessBuilder(
                binary.toString(), "run", "-c", config.toString(),
                "-format", "json"
            )
                .directory(APP_DIR.toFile())
                .redirectErrorStream(false)

            val proc = pb.start()
            process = proc
            logPumpActive = true
            pumpLogs(proc)
            pumpStderr(proc)
            watchExit(proc)
            log("[OK] Xray process started (pid ${proc.pid()})")

            // ---- 5. TUN readiness ---------------------------------------------------
            log("[START] Waiting for TUN")
            waitAndThrowIfDead(proc)
            awaitTunAdapter()
            log("[OK] TUN adapter detected (${tunName()})")

            // ---- 6. connectivity verification ---------------------------------------
            log("[START] Verifying routing")
            waitAndThrowIfDead(proc)
            require(localInboundReady()) {
                "Xray local inbound (127.0.0.1:$LOCAL_PROBE_PORT) never became reachable" +
                    startupHint()
            }
            log("[OK] Routing verified")

            // ---- 7. CONNECTED --------------------------------------------------------
            _state.value = _state.value.copy(
                status = DesktopConnectionStatus.CONNECTED,
                connectedAtMs = System.currentTimeMillis(),
                errorMessage = null
            )
            log("[VPN] Connected")
            startTrafficSampler()
        } catch (e: Exception) {
            appendLog("[ERROR]", "Connect failed: ${e.message}")
            stopInternal()
            _state.value = _state.value.copy(
                status = DesktopConnectionStatus.FAILED,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    fun disconnect() {
        _state.value = _state.value.copy(status = DesktopConnectionStatus.DISCONNECTING)
        stopInternal()
        _state.value = DesktopConnectionState(status = DesktopConnectionStatus.DISCONNECTED)
        appendLog("[VPN]", "Disconnected — TUN released, routes restored.")
    }

    /** True when the Xray process is alive. */
    fun isRunning(): Boolean = process?.isAlive == true

    fun clearLogs() { _logs.value = emptyList() }

    // ---------- internals ----------

    private fun stopInternal() {
        logPumpActive = false
        try {
            process?.destroy()
            // Give a graceful window, then force-kill; never orphan the child.
            if (process != null && !process!!.waitFor(3, TimeUnit.SECONDS)) {
                process?.destroyForcibly()
                process?.waitFor(2, TimeUnit.SECONDS)
            }
        } catch (_: Exception) {}
        process = null
        if (isWindows()) killStaleProcesses()
        _traffic.value = TrafficSnapshot()
    }

    private fun pumpLogs(proc: Process) {
        scope.launch {
            proc.inputStream.bufferedReader().forEachLine { line ->
                appendLogRaw(line.take(500))
            }
        }
    }

    /** Capture stderr into a bounded ring buffer so real Xray errors surface in diagnostics. */
    private fun pumpStderr(proc: Process) {
        scope.launch {
            proc.errorStream.bufferedReader().forEachLine { line ->
                stderrBuffer.add(line)
                appendLogRaw("[xray:err] ${line.take(400)}")
            }
        }
    }

    private fun watchExit(proc: Process) {
        scope.launch {
            val code = proc.waitFor()
            if (logPumpActive && _state.value.status == DesktopConnectionStatus.CONNECTED) {
                // Unexpected death while connected — fail closed (TUN dies with process).
                appendLog("[ERROR]", "Xray-core exited unexpectedly (code $code). Tunnel closed.")
                _state.value = _state.value.copy(
                    status = DesktopConnectionStatus.FAILED,
                    errorMessage = "Xray-core exited unexpectedly (code $code)"
                )
            }
        }
    }

    private fun waitAndThrowIfDead(proc: Process) {
        if (!proc.isAlive) {
            throw IllegalStateException(
                "Xray exited during startup (code ${proc.exitValue()})" + startupHint()
            )
        }
    }

    private fun startupHint(): String {
        val err = synchronized(stderrBuffer.lines) {
            stderrBuffer.lines.filter { it.isNotBlank() }.takeLast(3).joinToString("; ")
        }
        return if (err.isBlank()) "" else " — Xray said: $err"
    }

    /**
     * Run `xray run -test -c config` and fail loudly on invalid configuration.
     * Uses a bounded wait so a hung Xray can't stall connect forever.
     */
    private fun validateConfig(binary: Path, config: Path) {
        val proc = ProcessBuilder(binary.toString(), "run", "-test", "-c", config.toString())
            .directory(APP_DIR.toFile())
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        proc.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
        val finished = proc.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            throw IllegalStateException("Xray config validation timed out")
        }
        if (proc.exitValue() != 0) {
            val detail = output.lines().filter { it.isNotBlank() }.takeLast(4).joinToString("; ")
            throw IllegalStateException(
                "Xray configuration is invalid" + (if (detail.isBlank()) "" else ": $detail")
            )
        }
    }

    /** Poll until the expected TUN adapter exists or timeout. Never sleeps blindly. */
    private fun awaitTunAdapter() {
        val name = tunName()
        val deadline = System.currentTimeMillis() + TUN_WAIT_SECONDS * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive != true) {
                throw IllegalStateException("Xray exited during startup" + startupHint())
            }
            if (tunAdapterExists(name)) return
            Thread.sleep(300)
        }
        throw IllegalStateException(
            "Timed out waiting for TUN adapter '$name' after ${TUN_WAIT_SECONDS}s." +
                if (isWindows()) " Is Wintun installed and is the app elevated?" else ""
        )
    }

    private fun tunAdapterExists(name: String): Boolean =
        if (isWindows()) windowsAdapterExists(name) else linuxAdapterExists(name)

    /** `netsh interface show interface name=...` succeeds only when the adapter exists. */
    private fun windowsAdapterExists(name: String): Boolean = try {
        val p = ProcessBuilder("netsh", "interface", "show", "interface", "name=$name")
            .redirectErrorStream(true).start()
        val ok = p.waitFor(10, TimeUnit.SECONDS)
        p.inputStream.close()
        ok
    } catch (_: Exception) { false }

    private fun linuxAdapterExists(name: String): Boolean =
        java.io.File("/sys/class/net/$name").exists()

    /**
     * Probe the local SOCKS inbound Xray binds at 127.0.0.1:LOCAL_PROBE_PORT.
     * When this accepts TCP connections, Xray's internal routing table is live.
     */
    private fun localInboundReady(): Boolean {
        repeat(10) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", LOCAL_PROBE_PORT), 1000)
                }
                return true
            } catch (_: Exception) {
                Thread.sleep(300)
            }
        }
        return false
    }

    /** Kill leftover maximus-vpn xray processes from crashed sessions (best effort). */
    private fun killStaleProcesses() {
        if (!isWindows()) return
        try {
            val p = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq xray.exe", "/FO", "CSV", "/NH")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(10, TimeUnit.SECONDS)
            // tasklist prints one quoted-CSV row per matching process
            val count = out.lineSequence().count { it.startsWith("\"xray.exe\"") }
            if (count > 1 || (count >= 1 && process?.isAlive == true)) {
                appendLog("[WARN]", "Found $count stale xray.exe process(es); terminating them.")
                ProcessBuilder("taskkill", "/F", "/IM", "xray.exe").start().waitFor(10, TimeUnit.SECONDS)
            }
        } catch (_: Exception) {}
    }

    /**
     * Extract the pinned Xray binary into BIN_DIR (never CWD).
     * Verifies presence, readability, and PE/ELF machine architecture before returning.
     */
    internal fun ensureXrayBinary(): Path {
        val binaryName = if (isWindows()) "xray.exe" else "xray"
        val target = BIN_DIR.resolve(binaryName)
        if (!Files.exists(target)) {
            val configured = System.getenv("MAXIMUS_XRAY_PATH")?.takeIf { it.isNotBlank() }?.let(Path::of)
            val source = when {
                configured != null && Files.exists(configured) -> configured
                else -> {
                    val stream = XrayDesktopEngine::class.java.getResourceAsStream("/xray/$binaryName")
                        ?: throw IllegalStateException(
                            "Xray binary is not bundled with this build (missing resource /xray/$binaryName). " +
                            "CI injects it at release time."
                        )
                    stream.use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
                    target
                }
            }
            if (source != target) Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
        target.toFile().setExecutable(true)
        validateExecutableArch(target)
        return target
    }

    /**
     * Extract geoip.dat/geosite.dat next to the binary so `geoip:private` routing
     * works offline (Xray otherwise tries to download them at startup).
     */
    internal fun ensureGeoData() {
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val target = BIN_DIR.resolve(name)
            if (!Files.exists(target)) {
                val stream = XrayDesktopEngine::class.java.getResourceAsStream("/xray/$name")
                    ?: continue // optional resource — config still valid without geo rules
                stream.use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
                appendLogRaw("[OK] $name extracted")
            }
        }
    }

    /**
     * Extract wintun.dll next to xray.exe so LoadLibrary finds it.
     * Verifies presence and that it really is a 64-bit PE.
     */
    internal fun ensureWintunRuntime(): Path {
        if (!isWindows()) return APP_DIR // Linux uses kernel TUN; nothing to extract.
        val target = BIN_DIR.resolve("wintun.dll")
        if (!Files.exists(target)) {
            val stream = XrayDesktopEngine::class.java.getResourceAsStream("/xray/wintun.dll")
                ?: throw IllegalStateException(
                    "[ERROR] Wintun runtime is missing — /xray/wintun.dll was not packaged ($WINTUN_VERSION_PINNED required)."
                )
            stream.use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
        }
        val bytes = Files.readAllBytes(target)
        require(bytes.size > 64 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'Z'.code.toByte()) {
            "wintun.dll is corrupt (bad MZ header)"
        }
        require(isPe64(bytes)) { "wintun.dll is not a 64-bit DLL" }
        return target
    }

    /** True when the current process holds an elevated (admin) token on Windows. */
    internal fun isElevatedWindows(): Boolean {
        if (!isWindows()) return true
        return try {
            val p = ProcessBuilder("net", "session").redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS) == true
        } catch (_: Exception) { false }
    }

    private fun validateExecutableArch(path: Path) {
        val bytes = Files.readAllBytes(path)
        if (isWindows()) {
            require(bytes.size > 64 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'Z'.code.toByte()) {
                "${path.fileName} is corrupt (bad MZ header)"
            }
            require(isPe64(bytes)) { "${path.fileName} is not a 64-bit executable" }
        } else {
            // ELF: magic \x7fELF and EI_CLASS==2 (64-bit), EI_DATA==1 (little-endian)
            require(bytes.size > 20 &&
                    bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() &&
                    bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
                "${path.fileName} is not an ELF binary"
            }
            require(bytes[4].toInt() == 2) { "${path.fileName} is not 64-bit ELF" }
        }
    }

    /** Read PE optional-header Magic: 0x20b == PE32+ (64-bit). */
    private fun isPe64(b: ByteArray): Boolean {
        if (b.size < 0x40 + 4 + 20 + 2) return false
        val peOffset = ((b[0x3f].toInt() and 0xff) shl 24) or
                ((b[0x3e].toInt() and 0xff) shl 16) or
                ((b[0x3d].toInt() and 0xff) shl 8) or
                (b[0x3c].toInt() and 0xff)
        if (peOffset <= 0 || peOffset + 4 + 20 + 2 > b.size) return false
        if (b[peOffset] != 'P'.code.toByte() || b[peOffset + 1] != 'E'.code.toByte()) return false
        val optHeaderStart = peOffset + 4 + 20
        val magic = ((b[optHeaderStart].toInt() and 0xff) shl 8) or (b[optHeaderStart + 1].toInt() and 0xff)
        return magic == 0x20b
    }

    private fun tunName(): String =
        if (isWindows()) "MaximusVPN" else "maximus0"

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

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

    private fun log(message: String) = appendLogRaw(message)

    private fun appendLog(category: String, message: String) {
        appendLogRaw("$category $message")
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

    internal fun ensureXrayBinaryLegacy(): Path = ensureXrayBinary()
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
