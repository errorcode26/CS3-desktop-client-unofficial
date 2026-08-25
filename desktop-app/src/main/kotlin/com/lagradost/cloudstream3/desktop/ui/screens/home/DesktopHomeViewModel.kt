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

private fun computeActiveProviderApis(activeProviders: List<String>, providers: List<MainAPI>): List<MainAPI> {
    return activeProviders.mapNotNull { key ->
        providers.firstOrNull { p ->
            val pKey = if (p.sourcePlugin != null && p.sourcePlugin != "built-in") {
                "${java.io.File(p.sourcePlugin).parentFile?.name ?: ""}::${p.name}"
            } else {
                p.name
            }
            pKey == key || p.name == key
        }
    }
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
                updateState {
                    copy(
                        activeProviders = validNames,
                        activeProviderApis = computeActiveProviderApis(validNames, providers),
                    )
                }
            } else {
                // Fallback to old key or empty
                val oldName = DesktopDataStore.getKey<String>("preferred_provider_name")
                if (oldName != null && APIHolder.allProviders.any { it.matchesKey(oldName) && it.isRealProvider() }) {
                    val list = listOf(oldName)
                    updateState {
                        copy(
                            activeProviders = list,
                            activeProviderApis = computeActiveProviderApis(list, providers),
                        )
                    }
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
                updateState {
                    val current = activeProviders.toMutableList()
                    if (event.isActive) {
                        if (!current.contains(event.providerName)) current.add(event.providerName)
                    } else {
                        current.remove(event.providerName)
                    }
                    copy(
                        activeProviders = current,
                        activeProviderApis = computeActiveProviderApis(current, providers),
                    )
                }
            }
            is HomeUiEvent.OnSetSingleProvider -> {
                updateState {
                    val list = listOf(event.providerName)
                    copy(
                        activeProviders = list,
                        activeProviderApis = computeActiveProviderApis(list, providers),
                    )
                }
            }
            is HomeUiEvent.OnMoveProvider -> {
                updateState {
                    val current = activeProviders.toMutableList()
                    if (event.fromIndex in current.indices && event.toIndex in current.indices) {
                        val item = current.removeAt(event.fromIndex)
                        current.add(event.toIndex, item)
                    }
                    copy(
                        activeProviders = current,
                        activeProviderApis = computeActiveProviderApis(current, providers),
                    )
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
                updateState {
                    val currentDisabled = disabledCatalogs[event.providerName] ?: emptySet()
                    val newDisabled = if (event.isEnabled) {
                        currentDisabled - event.catalogName
                    } else {
                        currentDisabled + event.catalogName
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        DesktopDataStore.setKey("disabled_catalogs_${event.providerName}", newDisabled)
                    }
                    copy(disabledCatalogs = disabledCatalogs + (event.providerName to newDisabled))
                }
            }
        }
    }

    private fun updateProviders() {
        val currentProviders = APIHolder.allProviders.filter { it.isRealProvider() }
        updateState {
            if (currentProviders.size != providers.size || !currentProviders.containsAll(providers)) {
                val validActive = activeProviders.filter { active -> currentProviders.any { it.matchesKey(active) } }

                if (validActive.isNotEmpty()) {
                    copy(
                        providers = currentProviders,
                        activeProviders = validActive,
                        activeProviderApis = computeActiveProviderApis(validActive, currentProviders),
                    )
                } else if (currentProviders.isNotEmpty()) {
                    val restored = DesktopDataStore.getKey<List<String>>(PREF_ACTIVE_PROVIDERS)?.filter { active -> currentProviders.any { it.matchesKey(active) } }
                    if (!restored.isNullOrEmpty()) {
                        copy(
                            providers = currentProviders,
                            activeProviders = restored,
                            activeProviderApis = computeActiveProviderApis(restored, currentProviders),
                        )
                    } else {
                        val fallbackOld = DesktopDataStore.getKey<String>("preferred_provider_name")
                        val fallbackActive = if (fallbackOld != null && currentProviders.any { it.matchesKey(fallbackOld) }) {
                            listOf(fallbackOld)
                        } else {
                            listOf(currentProviders.first().getProviderKey())
                        }
                        copy(
                            providers = currentProviders,
                            activeProviders = fallbackActive,
                            activeProviderApis = computeActiveProviderApis(fallbackActive, currentProviders),
                        )
                    }
                } else {
                    copy(
                        providers = currentProviders,
                        activeProviders = emptyList(),
                        activeProviderApis = emptyList(),
                    )
                }
            } else {
                this
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
                            copy(heroMetaMap = heroMetaMap + (update.url to update.meta))
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
        viewModelScope.launch {
            val current = uiState.value.activeProviders
            if (current.isNotEmpty()) {
                updateState { copy(activeProviders = emptyList(), activeProviderApis = emptyList()) }
                kotlinx.coroutines.delay(50)
                updateState {
                    copy(
                        activeProviders = current,
                        activeProviderApis = computeActiveProviderApis(current, providers),
                    )
                }
            }
        }
    }
}
