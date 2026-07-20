package com.lagradost.cloudstream3.desktop.ui.screens.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
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
import kotlinx.coroutines.withContext

const val PREF_SELECTED_PROVIDER = "preferred_provider_name"
const val PREF_GLOBAL_SEARCH = "global_search_enabled"

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

typealias HomeUiState = com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState

class DesktopHomeViewModel : BaseMviViewModel<HomeUiState, HomeUiEvent, HomeUiEffect>(
    initialState = HomeUiState(),
) {
    private val prefetchingUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Redundant StateFlow mappings have been permanently deleted in accordance with MVI best practices.
    // UI should collect `uiState` and read properties directly from the immutable snapshot.

    init {
        updateProviders()

        viewModelScope.launch {
            BookmarksRepository.bookmarksFlow.collect { bookmarks ->
                updateState { copy(bookmarks = bookmarks) }
            }
        }

        val savedName = DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER)
        if (savedName != null && APIHolder.allProviders.any { it.name == savedName && it.isRealProvider() }) {
            updateState { copy(selectedProviderName = savedName) }
        }

        viewModelScope.launch {
            uiState.map { it.selectedProviderName }.distinctUntilChanged().collect { name ->
                if (!name.isNullOrBlank()) {
                    DesktopDataStore.setKey(PREF_SELECTED_PROVIDER, name)
                } else {
                    DesktopDataStore.removeKey(PREF_SELECTED_PROVIDER)
                }
            }
        }

        updateState { copy(isGlobalSearchEnabled = DesktopDataStore.getKey<Boolean>(PREF_GLOBAL_SEARCH) ?: false) }
        viewModelScope.launch {
            uiState.map { it.isGlobalSearchEnabled }.distinctUntilChanged().collect { enabled ->
                DesktopDataStore.setKey(PREF_GLOBAL_SEARCH, enabled)
            }
        }

        // The while(true) polling loop was successfully exterminated.
        // We now rely solely on `DesktopRepositoryManager.syncGeneration` (below) to update providers.

        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            uiState.map { it.searchQuery }.debounce(500)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        updateState { copy(searchResultsGrouped = null) }
                    } else {
                        search()
                    }
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
            combine(uiState.map { it.selectedProvider }, DesktopDataStore.historyUpdates) { _, _ -> }.collect {
                updateHistory()
            }
        }

        updateHistory()
        reloadIcons()
    }

    override fun handleEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.OnSearchQueryChange -> setSearchQuery(event.query)
            is HomeUiEvent.OnSearch -> search()
            is HomeUiEvent.OnClearSearch -> {
                setSearchQuery("")
                clearSearchResults()
            }
            is HomeUiEvent.OnSelectProvider -> {
                setSelectedProvider(event.providerName)
                clearSearchResults()
            }
            is HomeUiEvent.OnClearHistory -> clearHistory()
            is HomeUiEvent.OnRemoveHistoryItem -> removeHistoryItem(event.parentId)
            is HomeUiEvent.OnPrefetchHeroItem -> prefetchHeroItem(event.provider, event.item)
            is HomeUiEvent.OnSetCurrentHeroColor -> setCurrentHeroColor(event.itemUrl)
            is HomeUiEvent.OnUpdateHeroColor -> updateHeroColor(event.imageUrl, event.itemUrl)
            is HomeUiEvent.OnProviderRefresh -> reloadProvider()
        }
    }

    fun updateHeroColor(imageUrl: String?, itemUrl: String? = null) {
        if (imageUrl == null) {
            if (itemUrl == null) updateState { copy(heroExtractedColor = null) }
            return
        }
        viewModelScope.launch {
            val colorLong = com.lagradost.cloudstream3.desktop.repo.HeroRepository.getHeroColor(imageUrl) ?: return@launch
            val color = androidx.compose.ui.graphics.Color(colorLong.toULong())
            if (itemUrl == null) {
                updateState { copy(heroExtractedColor = color) }
            } else {
                updateState { copy(heroColorMap = heroColorMap + (itemUrl to color)) }
            }
        }
    }

    fun setCurrentHeroColor(itemUrl: String?) {
        if (itemUrl != null) {
            uiState.value.heroColorMap[itemUrl]?.let { color ->
                updateState { copy(heroExtractedColor = color) }
            }
        }
    }

    private fun updateProviders() {
        val currentProviders = APIHolder.allProviders.filter { it.isRealProvider() }
        val currentProvState = uiState.value.providers
        if (currentProviders.size != currentProvState.size || !currentProviders.containsAll(currentProvState)) {
            val currentSelection = uiState.value.selectedProviderName
            if (currentSelection != null && currentProviders.none { it.name == currentSelection }) {
                updateState { copy(providers = currentProviders, selectedProviderName = currentProviders.firstOrNull()?.name, searchResultsGrouped = null) }
            } else if (currentSelection == null && currentProviders.isNotEmpty()) {
                val restored = currentProviders.firstOrNull { it.name == DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER) }
                val targetName = restored?.name
                updateState { copy(providers = currentProviders, selectedProviderName = targetName ?: selectedProviderName) }
            } else {
                updateState { copy(providers = currentProviders) }
            }
        }
    }

    private fun updateHistory() {
        val newHistory = DesktopDataStore.getAllWatchHistory()
            .filter { it.duration >= 30L && (it.position * 100 / it.duration) > 1L }
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
                    when (update) {
                        is HeroUpdate.Meta -> {
                            updateState {
                                copy(heroMetaMap = heroMetaMap.toMutableMap().apply { put(update.url, update.meta) })
                            }
                        }
                        is HeroUpdate.ColorTarget -> {
                            updateHeroColor(update.posterUrl, itemUrl = update.url)
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

    fun search() {
        val query = uiState.value.searchQuery
        if (query.isBlank()) return

        viewModelScope.launch {
            updateState { copy(isLoadingSearch = true, searchResultsGrouped = emptyList()) }
            try {
                val activeProviders = if (uiState.value.isGlobalSearchEnabled) {
                    uiState.value.providers.filter { it.hasMainPage || it.supportedTypes.isNotEmpty() }
                } else {
                    uiState.value.selectedProvider?.let { listOf(it) } ?: emptyList()
                }

                val resultsArray = Array<Pair<MainAPI, List<SearchResponse>>?>(activeProviders.size) { null }

                withContext(Dispatchers.IO) {
                    activeProviders.forEachIndexed { index, p ->
                        launch {
                            try {
                                val res = p.search(query, 1)
                                if (res != null && res.items.isNotEmpty()) {
                                    resultsArray[index] = Pair(p, res.items)
                                    val nonNull = resultsArray.filterNotNull()
                                    updateState { copy(searchResultsGrouped = nonNull) }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                DesktopErrorReporter.report("Search provider ${p.name} failed", e)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                DesktopErrorReporter.report("Search failed", e)
            } finally {
                updateState { copy(isLoadingSearch = false) }
            }
        }
    }

    fun clearHistory() {
        DesktopDataStore.clearAllWatchHistory()
        updateState { copy(historyList = emptyList()) }
    }

    fun removeHistoryItem(parentId: String) {
        DesktopDataStore.removeWatchHistory(parentId)
        updateHistory()
    }

    fun refreshErrorSnapshot() {
        updateState { copy(errorSnapshot = DesktopErrorReporter.getSnapshot()) }
    }

    fun reloadProvider() {
        val current = uiState.value.selectedProviderName
        if (current != null) {
            viewModelScope.launch {
                updateState { copy(selectedProviderName = null) }
                kotlinx.coroutines.delay(10)
                updateState { copy(selectedProviderName = current) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        updateState { copy(searchQuery = query) }
    }

    fun clearSearchResults() {
        updateState { copy(searchResultsGrouped = null) }
    }

    fun setSelectedProvider(name: String?) {
        updateState { copy(selectedProviderName = name) }
    }

    override fun dispose() {
        super.dispose()
    }
}
