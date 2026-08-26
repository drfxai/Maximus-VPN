package com.drfxai.maximusvpn.xray

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.model.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class XrayCoreEngine private constructor(private val appContext: Context) {
    companion object {
        const val XRAY_VERSION = "Xray-core 26.7.28 (XTLS/Xray-core official Android build)"
        const val ENV_TUN_FD_LEGACY = "xray.tun.fd"
        const val ENV_TUN_FD = "XRAY_TUN_FD"
        @Volatile private var instance: XrayCoreEngine? = null
        fun getInstance(context: Context): XrayCoreEngine = instance ?: synchronized(this) {
            instance ?: XrayCoreEngine(context.applicationContext).also { instance = it }
        }
    }

    private val isRunningFlag = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logJob: Job? = null
    private var statsJob: Job? = null
    private var watchdogJob: Job? = null
    private val txBytesCounter = java.util.concurrent.atomic.AtomicLong(0)
    private val rxBytesCounter = java.util.concurrent.atomic.AtomicLong(0)
    private val _statsFlow = MutableStateFlow(TrafficStats())
    val statsFlow: StateFlow<TrafficStats> = _statsFlow.asStateFlow()
    private var process: Process? = null

    val isNativeAvailable get() = nativeBinary()?.exists() == true
    private fun nativeBinary(): File? = try {
        File(appContext.applicationInfo.nativeLibraryDir).listFiles()?.firstOrNull { it.name == "libxray.so" }
    } catch (_: Exception) { null }
    fun ensureBinary(): File = nativeBinary()?.takeIf { it.canExecute() } ?: throw IllegalStateException(
        "Xray-core native library not found or not executable in ${appContext.applicationInfo.nativeLibraryDir}"
    )
    private fun assetDir() = File(appContext.filesDir, "xray-runtime").apply { mkdirs() }
    @Synchronized fun ensureGeoAssets(): File {
        val dir = assetDir()
        for (name in listOf("geoip.dat", "geosite.dat")) try {
            appContext.assets.open(name).use { input -> File(dir, name).outputStream().use(input::copyTo) }
        } catch (_: Exception) {}
        return dir
    }

    /** Duplicate the VPN fd using public ParcelFileDescriptor semantics; never use hidden FileDescriptor constructors. */
    private fun duplicateFd(tunFd: Int): java.io.FileDescriptor {
        val original = java.io.FileDescriptor::class.java.getDeclaredField("descriptor").let { field ->
            field.isAccessible = true
            java.io.FileDescriptor().also { field.setInt(it, tunFd) }
        }
        return Os.dup(original)
    }
    private fun descriptorNumber(fd: java.io.FileDescriptor): Int {
        val field = java.io.FileDescriptor::class.java.getDeclaredField("descriptor")
        field.isAccessible = true
        return field.getInt(fd)
    }

    fun start(configJson: String, tunFd: Int, onUnexpectedExit: (() -> Unit)? = null): AppResult<Unit> {
        stop(); txBytesCounter.set(0); rxBytesCounter.set(0)
        return try {
            val binary = ensureBinary()
            val dir = ensureGeoAssets()
            val cfg = File(dir, "config.json").apply { writeText(configJson) }
            val inheritedFd = duplicateFd(tunFd)
            try {
                val childFd = descriptorNumber(inheritedFd)
                val flags = Os.fcntlInt(inheritedFd, OsConstants.F_GETFD, 0)
                Os.fcntlInt(inheritedFd, OsConstants.F_SETFD, flags and OsConstants.FD_CLOEXEC.inv())
                val pb = ProcessBuilder(binary.absolutePath, "run", "-c", cfg.absolutePath)
                    .redirectErrorStream(true).directory(dir)
                pb.environment()[ENV_TUN_FD_LEGACY] = childFd.toString()
                pb.environment()[ENV_TUN_FD] = childFd.toString()
                pb.environment()["XRAY_LOCATION_ASSET"] = dir.absolutePath
                val proc = pb.start(); process = proc
                Thread.sleep(600)
                if (!proc.isAlive) {
                    val tail = proc.inputStream.bufferedReader().readText().takeLast(500)
                    return AppResult.Error(
                        com.drfxai.maximusvpn.core.VpnException.ConfigurationError("Xray exited immediately: $tail"),
                        "Xray-core failed to start. Check server settings and try again."
                    )
                }
                isRunningFlag.set(true)
                logJob = scope.launch { proc.inputStream.bufferedReader().forEachLine { XrayLogManager.appendLog(it.take(400), "XRAY") } }
                watchdogJob = scope.launch {
                    val code = proc.waitFor()
                    if (isRunningFlag.compareAndSet(true, false)) {
                        XrayLogManager.appendLog("Xray-core exited unexpectedly (code $code).", "ERROR")
                        onUnexpectedExit?.invoke()
                    }
                }
                startStatsSampler()
                XrayLogManager.appendLog("Xray-core process running with inherited TUN fd=$childFd.", "VPN")
                AppResult.Success(Unit)
            } finally { try { Os.close(inheritedFd) } catch (_: Exception) {} }
        } catch (e: Exception) {
            XrayLogManager.appendLog("Failed to start Xray-core: ${e.message}", "ERROR")
            AppResult.Error(com.drfxai.maximusvpn.core.VpnException.XrayStartupFailed(e.message ?: "unknown", e),
                "Could not start the VPN engine: ${e.message ?: "unknown error"}")
        }
    }

    private fun startStatsSampler() {
        statsJob?.cancel(); statsJob = scope.launch {
            var lastTx = readUidTxBytes(); var lastRx = readUidRxBytes()
            while (isActive && isRunning()) {
                delay(1000)
                val tx = readUidTxBytes(); val rx = readUidRxBytes()
                val dTx = (tx - lastTx).coerceAtLeast(0); val dRx = (rx - lastRx).coerceAtLeast(0)
                lastTx = tx; lastRx = rx
                _statsFlow.value = TrafficStats(txBytes = txBytesCounter.addAndGet(dTx), rxBytes = rxBytesCounter.addAndGet(dRx), txSpeedBps = dTx, rxSpeedBps = dRx)
            }
        }
    }
    private fun readUidTxBytes() = try { android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid()) } catch (_: Exception) { 0L }
    private fun readUidRxBytes() = try { android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()) } catch (_: Exception) { 0L }
    fun isRunning() = isRunningFlag.get() && process?.isAlive == true
    fun getVersion() = XRAY_VERSION
    fun stop() {
        isRunningFlag.set(false); logJob?.cancel(); logJob = null; statsJob?.cancel(); statsJob = null; watchdogJob?.cancel(); watchdogJob = null
        try { process?.destroy(); Thread.sleep(200); if (process?.isAlive == true) process?.destroyForcibly() } catch (_: Exception) {}
        process = null; _statsFlow.value = TrafficStats(); XrayLogManager.appendLog("Xray-core stopped.", "VPN")
    }
}
