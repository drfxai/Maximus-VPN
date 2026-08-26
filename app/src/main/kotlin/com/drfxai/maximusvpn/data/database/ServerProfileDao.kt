package com.drfxai.maximusvpn.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {

    @Query("SELECT * FROM server_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteProfiles(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ServerProfileEntity?

    @Query("SELECT COUNT(*) FROM server_profiles")
    suspend fun getCount(): Int

    /** De-duplication helper for imports/subscriptions. */
    @Query(
        "SELECT * FROM server_profiles WHERE protocol = :protocol AND address = :address " +
            "AND port = :port AND uuid = :uuid LIMIT 1"
    )
    suspend fun findByEndpoint(protocol: String, address: String, port: Int, uuid: String): ServerProfileEntity?

    @Query("SELECT * FROM server_profiles WHERE subscriptionId = :subscriptionId")
    suspend fun getBySubscriptionId(subscriptionId: String): List<ServerProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ServerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ServerProfileEntity>)

    @Update
    suspend fun update(entity: ServerProfileEntity)

    @Query("UPDATE server_profiles SET lastLatencyMs = :latencyMs, lastTestedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLatency(id: String, latencyMs: Long?, timestamp: Long)

    @Query("UPDATE server_profiles SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM server_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM server_profiles WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: String)

    @Query("DELETE FROM server_profiles")
    suspend fun deleteAll()
}
