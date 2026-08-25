package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.model.VlessProfile
import com.example.data.repository.ServerRepository
import com.example.data.repository.SettingsRepository
import com.example.vless.VlessParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RayApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var serverRepository: ServerRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        serverRepository = ServerRepository(database.serverProfileDao())
        settingsRepository = SettingsRepository(this)

        // Seed default sample configurations if database is empty on first install
        CoroutineScope(Dispatchers.IO).launch {
            if (serverRepository.getCount() == 0) {
                seedInitialProfiles()
            }
        }
    }

    private suspend fun seedInitialProfiles() {
        val sample1 = VlessProfile(
            name = "US High-Speed REALITY",
            address = "us-east.maximus-vpn.net",
            port = 443,
            uuid = "e7b99c42-88f1-4b19-9182-3d84a7e93f12",
            encryption = "none",
            transport = "tcp",
            security = "reality",
            sni = "gateway.icloud.com",
            flow = "xtls-rprx-vision",
            fingerprint = "chrome",
            publicKey = "D4g8xP_98uI1O4L6v3Yq0eN7w2m1k0j9i8h7g6f5e4d",
            shortId = "6ba7b810",
            spiderX = "/",
            isFavorite = true,
            countryCode = "US"
        )

        val sample2 = VlessProfile(
            name = "Frankfurt Edge WS-TLS",
            address = "de-fra.maximus-vpn.net",
            port = 443,
            uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            encryption = "none",
            transport = "ws",
            security = "tls",
            sni = "de-fra.maximus-vpn.net",
            host = "de-fra.maximus-vpn.net",
            path = "/vless-ws",
            fingerprint = "firefox",
            alpn = "h2,http/1.1",
            countryCode = "DE"
        )

        val sample3 = VlessProfile(
            name = "Tokyo Cloud gRPC",
            address = "jp-tyo.maximus-vpn.net",
            port = 443,
            uuid = "99887766-5544-3322-1100-aabbccddeeff",
            encryption = "none",
            transport = "grpc",
            security = "tls",
            sni = "jp-tyo.maximus-vpn.net",
            serviceName = "maximus-grpc-service",
            fingerprint = "chrome",
            countryCode = "JP"
        )

        serverRepository.insertAll(listOf(sample1, sample2, sample3))
        settingsRepository.setSelectedProfileId(sample1.id)
    }

    companion object {
        lateinit var instance: RayApplication
            private set
    }
}
