package com.drfxai.maximusvpn.data.repository

import com.drfxai.maximusvpn.data.database.ServerProfileDao
import com.drfxai.maximusvpn.data.database.ServerProfileEntity
import com.drfxai.maximusvpn.data.model.VlessProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerRepository(private val dao: ServerProfileDao) {

    val allProfiles: Flow<List<VlessProfile>> = dao.getAllProfiles().map { list ->
        list.map { it.toDomain() }
    }

    val favoriteProfiles: Flow<List<VlessProfile>> = dao.getFavoriteProfiles().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getProfileById(id: String): VlessProfile? {
        return dao.getProfileById(id)?.toDomain()
    }

    suspend fun getCount(): Int {
        return dao.getCount()
    }

    suspend fun insert(profile: VlessProfile) {
        dao.insert(ServerProfileEntity.fromDomain(profile))
    }

    suspend fun insertAll(profiles: List<VlessProfile>) {
        dao.insertAll(profiles.map { ServerProfileEntity.fromDomain(it) })
    }

    suspend fun update(profile: VlessProfile) {
        dao.update(ServerProfileEntity.fromDomain(profile))
    }

    suspend fun updateLatency(id: String, latencyMs: Long?) {
        dao.updateLatency(id, latencyMs, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        dao.updateFavorite(id, isFavorite)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
