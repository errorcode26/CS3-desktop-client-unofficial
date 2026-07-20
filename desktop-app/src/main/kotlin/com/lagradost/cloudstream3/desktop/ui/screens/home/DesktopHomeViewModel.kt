package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.common.logging.AppLogger
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
    initialState = HomeUiState()
) {
    private val prefetchingUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    val providers: StateFlow<List<MainAPI>> = uiState.map { it.providers }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.providers)

    val selectedProviderName: StateFlow<String?> = uiState.map { it.selectedProviderName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.selectedProviderName)

    val selectedProvider: StateFlow<MainAPI?> = uiState.map { it.selectedProvider }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.selectedProvider)

    val searchResultsGrouped: StateFlow<List<Pair<MainAPI, List<SearchResponse>>>?> = uiState.map { it.searchResultsGrouped }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.searchResultsGrouped)

    val isLoadingSearch: StateFlow<Boolean> = uiState.map { it.isLoadingSearch }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.isLoadingSearch)

    val searchQuery: StateFlow<String> = uiState.map { it.searchQuery }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.searchQuery)

    val isGlobalSearchEnabled: StateFlow<Boolean> = uiState.map { it.isGlobalSearchEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.isGlobalSearchEnabled)

    val errorSnapshot: StateFlow<String> = uiState.map { it.errorSnapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.errorSnapshot)

    val historyList: StateFlow<List<com.lagradost.common.storage.WatchHistory>> = uiState.map { it.historyList }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.historyList)

    val mergedPluginIcons: StateFlow<Map<String, String>> = uiState.map { it.mergedPluginIcons }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.mergedPluginIcons)

    val heroMetaMap: StateFlow<Map<String, HeroMeta>> = uiState.map { it.heroMetaMap }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.heroMetaMap)

    val heroExtractedColor: StateFlow<Color?> = uiState.map { it.heroExtractedColor }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.heroExtractedColor)

    val heroColorMap: StateFlow<Map<String, Color>> = uiState.map { it.heroColorMap }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.heroColorMap)

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
            selectedProviderName.collect { name ->
                if (!name.isNullOrBlank()) {
                    DesktopDataStore.setKey(PREF_SELECTED_PROVIDER, name)
                } else {
                    DesktopDataStore.removeKey(PREF_SELECTED_PROVIDER)
                }
            }
        }

        updateState { copy(isGlobalSearchEnabled = DesktopDataStore.getKey<Boolean>(PREF_GLOBAL_SEARCH) ?: false) }
        viewModelScope.launch {
            isGlobalSearchEnabled.collect { enabled ->
                DesktopDataStore.setKey(PREF_GLOBAL_SEARCH, enabled)
            }
        }

        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                updateProviders()
            }
        }

        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            searchQuery.debounce(500)
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
            combine(selectedProvider, DesktopDataStore.historyUpdates) { _, _ -> }.collect {
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
        }
    }

    fun updateHeroColor(imageUrl: String?, itemUrl: String? = null) {
        if (imageUrl == null) {
            if (itemUrl == null) updateState { copy(heroExtractedColor = null) }
            return
        }
        com.lagradost.cloudstream3.desktop.utils.ImageColorExtractor.getCachedColor(imageUrl)?.let { cached ->
            if (itemUrl == null) {
                updateState { copy(heroExtractedColor = cached) }
            } else {
                updateState { copy(heroColorMap = heroColorMap + (itemUrl to cached)) }
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val dominant = com.lagradost.cloudstream3.desktop.utils.ImageColorExtractor.extractDominantColorFromUrl(imageUrl) ?: return@launch
            if (itemUrl == null) {
                updateState { copy(heroExtractedColor = dominant) }
            } else {
                updateState { copy(heroColorMap = heroColorMap + (itemUrl to dominant)) }
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
        viewModelScope.launch(Dispatchers.IO) {
            for (history in topHistory) {
                val provider = uiState.value.providers.find { it.name == history.apiName }
                if (provider != null && !com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCache.containsKey(history.showUrl)) {
                    try {
                        val raw = com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsRepository.fetchRaw(provider, history.showUrl)
                        if (raw != null) {
                            com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService.enrich(raw, history.showUrl, onScreenshotsLoaded = {})
                        }
                    } catch (e: Exception) {
                        AppLogger.e("HomeScreen", "Failed to prefetch history item", e)
                    }
                }
            }
        }
    }

    fun prefetchHeroItem(provider: MainAPI?, item: SearchResponse) {
        val cacheKey = "${provider?.name}_${item.url}"
        if (uiState.value.heroMetaMap.containsKey(item.url)) return

        val existing = HeroCache.get(cacheKey)
        if (existing != null) {
            updateState { copy(heroMetaMap = heroMetaMap + (item.url to existing)) }
            return
        }

        if (!prefetchingUrls.add(cacheKey)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dummyTitle = cleanHeroTitle(item.name)

                if (provider != null) {
                    val dummy = provider.newMovieLoadResponse(
                        name = dummyTitle,
                        url = item.url,
                        type = com.lagradost.cloudstream3.TvType.Movie,
                        dataUrl = item.url,
                    ) {
                        this.posterUrl = item.posterUrl
                    }

                    com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService.enrich(dummy, "dummy_${item.url}", onScreenshotsLoaded = {})

                    val backdropUrl = dummy.backgroundPosterUrl?.takeIf { it.isNotBlank() }?.let { provider.fixUrlNull(it) }
                    val logoUrl = dummy.logoUrl?.takeIf { it.isNotBlank() }?.let { provider.fixUrlNull(it) }
                    val title = dummy.name.takeIf { it.isNotBlank() && it != dummyTitle } ?: cleanHeroTitle(item.name)
                    val tags = dummy.tags?.take(4) ?: emptyList()
                    val plot = dummy.plot?.take(200)
                    val score = dummy.score?.toString()

                    val meta = HeroMeta(title, backdropUrl, logoUrl, tags, plot, score, dummy.year, dummy.type, dummy.contentRating, dummy.duration)
                    HeroCache.put(cacheKey, meta)
                    updateState { copy(heroMetaMap = heroMetaMap + (item.url to meta)) }
                    updateHeroColor(backdropUrl ?: provider.fixUrlNull(item.posterUrl), itemUrl = item.url)
                } else {
                    val meta = HeroMeta(dummyTitle, null, null, emptyList(), null, null, null, null, null, null)
                    HeroCache.put(cacheKey, meta)
                    updateState { copy(heroMetaMap = heroMetaMap + (item.url to meta)) }
                }

                if (provider != null) {
                    var details: com.lagradost.cloudstream3.LoadResponse? = null
                    var attempt = 0
                    while (attempt < 3 && details == null) {
                        try {
                            kotlinx.coroutines.delay(if (attempt == 0) 1500L else 2000L)
                            details = if (!com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCache.containsKey(item.url)) {
                                com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsRepository.fetchRaw(provider, item.url)
                            } else {
                                com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCache.get(item.url)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            attempt++
                            if (attempt >= 3) throw e
                        }
                    }

                    if (details != null) {
                        val currentMeta = uiState.value.heroMetaMap[item.url]
                        val cleanDetailsName = cleanHeroTitle(details.name)
                        val newTitle = cleanDetailsName.takeIf { it.isNotBlank() } ?: currentMeta?.title
                        val newBackdrop = details.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: currentMeta?.backdropUrl
                        val newLogo = details.logoUrl?.takeIf { it.isNotBlank() } ?: currentMeta?.logoUrl
                        val newTags = details.tags?.takeIf { it.isNotEmpty() } ?: currentMeta?.tags ?: emptyList()
                        val newPlot = details.plot?.takeIf { it.isNotBlank() } ?: currentMeta?.plot
                        val newScore = details.score?.toString() ?: currentMeta?.score
                        val newYear = details.year ?: currentMeta?.year
                        val newType = details.type ?: currentMeta?.type
                        val newContentRating = details.contentRating?.takeIf { it.isNotBlank() } ?: currentMeta?.contentRating
                        val newDuration = details.duration ?: currentMeta?.duration

                        val rawMeta = HeroMeta(newTitle, newBackdrop, newLogo, newTags, newPlot, newScore, newYear, newType, newContentRating, newDuration)
                        HeroCache.put(cacheKey, rawMeta)
                        updateState { copy(heroMetaMap = heroMetaMap + (item.url to rawMeta)) }
                        if (newBackdrop != null) updateHeroColor(newBackdrop, itemUrl = item.url)

                        com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService.enrich(
                            loaded = details,
                            url = item.url,
                            onScreenshotsLoaded = {},
                            onEnrichmentComplete = {
                                val enrichedMeta = uiState.value.heroMetaMap[item.url] ?: rawMeta
                                val finalMeta = enrichedMeta.copy(
                                    title = cleanHeroTitle(details.name).takeIf { it.isNotBlank() } ?: enrichedMeta.title,
                                    backdropUrl = details.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: enrichedMeta.backdropUrl,
                                    logoUrl = details.logoUrl?.takeIf { it.isNotBlank() } ?: enrichedMeta.logoUrl,
                                )
                                HeroCache.put(cacheKey, finalMeta)
                                updateState { copy(heroMetaMap = heroMetaMap + (item.url to finalMeta)) }
                                if (finalMeta.backdropUrl != null) updateHeroColor(finalMeta.backdropUrl, itemUrl = item.url)
                            },
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                HeroCache.remove(cacheKey)
                updateState { copy(heroMetaMap = heroMetaMap - item.url) }
            } finally {
                prefetchingUrls.remove(cacheKey)
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
        prefetchingUrls.clear()
    }
}
