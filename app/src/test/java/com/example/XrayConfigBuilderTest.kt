package com.example

import com.example.data.model.AppSettings
import com.example.data.model.RoutingMode
import com.example.data.model.VlessProfile
import com.example.xray.XrayConfigBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XrayConfigBuilderTest {

    @Test
    fun buildJson_realityProfile_generatesValidXrayStructure() {
        val profile = VlessProfile(
            name = "Test REALITY",
            address = "vpn.example.com",
            port = 443,
            uuid = "12345678-1234-1234-1234-123456789abc",
            encryption = "none",
            transport = "tcp",
            security = "reality",
            sni = "gateway.icloud.com",
            flow = "xtls-rprx-vision",
            fingerprint = "chrome",
            publicKey = "D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d",
            shortId = "6ba7b810"
        )
        val settings = AppSettings(
            routingMode = RoutingMode.RULE_BYPASS_LAN,
            dnsServer = "1.1.1.1"
        )

        val jsonString = XrayConfigBuilder.buildJson(profile, settings)
        val json = JSONObject(jsonString)

        assertTrue(json.has("inbounds"))
        assertTrue(json.has("outbounds"))
        assertTrue(json.has("routing"))
        assertTrue(json.has("dns"))

        // Validate Outbound
        val outbounds = json.getJSONArray("outbounds")
        val mainOutbound = outbounds.getJSONObject(0)
        assertEquals("vless", mainOutbound.getString("protocol"))
        assertEquals("proxy", mainOutbound.getString("tag"))

        val settingsObj = mainOutbound.getJSONObject("settings")
        val vnext = settingsObj.getJSONArray("vnext").getJSONObject(0)
        assertEquals("vpn.example.com", vnext.getString("address"))
        assertEquals(443, vnext.getInt("port"))

        val user = vnext.getJSONArray("users").getJSONObject(0)
        assertEquals("12345678-1234-1234-1234-123456789abc", user.getString("id"))
        assertEquals("xtls-rprx-vision", user.getString("flow"))

        // Validate Reality streamSettings
        val streamSettings = mainOutbound.getJSONObject("streamSettings")
        assertEquals("tcp", streamSettings.getString("network"))
        assertEquals("reality", streamSettings.getString("security"))

        val realitySettings = streamSettings.getJSONObject("realitySettings")
        assertEquals("gateway.icloud.com", realitySettings.getString("serverName"))
        assertEquals("D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d", realitySettings.getString("publicKey"))
        assertEquals("6ba7b810", realitySettings.getString("shortId"))
    }
}
