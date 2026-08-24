package com.lagradost.cloudstream3.desktop.ui.screens.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.HeroRepository.HeroUpdate
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

const val PREF_ACTIVE_PROVIDERS = "home_active_providers"

/**
 * Returns true only for real, user-facing content providers:
 * - Excludes built-in MetaProviders (Trakt, TMDB, CrossTMDB)
 * - Excludes "NONE"
 */
fun MainAPI.isRealProvider(): Boolean {
    if (name == "NONE" || name == "None") return false
    if (providerType == com.lagradost.cloudstream3.ProviderType.MetaProvider) return false
    return true
}

private fun MainAPI.getProviderKey(): String {
    val src = sourcePlugin
    if (!src.isNullOrBlank() && src != "built-in") {
        val folder = java.io.File(src).parentFile?.name ?: ""
        if (folder.isNotBlank()) return "$folder::$name"
    }
    return name
}

private fun MainAPI.matchesKey(key: String): Boolean {
    return getProviderKey() == key || name == key
}

class DesktopHomeViewModel : BaseMviViewModel<HomeUiState, HomeUiEvent, HomeUiEffect>(
    initialState = HomeUiState(),
) {

    // Redundant StateFlow mappings have been permanently deleted in accordance with MVI best practices.
    // UI should collect `uiState` and read properties directly from the immutable snapshot.

    init {
        updateProviders()

        viewModelScope.launch {
            BookmarksRepository.bookmarksFlow.collect { bookmarks ->
                updateState { copy(bookmarks = bookmarks) }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val savedNames = DesktopDataStore.getKey<List<String>>(PREF_ACTIVE_PROVIDERS)
            if (savedNames != null) {
                val validNames = savedNames.filter { name -> APIHolder.allProviders.any { it.matchesKey(name) && it.isRealProvider() } }
                updateState { copy(activeProviders = validNames) }
            } else {
                // Fallback to old key or empty
                val oldName = DesktopDataStore.getKey<String>("preferred_provider_name")
                if (oldName != null && APIHolder.allProviders.any { it.matchesKey(oldName) && it.isRealProvider() }) {
                    updateState { copy(activeProviders = listOf(oldName)) }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            uiState.map { it.activeProviders }.distinctUntilChanged().drop(1).collect { names ->
                DesktopDataStore.setKey(PREF_ACTIVE_PROVIDERS, names)
                val disabledMap = names.associateWith { name ->
                    DesktopDataStore.getKey<Set<String>>("disabled_catalogs_$name") ?: emptySet()
                }
                updateState { copy(disabledCatalogs = disabledMap) }
            }
        }

        viewModelScope.launch {
            DesktopRepositoryManager.syncGeneration.collect { syncGen ->
                if (syncGen > 0) {
                    updateProviders()
                    reloadIcons()
                }
            }
        }

        viewModelScope.launch {
            combine(uiState.map { it.activeProviders }, DesktopDataStore.historyUpdates) { _, _ -> }.collect {
                updateHistory()
            }
        }

        updateHistory()
        reloadIcons()
    }

    override fun handleEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.OnToggleProviderActive -> {
                val current = uiState.value.activeProviders.toMutableList()
                if (event.isActive) {
                    if (!current.contains(event.providerName)) current.add(event.providerName)
                } else {
                    current.remove(event.providerName)
                }
                updateState { copy(activeProviders = current) }
            }
            is HomeUiEvent.OnSetSingleProvider -> {
                updateState { copy(activeProviders = listOf(event.providerName)) }
            }
            is HomeUiEvent.OnMoveProvider -> {
                val current = uiState.value.activeProviders.toMutableList()
                if (event.fromIndex in current.indices && event.toIndex in current.indices) {
                    val item = current.removeAt(event.fromIndex)
                    current.add(event.toIndex, item)
                    updateState { copy(activeProviders = current) }
                }
            }
            is HomeUiEvent.OnClearHistory -> clearHistory()
            is HomeUiEvent.OnRemoveHistoryItem -> removeHistoryItem(event.parentId)
            is HomeUiEvent.OnPrefetchHeroItem -> prefetchHeroItem(event.provider, event.item)
            is HomeUiEvent.OnProviderRefresh -> reloadProvider()
            is HomeUiEvent.OnShowHomeManagement -> {
                updateState { copy(showHomeManagement = event.show) }
            }
            is HomeUiEvent.OnToggleCatalog -> {
                val currentDisabledMap = uiState.value.disabledCatalogs.toMutableMap()
                val currentDisabled = currentDisabledMap[event.providerName] ?: emptySet()
                val newDisabled = if (event.isEnabled) {
                    currentDisabled - event.catalogName
                } else {
                    currentDisabled + event.catalogName
                }
                currentDisabledMap[event.providerName] = newDisabled
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey("disabled_catalogs_${event.providerName}", newDisabled)
                }
                updateState { copy(disabledCatalogs = currentDisabledMap) }
            }
        }
    }

    private fun updateProviders() {
        val currentProviders = APIHolder.allProviders.filter { it.isRealProvider() }
        val currentProvState = uiState.value.providers
        if (currentProviders.size != currentProvState.size || !currentProviders.containsAll(currentProvState)) {
            val currentActive = uiState.value.activeProviders
            val validActive = currentActive.filter { active -> currentProviders.any { it.matchesKey(active) } }

            if (validActive.isNotEmpty()) {
                updateState { copy(providers = currentProviders, activeProviders = validActive) }
            } else if (currentProviders.isNotEmpty()) {
                val restored = DesktopDataStore.getKey<List<String>>(PREF_ACTIVE_PROVIDERS)?.filter { active -> currentProviders.any { it.matchesKey(active) } }
                if (!restored.isNullOrEmpty()) {
                    updateState { copy(providers = currentProviders, activeProviders = restored) }
                } else {
                    val fallbackOld = DesktopDataStore.getKey<String>("preferred_provider_name")
                    if (fallbackOld != null && currentProviders.any { it.matchesKey(fallbackOld) }) {
                        updateState { copy(providers = currentProviders, activeProviders = listOf(fallbackOld)) }
                    } else {
                        updateState { copy(providers = currentProviders, activeProviders = listOf(currentProviders.first().getProviderKey())) }
                    }
                }
            } else {
                updateState { copy(providers = currentProviders) }
            }
        }
    }

    private fun updateHistory() {
        val all = DesktopDataStore.getAllWatchHistory()
        val grouped = all.groupBy { it.parentId }
        val newHistory = grouped.mapNotNull { (_, histories) ->
            val inProgressOrQueued = histories.filter {
                val isCompleted = it.duration > 0L && com.lagradost.player.impl.PlayerLinkHandler.isCompleted(it.position, it.duration)
                !isCompleted
            }.maxByOrNull { it.updateTime }

            if (inProgressOrQueued != null) {
                inProgressOrQueued
            } else {
                val latestCompleted = histories.maxByOrNull { it.updateTime }
                if (latestCompleted != null && (latestCompleted.episode != null || latestCompleted.season != null)) {
                    latestCompleted.copy(
                        episode = (latestCompleted.episode ?: 0) + 1,
                        position = 0L,
                        duration = 0L,
                        screenshotUrl = null,
                        episodeThumbnailUrl = null,
                    )
                } else {
                    null
                }
            }
        }.sortedByDescending { it.updateTime }

        updateState { copy(historyList = newHistory) }
        prefetchTopHistory(newHistory.take(3))
    }

    private fun prefetchTopHistory(topHistory: List<com.lagradost.common.storage.WatchHistory>) {
        if (topHistory.isEmpty()) return
        viewModelScope.launch {
            com.lagradost.cloudstream3.desktop.repo.HeroRepository.prefetchTopHistory(topHistory, uiState.value.providers)
        }
    }

    private fun prefetchHeroItem(provider: MainAPI?, item: SearchResponse) {
        viewModelScope.launch {
            com.lagradost.cloudstream3.desktop.repo.HeroRepository.prefetchHeroItem(provider, item)
                .collect { update ->
                    if (update is HeroUpdate.Meta) {
                        updateState {
                            copy(heroMetaMap = heroMetaMap.toMutableMap().apply { put(update.url, update.meta) })
                        }
                    }
                }
        }
    }

    private fun reloadIcons() {
        viewModelScope.launch(Dispatchers.IO) {
            val icons = DesktopRepositoryManager.remotePluginIcons.value
            updateState { copy(mergedPluginIcons = icons) }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.clearAllWatchHistory()
        }
        updateState { copy(historyList = emptyList()) }
    }

    private fun removeHistoryItem(parentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.removeWatchHistory(parentId)
        }
        updateHistory()
    }

    private fun reloadProvider() {
        com.lagradost.cloudstream3.desktop.ui.screens.home.HomeCategorySectionCache.clear()
        val current = uiState.value.activeProviders
        if (current.isNotEmpty()) {
            viewModelScope.launch {
                updateState { copy(activeProviders = emptyList()) }
                kotlinx.coroutines.delay(50)
                updateState { copy(activeProviders = current) }
            }
        }
    }
}
