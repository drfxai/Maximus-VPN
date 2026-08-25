package com.drfxai.maximusvpn.xray

import android.content.Context
import android.os.Build
import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.model.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Real Xray-core engine.
 *
 * Bundles the official XTLS/Xray-core Android arm64-v8a executable as jniLibs/arm64-v8a/libxray.so
 * (the standard packaging trick used by production Xray clients: Android's package installer
 * extracts JNI libraries with the executable bit preserved and marks them executable).
 *
 * The engine:
 *  1. Extracts libxray.so to private storage on first run
 *  2. Writes the generated config.json
 *  3. Spawns `xray run -c config.json` with XRAY_TUN_FD env var pointing at the VpnService TUN fd
 *     (Xray-core reads xray.tun.fd on Android builds to attach its gVisor TCP/IP stack to our TUN)
 *  4. Captures stdout/stderr into XrayLogManager for diagnostics
 *
 * All traffic (TCP, UDP, DNS) flows through Xray-core. There is no direct-socket fallback:
 * if the engine fails to start, the VPN service tears down the TUN interface (fail-closed).
 */
class XrayCoreEngine private constructor(private val appContext: Context) {

    companion object {
        const val XRAY_VERSION = "Xray-core 26.7.28 (XTLS/Xray-core official Android build)"

        @Volatile
        private var instance: XrayCoreEngine? = null

        fun getInstance(context: Context): XrayCoreEngine =
            instance ?: synchronized(this) {
                instance ?: XrayCoreEngine(context.applicationContext).also { instance = it }
            }
    }

    private val isRunningFlag = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logJob: Job? = null
    private var statsJob: Job? = null

    private val txBytesCounter = AtomicLong(0)
    private val rxBytesCounter = AtomicLong(0)
    private val _statsFlow = MutableStateFlow(TrafficStats())
    val statsFlow: StateFlow<TrafficStats> = _statsFlow.asStateFlow()

    private var process: Process? = null
    private var binaryFile: File? = null
    private var configFile: File? = null

    val isNativeAvailable: Boolean
        get() = binaryFile?.exists() == true || nativeLibraryPath() != null

    /** Extract the bundled libxray.so to filesDir and mark executable. Idempotent per version. */
    @Synchronized
    fun ensureBinary(): File {
        binaryFile?.let { if (it.exists()) return it }

        val outDir = File(appContext.filesDir, "xray").apply { mkdirs() }
        val target = File(outDir, "xray")

        // Re-extract when missing (fresh install / cache cleared)
        if (!target.exists()) {
            val libName = if (Build.SUPPORTED_ABIS.isNotEmpty() &&
                Build.SUPPORTED_ABIS[0] == "arm64-v8a"
            ) "libxray.so" else "libxray.so" // single ABI shipped; guarded by abiFilters
            val src = nativeLibraryPath()
            if (src != null && File(src).exists()) {
                // Preferred path: use the already-executable extracted JNI library directly
                target.delete()
                File(src).let { lib ->
                    // Symlink not portable across all FS; hard copy is safest
                    lib.copyTo(target, overwrite = true)
                }
            } else {
                throw IllegalStateException(
                    "Xray-core native library not found. Ensure the release APK includes jniLibs/arm64-v8a/libxray.so."
                )
            }
        }
        target.setExecutable(true, false)
        binaryFile = target
        return target
    }

    private fun nativeLibraryPath(): String? =
        // applicationInfo.nativeLibraryDir contains extracted jniLibs entries
        try {
            val dir = File(appContext.applicationInfo.nativeLibraryDir)
            dir.listFiles()?.firstOrNull { it.name == "libxray.so" }?.absolutePath
        } catch (_: Exception) {
            null
        }

    /**
     * Start Xray-core with the given JSON config and the VpnService TUN file descriptor.
     * Blocks until the process is spawned; returns Error if the binary is missing or exits immediately.
     */
    fun start(configJson: String, tunFd: Int): AppResult<Unit> {
        stop()
        return try {
            val binary = ensureBinary()
            val cfg = File(File(appContext.filesDir, "xray"), "config.json")
            cfg.writeText(configJson)
            configFile = cfg

            XrayLogManager.appendLog("Starting $XRAY_VERSION", "XRAY")
            XrayLogManager.appendLog("TUN fd=$tunFd passed via xray.tun.fd", "XRAY")

            val pb = ProcessBuilder(binary.absolutePath, "run", "-c", cfg.absolutePath)
                .redirectErrorStream(true)
                .directory(binary.parentFile)
            pb.environment()["xray.tun.fd"] = tunFd.toString()
            pb.environment()["XRAY_LOCATION_ASSET"] = binary.parentFile?.absolutePath ?: ""

            val proc = pb.start()
            process = proc

            // Watchdog: detect immediate exit (bad config, unsupported flag...)
            Thread.sleep(600)
            if (!proc.isAlive) {
                val tail = proc.inputStream.bufferedReader().readText().takeLast(500)
                XrayLogManager.appendLog("Xray exited immediately (code ${proc.exitValue()}): $tail", "ERROR")
                return AppResult.Error(
                    com.drfxai.maximusvpn.core.VpnException.ConfigurationError(
                        "Xray-core failed to start (exit ${proc.exitValue()}): ${tail.lineSequence().lastOrNull() ?: "no output"}"
                    ),
                    "Xray-core failed to start. Check server settings and try again."
                )
            }
            isRunningFlag.set(true)

            // Stream logs
            logJob = scope.launch {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    XrayLogManager.appendLog(line.take(400), "XRAY")
                }
            }
            isRunningFlag.get().let {
                XrayLogManager.appendLog("Xray-core process running.", "VPN")
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            XrayLogManager.appendLog("Failed to start Xray-core: ${e.message}", "ERROR")
            AppResult.Error(
                com.drfxai.maximusvpn.core.VpnException.XrayStartupFailed(e.message ?: "unknown", e),
                "Could not start the VPN engine: ${e.message ?: "unknown error"}"
            )
        }
    }

    fun recordTraffic(sent: Long, received: Long) {
        txBytesCounter.addAndGet(sent.coerceAtLeast(0))
        rxBytesCounter.addAndGet(received.coerceAtLeast(0))
    }

    fun isRunning(): Boolean =
        isRunningFlag.get() && process?.isAlive == true

    fun getVersion(): String = XRAY_VERSION

    fun stop() {
        isRunningFlag.set(false)
        logJob?.cancel(); logJob = null
        try {
            process?.destroy()
            // Give it a moment, then force-kill
            Thread.sleep(200)
            if (process?.isAlive == true) process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null
        XrayLogManager.appendLog("Xray-core stopped.", "VPN")
    }
}
