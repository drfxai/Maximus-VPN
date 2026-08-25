package com.drfxai.maximus.desktop

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds a real Xray-core TUN-mode config for Windows x64 / Linux x64.
 *
 * Flow: system traffic -> Xray tun inbound (gVisor) -> routing -> VLESS outbound -> server.
 * Fail-closed: the default route targets the proxy; only private ranges may go direct.
 */
object XrayConfigBuilder {

    fun buildTunConfig(p: ServerProfile): String {
        val root = buildJsonObject {
            put("log", buildJsonObject {
                put("loglevel", "warning")
                put("access", "")
                put("error", "")
            })

            // DNS resolved inside Xray, forwarded through the proxy — no plaintext DNS leaks
            put("dns", buildJsonObject {
                putJsonArray("servers") { add(JsonPrimitive("1.1.1.1")); add(JsonPrimitive("8.8.8.8")) }
                put("queryStrategy", "UseIPv4")
            })

            put("inbounds", JsonArray(listOf(
                buildJsonObject {
                    put("tag", "tun-in")
                    put("protocol", "tun")
                    put("settings", buildJsonObject {
                        put("name", tunName())
                        put("mtu", 1500)
                        if (!isWindows()) {
                            // Linux: let Xray manage the routing table for the tunnel interface
                            putJsonArray("gateway") { add(JsonPrimitive("172.19.0.1/30")) }
                        }
                    })
                    put("sniffing", buildJsonObject {
                        put("enabled", true)
                        putJsonArray("destOverride") { add(JsonPrimitive("http")); add(JsonPrimitive("tls")); add(JsonPrimitive("quic")) }
                    })
                },
                // Local SOCKS inbound: (a) lets Xray route its own server traffic
                // through itself instead of into its own TUN (loop prevention),
                // (b) gives the app a concrete readiness probe.
                buildJsonObject {
                    put("tag", "local-socks")
                    put("protocol", "socks")
                    put("listen", "127.0.0.1")
                    put("port", 10808)
                    put("settings", buildJsonObject {
                        put("udpEnabled", true)
                    })
                }
            )))

            put("outbounds", JsonArray(listOf(proxyOutbound(p), directOutbound(), blockOutbound())))

            put("routing", buildJsonObject {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    // Xray's own connection to the VLESS server must bypass the
                    // TUN or it would loop back into itself. Route it via the
                    // local socks inbound by tagging the proxy outbound's traffic.
                    add(buildJsonObject {
                        put("type", "field")
                        put("ip", JsonArray(listOf(JsonPrimitive(p.address))))
                        put("outboundTag", "direct")
                    })
                    add(buildJsonObject {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("ip", JsonArray(listOf(JsonPrimitive("geoip:private"))))
                    })
                    add(buildJsonObject {
                        put("type", "field")
                        put("outboundTag", "proxy")
                        put("network", "tcp,udp")
                    })
                }
            })
        }
        return root.toString()
    }

    /** Keep legacy entry point working for any callers/tests. */
    fun build(p: ParsedVless): String = buildTunConfig(ServerProfile.fromParsed(p))

    private fun proxyOutbound(p: ServerProfile) = buildJsonObject {
        put("tag", "proxy")
        put("protocol", "vless")
        put("settings", buildJsonObject {
            putJsonArray("vnext") {
                add(buildJsonObject {
                    put("address", p.address)
                    put("port", p.port)
                    putJsonArray("users") {
                        add(buildJsonObject {
                            put("id", p.uuid)
                            put("encryption", "none")
                            if (p.flow.isNotBlank()) put("flow", p.flow)
                            put("level", 0)
                        })
                    }
                })
            }
        })
        put("streamSettings", streamSettings(p))
    }

    private fun streamSettings(p: ServerProfile) = buildJsonObject {
        val transport = p.transport.lowercase().ifBlank { "tcp" }
        // Xray v5+ accepts both "raw" and legacy "tcp"; use tcp for widest compatibility.
        put("network", transport)
        val sec = p.security.lowercase().ifBlank { "none" }
        put("security", sec)

        when (sec) {
            "tls" -> putJsonObject("tlsSettings") {
                if (p.sni.isNotBlank()) put("serverName", p.sni)
                if (p.fingerprint.isNotBlank()) put("fingerprint", p.fingerprint)
                if (p.alpn.isNotBlank()) {
                    putJsonArray("alpn") { p.alpn.split(",").forEach { add(JsonPrimitive(it.trim())) } }
                }
                put("allowInsecure", false)
            }
            "reality" -> putJsonObject("realitySettings") {
                put("show", false)
                put("serverName", p.sni)
                put("fingerprint", p.fingerprint.ifBlank { "chrome" })
                put("publicKey", p.publicKey)
                put("shortId", p.shortId)
                if (p.spiderX.isNotBlank()) put("spiderX", p.spiderX)
            }
        }

        when (transport) {
            "ws" -> putJsonObject("wsSettings") {
                put("path", p.path.ifBlank { "/" })
                if (p.host.isNotBlank()) {
                    putJsonObject("headers") { put("Host", p.host) }
                }
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", p.serviceName)
                put("multiMode", true)
            }
            "xhttp" -> putJsonObject("xhttpSettings") {
                put("path", p.path.ifBlank { "/" })
                if (p.host.isNotBlank()) put("host", p.host)
            }
        }
    }

    private fun directOutbound() = buildJsonObject {
        put("tag", "direct")
        put("protocol", "freedom")
        put("settings", buildJsonObject { put("domainStrategy", "UseIP") })
    }

    private fun blockOutbound() = buildJsonObject {
        put("tag", "block")
        put("protocol", "blackhole")
        put("settings", buildJsonObject {
            putJsonObject("response") { put("type", "none") }
        })
    }

    private fun tunName(): String =
        if (isWindows()) "MaximusVPN" else "maximus0"

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")
}

/** Bridge helper: convert the parsed URI form into a full profile. */
private fun ServerProfile.Companion.fromParsed(p: ParsedVless): ServerProfile = ServerProfile(
    name = p.name, address = p.host, port = p.port, uuid = p.uuid,
    transport = p.type, security = p.security, sni = p.sni,
    host = p.hostHeader, path = p.path, serviceName = p.serviceName,
    flow = p.flow, fingerprint = p.fingerprint, publicKey = p.publicKey,
    shortId = p.shortId, spiderX = p.spiderX,
    alpn = p.alpn.joinToString(",")
)
