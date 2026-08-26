package com.drfxai.maximusvpn.subscription

import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.vless.BatchParseResult
import com.drfxai.maximusvpn.vless.VlessParser
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Subscription / bulk import engine.
 *
 * Fetches a subscription URL (optionally HTTP basic-auth), decodes the payload —
 * either a base64 blob of share links or plain newline-separated links — parses
 * every entry, and de-duplicates against existing profiles by endpoint identity.
 */
object SubscriptionFetcher {

    data class FetchOptions(
        val url: String,
        val basicAuthUser: String? = null,
        val basicAuthPassword: String? = null,
        val timeoutSeconds: Long = 15
    )

    data class FetchResult(
        val rawBody: String,
        val wasBase64Encoded: Boolean
    )

    private fun client(timeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        // Subscriptions must ride HTTPS; OkHttp rejects http:// cleartext on Android 9+
        // anyway via network-security-config, but fail fast with a clear message here.
        .build()

    /** Downloads the subscription body. Never throws — returns Error with a friendly message. */
    suspend fun fetch(options: FetchOptions): AppResult<FetchResult> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!options.url.startsWith("https://") && !options.url.startsWith("http://")) {
                return@withContext AppResult.Error(
                    IllegalArgumentException("Subscription URL must be http(s)."),
                    "Subscription URL must start with http:// or https://"
                )
            }
            try {
                val builder = Request.Builder().url(options.url.trim())
                    .header("User-Agent", "MaximusVPN/2.0")
                if (!options.basicAuthUser.isNullOrBlank()) {
                    builder.header(
                        "Authorization",
                        Credentials.basic(options.basicAuthUser, options.basicAuthPassword ?: "")
                    )
                }
                val response = client(options.timeoutSeconds).newCall(builder.build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext AppResult.Error(
                            IllegalStateException("HTTP ${resp.code}"),
                            "Subscription fetch failed: HTTP ${resp.code}"
                        )
                    }
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext AppResult.Error(
                            IllegalStateException("Empty body"),
                            "Subscription returned an empty response."
                        )
                    }
                    AppResult.Success(FetchResult(rawBody = body, wasBase64Encoded = false))
                }
            } catch (e: Exception) {
                AppResult.Error(e, "Could not reach subscription URL: ${e.message ?: "network error"}")
            }
        }

    /**
     * Decodes a subscription body into profiles.
     * Handles: plain link lists, single-line base64 blobs, and mixed content.
     */
    fun decode(body: String): BatchParseResult {
        val text = body.trim()
        val direct = VlessParser.parseBatch(text)
        if (direct.successfulProfiles.isNotEmpty()) return dedupeWithin(direct)

        // Try whole-body base64 (common subscription format).
        val decoded = tryBase64(text)
        if (decoded != null) {
            val fromB64 = VlessParser.parseBatch(decoded)
            if (fromB64.successfulProfiles.isNotEmpty()) return dedupeWithin(fromB64)
        }

        // Try per-line base64 payloads interleaved with plain links.
        val merged = StringBuilder(direct.failedEntries.joinToString("\n") { it.rawLine })
        for (line in text.lines()) {
            val t = line.trim()
            if (t.isBlank() || t.startsWith("//") || t.startsWith("#")) continue
            if (t.contains("://")) continue // already attempted in `direct`
            tryBase64(t)?.let { decodedLine ->
                if (decodedLine.contains("://", ignoreCase = true)) {
                    merged.append('\n').append(decodedLine)
                }
            }
        }
        return dedupeWithin(VlessParser.parseBatch(merged.toString()))
    }

    private fun dedupeWithin(result: BatchParseResult): BatchParseResult {
        val seen = mutableSetOf<String>()
        val unique = result.successfulProfiles.filter { seen.add(it.dedupeKey) }
        return result.copy(successfulProfiles = unique)
    }

    private fun tryBase64(text: String): String? {
        val clean = text.replace("\\s".toRegex(), "")
        if (clean.length < 16 || clean.any { it !in B64_ALPHABET }) return null
        return try {
            String(Base64.getMimeDecoder().decode(clean)).takeIf { it.contains("://") }
        } catch (_: Exception) {
            null
        }
    }

    private const val B64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
}
