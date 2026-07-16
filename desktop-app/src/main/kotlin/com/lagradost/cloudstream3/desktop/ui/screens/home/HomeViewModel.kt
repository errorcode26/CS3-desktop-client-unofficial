package com.lagradost.cloudstream3.desktop.ui.screens.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.newMovieLoadResponse

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

class HomeViewModel(private val coroutineScope: CoroutineScope) {
    val providers = MutableStateFlow<List<MainAPI>>(emptyList())
    val selectedProviderName = MutableStateFlow<String?>(null)

    val selectedProvider: StateFlow<MainAPI?> = combine(providers, selectedProviderName) { provs, name ->
        provs.firstOrNull { it.name == name }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, null)

    val searchResultsGrouped = MutableStateFlow<List<Pair<MainAPI, List<SearchResponse>>>?>(null)
    val isLoadingSearch = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val isGlobalSearchEnabled = MutableStateFlow(false)
    val errorSnapshot = MutableStateFlow(DesktopErrorReporter.getSnapshot())

    val historyList = MutableStateFlow<List<com.lagradost.common.storage.WatchHistory>>(emptyList())
    val mergedPluginIcons = MutableStateFlow<Map<String, String>>(emptyMap())

    val heroMetaMap = MutableStateFlow<Map<String, HeroMeta>>(emptyMap())

    init {
        // Initialize providers
        updateProviders()

        // Load saved provider preference
        val savedName = DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER)
        if (savedName != null && APIHolder.allProviders.any { it.name == savedName && it.isRealProvider() }) {
            selectedProviderName.value = savedName
        }

        // Save selected provider when it changes
        coroutineScope.launch {
            selectedProviderName.collect { name ->
                if (!name.isNullOrBlank()) {
                    DesktopDataStore.setKey(PREF_SELECTED_PROVIDER, name)
                } else {
                    DesktopDataStore.removeKey(PREF_SELECTED_PROVIDER)
                }
            }
        }

        // Initialize and persist global search toggle
        isGlobalSearchEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_GLOBAL_SEARCH) ?: false
        coroutineScope.launch {
            isGlobalSearchEnabled.collect { enabled ->
                DesktopDataStore.setKey(PREF_GLOBAL_SEARCH, enabled)
            }
        }

        // Poll for provider updates
        coroutineScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                updateProviders()
            }
        }

        // Auto-search on typing
        coroutineScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            searchQuery.debounce(500)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        searchResultsGrouped.value = null
                    } else {
                        search()
                    }
                }
        }

        // Listen for sync generation updates
        coroutineScope.launch {
            DesktopRepositoryManager.syncGeneration.collect { syncGen ->
                if (syncGen > 0) {
                    updateProviders()
                    reloadIcons()
                }
            }
        }

        // Handle history updates
        coroutineScope.launch {
            combine(selectedProvider, DesktopDataStore.historyUpdates) { _, _ -> }.collect {
                updateHistory()
            }
        }

        // Listen for global provider refresh
        coroutineScope.launch {
            com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceProviderRefresh.collect { value ->
                if (value > 0) reloadProvider()
            }
        }

        updateHistory()
        reloadIcons()
    }

    private fun updateProviders() {
        val currentProviders = APIHolder.allProviders.filter { it.isRealProvider() }
        if (currentProviders.size != providers.value.size) {
            providers.value = currentProviders
            if (selectedProviderName.value == null && currentProviders.isNotEmpty()) {
                val restored = currentProviders.firstOrNull { it.name == DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER) }
                if (restored != null) {
                    selectedProviderName.value = restored.name
                }
            }
        }
    }

    private fun updateHistory() {
        val newHistory = DesktopDataStore.getAllWatchHistory()
            .filter { it.duration >= 30L && (it.position * 100 / it.duration) > 1L }
            .sortedByDescending { it.updateTime }
            .distinctBy { it.parentId }
            
        historyList.value = newHistory
        prefetchTopHistory(newHistory.take(3))
    }

    private fun prefetchTopHistory(topHistory: List<com.lagradost.common.storage.WatchHistory>) {
        if (topHistory.isEmpty()) return
        coroutineScope.launch(Dispatchers.IO) {
            for (history in topHistory) {
                val provider = providers.value.find { it.name == history.apiName }
                if (provider != null && !com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.cache.containsKey(history.showUrl)) {
                    try {
                        val raw = com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.fetchRaw(provider, history.showUrl)
                        if (raw != null) {
                            com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.enrich(raw, history.showUrl) {}
                        }
                    } catch (e: Exception) {
                        com.lagradost.common.logging.AppLogger.e("HomeScreen", "Failed to prefetch history item", e)
                    }
                }
            }
        }
    }

    fun prefetchHeroItem(provider: MainAPI?, item: SearchResponse) {
        val cacheKey = "${provider?.name}_${item.url}"
        if (heroMetaMap.value.containsKey(item.url)) return

        val existing = HeroCache.cache[cacheKey]
        if (existing != null) {
            heroMetaMap.update { it + (item.url to existing) }
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
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

                    com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.enrich(dummy, "dummy_${item.url}") {}

                    val backdropUrl = dummy.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                    val logoUrl = dummy.logoUrl?.takeIf { it.isNotBlank() }
                    val title = dummy.name.takeIf { it.isNotBlank() && it != dummyTitle } ?: cleanHeroTitle(item.name)
                    val tags = dummy.tags?.take(4) ?: emptyList()
                    val plot = dummy.plot?.take(200)
                    val score = dummy.score?.toString() // Fallback if toStringNull isn't strictly available here

                    val meta = HeroMeta(title, backdropUrl, logoUrl, tags, plot, score, dummy.year, dummy.type)
                    HeroCache.cache[cacheKey] = meta
                    heroMetaMap.update { it + (item.url to meta) }
                } else {
                    val meta = HeroMeta(dummyTitle, null, null, emptyList(), null, null, null, null)
                    HeroCache.cache[cacheKey] = meta
                    heroMetaMap.update { it + (item.url to meta) }
                }

                // SLOW PATH
                kotlinx.coroutines.delay(1500)
                if (provider != null) {
                    val details = if (!com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.cache.containsKey(item.url)) {
                        com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.fetchRaw(provider, item.url)
                    } else {
                        com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.cache[item.url]
                    }

                    if (details != null) {
                        com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.enrich(details, item.url) {}

                        val currentMeta = heroMetaMap.value[item.url]
                        val newTitle = details.name.takeIf { it.isNotBlank() } ?: currentMeta?.title
                        val newBackdrop = details.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: currentMeta?.backdropUrl
                        val newLogo = details.logoUrl?.takeIf { it.isNotBlank() } ?: currentMeta?.logoUrl
                        val newTags = details.tags?.takeIf { it.isNotEmpty() } ?: currentMeta?.tags ?: emptyList()
                        val newPlot = details.plot?.takeIf { it.isNotBlank() } ?: currentMeta?.plot
                        val newScore = details.score?.toString() ?: currentMeta?.score
                        val newYear = details.year ?: currentMeta?.year
                        val newType = details.type ?: currentMeta?.type

                        val finalMeta = HeroMeta(newTitle, newBackdrop, newLogo, newTags, newPlot, newScore, newYear, newType)
                        HeroCache.cache[cacheKey] = finalMeta
                        heroMetaMap.update { it + (item.url to finalMeta) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore background errors
            }
        }
    }

    private fun reloadIcons() {
        coroutineScope.launch(Dispatchers.IO) {
            mergedPluginIcons.value = DesktopRepositoryManager.remotePluginIcons.value
        }
    }

    fun search() {
        val query = searchQuery.value
        if (query.isBlank()) return

        coroutineScope.launch {
            isLoadingSearch.value = true
            searchResultsGrouped.value = emptyList()
            try {
                val activeProviders = if (isGlobalSearchEnabled.value) {
                    providers.value.filter { it.hasMainPage || it.supportedTypes.isNotEmpty() }
                } else {
                    selectedProvider.value?.let { listOf(it) } ?: emptyList()
                }

                val resultsArray = Array<Pair<MainAPI, List<SearchResponse>>?>(activeProviders.size) { null }

                withContext(Dispatchers.IO) {
                    activeProviders.forEachIndexed { index, p ->
                        launch {
                            try {
                                val res = p.search(query, 1)
                                if (res != null && res.items.isNotEmpty()) {
                                    resultsArray[index] = Pair(p, res.items)
                                    searchResultsGrouped.value = resultsArray.filterNotNull()
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
                isLoadingSearch.value = false
            }
        }
    }

    fun clearHistory() {
        DesktopDataStore.clearAllWatchHistory()
        historyList.value = emptyList()
    }

    fun removeHistoryItem(parentId: String) {
        DesktopDataStore.removeWatchHistory(parentId)
        updateHistory()
    }

    fun refreshErrorSnapshot() {
        errorSnapshot.value = DesktopErrorReporter.getSnapshot()
    }

    fun reloadProvider() {
        val current = selectedProviderName.value
        if (current != null) {
            coroutineScope.launch {
                selectedProviderName.value = null
                kotlinx.coroutines.delay(10)
                selectedProviderName.value = current
            }
        }
    }
}
