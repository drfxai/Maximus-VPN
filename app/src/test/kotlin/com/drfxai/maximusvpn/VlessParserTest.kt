package com.drfxai.maximusvpn

import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.vless.VlessParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessParserTest {

    @Test
    fun parse_validRealityVlessUri_success() {
        val uri = "vless://e7b99c42-88f1-4b19-9182-3d84a7e93f12@us.example.com:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=gateway.icloud.com&fp=chrome&pbk=D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d&sid=6ba7b810&spx=%2F&type=tcp#US%20Reality%20Node"
        val result = VlessParser.parse(uri)

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("US Reality Node", profile.name)
        assertEquals("us.example.com", profile.address)
        assertEquals(443, profile.port)
        assertEquals("e7b99c42-88f1-4b19-9182-3d84a7e93f12", profile.uuid)
        assertEquals("reality", profile.security)
        assertEquals("tcp", profile.transport)
        assertEquals("xtls-rprx-vision", profile.flow)
        assertEquals("gateway.icloud.com", profile.sni)
        assertEquals("chrome", profile.fingerprint)
        assertEquals("D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d", profile.publicKey)
        assertEquals("6ba7b810", profile.shortId)
        assertEquals("/", profile.spiderX)
    }

    @Test
    fun parse_validWebSocketTlsUri_success() {
        val uri = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@de.example.com:8443?encryption=none&security=tls&type=ws&host=de.example.com&path=%2Fvless-ws&alpn=h2%2Chttp%2F1.1#Frankfurt%20WS"
        val result = VlessParser.parse(uri)

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("Frankfurt WS", profile.name)
        assertEquals("de.example.com", profile.address)
        assertEquals(8443, profile.port)
        assertEquals("ws", profile.transport)
        assertEquals("tls", profile.security)
        assertEquals("/vless-ws", profile.path)
        assertEquals("de.example.com", profile.host)
        assertEquals("h2,http/1.1", profile.alpn)
    }

    @Test
    fun parseBatch_mixedContent_extractsValidProfiles() {
        val batchText = """
            vless://11111111-2222-3333-4444-555555555555@node1.com:443?security=tls&type=tcp#Node%201
            invalid link here
            https://google.com
            vless://22222222-3333-4444-5555-666666666666@node2.com:8080?security=none&type=ws#Node%202
        """.trimIndent()

        val batchResult = VlessParser.parseBatch(batchText)
        assertEquals(2, batchResult.successfulProfiles.size)
        assertEquals(2, batchResult.failedEntries.size)
        assertEquals("Node 1", batchResult.successfulProfiles[0].name)
        assertEquals("Node 2", batchResult.successfulProfiles[1].name)
    }

    @Test
    fun parse_validIPv6Uri_success() {
        val uri = "vless://e7b99c42-88f1-4b19-9182-3d84a7e93f12@[2001:db8::1]:443?encryption=none&security=tls&type=tcp#IPv6%20Server"
        val result = VlessParser.parse(uri)

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("IPv6 Server", profile.name)
        assertEquals("2001:db8::1", profile.address)
        assertEquals(443, profile.port)
        assertEquals("e7b99c42-88f1-4b19-9182-3d84a7e93f12", profile.uuid)
    }

    @Test
    fun toUri_andParseRoundtrip_maintainsEquality() {
        val originalUri = "vless://e7b99c42-88f1-4b19-9182-3d84a7e93f12@us.example.com:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=gateway.icloud.com&fp=chrome&pbk=D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d&sid=6ba7b810&spx=%2F&type=tcp#US%20Reality%20Node"
        val parsed = (VlessParser.parse(originalUri) as AppResult.Success).data

        val generatedUri = VlessParser.toUri(parsed)
        val reparsed = (VlessParser.parse(generatedUri) as AppResult.Success).data

        assertEquals(parsed.name, reparsed.name)
        assertEquals(parsed.address, reparsed.address)
        assertEquals(parsed.port, reparsed.port)
        assertEquals(parsed.uuid, reparsed.uuid)
        assertEquals(parsed.security, reparsed.security)
        assertEquals(parsed.publicKey, reparsed.publicKey)
    }
}
