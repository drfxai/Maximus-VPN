package com.example.core

import java.util.regex.Pattern

object SecretRedactor {

    private val UUID_PATTERN = Pattern.compile(
        "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
    )

    private val VLESS_URL_PATTERN = Pattern.compile(
        "vless://([^@]+)@([^:/?#]+):(\\d+)(\\?[^#]*)?(#.*)?"
    )

    private val REALITY_PBK_PATTERN = Pattern.compile(
        "(pbk|publicKey|public_key)=([^&\\s,}{\"]+)"
    )

    private val REALITY_SID_PATTERN = Pattern.compile(
        "(sid|shortId|short_id)=([^&\\s,}{\"]+)"
    )

    private val JSON_ID_PATTERN = Pattern.compile(
        "\"id\"\\s*:\\s*\"[^\"]+\""
    )

    private val JSON_PBK_PATTERN = Pattern.compile(
        "\"publicKey\"\\s*:\\s*\"[^\"]+\""
    )

    private val JSON_SID_PATTERN = Pattern.compile(
        "\"shortId\"\\s*:\\s*\"[^\"]+\""
    )

    /**
     * Redacts all sensitive fields from logs, error stack traces, and diagnostics text.
     */
    fun redact(text: String): String {
        if (text.isBlank()) return text
        var result = text

        // Redact full VLESS URLs
        val vlessMatcher = VLESS_URL_PATTERN.matcher(result)
        if (vlessMatcher.find()) {
            result = vlessMatcher.replaceAll("vless://[REDACTED_UUID]@$2:$3[REDACTED_PARAMS]$5")
        }

        // Redact standalone UUIDs
        result = UUID_PATTERN.matcher(result).replaceAll("[REDACTED_UUID]")

        // Redact REALITY parameters
        result = REALITY_PBK_PATTERN.matcher(result).replaceAll("$1=[REDACTED_KEY]")
        result = REALITY_SID_PATTERN.matcher(result).replaceAll("$1=[REDACTED_SID]")

        // Redact JSON fields
        result = JSON_ID_PATTERN.matcher(result).replaceAll("\"id\": \"[REDACTED_UUID]\"")
        result = JSON_PBK_PATTERN.matcher(result).replaceAll("\"publicKey\": \"[REDACTED_KEY]\"")
        result = JSON_SID_PATTERN.matcher(result).replaceAll("\"shortId\": \"[REDACTED_SID]\"")

        return result
    }

    /**
     * Masks a UUID for UI display (e.g. "a1b2...c3d4").
     */
    fun maskUuid(uuid: String): String {
        if (uuid.length <= 8) return "****"
        return "${uuid.take(4)}...${uuid.takeLast(4)}"
    }
}
