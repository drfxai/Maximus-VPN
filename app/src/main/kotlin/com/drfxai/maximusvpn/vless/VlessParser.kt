package com.drfxai.maximusvpn.vless

import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.core.VpnException
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.data.model.VpnProtocol
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

object VlessValidator {

    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * Validates a profile thoroughly and throws specific VpnExceptions on violation.
     * UUID-format is enforced ONLY for protocols that genuinely use a user id
     * (VLESS/VMess); Trojan/Shadowsocks/Hysteria2 credentials are opaque strings.
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

        when (profile.protocolEnum) {
            VpnProtocol.VLESS, VpnProtocol.VMESS -> {
                if (!isValidUuid(profile.uuid)) {
                    throw VpnException.InvalidUuid(profile.uuid)
                }
            }
            else -> { /* opaque credential — non-empty is enough */ }
        }

        val validTransports = listOf("tcp", "ws", "grpc", "http", "h2", "quic", "kcp")
        val transport = profile.transport.lowercase()
        // Hysteria2 rides UDP/QUIC only; everything else uses the standard set.
        if (profile.protocolEnum == VpnProtocol.HYSTERIA2) {
            if (transport != "udp") throw VpnException.UnsupportedTransport(profile.transport)
        } else if (transport !in validTransports) {
            throw VpnException.UnsupportedTransport(profile.transport)
        }

        // REALITY checks (VLESS-only construct)
        if (profile.security.equals("reality", ignoreCase = true)) {
            if (profile.protocolEnum != VpnProtocol.VLESS) {
                throw VpnException.ConfigurationError("REALITY security is only supported with VLESS.")
            }
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

/**
 * Multi-protocol share-link parser (v2.0).
 *
 * Supported schemes:
 *  - vless://uuid@host:port?params#name           (full param matrix incl. REALITY)
 *  - trojan://password@host:port?sni=&type=#name
 *  - ss://base64(method:password)@host:port#name  (+ SIP002 plain and legacy full-b64)
 *  - hysteria2://password@host:port?sni=&obfs-password=#name (stored, connect blocked)
 *  - vmess://base64(json)                          (v2rayN format)
 */
object VlessParser {

    /** Parses a single share-link URI of any supported protocol into a profile. */
    fun parse(rawUri: String): AppResult<VlessProfile> {
        val trimmed = rawUri.trim()
        if (trimmed.startsWith("vmess://", ignoreCase = true)) return parseVmess(trimmed)

        // Determine scheme generically: scheme://rest
        val schemeMatch = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://(.*)$").find(trimmed)
            ?: return AppResult.Error(
                VpnException.InvalidVlessUrl("URI must start with a supported scheme (vless, vmess, trojan, ss, hysteria2)."),
                "Invalid link scheme."
            )
        val protocol = VpnProtocol.fromScheme(schemeMatch.groupValues[1])
            ?: return AppResult.Error(
                VpnException.InvalidVlessUrl("Unsupported scheme '${schemeMatch.groupValues[1]}'."),
                "Unsupported link scheme: ${schemeMatch.groupValues[1]}"
            )
        val rest = schemeMatch.groupValues[2]

        try {
            // Split fragment (#name) then query (?params)
            val fragmentIndex = rest.indexOf('#')
            val beforeFragment = if (fragmentIndex != -1) rest.substring(0, fragmentIndex) else rest
            val fragment = if (fragmentIndex != -1) rest.substring(fragmentIndex + 1) else ""

            val serverName = if (fragment.isNotBlank()) decodeUrl(fragment)
            else "${protocol.label} Server"

            val queryIndex = beforeFragment.indexOf('?')
            val mainPart = if (queryIndex != -1) beforeFragment.substring(0, queryIndex) else beforeFragment
            val queryString = if (queryIndex != -1) beforeFragment.substring(queryIndex + 1) else ""
            val params = parseQueryParams(queryString)

            // Shadowsocks has two shapes: ss://base64(method:pass@host:port)#name (legacy)
            // and SIP002 ss://base64(method:pass)@host:port?plugin..#name.
            if (protocol == VpnProtocol.SHADOWSOCKS && !mainPart.contains('@')) {
                return parseSsLegacyBlob(mainPart, serverName)
            }

            // Generic userinfo@host:port for vless/trojan/ss(SIP002)/hysteria2
            val atIndex = mainPart.indexOf('@')
            if (atIndex == -1) {
                return AppResult.Error(
                    VpnException.MissingUuid(),
                    "Invalid $serverName link: missing credential before '@'."
                )
            }
            val credential = decodeIfBase64(mainPart.substring(0, atIndex).trim(), protocol)
            val hostPort = mainPart.substring(atIndex + 1).trim()

            val (host, port) = parseHostPort(hostPort)
                ?: return AppResult.Error(
                    VpnException.InvalidVlessUrl("Malformed host:port '$hostPort'."),
                    "Invalid configuration: cannot read address/port."
                )

            val transport = when (protocol) {
                VpnProtocol.HYSTERIA2 -> "udp"
                else -> (params["type"] ?: params["net"] ?: "tcp").lowercase()
            }

            val obfsPassword = params["obfs-password"] ?: params["obfsPassword"] ?: ""

            val profile = VlessProfile(
                name = serverName,
                address = host,
                port = port,
                uuid = credential,
                encryption = when (protocol) {
                    VpnProtocol.SHADOWSOCKS -> params["method"]
                        ?: extractSsMethod(credential) ?: "aes-256-gcm"
                    VpnProtocol.VMESS -> params["encryption"] ?: "auto"
                    else -> params["encryption"] ?: "none"
                },
                transport = transport,
                security = when (protocol) {
                    VpnProtocol.HYSTERIA2 -> "tls" // QUIC-based; TLS implied
                    else -> (params["security"] ?: defaultSecurity(protocol)).lowercase()
                },
                sni = params["sni"] ?: params["serverName"] ?: params["peer"] ?: "",
                host = params["host"] ?: "",
                path = params["path"] ?: "/",
                serviceName = params["serviceName"] ?: params["service_name"] ?: "",
                flow = if (protocol == VpnProtocol.VLESS) params["flow"] ?: "" else "",
                fingerprint = params["fp"] ?: params["fingerprint"] ?: "",
                publicKey = params["pbk"] ?: params["publicKey"] ?: params["public_key"] ?: "",
                shortId = params["sid"] ?: params["shortId"] ?: params["short_id"] ?: "",
                spiderX = params["spx"] ?: params["spiderX"] ?: "",
                alpn = params["alpn"] ?: "",
                headerType = params["headerType"] ?: "",
                protocol = protocol.name,
                allowInsecure = params["allowInsecure"] == "1" || params["insecure"] == "1",
                // Hysteria2 obfs rides in headerType as a "salamander" marker
                headerType = if (obfsPassword.isNotBlank()) "salamander" else headerType,
                obfsPassword = obfsPassword
            )

            VlessValidator.validate(profile)
            return AppResult.Success(fixSsCredential(profile))
        } catch (e: VpnException) {
            return AppResult.Error(e, e.message ?: "Validation error")
        } catch (e: Exception) {
            return AppResult.Error(
                VpnException.InvalidVlessUrl(e.message ?: "Failed to parse URI"),
                "Invalid ${protocol.label} configuration format: ${e.localizedMessage}"
            )
        }
    }

    private fun defaultSecurity(protocol: VpnProtocol): String = when (protocol) {
        VpnProtocol.TROJAN, VpnProtocol.HYSTERIA2 -> "tls"
        else -> "none"
    }

    /** SS SIP002 userinfo may be base64(method:password) or plain method:password. */
    private fun decodeIfBase64(userInfo: String, protocol: VpnProtocol): String {
        if (protocol != VpnProtocol.SHADOWSOCKS) return userInfo
        if (userInfo.contains(':')) return userInfo // already plain
        return try {
            String(Base64.getUrlDecoder().decode(padBase64(userInfo)))
        } catch (_: Exception) {
            userInfo
        }
    }

    private fun extractSsMethod(credential: String): String? =
        credential.substringBefore(':', "").takeIf { it.isNotBlank() }

    /** Ensures `uuid` holds just the password and `encryption` holds the method. */
    private fun fixSsCredential(profile: VlessProfile): VlessProfile {
        if (profile.protocolEnum != VpnProtocol.SHADOWSOCKS) return profile
        if (profile.uuid.contains(':')) {
            val method = profile.uuid.substringBefore(':')
            val password = profile.uuid.substringAfter(':')
            return profile.copy(encryption = method.ifBlank { profile.encryption }, uuid = password)
        }
        return profile
    }

    /** Legacy ss://BASE64(method:password@host:port) form. */
    private fun parseSsLegacyBlob(blob: String, fallbackName: String): AppResult<VlessProfile> {
        return try {
            val decoded = String(Base64.getUrlDecoder().decode(padBase64(blob)))
            val at = decoded.lastIndexOf('@')
                ?: return AppResult.Error(VpnException.InvalidVlessUrl("Bad legacy SS blob"), "Invalid Shadowsocks link.")
            if (at <= 0) return AppResult.Error(VpnException.InvalidVlessUrl("Bad legacy SS blob"), "Invalid Shadowsocks link.")
            val credPart = decoded.substring(0, at)
            val hostPart = decoded.substring(at + 1)
            val (host, port) = parseHostPort(hostPart)
                ?: return AppResult.Error(VpnException.InvalidVlessUrl("Bad legacy SS blob"), "Invalid Shadowsocks address.")
            val method = credPart.substringBefore(':')
            val password = credPart.substringAfter(':', "")
            val profile = VlessProfile(
                name = fallbackName,
                address = host,
                port = port,
                uuid = password,
                encryption = method,
                protocol = VpnProtocol.SHADOWSOCKS.name
            )
            VlessValidator.validate(profile)
            AppResult.Success(profile)
        } catch (e: Exception) {
            AppResult.Error(
                VpnException.InvalidVlessUrl(e.message ?: "Bad legacy SS blob"),
                "Invalid Shadowsocks link: ${e.localizedMessage}"
            )
        }
    }

    private fun padBase64(s: String): String {
        val clean = s.trim().replace('-', '+').replace('_', '/')
        val rem = clean.length % 4
        return if (rem == 0) clean else clean + "=".repeat(4 - rem)
    }

    /** VMess v2rayN format: vmess://base64(JSON). */
    private fun parseVmess(uri: String): AppResult<VlessProfile> {
        return try {
            val b64 = uri.removePrefix("vmess://").removePrefix("VMESS://").trim()
            val json = JSONObject(String(Base64.getUrlDecoder().decode(padBase64(b64))))
            val port = json.optInt("port", -1)
            val profile = VlessProfile(
                name = json.optString("ps", "").ifBlank { "VMess Server" },
                address = json.optString("add", ""),
                port = if (json.get("port") is Int) port else port,
                uuid = json.optString("id", ""),
                encryption = json.optString("scy", "auto").ifBlank { "auto" },
                transport = json.optString("net", "tcp").lowercase(),
                security = json.optString("tls", "").let { if (it == "tls" || it == "reality") it else "none" },
                sni = json.optString("sni", ""),
                host = json.optString("host", ""),
                path = json.optString("path", "/"),
                serviceName = json.optString("path", "").takeIf { json.optString("net") == "grpc" } ?: "",
                alpn = json.optString("alpn", ""),
                fingerprint = json.optString("fp", ""),
                protocol = VpnProtocol.VMESS.name,
                alterId = json.optInt("aid", 0),
                allowInsecure = json.optInt("allowInsecure", 0) == 1
            )
            VlessValidator.validate(profile)
            AppResult.Success(profile)
        } catch (e: VpnException) {
            AppResult.Error(e, e.message ?: "Validation error")
        } catch (e: Exception) {
            AppResult.Error(
                VpnException.InvalidVlessUrl(e.message ?: "Bad VMess payload"),
                "Invalid VMess link: ${e.localizedMessage}"
            )
        }
    }

    private fun parseHostPort(hostPort: String): Pair<String, Int>? {
        return if (hostPort.startsWith("[")) {
            val closing = hostPort.indexOf(']') ; if (closing == -1) return null
            val host = hostPort.substring(1, closing).trim()
            val after = hostPort.substring(closing + 1)
            val colon = after.indexOf(':') ; if (colon == -1) return null
            val port = after.substring(colon + 1).trim().toIntOrNull() ?: return null
            host to port
        } else {
            val lastColon = hostPort.lastIndexOf(':') ; if (lastColon == -1) return null
            val host = hostPort.substring(0, lastColon).trim()
            val port = hostPort.substring(lastColon + 1).trim().toIntOrNull() ?: return null
            host to port
        }
    }

    /**
     * Parses multiple lines containing any mix of supported share links
     * (clipboard, file import, subscription bodies).
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

    /** Detects whether raw text contains at least one supported share link. */
    fun looksLikeShareLink(text: String): Boolean =
        text.lineSequence().any { line ->
            val t = line.trim()
            VpnProtocol.entries.any { t.startsWith(it.scheme + "://", ignoreCase = true) }
        }

    /** Converts a profile back into its canonical share-link URI for export/sharing. */
    fun toUri(profile: VlessProfile): String {
        if (profile.protocolEnum == VpnProtocol.VMESS) return toVmessUri(profile)

        val queryParams = mutableListOf<String>()
        if (profile.transport.isNotBlank() && profile.transport != "udp") {
            queryParams.add("type=${encodeUrl(profile.transport)}")
        }
        if (profile.security.isNotBlank() && profile.security != "none") {
            queryParams.add("security=${encodeUrl(profile.security)}")
        }
        if (profile.flow.isNotBlank()) queryParams.add("flow=${encodeUrl(profile.flow)}")
        if (profile.sni.isNotBlank()) queryParams.add("sni=${encodeUrl(profile.sni)}")
        if (profile.host.isNotBlank()) queryParams.add("host=${encodeUrl(profile.host)}")
        if (profile.path.isNotBlank() && profile.path != "/") queryParams.add("path=${encodeUrl(profile.path)}")
        if (profile.serviceName.isNotBlank()) queryParams.add("serviceName=${encodeUrl(profile.serviceName)}")
        if (profile.fingerprint.isNotBlank()) queryParams.add("fp=${encodeUrl(profile.fingerprint)}")
        if (profile.publicKey.isNotBlank()) queryParams.add("pbk=${encodeUrl(profile.publicKey)}")
        if (profile.shortId.isNotBlank()) queryParams.add("sid=${encodeUrl(profile.shortId)}")
        if (profile.spiderX.isNotBlank()) queryParams.add("spx=${encodeUrl(profile.spiderX)}")
        if (profile.alpn.isNotBlank()) queryParams.add("alpn=${encodeUrl(profile.alpn)}")
        if (profile.headerType.isNotBlank() &&
            profile.protocolEnum != VpnProtocol.SHADOWSOCKS &&
            profile.headerType != "salamander"
        ) queryParams.add("headerType=${encodeUrl(profile.headerType)}")
        if (profile.allowInsecure) queryParams.add("allowInsecure=1")
        if (profile.obfsPassword.isNotBlank()) queryParams.add("obfs-password=${encodeUrl(profile.obfsPassword)}")

        val scheme = profile.protocolEnum.scheme
        // Shadowsocks SIP002: userinfo is base64(method:password)
        val userInfo = if (profile.protocolEnum == VpnProtocol.SHADOWSOCKS) {
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString("${profile.encryption}:${profile.uuid}".toByteArray())
        } else {
            encodeUrl(profile.uuid)
        }

        val queryString = if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
        val fragmentString = if (profile.name.isNotBlank()) "#${encodeUrl(profile.name)}" else ""
        val hostFormatted = if (profile.address.contains(":")) "[${profile.address}]" else profile.address
        return "$scheme://$userInfo@$hostFormatted:${profile.port}$queryString$fragmentString"
    }

    private fun toVmessUri(profile: VlessProfile): String {
        val json = JSONObject().apply {
            put("v", "2")
            put("ps", profile.name)
            put("add", profile.address)
            put("port", profile.port.toString())
            put("id", profile.uuid)
            put("aid", profile.alterId.toString())
            put("scy", profile.encryption)
            put("net", profile.transport)
            put("type", profile.headerType.ifBlank { "none" })
            if (profile.host.isNotBlank()) put("host", profile.host)
            if (profile.path.isNotBlank()) put("path", profile.path)
            put("tls", if (profile.security == "tls") "tls" else "")
            if (profile.sni.isNotBlank()) put("sni", profile.sni)
            if (profile.fingerprint.isNotBlank()) put("fp", profile.fingerprint)
            if (profile.alpn.isNotBlank()) put("alpn", profile.alpn)
            if (profile.allowInsecure) put("allowInsecure", 1)
        }
        val b64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toString().toByteArray())
        return "vmess://$b64"
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isBlank()) return map

        val pairs = queryString.split('&')
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                map[decodeUrl(pair.substring(0, idx))] = decodeUrl(pair.substring(idx + 1))
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
