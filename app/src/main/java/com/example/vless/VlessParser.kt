package com.example.vless

import com.example.core.AppResult
import com.example.core.VpnException
import com.example.data.model.VlessProfile
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object VlessValidator {

    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * Validates a VlessProfile thoroughly and throws specific VpnExceptions on violation.
     */
    fun validate(profile: VlessProfile) {
        if (profile.address.isBlank()) {
            throw VpnException.MissingHost()
        }

        if (profile.port !in 1..65535) {
            throw VpnException.InvalidPort(profile.port)
        }

        if (profile.uuid.isBlank()) {
            throw VpnException.MissingUuid()
        }

        if (!isValidUuid(profile.uuid)) {
            throw VpnException.InvalidUuid(profile.uuid)
        }

        val validTransports = listOf("tcp", "ws", "grpc", "http", "h2", "quic", "kcp")
        if (profile.transport.lowercase() !in validTransports) {
            throw VpnException.UnsupportedTransport(profile.transport)
        }

        // REALITY checks
        if (profile.security.equals("reality", ignoreCase = true)) {
            if (profile.publicKey.isBlank()) {
                throw VpnException.ConfigurationError("REALITY security requires a Public Key (pbk).")
            }
            if (profile.sni.isBlank()) {
                throw VpnException.ConfigurationError("REALITY security requires a Server Name (SNI).")
            }
        }
    }

    fun isValidUuid(uuid: String): Boolean {
        return UUID_REGEX.matches(uuid.trim())
    }
}

data class BatchParseResult(
    val successfulProfiles: List<VlessProfile>,
    val failedEntries: List<FailedEntry>
)

data class FailedEntry(
    val rawLine: String,
    val errorMessage: String
)

object VlessParser {

    /**
     * Parses a single VLESS URI string into a VlessProfile.
     */
    fun parse(rawUri: String): AppResult<VlessProfile> {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("vless://", ignoreCase = true)) {
            return AppResult.Error(
                VpnException.InvalidVlessUrl("URI must start with 'vless://' scheme."),
                "Invalid link scheme: Must begin with vless://"
            )
        }

        try {
            // Strip vless://
            val withoutScheme = trimmed.substring(8)
            val fragmentIndex = withoutScheme.indexOf('#')
            val beforeFragment = if (fragmentIndex != -1) withoutScheme.substring(0, fragmentIndex) else withoutScheme
            val fragment = if (fragmentIndex != -1) withoutScheme.substring(fragmentIndex + 1) else ""

            val serverName = if (fragment.isNotBlank()) {
                decodeUrl(fragment)
            } else {
                "VLESS Server"
            }

            val queryIndex = beforeFragment.indexOf('?')
            val mainPart = if (queryIndex != -1) beforeFragment.substring(0, queryIndex) else beforeFragment
            val queryString = if (queryIndex != -1) beforeFragment.substring(queryIndex + 1) else ""

            // Main part: uuid@host:port
            val atIndex = mainPart.indexOf('@')
            if (atIndex == -1) {
                return AppResult.Error(
                    VpnException.MissingUuid(),
                    "Invalid VLESS configuration: Missing UUID before '@'."
                )
            }

            val uuid = mainPart.substring(0, atIndex).trim()
            val hostPort = mainPart.substring(atIndex + 1).trim()

            val host: String
            val port: Int

            if (hostPort.startsWith("[")) {
                val closingBracket = hostPort.indexOf(']')
                if (closingBracket == -1) {
                    return AppResult.Error(
                        VpnException.InvalidVlessUrl("Malformed bracketed IPv6 address."),
                        "Invalid VLESS configuration: Unclosed IPv6 bracket."
                    )
                }
                host = hostPort.substring(1, closingBracket).trim()
                val afterBracket = hostPort.substring(closingBracket + 1)
                val colonIdx = afterBracket.indexOf(':')
                if (colonIdx == -1) {
                    return AppResult.Error(
                        VpnException.InvalidVlessUrl("Missing port in host address."),
                        "Invalid VLESS configuration: Missing port number."
                    )
                }
                val portStr = afterBracket.substring(colonIdx + 1).trim()
                port = portStr.toIntOrNull()
                    ?: return AppResult.Error(
                        VpnException.InvalidPort(-1),
                        "Invalid VLESS configuration: Port '$portStr' is not a valid number."
                    )
            } else {
                val lastColon = hostPort.lastIndexOf(':')
                if (lastColon == -1) {
                    return AppResult.Error(
                        VpnException.InvalidVlessUrl("Missing port in host address."),
                        "Invalid VLESS configuration: Missing port number."
                    )
                }
                host = hostPort.substring(0, lastColon).trim()
                val portStr = hostPort.substring(lastColon + 1).trim()
                port = portStr.toIntOrNull()
                    ?: return AppResult.Error(
                        VpnException.InvalidPort(-1),
                        "Invalid VLESS configuration: Port '$portStr' is not a valid number."
                    )
            }

            // Parse Query Parameters
            val params = parseQueryParams(queryString)

            val encryption = params["encryption"] ?: "none"
            val transport = params["type"] ?: params["net"] ?: "tcp"
            val security = params["security"] ?: "none"
            val sni = params["sni"] ?: params["serverName"] ?: params["peer"] ?: ""
            val wsHost = params["host"] ?: ""
            val path = params["path"] ?: "/"
            val serviceName = params["serviceName"] ?: params["service_name"] ?: ""
            val flow = params["flow"] ?: ""
            val fingerprint = params["fp"] ?: params["fingerprint"] ?: ""
            val publicKey = params["pbk"] ?: params["publicKey"] ?: params["public_key"] ?: ""
            val shortId = params["sid"] ?: params["shortId"] ?: params["short_id"] ?: ""
            val spiderX = params["spx"] ?: params["spiderX"] ?: ""
            val alpn = params["alpn"] ?: ""
            val headerType = params["headerType"] ?: ""

            val profile = VlessProfile(
                name = serverName,
                address = host,
                port = port,
                uuid = uuid,
                encryption = encryption,
                transport = transport.lowercase(),
                security = security.lowercase(),
                sni = sni,
                host = wsHost,
                path = path,
                serviceName = serviceName,
                flow = flow,
                fingerprint = fingerprint,
                publicKey = publicKey,
                shortId = shortId,
                spiderX = spiderX,
                alpn = alpn,
                headerType = headerType
            )

            // Validate
            VlessValidator.validate(profile)

            return AppResult.Success(profile)
        } catch (e: VpnException) {
            return AppResult.Error(e, e.message ?: "Validation error")
        } catch (e: Exception) {
            return AppResult.Error(
                VpnException.InvalidVlessUrl(e.message ?: "Failed to parse URI"),
                "Invalid VLESS configuration format: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Parses multiple lines containing VLESS URLs (e.g. from clipboard or file).
     */
    fun parseBatch(rawText: String): BatchParseResult {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("//") && !it.startsWith("#") }

        val successful = mutableListOf<VlessProfile>()
        val failed = mutableListOf<FailedEntry>()

        for (line in lines) {
            when (val result = parse(line)) {
                is AppResult.Success -> successful.add(result.data)
                is AppResult.Error -> failed.add(FailedEntry(line, result.userFriendlyMessage))
            }
        }

        return BatchParseResult(successful, failed)
    }

    /**
     * Converts a VlessProfile into a standard VLESS URI for export/sharing.
     */
    fun toUri(profile: VlessProfile): String {
        val queryParams = mutableListOf<String>()

        if (profile.transport.isNotBlank()) {
            queryParams.add("type=${encodeUrl(profile.transport)}")
        }
        if (profile.security.isNotBlank() && profile.security != "none") {
            queryParams.add("security=${encodeUrl(profile.security)}")
        }
        if (profile.encryption.isNotBlank() && profile.encryption != "none") {
            queryParams.add("encryption=${encodeUrl(profile.encryption)}")
        }
        if (profile.flow.isNotBlank()) {
            queryParams.add("flow=${encodeUrl(profile.flow)}")
        }
        if (profile.sni.isNotBlank()) {
            queryParams.add("sni=${encodeUrl(profile.sni)}")
        }
        if (profile.host.isNotBlank()) {
            queryParams.add("host=${encodeUrl(profile.host)}")
        }
        if (profile.path.isNotBlank() && profile.path != "/") {
            queryParams.add("path=${encodeUrl(profile.path)}")
        }
        if (profile.serviceName.isNotBlank()) {
            queryParams.add("serviceName=${encodeUrl(profile.serviceName)}")
        }
        if (profile.fingerprint.isNotBlank()) {
            queryParams.add("fp=${encodeUrl(profile.fingerprint)}")
        }
        if (profile.publicKey.isNotBlank()) {
            queryParams.add("pbk=${encodeUrl(profile.publicKey)}")
        }
        if (profile.shortId.isNotBlank()) {
            queryParams.add("sid=${encodeUrl(profile.shortId)}")
        }
        if (profile.spiderX.isNotBlank()) {
            queryParams.add("spx=${encodeUrl(profile.spiderX)}")
        }
        if (profile.alpn.isNotBlank()) {
            queryParams.add("alpn=${encodeUrl(profile.alpn)}")
        }
        if (profile.headerType.isNotBlank()) {
            queryParams.add("headerType=${encodeUrl(profile.headerType)}")
        }

        val queryString = if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
        val fragmentString = if (profile.name.isNotBlank()) "#${encodeUrl(profile.name)}" else ""

        val hostFormatted = if (profile.address.contains(":")) "[${profile.address}]" else profile.address
        return "vless://${profile.uuid}@$hostFormatted:${profile.port}$queryString$fragmentString"
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isBlank()) return map

        val pairs = queryString.split('&')
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = decodeUrl(pair.substring(0, idx))
                val value = decodeUrl(pair.substring(idx + 1))
                map[key] = value
            } else if (pair.isNotBlank()) {
                map[decodeUrl(pair)] = ""
            }
        }
        return map
    }

    private fun decodeUrl(str: String): String {
        return try {
            URLDecoder.decode(str, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            str
        }
    }

    private fun encodeUrl(str: String): String {
        return try {
            URLEncoder.encode(str, StandardCharsets.UTF_8.name()).replace("+", "%20")
        } catch (_: Exception) {
            str
        }
    }
}
