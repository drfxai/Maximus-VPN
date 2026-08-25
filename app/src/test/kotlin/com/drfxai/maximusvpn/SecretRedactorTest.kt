package com.drfxai.maximusvpn

import com.drfxai.maximusvpn.core.SecretRedactor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

    @Test
    fun redact_replacesUuidWithSanitizedPlaceholder() {
        val rawLog = "Handshake with user UUID e7b99c42-88f1-4b19-9182-3d84a7e93f12 established"
        val redacted = SecretRedactor.redact(rawLog)

        assertFalse(redacted.contains("e7b99c42-88f1-4b19-9182-3d84a7e93f12"))
        assertTrue(redacted.contains("[REDACTED_UUID]"))
    }

    @Test
    fun redact_replacesJsonSecrets() {
        val rawJson = """{"id": "e7b99c42-88f1-4b19-9182-3d84a7e93f12", "publicKey": "D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d"}"""
        val redacted = SecretRedactor.redact(rawJson)

        assertFalse(redacted.contains("D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d"))
        assertTrue(redacted.contains("[REDACTED_KEY]"))
    }
}
