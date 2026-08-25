package com.drfxai.maximus.desktop

import kotlinx.serialization.json.*

object XrayConfigBuilder {
    fun build(p: ParsedVless): String {
        val stream = buildJsonObject {
            put("network", if (p.type == "tcp") "raw" else p.type)
            put("security", p.security)
            if (p.security == "tls") {
                put("tlsSettings", buildJsonObject {
                    if (p.sni.isNotBlank()) put("serverName", p.sni)
                    if (p.fingerprint.isNotBlank()) put("fingerprint", p.fingerprint)
                    if (p.alpn.isNotEmpty()) put("alpn", JsonArray(p.alpn.map(::JsonPrimitive)))
                })
            } else if (p.security == "reality") {
                put("realitySettings", buildJsonObject {
                    put("serverName", p.sni)
                    put("fingerprint", if (p.fingerprint.isBlank()) "chrome" else p.fingerprint)
                    put("publicKey", p.publicKey)
                    put("shortId", p.shortId)
                    if (p.spiderX.isNotBlank()) put("spiderX", p.spiderX)
                })
            }
            when (p.type) {
                "ws" -> put("wsSettings", buildJsonObject {
                    put("path", p.path)
                    if (p.hostHeader.isNotBlank()) put("headers", buildJsonObject { put("Host", p.hostHeader) })
                })
                "grpc" -> put("grpcSettings", buildJsonObject { put("serviceName", p.serviceName) })
                "xhttp" -> put("xhttpSettings", buildJsonObject { put("path", p.path) })
            }
        }

        val outbound = buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", buildJsonObject {
                put("vnext", buildJsonArray {
                    add(buildJsonObject {
                        put("address", p.host)
                        put("port", p.port)
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("id", p.uuid)
                                put("encryption", "none")
                                if (p.flow.isNotBlank()) put("flow", p.flow)
                            })
                        })
                    })
                })
            })
            put("streamSettings", stream)
        }

        val root = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            put("dns", buildJsonObject { put("servers", buildJsonArray { add("1.1.1.1"); add("8.8.8.8") }) })
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("tag", "tun-in")
                    put("protocol", "tun")
                    put("settings", buildJsonObject {
                        put("name", if (System.getProperty("os.name").lowercase().contains("win")) "MaximusVPN" else "maximus0")
                        put("mtu", 1500)
                        put("gateway", buildJsonArray { add("172.19.0.1/30"); add("fd00:19::1/126") })
                        put("dns", buildJsonArray { add("1.1.1.1"); add("8.8.8.8") })
                        put("autoSystemRoutingTable", buildJsonArray { add("0.0.0.0/0"); add("::/0") })
                        put("autoOutboundsInterface", "auto")
                    })
                })
            })
            put("outbounds", buildJsonArray {
                add(outbound)
                add(buildJsonObject { put("tag", "direct"); put("protocol", "freedom") })
                add(buildJsonObject { put("tag", "block"); put("protocol", "blackhole") })
            })
            put("routing", buildJsonObject {
                put("domainStrategy", "IPIfNonMatch")
                put("rules", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "field")
                        put("network", "tcp,udp")
                        put("outboundTag", "proxy")
                    })
                })
            })
        }
        return root.toString()
    }
}
