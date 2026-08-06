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
                val validNames = savedNames.filter { name -> APIHolder.allProviders.any { it.name == name && it.isRealProvider() } }
                updateState { copy(activeProviders = validNames) }
            } else {
                // Fallback to old key or empty
                val oldName = DesktopDataStore.getKey<String>("preferred_provider_name")
                if (oldName != null && APIHolder.allProviders.any { it.name == oldName && it.isRealProvider() }) {
                    updateState { copy(activeProviders = listOf(oldName)) }
                }
            }
        }

        viewModelScope.launch {
            uiState.map { it.activeProviders }.distinctUntilChanged().collect { names ->
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
            val validActive = currentActive.filter { active -> currentProviders.any { it.name == active } }
            
            if (validActive.isNotEmpty()) {
                updateState { copy(providers = currentProviders, activeProviders = validActive) }
            } else if (currentProviders.isNotEmpty()) {
                val restored = DesktopDataStore.getKey<List<String>>(PREF_ACTIVE_PROVIDERS)?.filter { active -> currentProviders.any { it.name == active } }
                if (!restored.isNullOrEmpty()) {
                    updateState { copy(providers = currentProviders, activeProviders = restored) }
                } else {
                    val fallbackOld = DesktopDataStore.getKey<String>("preferred_provider_name")
                    if (fallbackOld != null && currentProviders.any { it.name == fallbackOld }) {
                        updateState { copy(providers = currentProviders, activeProviders = listOf(fallbackOld)) }
                    } else {
                        updateState { copy(providers = currentProviders, activeProviders = listOf(currentProviders.first().name)) }
                    }
                }
            } else {
                updateState { copy(providers = currentProviders) }
            }
        }
    }

    private fun updateHistory() {
        val newHistory = DesktopDataStore.getAllWatchHistory()
            .filter { 
                // Either it's a partially watched episode
                (it.duration >= 30L && (it.position * 100 / it.duration) > 1L) ||
                // OR it's a queued "Next Episode" (position = 0, duration = 0)
                (it.duration == 0L && it.position == 0L)
            }
            .sortedByDescending { it.updateTime }
            .distinctBy { it.parentId }
            .filter {
                val percentage = if (it.duration > 0) (it.position.toFloat() / it.duration) else 0f
                percentage < 0.90f
            }
        updateState { copy(historyList = newHistory) }
        prefetchTopHistory(newHistory.take(3))
    }

    private fun prefetchTopHistory(topHistory: List<com.lagradost.common.storage.WatchHistory>) {
        if (topHistory.isEmpty()) return
        viewModelScope.launch {
            com.lagradost.cloudstream3.desktop.repo.HeroRepository.prefetchTopHistory(topHistory, uiState.value.providers)
        }
    }

    fun prefetchHeroItem(provider: MainAPI?, item: SearchResponse) {
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

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.clearAllWatchHistory()
        }
        updateState { copy(historyList = emptyList()) }
    }

    fun removeHistoryItem(parentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.removeWatchHistory(parentId)
        }
        updateHistory()
    }

    fun reloadProvider() {
        val current = uiState.value.activeProviders
        if (current.isNotEmpty()) {
            viewModelScope.launch {
                updateState { copy(activeProviders = emptyList()) }
                kotlinx.coroutines.delay(10)
                updateState { copy(activeProviders = current) }
            }
        }
    }

}
