package com.drfxai.maximusvpn

import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.model.VpnProtocol
import com.drfxai.maximusvpn.vless.VlessParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiProtocolParserTest {

    @Test
    fun parse_vmessUri_success() {
        val uri = "vmess://eyJ2IjoiMiIsInBzIjoiVk1lc3MgTm9kZSIsImFkZCI6InVzLmV4YW1wbGUuY29tIiwicG9ydCI6IjQ0MyIsImlkIjoiZDQ2YjRlYjItNmFiYy00YjM3LTg2Y2QtZDk3MmUxYzQ1Njc4IiwiYWlkIjoiMCIsInNjeSI6ImF1dG8iLCJuZXQiOiJ0Y3AiLCJ0bHMiOiJ0bHMiLCJzbmkiOiJnb29nbGUuY29tIiwiaG9zdCI6IiIsInBhdGgiOiIvIiwiYWxwbiI6IiJ9"
        val result = VlessParser.parse(uri)

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("VMess Node", profile.name)
        assertEquals(VpnProtocol.VMESS, profile.protocolEnum)
        assertEquals("us.example.com", profile.address)
        assertEquals(443, profile.port)
        assertEquals("d46b4eb2-6abc-4b37-86cd-d972e1c45678", profile.uuid)
        assertEquals(0, profile.alterId)
        assertEquals("auto", profile.encryption)
        assertEquals("tcp", profile.transport)
        assertEquals("tls", profile.security)
        assertEquals("google.com", profile.sni)
    }

    @Test
    fun parse_trojanUri_success() {
        val uri = "trojan://mypassword@de.example.com:443?sni=google.com&fp=chrome&type=tcp#Trojan%20Node"
        val result = VlessParser.parse(uri)

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("Trojan Node", profile.name)
        assertEquals(VpnProtocol.TROJAN, profile.protocolEnum)
        assertEquals("de.example.com", profile.address)
        assertEquals(443, profile.port)
        assertEquals("mypassword", profile.uuid)
        assertEquals("tcp", profile.transport)
        assertEquals("tls", profile.security)
        assertEquals("google.com", profile.sni)
        assertEquals("chrome", profile.fingerprint)
    }

    @Test
    fun parse_shadowsocksUri_sip002_success() {
        val uri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@sg.example.com:8388#SS%20Node"
        val result = VlessParser.parse(uri)

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("SS Node", profile.name)
        assertEquals(VpnProtocol.SHADOWSOCKS, profile.protocolEnum)
        assertEquals("sg.example.com", profile.address)
        assertEquals(8388, profile.port)
        assertEquals("aes-256-gcm", profile.encryption)
        assertEquals("password", profile.uuid)
        assertEquals("tcp", profile.transport)
    }

    @Test
    fun parse_shadowsocksUri_legacy_success() {
        // Legacy ss://base64@host:port format
        val uri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@sg.example.com:8388#Legacy%20SS"
        val result = VlessParser.parse(uri)

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("Legacy SS", profile.name)
        assertEquals(VpnProtocol.SHADOWSOCKS, profile.protocolEnum)
    }

    @Test
    fun parse_hysteria2Uri_success() {
        val uri = "hysteria2://password@jp.example.com:443?sni=google.com&fp=chrome&insecure=1&obfs=salamander&obfs-password=obfs123#Hysteria2%20Node"
        val result = VlessParser.parse(uri)

        assertTrue(result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("Hysteria2 Node", profile.name)
        assertEquals(VpnProtocol.HYSTERIA2, profile.protocolEnum)
        assertEquals("jp.example.com", profile.address)
        assertEquals(443, profile.port)
        assertEquals("password", profile.uuid)
        assertEquals("udp", profile.transport) // Hysteria2 defaults to udp per parser
        assertEquals("tls", profile.security)
        assertEquals("google.com", profile.sni)
        assertEquals("chrome", profile.fingerprint)
        assertTrue(profile.allowInsecure)
        assertEquals("salamander", profile.headerType)
        assertEquals("obfs123", profile.obfsPassword)
    }

    @Test
    fun parseBatch_multiProtocol_extractsValidProfiles() {
        val batchText = """
            vless://11111111-2222-3333-4444-555555555555@node1.com:443?security=tls#VLESS
            vmess://eyJ2IjoiMiIsInBzIjoiVk1lc3MiLCJhZGQiOiJub2RlMi5jb20iLCJwb3J0IjoiODA4MCIsImlkIjoiYWFiYmNjZGQtZWVmZi00YWFiLWI4Y2MtZGRlZWYwMDAwMDAwMCIsImFpZCI6IjAiLCJzY3kiOiJhdXRvIiwibmV0IjoidGNwIiwidGxzIjoiIn0=
            trojan://pwd@node3.com:443?sni=test.com#Trojan
            ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@node4.com:8388#SS
            hysteria2://pass@node5.com:443?sni=test.com#Hy2
            invalid
        """.trimIndent()

        val batchResult = VlessParser.parseBatch(batchText)
        assertEquals(5, batchResult.successfulProfiles.size)
        assertEquals(1, batchResult.failedEntries.size)
        assertEquals(VpnProtocol.VLESS, batchResult.successfulProfiles[0].protocolEnum)
        assertEquals(VpnProtocol.VMESS, batchResult.successfulProfiles[1].protocolEnum)
        assertEquals(VpnProtocol.TROJAN, batchResult.successfulProfiles[2].protocolEnum)
        assertEquals(VpnProtocol.SHADOWSOCKS, batchResult.successfulProfiles[3].protocolEnum)
        assertEquals(VpnProtocol.HYSTERIA2, batchResult.successfulProfiles[4].protocolEnum)
    }
}
