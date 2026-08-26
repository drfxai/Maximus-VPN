package com.drfxai.maximusvpn.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.drfxai.maximusvpn.subscription.SubscriptionDao
import com.drfxai.maximusvpn.subscription.SubscriptionEntity

@Database(
    entities = [ServerProfileEntity::class, SubscriptionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun serverProfileDao(): ServerProfileDao

    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        /**
         * v1 → v2 (v2.0 upgrade): adds the unified multi-protocol columns.
         * All existing rows stay VLESS — the defaults preserve their meaning exactly.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN protocol TEXT NOT NULL DEFAULT 'VLESS'")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN alterId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN allowInsecure INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN obfsPassword TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN subscriptionId TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "raytunnel_vpn.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
