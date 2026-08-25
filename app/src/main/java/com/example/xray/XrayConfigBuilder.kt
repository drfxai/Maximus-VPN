package com.example.xray

import com.example.data.model.AppSettings
import com.example.data.model.RoutingMode
import com.example.data.model.VlessProfile
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    const val DEFAULT_SOCKS_PORT = 10808
    const val DEFAULT_DOKODEMO_PORT = 10809

    /**
     * Builds a complete, valid Xray-core JSON configuration object from a VlessProfile and AppSettings.
     */
    fun buildJson(profile: VlessProfile, settings: AppSettings): String {
        val root = JSONObject()

        // 1. Logging
        val logObj = JSONObject().apply {
            put("loglevel", settings.logLevel.lowercase())
            put("access", "")
            put("error", "")
        }
        root.put("log", logObj)

        // 2. DNS
        val dnsObj = JSONObject()
        val dnsServers = JSONArray().apply {
            put(settings.dnsServer)
            if (settings.customDns.isNotBlank() && settings.customDns != settings.dnsServer) {
                put(settings.customDns)
            }
            put("8.8.8.8")
            put("localhost")
        }
        dnsObj.put("servers", dnsServers)
        root.put("dns", dnsObj)

        // 3. Inbounds (Local SOCKS & Dokodemo-Door for TUN forwarder)
        val inbounds = JSONArray()

        val socksInbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", DEFAULT_SOCKS_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http")
                    put("tls")
                    put("quic")
                })
            })
        }
        inbounds.put(socksInbound)

        val dokodemoInbound = JSONObject().apply {
            put("tag", "dokodemo-in")
            put("port", DEFAULT_DOKODEMO_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "dokodemo-door")
            put("settings", JSONObject().apply {
                put("network", "tcp,udp")
                put("followRedirect", true)
            })
        }
        inbounds.put(dokodemoInbound)
        root.put("inbounds", inbounds)

        // 4. Outbounds (VLESS Proxy, Direct Freedom, Block Blackhole)
        val outbounds = JSONArray()

        // Main VLESS Outbound
        val proxyOutbound = JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")

            // User & Server Settings
            val userObj = JSONObject().apply {
                put("id", profile.uuid)
                put("encryption", if (profile.encryption.isNotBlank()) profile.encryption else "none")
                if (profile.flow.isNotBlank()) {
                    put("flow", profile.flow)
                }
                put("level", 0)
            }

            val vnextArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("address", profile.address)
                    put("port", profile.port)
                    put("users", JSONArray().apply { put(userObj) })
                })
            }

            put("settings", JSONObject().apply {
                put("vnext", vnextArray)
            })

            // Stream Settings (Transport & Security)
            val streamSettings = JSONObject().apply {
                put("network", if (profile.transport.isNotBlank()) profile.transport else "tcp")

                val sec = profile.security.lowercase()
                put("security", if (sec.isNotBlank()) sec else "none")

                // TLS / REALITY Settings
                if (sec == "tls") {
                    val tlsObj = JSONObject().apply {
                        if (profile.sni.isNotBlank()) put("serverName", profile.sni)
                        if (profile.fingerprint.isNotBlank()) put("fingerprint", profile.fingerprint)
                        if (profile.alpn.isNotBlank()) {
                            val alpnArray = JSONArray()
                            profile.alpn.split(",").forEach { alpnArray.put(it.trim()) }
                            put("alpn", alpnArray)
                        }
                    }
                    put("tlsSettings", tlsObj)
                } else if (sec == "reality") {
                    val realityObj = JSONObject().apply {
                        put("show", false)
                        if (profile.fingerprint.isNotBlank()) put("fingerprint", profile.fingerprint)
                        if (profile.sni.isNotBlank()) put("serverName", profile.sni)
                        if (profile.publicKey.isNotBlank()) put("publicKey", profile.publicKey)
                        if (profile.shortId.isNotBlank()) put("shortId", profile.shortId)
                        if (profile.spiderX.isNotBlank()) put("spiderX", profile.spiderX)
                    }
                    put("realitySettings", realityObj)
                }

                // Transport Specific Settings
                when (profile.transport.lowercase()) {
                    "ws" -> {
                        val wsObj = JSONObject().apply {
                            put("path", if (profile.path.isNotBlank()) profile.path else "/")
                            val headers = JSONObject()
                            if (profile.host.isNotBlank()) headers.put("Host", profile.host)
                            put("headers", headers)
                        }
                        put("wsSettings", wsObj)
                    }
                    "grpc" -> {
                        val grpcObj = JSONObject().apply {
                            put("serviceName", if (profile.serviceName.isNotBlank()) profile.serviceName else "")
                            put("multiMode", true)
                        }
                        put("grpcSettings", grpcObj)
                    }
                    "http", "h2" -> {
                        val httpObj = JSONObject().apply {
                            put("path", if (profile.path.isNotBlank()) profile.path else "/")
                            if (profile.host.isNotBlank()) {
                                put("host", JSONArray().apply { put(profile.host) })
                            }
                        }
                        put("httpSettings", httpObj)
                    }
                    "tcp" -> {
                        if (profile.headerType.equals("http", ignoreCase = true)) {
                            val tcpObj = JSONObject().apply {
                                put("header", JSONObject().apply {
                                    put("type", "http")
                                    put("request", JSONObject().apply {
                                        put("path", JSONArray().apply { put(if (profile.path.isNotBlank()) profile.path else "/") })
                                        if (profile.host.isNotBlank()) {
                                            put("headers", JSONObject().apply {
                                                put("Host", JSONArray().apply { put(profile.host) })
                                            })
                                        }
                                    })
                                })
                            }
                            put("tcpSettings", tcpObj)
                        }
                    }
                }
            }

            put("streamSettings", streamSettings)
        }
        outbounds.put(proxyOutbound)

        // Freedom (Direct) Outbound
        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().apply {
                put("domainStrategy", "UseIP")
            })
        }
        outbounds.put(directOutbound)

        // Blackhole (Block) Outbound
        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject().apply {
                put("response", JSONObject().apply { put("type", "none") })
            })
        }
        outbounds.put(blockOutbound)

        root.put("outbounds", outbounds)

        // 5. Routing
        val routingObj = JSONObject()
        routingObj.put("domainStrategy", "IPIfNonMatch")
        val rulesArray = JSONArray()

        when (settings.routingMode) {
            RoutingMode.GLOBAL -> {
                // All traffic goes to proxy
                rulesArray.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                    put("network", "tcp,udp")
                })
            }
            RoutingMode.RULE_BYPASS_LAN -> {
                // Direct private IP addresses
                rulesArray.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply {
                        put("geoip:private")
                        put("10.0.0.0/8")
                        put("172.16.0.0/12")
                        put("192.168.0.0/16")
                        put("127.0.0.0/8")
                    })
                })
                // Direct local domain names
                rulesArray.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("domain", JSONArray().apply {
                        put("domain:local")
                        put("domain:localhost")
                        put("domain:lan")
                    })
                })
                // Everything else -> proxy
                rulesArray.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                    put("network", "tcp,udp")
                })
            }
            RoutingMode.BYPASS_SELECTED -> {
                // Custom bypass rules
                if (settings.customBypassRules.isNotBlank()) {
                    val customDomains = JSONArray()
                    settings.customBypassRules.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { customDomains.put(it) }

                    if (customDomains.length() > 0) {
                        rulesArray.put(JSONObject().apply {
                            put("type", "field")
                            put("outboundTag", "direct")
                            put("domain", customDomains)
                        })
                    }
                }
                // Private IPs
                rulesArray.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply { put("geoip:private") })
                })
                // Default -> proxy
                rulesArray.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                    put("network", "tcp,udp")
                })
            }
        }

        routingObj.put("rules", rulesArray)
        root.put("routing", routingObj)

        return root.toString(2)
    }
}
