package com.drfxai.maximusvpn.data.subscription

import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.vless.VlessParser
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

/**
 * Subscription fetcher: downloads a subscription URL (HTTP/HTTPS, optional basic auth)
 * and parses the body as either a base64-encoded blob or plain line-separated URIs.
 *
 * Supported URI schemes: vless:// (primary). Unknown schemes in a batch are skipped,
 * never fatal — partial imports are expected from mixed-protocol subs.
 */
object SubscriptionImporter {

    data class ImportResult(
        val profiles: List<VlessProfile>,
        val skipped: Int,
        val duplicatesInSource: Int
    )

    fun fetch(url: String, basicAuthUser: String? = null, basicAuthPass: String? = null): AppResult<String> {
        return try {
            require(url.startsWith("https://") || url.startsWith("http://")) {
                "Subscription URL must be http(s)"
            }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "MaximusVPN/2.0")
            if (!basicAuthUser.isNullOrBlank()) {
                val token = Base64.encodeToString(
                    "$basicAuthUser:${basicAuthPass.orEmpty()}".toByteArray(),
                    Base64.NO_WRAP
                )
                conn.setRequestProperty("Authorization", "Basic $token")
            }
            if (conn.responseCode !in 200..299) {
                return AppResult.Error(
                    com.drfxai.maximusvpn.core.VpnException.ConfigurationError(
                        "Subscription server returned HTTP ${conn.responseCode}"
                    ),
                    "Subscription download failed (HTTP ${conn.responseCode})."
                )
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            AppResult.Success(body)
        } catch (e: Exception) {
            AppResult.Error(
                com.drfxai.maximusvpn.core.VpnException.ConfigurationError(e.message ?: "fetch failed"),
                "Could not download subscription: ${e.message ?: "network error"}"
            )
        }
    }

    /** Decode base64 (standard or URL-safe, padded or not) when the body isn't plain URIs. */
    fun decodeBody(body: String): List<String> {
        val trimmed = body.trim()
        val looksPlain = trimmed.contains("://")
        if (looksPlain) return trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return try {
            val cleaned = trimmed.replace("\n", "").replace("\r", "")
            val padded = cleaned.padEnd((cleaned.length + 3) / 4 * 4, '=')
            val decoded = try {
                Base64.decode(padded, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                Base64.decode(cleaned.replace('+', '-').replace('/', '_'), Base64.NO_WRAP or Base64.URL_SAFE)
            }
            String(decoded, Charsets.UTF_8).lines().map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parse lines into profiles. Dedupes by (address, port, uuid) — first wins.
     * Lines that fail validation are counted as skipped, never abort the import.
     */
    fun parseBatch(lines: List<String>): ImportResult {
        val profiles = mutableListOf<VlessProfile>()
        val seen = mutableSetOf<Triple<String, Int, String>>()
        var skipped = 0
        var dupes = 0
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith("vless://")) { skipped++; continue }
            when (val res = VlessParser.parse(line)) {
                is com.drfxai.maximusvpn.core.AppResult.Success -> {
                    val p = res.data
                    val key = Triple(p.address, p.port, p.uuid)
                    if (!seen.add(key)) { dupes++; continue }
                    // Skip entries failing business validation (e.g. REALITY missing pbk)
                    val err = try {
                        com.drfxai.maximusvpn.vless.VlessValidator.validate(p); null
                    } catch (e: com.drfxai.maximusvpn.core.VpnException) {
                        e.message
                    } catch (e: Exception) { e.message }
                    if (err != null) { skipped++; continue }
                    profiles.add(p)
                }
                else -> skipped++
            }
        }
        return ImportResult(profiles, skipped, dupes)
    }
}
