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

import com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository

const val PREF_ACTIVE_PROVIDERS = "home_active_providers"

/**
 * Returns true only for real, user-facing content providers:
 * - Excludes built-in MetaProviders (Trakt, TMDB, CrossTMDB)
 * - Excludes "NONE"
 */
fun MainAPI.isRealProvider(): Boolean = ActiveProviderRepository.isRealContentProvider(this)

class DesktopHomeViewModel : BaseMviViewModel<HomeUiState, HomeUiEvent, HomeUiEffect>(
    initialState = HomeUiState(),
) {

    // Redundant StateFlow mappings have been permanently deleted in accordance with MVI best practices.
    // UI should collect `uiState` and read properties directly from the immutable snapshot.

    init {
        viewModelScope.launch {
            BookmarksRepository.bookmarksFlow.collect { bookmarks ->
                updateState { copy(bookmarks = bookmarks) }
            }
        }

        // Reactively observe providers from single source of truth
        viewModelScope.launch {
            ActiveProviderRepository.allRealProviders.collectLatest { realProviders ->
                updateState { copy(providers = realProviders) }
            }
        }

        viewModelScope.launch {
            ActiveProviderRepository.activeProviders.collectLatest { activeApis ->
                val keys = activeApis.map { ActiveProviderRepository.getProviderKey(it) }
                updateState {
                    copy(
                        activeProviderApis = activeApis,
                        activeProviders = keys,
                    )
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            uiState.map { it.activeProviders }.distinctUntilChanged().collect { names ->
                val disabledMap = names.associateWith { name ->
                    DesktopDataStore.getKey<Set<String>>("disabled_catalogs_$name") ?: emptySet()
                }
                updateState { copy(disabledCatalogs = disabledMap) }
            }
        }

        viewModelScope.launch {
            DesktopRepositoryManager.syncGeneration.collect { syncGen ->
                if (syncGen > 0) {
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
                ActiveProviderRepository.setActiveProviders(current)
            }
            is HomeUiEvent.OnSetSingleProvider -> {
                ActiveProviderRepository.setActiveProviders(listOf(event.providerName))
            }
            is HomeUiEvent.OnMoveProvider -> {
                val current = uiState.value.activeProviders.toMutableList()
                if (event.fromIndex in current.indices && event.toIndex in current.indices) {
                    val item = current.removeAt(event.fromIndex)
                    current.add(event.toIndex, item)
                }
                ActiveProviderRepository.setActiveProviders(current)
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
            val currentApis = ActiveProviderRepository.activeProviders.value
            val currentKeys = uiState.value.activeProviders
            if (currentApis.isNotEmpty()) {
                updateState { copy(activeProviders = emptyList(), activeProviderApis = emptyList()) }
                kotlinx.coroutines.delay(50)
                updateState {
                    copy(
                        activeProviders = currentKeys,
                        activeProviderApis = currentApis,
                    )
                }
            }
        }
    }
}
