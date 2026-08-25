package com.drfxai.maximus.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Shared server-profile model for the desktop client.
 * Persisted as JSON in ~/.maximus-vpn/servers.json — never in source code.
 */
@Serializable
data class ServerProfile(
    val id: String = newId(),
    val name: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val protocol: String = "vless",
    val transport: String = "tcp",      // tcp, ws, grpc, xhttp
    val security: String = "none",      // none, tls, reality
    val sni: String = "",
    val host: String = "",
    val path: String = "/",
    val serviceName: String = "",
    val flow: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val alpn: String = "",
    val favorite: Boolean = false,
    val lastLatencyMs: Long? = null
) {
    companion object {
        fun newId(): String =
            java.util.UUID.randomUUID().toString()

        /** Parse a vless:// URI into a profile. Throws IllegalArgumentException with a readable message on bad input. */
        fun fromVlessUri(raw: String): ServerProfile {
            val p = VlessParser.parseLegacy(raw)
            return ServerProfile(
                name = p.name, address = p.host, port = p.port, uuid = p.uuid,
                transport = p.type, security = p.security, sni = p.sni,
                host = p.hostHeader, path = p.path, serviceName = p.serviceName,
                flow = p.flow, fingerprint = p.fingerprint, publicKey = p.publicKey,
                shortId = p.shortId, spiderX = p.spiderX,
                alpn = p.alpn.joinToString(",")
            )
        }
    }

    // ---------- validation ----------

    val validationError: String?
        get() = when {
            name.isBlank() -> "Server name is required"
            address.isBlank() -> "Server address is required"
            port !in 1..65535 -> "Port must be 1-65535"
            !isValidUuid(uuid) -> "UUID must be a valid 36-character UUID"
            transport.lowercase() !in setOf("tcp", "raw", "ws", "grpc", "xhttp") ->
                "Unsupported transport '$transport'"
            security.lowercase() !in setOf("none", "tls", "reality") ->
                "Unsupported security '$security'"
            security.equals("reality", true) && publicKey.isBlank() ->
                "REALITY requires a public key (pbk)"
            security.equals("reality", true) && sni.isBlank() ->
                "REALITY requires an SNI"
            else -> null
        }

    val isValid: Boolean get() = validationError == null

    private fun isValidUuid(u: String): Boolean =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(u.trim())

    val subtitle: String
        get() = "$address:$port • ${transport.uppercase()}${if (security != "none") "/" + security.uppercase() else ""}"
}

/**
 * JSON-file-backed server store at ~/.maximus-vpn/servers.json.
 */
class ServerStore(private val file: Path = XrayDesktopEngine.APP_DIR.resolve("servers.json")) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<ServerProfile> = load()

    @Synchronized
    fun all(): List<ServerProfile> = cache

    @Synchronized
    fun upsert(profile: ServerProfile) {
        val list = cache.toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        save(list); cache = list
    }

    @Synchronized
    fun delete(id: String) {
        val list = cache.filterNot { it.id == id }
        save(list); cache = list
    }

    @Synchronized
    fun setFavorite(id: String, favorite: Boolean) {
        cache.firstOrNull { it.id == id }?.let { upsert(it.copy(favorite = favorite)) }
    }

    @Synchronized
    fun updateLatency(id: String, latencyMs: Long?) {
        cache.firstOrNull { it.id == id }?.let { upsert(it.copy(lastLatencyMs = latencyMs)) }
    }

    private fun load(): List<ServerProfile> = try {
        if (Files.exists(file)) {
            json.decodeFromString<List<ServerProfile>>(Files.readString(file))
        } else emptyList()
    } catch (_: Exception) { emptyList() }

    private fun save(list: List<ServerProfile>) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(list))
    }
}
