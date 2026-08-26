package com.drfxai.maximusvpn.subscription

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.database.AppDatabase
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.data.model.VpnProtocol
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val basicAuthUser: String = "",
    /** Stored encrypted via SecureStorage; this column only marks presence. */
    val hasPassword: Boolean = false,
    val autoUpdateHours: Int = 24,
    val lastUpdatedTimestamp: Long? = null,
    val lastServerCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions")
    suspend fun getAllOnce(): List<SubscriptionEntity>

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * Manages subscription entities and the import/sync lifecycle.
 *
 * Sync semantics (conflict resolution):
 *  - Profiles are matched against the DB by [VlessProfile.dedupeKey] identity
 *    (protocol+address+port+credential+transport+security).
 *  - Existing profiles keep their id, favorite flag and latency history.
 *  - Profiles previously imported by this subscription that vanished from the
 *    remote list are removed.
 */
class SubscriptionRepository(private val context: Context) {

    private val dao: SubscriptionDao =
        AppDatabase.getInstance(context).subscriptionDao()

    private val serverDao get() = AppDatabase.getInstance(context).serverProfileDao()

    private val secureStorage = com.drfxai.maximusvpn.data.security.SecureStorage(context)

    fun allSubscriptions(): Flow<List<SubscriptionEntity>> = dao.getAll()

    suspend fun addSubscription(
        name: String,
        url: String,
        basicAuthUser: String?,
        basicAuthPassword: String?
    ): SubscriptionEntity {
        val sub = SubscriptionEntity(
            name = name.ifBlank { "Subscription" },
            url = url.trim(),
            basicAuthUser = basicAuthUser.orEmpty(),
            hasPassword = !basicAuthPassword.isNullOrBlank()
        )
        if (!basicAuthPassword.isNullOrBlank()) {
            secureStorage.encryptAndSave(passwordKey(sub.id), basicAuthPassword)
        }
        dao.upsert(sub)
        return sub
    }

    suspend fun updateSubscription(subscription: SubscriptionEntity) = dao.upsert(subscription)

    suspend fun deleteSubscription(id: String) {
        // Remove member profiles too — they are meaningless without their source.
        serverDao.deleteBySubscriptionId(id)
        secureStorage.encryptAndSave(passwordKey(id), "")
        dao.deleteById(id)
    }

    suspend fun getPassword(subscriptionId: String): String =
        secureStorage.getAndDecrypt(passwordKey(subscriptionId))

    /**
     * Fetches + imports a subscription. Returns counts for UI feedback.
     */
    suspend fun sync(subscriptionId: String): SyncResult {
        val sub = dao.getById(subscriptionId)
            ?: return SyncResult(added = 0, updated = 0, removed = 0, error = "Subscription not found")

        val fetch = SubscriptionFetcher.fetch(
            SubscriptionFetcher.FetchOptions(
                url = sub.url,
                basicAuthUser = sub.basicAuthUser.takeIf { it.isNotBlank() },
                basicAuthPassword = getPassword(sub.id).takeIf { it.isNotBlank() }
            )
        )
        if (fetch is AppResult.Error) {
            markResult(sub, count = sub.lastServerCount, error = fetch.userFriendlyMessage)
            return SyncResult(0, 0, 0, fetch.userFriendlyMessage)
        }
        val body = (fetch as AppResult.Success<SubscriptionFetcher.FetchResult>).data.rawBody

        val parsed = SubscriptionFetcher.decode(body)
        if (parsed.successfulProfiles.isEmpty()) {
            val err = "No valid servers found in subscription" +
                    (parsed.failedEntries.firstOrNull()?.let { ": ${it.errorMessage}" } ?: "")
            markResult(sub, count = 0, error = err)
            return SyncResult(0, 0, 0, err)
        }

        // Import with conflict resolution against existing rows.
        var added = 0
        var updated = 0
        val now = System.currentTimeMillis()
        val importedIds = mutableSetOf<String>()
        for (profile in parsed.successfulProfiles) {
            val existing = findDuplicate(profile)
            if (existing != null) {
                // Keep user's id/favorite/latency; refresh endpoint fields from source.
                val merged = profile.copy(
                    id = existing.id,
                    isFavorite = existing.isFavorite,
                    lastLatencyMs = existing.lastLatencyMs,
                    lastTestedTimestamp = existing.lastTestedTimestamp,
                    createdAt = existing.createdAt,
                    subscriptionId = sub.id
                )
                serverDao.insert(com.drfxai.maximusvpn.data.database.ServerProfileEntity.fromDomain(merged))
                importedIds.add(existing.id)
                updated++
            } else {
                val withSub = profile.copy(subscriptionId = sub.id, createdAt = now + added)
                serverDao.insert(com.drfxai.maximusvpn.data.database.ServerProfileEntity.fromDomain(withSub))
                importedIds.add(withSub.id)
                added++
            }
        }

        // Remove profiles from THIS subscription no longer present remotely.
        var removed = 0
        for (old in serverDao.getBySubscriptionId(sub.id)) {
            if (old.id !in importedIds) {
                serverDao.deleteById(old.id)
                removed++
            }
        }

        markResult(sub, count = importedIds.size, error = null)
        return SyncResult(added, updated, removed, error = null)
    }

    data class SyncResult(val added: Int, val updated: Int, val removed: Int, val error: String?)

    private suspend fun findDuplicate(profile: VlessProfile) =
        serverDao.findByEndpoint(
            protocol = profile.protocolEnum.name,
            address = profile.address,
            port = profile.port,
            uuid = profile.uuid
        )

    private suspend fun markResult(sub: SubscriptionEntity, count: Int, error: String?) {
        dao.upsert(
            sub.copy(
                lastUpdatedTimestamp = System.currentTimeMillis(),
                lastServerCount = count,
                lastError = error?.let { com.drfxai.maximusvpn.core.SecretRedactor.redact(it).take(300) }
            )
        )
    }

    companion object {
        fun passwordKey(subscriptionId: String) = "sub_pass_$subscriptionId"

        @Volatile private var instance: SubscriptionRepository? = null
        fun getInstance(context: Context): SubscriptionRepository =
            instance ?: synchronized(this) {
                instance ?: SubscriptionRepository(context.applicationContext).also { instance = it }
            }
    }
}
