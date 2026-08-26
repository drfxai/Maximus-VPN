package com.drfxai.maximusvpn.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.data.model.VpnProtocol

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val encryption: String,
    val transport: String,
    val security: String,
    val sni: String,
    val host: String,
    val path: String,
    val serviceName: String,
    val flow: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val spiderX: String,
    val alpn: String,
    val headerType: String,
    // --- v2.0 columns ---
    val protocol: String = VpnProtocol.VLESS.name,
    val alterId: Int = 0,
    val allowInsecure: Boolean = false,
    val obfsPassword: String = "",
    val subscriptionId: String? = null,
    val isFavorite: Boolean,
    val lastLatencyMs: Long?,
    val lastTestedTimestamp: Long?,
    val countryCode: String?,
    val createdAt: Long
) {
    fun toDomain(): VlessProfile = VlessProfile(
        id = id, name = name, address = address, port = port, uuid = uuid,
        encryption = encryption, transport = transport, security = security,
        sni = sni, host = host, path = path, serviceName = serviceName,
        flow = flow, fingerprint = fingerprint, publicKey = publicKey,
        shortId = shortId, spiderX = spiderX, alpn = alpn, headerType = headerType,
        protocol = protocol, alterId = alterId, allowInsecure = allowInsecure,
        obfsPassword = obfsPassword, subscriptionId = subscriptionId,
        isFavorite = isFavorite, lastLatencyMs = lastLatencyMs,
        lastTestedTimestamp = lastTestedTimestamp, countryCode = countryCode,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(p: VlessProfile): ServerProfileEntity = ServerProfileEntity(
            id = p.id, name = p.name, address = p.address, port = p.port, uuid = p.uuid,
            encryption = p.encryption, transport = p.transport, security = p.security,
            sni = p.sni, host = p.host, path = p.path, serviceName = p.serviceName,
            flow = p.flow, fingerprint = p.fingerprint, publicKey = p.publicKey,
            shortId = p.shortId, spiderX = p.spiderX, alpn = p.alpn, headerType = p.headerType,
            protocol = p.protocolEnum.name, alterId = p.alterId,
            allowInsecure = p.allowInsecure, obfsPassword = p.obfsPassword,
            subscriptionId = p.subscriptionId,
            isFavorite = p.isFavorite, lastLatencyMs = p.lastLatencyMs,
            lastTestedTimestamp = p.lastTestedTimestamp, countryCode = p.countryCode,
            createdAt = p.createdAt
        )
    }
}
