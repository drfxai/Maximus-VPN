package com.drfxai.maximusvpn

import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.model.VpnProtocol
import com.drfxai.maximusvpn.vless.VlessParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Multi-protocol share-link parsing tests.
 *
 * Runs under Robolectric so that org.json (used by the VMess parser) behaves
 * exactly as it does on device — plain JUnit on the JVM gets the Android stub
 * jar where every org.json method throws "not mocked".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiProtocolParserTest {

    // {"add":"vmess.example.com","aid":"0","alpn":"","fp":"chrome","host":"","id":"d46b4eb2-6abc-4b37-86cd-d972e1c45678","net":"tcp","path":"/","port":"443","ps":"VMess Node","scy":"auto","sni":"vmess.example.com","tls":"tls","v":"2"}
    private val vmessUri = "vmess://eyJhZGQiOiJ2bWVzcy5leGFtcGxlLmNvbSIsImFpZCI6IjAiLCJhbHBuIjoiIiwiZnAiOiJjaHJvbWUiLCJob3N0IjoiIiwiaWQiOiJkNDZiNGViMi02YWJjLTRiMzctODZjZC1kOTcyZTFjNDU2NzgiLCJuZXQiOiJ0Y3AiLCJwYXRoIjoiLyIsInBvcnQiOiI0NDMiLCJwcyI6IlZNZXNzIE5vZGUiLCJzY3kiOiJhdXRvIiwic25pIjoidm1lc3MuZXhhbXBsZS5jb20iLCJ0bHMiOiJ0bHMiLCJ2IjoiMiJ9"

    // {"add":"node2.example.com","aid":"0","id":"aabbccdd-eeff-4aab-b8cc-ddeef0000000","net":"tcp","port":"8080","ps":"VMess Sub","scy":"auto","tls":"tls","v":"2"}
    private val vmessUri2 = "vmess://eyJhZGQiOiJub2RlMi5leGFtcGxlLmNvbSIsImFpZCI6IjAiLCJpZCI6ImFhYmJjY2RkLWVlZmYtNGFhYi1iOGNjLWRkZWVmMDAwMDAwMCIsIm5ldCI6InRjcCIsInBvcnQiOiI4MDgwIiwicHMiOiJWTWVzcyBTdWIiLCJzY3kiOiJhdXRvIiwidGxzIjoidGxzIiwidiI6IjIifQ=="

    @Test
    fun parse_vmessUri_success() {
        val result = VlessParser.parse(vmessUri)

        assertTrue("Expected Success, got: $result", result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("VMess Node", profile.name)
        assertEquals(VpnProtocol.VMESS, profile.protocolEnum)
        assertEquals("vmess.example.com", profile.address)
        assertEquals(443, profile.port)
        assertEquals("d46b4eb2-6abc-4b37-86cd-d972e1c45678", profile.uuid)
        assertEquals(0, profile.alterId)
        assertEquals("auto", profile.encryption)
        assertEquals("tcp", profile.transport)
        assertEquals("tls", profile.security)
        assertEquals("vmess.example.com", profile.sni)
    }

    @Test
    fun parse_trojanUri_success() {
        val uri = "trojan://mypassword@de.example.com:443?sni=google.com&fp=chrome&type=tcp#Trojan%20Node"
        val result = VlessParser.parse(uri)

        assertTrue("Expected Success, got: $result", result is AppResult.Success)
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
        // SIP002: ss://base64(method:password)@host:port#name
        // base64("aes-256-gcm:password") = YWVzLTI1Ni1nY206cGFzc3dvcmQ=
        val uri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@sg.example.com:8388#SS%20Node"
        val result = VlessParser.parse(uri)

        assertTrue("Expected Success, got: $result", result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("SS Node", profile.name)
        assertEquals(VpnProtocol.SHADOWSOCKS, profile.protocolEnum)
        assertEquals("sg.example.com", profile.address)
        assertEquals(8388, profile.port)
        assertEquals("aes-256-gcm", profile.encryption)
        assertEquals("password", profile.uuid)
    }

    @Test
    fun parse_shadowsocksUri_legacy_success() {
        // Legacy: ss://base64(method:password@host:port)#name
        // base64-url("aes-256-gcm:password@sg.example.com:8388") = YWVzLTI1Ni1nY206cGFzc3dvcmRAc2cuZXhhbXBsZS5jb206ODM4OA
        val uri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmRAc2cuZXhhbXBsZS5jb206ODM4OA#Legacy%20SS"
        val result = VlessParser.parse(uri)

        assertTrue("Expected Success, got: $result", result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("Legacy SS", profile.name)
        assertEquals(VpnProtocol.SHADOWSOCKS, profile.protocolEnum)
        assertEquals("sg.example.com", profile.address)
        assertEquals(8388, profile.port)
        assertEquals("password", profile.uuid)
        assertEquals("aes-256-gcm", profile.encryption)
    }

    @Test
    fun parse_hysteria2Uri_success() {
        val uri = "hysteria2://password@jp.example.com:443?sni=google.com&fp=chrome&insecure=1&obfs=salamander&obfs-password=obfs123#Hysteria2%20Node"
        val result = VlessParser.parse(uri)

        assertTrue("Expected Success, got: $result", result is AppResult.Success)
        val profile = (result as AppResult.Success).data
        assertEquals("Hysteria2 Node", profile.name)
        assertEquals(VpnProtocol.HYSTERIA2, profile.protocolEnum)
        assertEquals("jp.example.com", profile.address)
        assertEquals(443, profile.port)
        assertEquals("password", profile.uuid)
        assertEquals("udp", profile.transport)
        assertEquals("tls", profile.security)
        assertEquals("google.com", profile.sni)
        assertEquals("chrome", profile.fingerprint)
        assertTrue(profile.allowInsecure)
        assertEquals("obfs123", profile.obfsPassword)
    }

    @Test
    fun parseBatch_multiProtocol_extractsValidProfiles() {
        val batchText = """
            vless://11111111-2222-3333-4444-555555555555@node1.example.com:443?security=tls#VLESS
            $vmessUri2
            trojan://pwd@node3.example.com:443?sni=test.example.com#Trojan
            ss://YWVzLTI1Ni1nY206cGFzc3dvcmRAc2cuZXhhbXBsZS5jb206ODM4OA#SS
            hysteria2://pass@node5.example.com:443?sni=test.example.com#Hy2
            invalid-not-a-proxy-link
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
