package com.drfxai.maximusvpn

import com.drfxai.maximusvpn.data.model.AppSettings
import com.drfxai.maximusvpn.data.model.RoutingMode
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.xray.XrayConfigBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XrayConfigBuilderTest {

    private fun realityProfile() = VlessProfile(
        name = "Test REALITY",
        address = "vpn.example.com",
        port = 443,
        uuid = "12345678-1234-1234-1234-123456789abc",
        transport = "tcp",
        security = "reality",
        sni = "gateway.icloud.com",
        flow = "xtls-rprx-vision",
        fingerprint = "chrome",
        publicKey = "D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d",
        shortId = "6ba7b810"
    )

    private fun settings() = AppSettings(
        routingMode = RoutingMode.RULE_BYPASS_LAN,
        dnsServer = "1.1.1.1"
    )

    @Test
    fun buildTunConfig_generatesValidXrayStructure() {
        val json = JSONObject(XrayConfigBuilder.buildTunConfig(realityProfile(), settings(), tunFd = 0))

        assertTrue(json.has("inbounds"))
        assertTrue(json.has("outbounds"))
        assertTrue(json.has("routing"))
        assertTrue(json.has("dns"))

        val inbounds = json.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        val inbound = inbounds.getJSONObject(0)
        assertEquals("tun", inbound.getString("protocol"))
        assertEquals("tun-in", inbound.getString("tag"))
        val tunSettings = inbound.getJSONObject("settings")
        val gateway = tunSettings.getJSONArray("gateway")
        assertEquals("172.19.0.1/30", gateway.getString(0))
        assertEquals("fdfe:dcba:9876::1/126", gateway.getString(1))
    }

    @Test
    fun buildTunConfig_androidTunFdIsPassedByEnvironmentNotJson() {
        val json = JSONObject(XrayConfigBuilder.buildTunConfig(realityProfile(), settings(), tunFd = 1234))
        val tunSettings = json.getJSONArray("inbounds").getJSONObject(0).getJSONObject("settings")
        assertFalse(tunSettings.has("fd"))
        assertFalse(tunSettings.has("inet4_address"))
        assertFalse(tunSettings.has("inet6_address"))
    }

    @Test
    fun buildTunConfig_realityOutbound_hasRealitySettingsAndVisionFlow() {
        val json = JSONObject(XrayConfigBuilder.buildTunConfig(realityProfile(), settings(), tunFd = 0))
        val outbounds = json.getJSONArray("outbounds")
        var proxy: JSONObject? = null
        for (i in 0 until outbounds.length()) {
            val o = outbounds.getJSONObject(i)
            if (o.getString("tag") == "proxy") proxy = o
        }
        assertNotNull(proxy)
        proxy!!.let {
            assertEquals("vless", it.getString("protocol"))
            val stream = it.getJSONObject("streamSettings")
            assertEquals("reality", stream.getString("security"))
            val reality = stream.getJSONObject("realitySettings")
            assertEquals("gateway.icloud.com", reality.getString("serverName"))
            assertEquals("chrome", reality.getString("fingerprint"))
            assertTrue(reality.has("publicKey"))
            assertTrue(reality.has("shortId"))
            val user = it.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
                .getJSONArray("users").getJSONObject(0)
            assertEquals("xtls-rprx-vision", user.getString("flow"))
        }
    }

    @Test
    fun buildTunConfig_defaultRouteIsProxy_failClosed() {
        val json = JSONObject(XrayConfigBuilder.buildTunConfig(realityProfile(), settings(), tunFd = 0))
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        val lastRule = rules.getJSONObject(rules.length() - 1)
        assertEquals("proxy", lastRule.getString("outboundTag"))
        assertEquals("tcp,udp", lastRule.getString("network"))
    }

    @Test
    fun buildTunConfig_privateBypassDoesNotRequireGeoIpFile() {
        val json = JSONObject(XrayConfigBuilder.buildTunConfig(realityProfile(), settings(), tunFd = 0))
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        val ipRules = rules.getJSONObject(0).getJSONArray("ip")
        assertTrue(ipRules.getString(0).startsWith("10."))
        assertTrue(ipRules.getString(ipRules.length() - 1).contains("198.18."))
    }

    @Test
    fun buildTunConfig_selectedBypassClassifiesIpAndDomain() {
        val settings = settings().copy(
            routingMode = RoutingMode.BYPASS_SELECTED,
            customBypassRules = "192.168.50.10,example.local"
        )
        val json = JSONObject(XrayConfigBuilder.buildTunConfig(realityProfile(), settings, tunFd = 0))
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        val selected = rules.getJSONObject(0)
        assertEquals("192.168.50.10", selected.getJSONArray("ip").getString(0))
        assertEquals("example.local", selected.getJSONArray("domain").getString(0))
    }

    @Test
    fun buildTunConfig_wsTransport_hasWsSettings() {
        val profile = realityProfile().copy(
            transport = "ws",
            security = "tls",
            path = "/wspath",
            host = "cdn.example.com",
            flow = ""
        )
        val json = JSONObject(XrayConfigBuilder.buildTunConfig(profile, settings(), tunFd = 0))
        val outbounds = json.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val o = outbounds.getJSONObject(i)
            if (o.getString("tag") == "proxy") {
                val stream = o.getJSONObject("streamSettings")
                assertEquals("ws", stream.getString("network"))
                assertEquals("tls", stream.getString("security"))
                assertTrue(stream.getJSONObject("wsSettings").has("path"))
                // allowInsecure must never be enabled
                assertFalse(stream.getJSONObject("tlsSettings").optBoolean("allowInsecure", false))
            }
        }
    }

    private fun assertNotNull(x: JSONObject?) {
        if (x == null) throw AssertionError("expected non-null")
    }
    private fun assertFalse(x: Boolean) {
        if (x) throw AssertionError("expected false")
    }
}
