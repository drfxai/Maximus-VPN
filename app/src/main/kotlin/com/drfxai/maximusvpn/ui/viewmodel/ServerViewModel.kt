package com.drfxai.maximusvpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drfxai.maximusvpn.MaximusApplication
import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.model.ServerTestStatus
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.data.repository.ServerRepository
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.subscription.SubscriptionRepository
import com.drfxai.maximusvpn.subscription.SubscriptionUpdateWorker
import com.drfxai.maximusvpn.vless.BatchParseResult
import com.drfxai.maximusvpn.vless.VlessParser
import com.drfxai.maximusvpn.vless.VlessValidator
import com.drfxai.maximusvpn.vpn.ServerTester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ServerSortOption {
    DEFAULT,
    LATENCY,
    NAME
}

class ServerViewModel(
    private val repository: ServerRepository = MaximusApplication.instance.serverRepository,
    private val settingsRepository: SettingsRepository = MaximusApplication.instance.settingsRepository,
    private val subscriptionRepository: SubscriptionRepository =
        SubscriptionRepository.getInstance(MaximusApplication.instance)
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites.asStateFlow()

    private val _sortOption = MutableStateFlow(ServerSortOption.DEFAULT)
    val sortOption: StateFlow<ServerSortOption> = _sortOption.asStateFlow()

    private val _isTestingAll = MutableStateFlow(false)
    val isTestingAll: StateFlow<Boolean> = _isTestingAll.asStateFlow()

    private val _serverTestingStates = MutableStateFlow<Map<String, ServerTestStatus>>(emptyMap())
    val serverTestingStates: StateFlow<Map<String, ServerTestStatus>> = _serverTestingStates.asStateFlow()

    // --- Import feedback ---
    private val _importFeedback = MutableStateFlow<String?>(null)
    val importFeedback: StateFlow<String?> = _importFeedback.asStateFlow()

    val subscriptions: StateFlow<List<com.drfxai.maximusvpn.subscription.SubscriptionEntity>> =
        subscriptionRepository.allSubscriptions().stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    private val _syncingSubIds = MutableStateFlow<Set<String>>(emptySet())
    val syncingSubIds: StateFlow<Set<String>> = _syncingSubIds.asStateFlow()

    val selectedProfileId: StateFlow<String?> = settingsRepository.settingsFlow
        .combine(MutableStateFlow(Unit)) { settings, _ -> settings.selectedProfileId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val serverList: StateFlow<List<VlessProfile>> = combine(
        repository.allProfiles,
        _searchQuery,
        _onlyFavorites,
        _sortOption
    ) { profiles, query, favoritesOnly, sort ->
        var list = profiles
        if (favoritesOnly) {
            list = list.filter { it.isFavorite }
        }
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.address.contains(query, ignoreCase = true) ||
                        it.transport.contains(query, ignoreCase = true) ||
                        it.security.contains(query, ignoreCase = true) ||
                        it.protocolEnum.label.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            ServerSortOption.LATENCY -> list.sortedWith(compareBy(nullsLast()) { it.lastLatencyMs })
            ServerSortOption.NAME -> list.sortedBy { it.name.lowercase() }
            ServerSortOption.DEFAULT -> list
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesFilter() {
        _onlyFavorites.value = !_onlyFavorites.value
    }

    fun setSortOption(option: ServerSortOption) {
        _sortOption.value = option
    }

    fun selectServer(profile: VlessProfile) {
        settingsRepository.setSelectedProfileId(profile.id)
    }

    fun toggleFavorite(profile: VlessProfile) {
        viewModelScope.launch {
            repository.toggleFavorite(profile.id, !profile.isFavorite)
        }
    }

    fun deleteServer(profileId: String) {
        viewModelScope.launch {
            repository.delete(profileId)
        }
    }

    fun duplicateServer(profile: VlessProfile) {
        viewModelScope.launch {
            val duplicate = profile.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${profile.name} (Copy)",
                createdAt = System.currentTimeMillis()
            )
            repository.insert(duplicate)
        }
    }

    fun addServer(profile: VlessProfile): AppResult<Unit> {
        return try {
            VlessValidator.validate(profile)
            viewModelScope.launch {
                repository.insert(profile)
                if (settingsRepository.getSettings().selectedProfileId == null) {
                    settingsRepository.setSelectedProfileId(profile.id)
                }
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(e, e.localizedMessage ?: "Invalid configuration")
        }
    }

    fun importFromVlessUri(rawUri: String): AppResult<VlessProfile> {
        val result = VlessParser.parse(rawUri)
        if (result is AppResult.Success) {
            viewModelScope.launch {
                repository.insert(result.data)
                if (settingsRepository.getSettings().selectedProfileId == null) {
                    settingsRepository.setSelectedProfileId(result.data.id)
                }
            }
        }
        return result
    }

    fun importBatch(rawText: String): BatchParseResult {
        val batchResult = VlessParser.parseBatch(rawText)
        if (batchResult.successfulProfiles.isNotEmpty()) {
            viewModelScope.launch {
                repository.insertAll(batchResult.successfulProfiles)
                if (settingsRepository.getSettings().selectedProfileId == null) {
                    settingsRepository.setSelectedProfileId(batchResult.successfulProfiles.first().id)
                }
                _importFeedback.value =
                    "Imported ${batchResult.successfulProfiles.size} servers" +
                    (if (batchResult.failedEntries.isNotEmpty())
                        " (${batchResult.failedEntries.size} skipped)" else "")
            }
        } else {
            _importFeedback.value = "No valid server links found"
        }
        return batchResult
    }

    fun clearImportFeedback() {
        _importFeedback.value = null
    }

    // --- Subscriptions ---

    fun addSubscription(name: String, url: String, user: String?, password: String?) {
        viewModelScope.launch {
            try {
                val sub = subscriptionRepository.addSubscription(name, url, user, password)
                _syncingSubIds.value = _syncingSubIds.value + sub.id
                val result = subscriptionRepository.sync(sub.id)
                _syncingSubIds.value = _syncingSubIds.value - sub.id
                _importFeedback.value = result.error
                    ?: "Subscription synced: +${result.added} new, ${result.updated} existing"
            } catch (e: Exception) {
                _syncingSubIds.value = emptySet()
                _importFeedback.value = "Subscription failed: ${e.message}"
            }
        }
    }

    fun syncSubscription(id: String) {
        viewModelScope.launch {
            _syncingSubIds.value = _syncingSubIds.value + id
            val result = subscriptionRepository.sync(id)
            _syncingSubIds.value = _syncingSubIds.value - id
            _importFeedback.value = result.error
                ?: "Subscription updated: +${result.added} ~${result.updated} -${result.removed}"
        }
    }

    fun deleteSubscription(id: String) {
        viewModelScope.launch { subscriptionRepository.deleteSubscription(id) }
    }

    fun scheduleAutoUpdates(context: android.content.Context, hours: Int, wifiOnly: Boolean) {
        SubscriptionUpdateWorker.schedule(context, hours, wifiOnly)
    }

    fun testServer(profile: VlessProfile) {
        viewModelScope.launch {
            _serverTestingStates.value = _serverTestingStates.value + (profile.id to ServerTestStatus.Testing)
            val result = ServerTester.testServer(profile)
            _serverTestingStates.value = _serverTestingStates.value + (profile.id to result.status)

            if (result.status is ServerTestStatus.Available) {
                repository.updateLatency(profile.id, result.status.latencyMs)
            } else if (result.status is ServerTestStatus.Slow) {
                repository.updateLatency(profile.id, result.status.latencyMs)
            } else {
                repository.updateLatency(profile.id, null)
            }
        }
    }

    fun testAllServers() {
        viewModelScope.launch {
            _isTestingAll.value = true
            val currentProfiles = serverList.value
            for (profile in currentProfiles) {
                _serverTestingStates.value = _serverTestingStates.value + (profile.id to ServerTestStatus.Testing)
                val result = ServerTester.testServer(profile, timeoutMs = 2500)
                _serverTestingStates.value = _serverTestingStates.value + (profile.id to result.status)

                val latency = when (result.status) {
                    is ServerTestStatus.Available -> result.status.latencyMs
                    is ServerTestStatus.Slow -> result.status.latencyMs
                    else -> null
                }
                repository.updateLatency(profile.id, latency)
            }
            _isTestingAll.value = false
        }
    }

    fun exportUri(profile: VlessProfile): String {
        return VlessParser.toUri(profile)
    }
}
