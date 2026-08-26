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

/**
 * Real Xray-core engine (exec model).
 *
 * The Xray binary ships as jniLibs/arm64-v8a/libxray.so. Android's package installer
 * extracts JNI libraries into applicationInfo.nativeLibraryDir with the exec bit and
 * SELinux execute permission for our uid — filesDir copies are NOT reliably executable
 * on Android 10+ (W^X). So we exec directly from nativeLibraryDir and never copy.
 *
 * TUN ownership: VpnService.establish() returns a ParcelFileDescriptor whose fd is
 * FD_CLOEXEC, so a naive ProcessBuilder child would never inherit it. We dup() the fd,
 * clear CLOEXEC on the duplicate, close the dup on our side once the child is up, and
 * pass the inherited-fd number to Xray via BOTH env vars used by current cores:
 *   xray.tun.fd  (legacy Android builds)
 *   XRAY_TUN_FD  (current sing-box-derived tun inbound)
 *
 * Fail-closed: if the process dies at any point the service tears down the VPN.
 */
class XrayCoreEngine private constructor(private val appContext: Context) {

    companion object {
        const val XRAY_VERSION = "Xray-core 26.7.28 (XTLS/Xray-core official Android build)"

        /** Env var names for passing the TUN fd to the core. */
        const val ENV_TUN_FD_LEGACY = "xray.tun.fd"
        const val ENV_TUN_FD = "XRAY_TUN_FD"

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
    private var watchdogJob: Job? = null

    private val txBytesCounter = java.util.concurrent.atomic.AtomicLong(0)
    private val rxBytesCounter = java.util.concurrent.atomic.AtomicLong(0)
    private val _statsFlow = MutableStateFlow(TrafficStats())
    val statsFlow: StateFlow<TrafficStats> = _statsFlow.asStateFlow()

    private var process: Process? = null
    private var configFile: File? = null

    val isNativeAvailable: Boolean
        get() = nativeBinary()?.exists() == true

    /**
     * Resolve the executable xray binary inside nativeLibraryDir.
     * Never copies to filesDir — Android 10+ W^X blocks exec from writable storage.
     */
    fun ensureBinary(): File {
        val lib = nativeBinary() ?: throw IllegalStateException(
            "Xray-core native library not found in ${appContext.applicationInfo.nativeLibraryDir}. " +
                "Ensure the APK includes jniLibs/arm64-v8a/libxray.so and extractNativeLibs is enabled."
        )
        if (!lib.canExecute()) {
            throw IllegalStateException("Xray binary exists but is not executable: ${lib.absolutePath}")
        }
        return lib
    }

    private fun nativeBinary(): File? =
        try {
            File(appContext.applicationInfo.nativeLibraryDir).listFiles()
                ?.firstOrNull { it.name == "libxray.so" }
        } catch (_: Exception) { null }

    /**
     * Wrap a raw fd number in a java.io.FileDescriptor via reflection.
     * `descriptor` field is the standard (if hidden) way to construct one from an int.
     */
    private fun makeFileDescriptor(fd: Int): java.io.FileDescriptor =
        try {
            val ctor = java.io.FileDescriptor::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            ctor.isAccessible = true
            ctor.newInstance(fd)
        } catch (e: Exception) {
            throw IllegalStateException("Cannot wrap TUN fd $fd: ${e.message}", e)
        }

    /** Read the numeric fd out of a FileDescriptor ("descriptor" field on Android). */
    private fun reflectDescriptorNumber(fdObj: java.io.FileDescriptor): Int? =
        try {
            val field = java.io.FileDescriptor::class.java.getDeclaredField("descriptor")
            field.isAccessible = true
            field.getInt(fdObj)
        } catch (_: Exception) { null }

    /** Writable runtime directory holding config.json and optional geo data. */
    private fun assetDir(): File = File(appContext.filesDir, "xray-runtime").apply { mkdirs() }

    /** Copy optional Xray geo assets into a writable runtime directory. */
    @Synchronized
    fun ensureGeoAssets(): File {
        val dir = assetDir().apply { mkdirs() }
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val target = File(dir, name)
            try {
                appContext.assets.open(name).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
                // Optional resource — config remains valid without geo rules.
            }
        }
        return dir
    }

    /**
     * Start Xray-core with the given JSON config attached to the VpnService TUN fd.
     * Blocks until the process is spawned; Error on missing binary or immediate exit.
     */
    fun start(configJson: String, tunFd: Int, onUnexpectedExit: (() -> Unit)? = null): AppResult<Unit> {
        stop()
        txBytesCounter.set(0)
        rxBytesCounter.set(0)
        return try {
            val binary = ensureBinary()
            val assetDir = ensureGeoAssets()
            val cfg = File(assetDir, "config.json")
            cfg.writeText(configJson)
            configFile = cfg

            // dup() gives a NEW fd we own; clearing FD_CLOEXEC on the dup makes it
            // survive exec*() in the child. The original pfd keeps its own CLOEXEC.
            val inheritedFd = Os.dup(makeFileDescriptor(tunFd))
            try {
                // The dup'd descriptor's number is what we hand to the child via env.
                val childFdNum = reflectDescriptorNumber(inheritedFd)
                    ?: throw IllegalStateException("Cannot read duplicated fd number")
                val flags = Os.fcntlInt(inheritedFd, OsConstants.F_GETFD, 0)
                Os.fcntlInt(inheritedFd, OsConstants.F_SETFD, flags and OsConstants.FD_CLOEXEC.inv())

                XrayLogManager.appendLog("Starting $XRAY_VERSION", "XRAY")
                XrayLogManager.appendLog("TUN fd=$tunFd duplicated as $childFdNum (CLOEXEC cleared)", "XRAY")

                val pb = ProcessBuilder(binary.absolutePath, "run", "-c", cfg.absolutePath)
                    .redirectErrorStream(true)
                    .directory(assetDir)
                pb.environment()[ENV_TUN_FD_LEGACY] = childFdNum.toString()
                pb.environment()[ENV_TUN_FD] = childFdNum.toString()
                pb.environment()["XRAY_LOCATION_ASSET"] = assetDir.absolutePath

                val proc = pb.start()
                process = proc

                // Watchdog: detect immediate exit (bad config, unsupported flag...)
                Thread.sleep(600)
                if (!proc.isAlive) {
                    val tail = proc.inputStream.bufferedReader().readText().takeLast(500)
                    XrayLogManager.appendLog(
                        "Xray exited immediately (code ${proc.exitValue()}): $tail", "ERROR"
                    )
                    return AppResult.Error(
                        com.drfxai.maximusvpn.core.VpnException.ConfigurationError(
                            "Xray-core failed to start (exit ${proc.exitValue()}): " +
                                tail.lineSequence().lastOrNull().orEmpty()
                        ),
                        "Xray-core failed to start. Check server settings and try again."
                    )
                }
                isRunningFlag.set(true)

                logJob = scope.launch {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        XrayLogManager.appendLog(line.take(400), "XRAY")
                    }
                }

                watchdogJob = scope.launch(Dispatchers.IO) {
                    val exitCode = proc.waitFor()
                    val unexpected = isRunningFlag.compareAndSet(true, false)
                    if (unexpected) {
                        XrayLogManager.appendLog(
                            "Xray-core exited unexpectedly (code $exitCode). VPN must be torn down.",
                            "ERROR"
                        )
                        onUnexpectedExit?.invoke()
                    }
                }

                startStatsSampler()

                XrayLogManager.appendLog("Xray-core process running.", "VPN")
                AppResult.Success(Unit)
            } finally {
                // The child inherits this dup after CLOEXEC is cleared; close our copy.
                try { Os.close(inheritedFd) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            XrayLogManager.appendLog("Failed to start Xray-core: ${e.message}", "ERROR")
            AppResult.Error(
                com.drfxai.maximusvpn.core.VpnException.XrayStartupFailed(e.message ?: "unknown", e),
                "Could not start the VPN engine: ${e.message ?: "unknown error"}"
            )
        }
    }

    /**
     * Sample per-second traffic. All user traffic flows through Xray's sockets in
     * our process UID, so UID TrafficStats deltas ARE tunnel throughput. Explicit
     * recordTraffic() calls (if any) are honored additively.
     */
    private fun startStatsSampler() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var lastTx = readUidTxBytes()
            var lastRx = readUidRxBytes()
            while (isActive && isRunning()) {
                delay(1000)
                val sysTx = readUidTxBytes()
                val sysRx = readUidRxBytes()
                val dTx = (sysTx - lastTx).coerceAtLeast(0)
                val dRx = (sysRx - lastRx).coerceAtLeast(0)
                lastTx = sysTx
                lastRx = sysRx
                _statsFlow.value = TrafficStats(
                    txBytes = txBytesCounter.addAndGet(dTx),
                    rxBytes = rxBytesCounter.addAndGet(dRx),
                    txSpeedBps = dTx,
                    rxSpeedBps = dRx
                )
            }
        }
    }

    private fun readUidTxBytes(): Long = try {
        android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid())
    } catch (_: Exception) { 0L }

    private fun readUidRxBytes(): Long = try {
        android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
    } catch (_: Exception) { 0L }

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
        statsJob?.cancel(); statsJob = null
        watchdogJob?.cancel(); watchdogJob = null
        try {
            process?.destroy()
            Thread.sleep(200)
            if (process?.isAlive == true) process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null
        _statsFlow.value = TrafficStats()
        XrayLogManager.appendLog("Xray-core stopped.", "VPN")
    }
}
