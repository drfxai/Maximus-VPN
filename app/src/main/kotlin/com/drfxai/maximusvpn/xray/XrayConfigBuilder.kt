package com.drfxai.maximusvpn.xray

import com.drfxai.maximusvpn.data.model.AppSettings
import com.drfxai.maximusvpn.data.model.RoutingMode
import com.drfxai.maximusvpn.data.model.VlessProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a real Xray-core configuration for the Android tun inbound.
 *
 * Flow: VpnService TUN fd -> [xray.tun.fd env] -> Xray tun inbound (gVisor stack)
 *       -> routing rules -> VLESS outbound (TLS/REALITY via Xray) -> server.
 *
 * UDP and DNS are handled inside Xray's userspace network stack — there are no
 * direct sockets from the app process for user traffic (fail-closed by construction).
 */
object XrayConfigBuilder {

    fun buildTunConfig(profile: VlessProfile, settings: AppSettings): String {
        val root = JSONObject()

        // Logging — keep at warning to limit PII in logs; access log disabled
        root.put("log", JSONObject().apply {
            put("loglevel", if (settings.logLevel.isNotBlank()) settings.logLevel.lowercase() else "warning")
            put("access", "")
            put("error", "")
        })

        // DNS handled inside Xray; queries from the TUN go through the proxy outbound
        val dnsServers = JSONArray().apply {
            val primary = if (settings.dnsServer.isNotBlank()) settings.dnsServer else "1.1.1.1"
            put(JSONObject().apply {
                put("address", primary)
                // Resolve DNS through the proxy so DNS cannot leak around the tunnel
                put("attributes", JSONArray().apply { })
            })
            if (settings.customDns.isNotBlank() && settings.customDns != settings.dnsServer) {
                put(settings.customDns)
            }
        }
        root.put("dns", JSONObject().apply {
            put("servers", dnsServers)
            put("queryStrategy", "UseIPv4")
            put("disableFallback", false)
        })

        // Inbounds: single tun inbound fed by the VpnService fd
        val inbounds = JSONArray()
        val tunInbound = JSONObject().apply {
            put("tag", "tun-in")
            put("protocol", "tun")
            put("settings", JSONObject().apply {
                put("mtu", settings.mtu)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http"); put("tls"); put("quic")
                })
                put("routeOnly", false)
            })
        }
        inbounds.put(tunInbound)
        root.put("inbounds", inbounds)

        // Outbounds: proxy / direct(LAN only) / block
        val outbounds = JSONArray()

        val userObj = JSONObject().apply {
            put("id", profile.uuid)
            put("encryption", "none")
            put("level", 0)
            if (profile.flow.isNotBlank()) put("flow", profile.flow)
        }
        val vnextArray = JSONArray().put(JSONObject().apply {
            put("address", profile.address)
            put("port", profile.port)
            put("users", JSONArray().put(userObj))
        })

        val streamSettings = buildStreamSettings(profile)

        outbounds.put(JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().apply { put("vnext", vnextArray) })
            put("streamSettings", streamSettings)
        })

        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().apply { put("domainStrategy", "UseIP") })
        })
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject().apply { put("response", JSONObject().put("type", "none")) })
        })
        root.put("outbounds", outbounds)

        // Routing: LAN bypass optional; everything else -> proxy. Default route is fail-closed:
        // any unmatched traffic goes to proxy, never direct.
        val rules = JSONArray()
        when (settings.routingMode) {
            RoutingMode.GLOBAL -> { /* everything -> proxy via default rule below */ }
            RoutingMode.RULE_BYPASS_LAN -> {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply {
                        put("geoip:private")
                    })
                })
            }
            RoutingMode.BYPASS_SELECTED -> {
                if (settings.customBypassRules.isNotBlank()) {
                    val domains = JSONArray()
                    settings.customBypassRules.split(",").map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { domains.put(it) }
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("domain", domains)
                    })
                }
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply { put("geoip:private") })
                })
            }
        }
        // Default: all remaining traffic through the proxy (fail-closed default)
        rules.put(JSONObject().apply {
            put("type", "field")
            put("outboundTag", "proxy")
            put("network", "tcp,udp")
        })

        root.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", rules)
        })

        return root.toString(2)
    }

    private fun buildStreamSettings(profile: VlessProfile): JSONObject {
        val stream = JSONObject()
        stream.put("network", if (profile.transport.isNotBlank()) profile.transport.lowercase() else "tcp")

        val sec = profile.security.lowercase().ifBlank { "none" }
        stream.put("security", sec)

        when (sec) {
            "tls" -> stream.put("tlsSettings", JSONObject().apply {
                if (profile.sni.isNotBlank()) put("serverName", profile.sni)
                if (profile.fingerprint.isNotBlank()) put("fingerprint", profile.fingerprint)
                if (profile.alpn.isNotBlank()) {
                    put("alpn", JSONArray().apply {
                        profile.alpn.split(",").forEach { put(it.trim()) }
                    })
                }
                put("allowInsecure", false)
            })
            "reality" -> stream.put("realitySettings", JSONObject().apply {
                put("show", false)
                if (profile.sni.isNotBlank()) put("serverName", profile.sni)
                put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
                if (profile.publicKey.isNotBlank()) put("publicKey", profile.publicKey)
                if (profile.shortId.isNotBlank()) put("shortId", profile.shortId)
                if (profile.spiderX.isNotBlank()) put("spiderX", profile.spiderX)
            })
        }

        when (profile.transport.lowercase()) {
            "ws" -> stream.put("wsSettings", JSONObject().apply {
                put("path", profile.path.ifBlank { "/" })
                val headers = JSONObject()
                if (profile.host.isNotBlank()) headers.put("Host", profile.host)
                put("headers", headers)
            })
            "grpc" -> stream.put("grpcSettings", JSONObject().apply {
                put("serviceName", profile.serviceName)
                put("multiMode", true)
            })
            "http", "h2" -> stream.put("httpSettings", JSONObject().apply {
                put("path", profile.path.ifBlank { "/" })
                if (profile.host.isNotBlank()) {
                    put("host", JSONArray().put(profile.host))
                }
            })
            "tcp" -> {
                if (profile.headerType.equals("http", ignoreCase = true)) {
                    stream.put("tcpSettings", JSONObject().apply {
                        put("header", JSONObject().apply {
                            put("type", "http")
                            put("request", JSONObject().apply {
                                put("path", JSONArray().put(profile.path.ifBlank { "/" }))
                                if (profile.host.isNotBlank()) {
                                    put("headers", JSONObject().apply {
                                        put("Host", JSONArray().put(profile.host))
                                    })
                                }
                            })
                        })
                    })
                }
            }
        }
        return stream
    }
}
