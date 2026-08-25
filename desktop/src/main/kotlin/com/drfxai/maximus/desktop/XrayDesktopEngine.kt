package com.drfxai.maximus.desktop

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

class XrayDesktopEngine {
    private var process: Process? = null

    fun connect(vlessUri: String) {
        stop()
        val parsed = VlessParser.parse(vlessUri)
        val baseDir = Path.of(System.getProperty("user.home"), ".maximus-vpn")
        Files.createDirectories(baseDir)
        val binary = ensureXrayBinary(baseDir)
        val config = baseDir.resolve("config.json")
        Files.writeString(config, XrayConfigBuilder.build(parsed))
        process = ProcessBuilder(binary.toString(), "run", "-c", config.toString())
            .directory(baseDir.toFile())
            .redirectErrorStream(true)
            .start()
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    private fun ensureXrayBinary(baseDir: Path): Path {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val binaryName = if (isWindows) "xray.exe" else "xray"
        val target = baseDir.resolve(binaryName)
        if (Files.exists(target)) {
            target.toFile().setExecutable(true)
            return target
        }
        val configured = System.getenv("MAXIMUS_XRAY_PATH")?.takeIf { it.isNotBlank() }?.let(Path::of)
        if (configured != null && Files.exists(configured)) {
            Files.copy(configured, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } else {
            val resource = "/xray/$binaryName"
            val stream = XrayDesktopEngine::class.java.getResourceAsStream(resource)
                ?: throw IllegalStateException("Xray binary is not bundled with this release.")
            stream.use { input -> Files.copy(input, target) }
        }
        target.toFile().setExecutable(true)
        return target
    }
}

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
    fun parse(raw: String): ParsedVless {
        val uri = URI(raw.trim())
        require(uri.scheme.equals("vless", true)) { "URI must use vless://" }
        val userInfo = uri.userInfo ?: error("Missing UUID")
        val host = uri.host ?: error("Missing server host")
        val port = uri.port.takeIf { it > 0 } ?: error("Missing server port")
        val params = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.associate {
            val p = it.split('=', limit = 2)
            decode(p[0]) to decode(p.getOrElse(1) { "" })
        }
        val fragment = uri.rawFragment?.let(::decode).orEmpty().ifBlank { "Maximus Server" }
        val type = params["type"] ?: params["net"] ?: "tcp"
        val security = params["security"] ?: "none"
        require(security != "reality" || type.lowercase() in setOf("tcp", "raw", "grpc", "xhttp")) {
            "Current Xray REALITY does not support this transport combination."
        }
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
