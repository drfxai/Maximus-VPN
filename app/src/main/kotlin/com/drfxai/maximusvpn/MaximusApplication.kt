package com.drfxai.maximusvpn

import android.app.Application
import com.drfxai.maximusvpn.data.database.AppDatabase
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.data.repository.ServerRepository
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.vless.VlessParser

class MaximusApplication : Application() {

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
        // NOTE: no seeded demo servers. Fake nodes guarantee first-run failures;
        // users import their own vless:// links.
    }

    companion object {
        lateinit var instance: MaximusApplication
            private set
    }
}
