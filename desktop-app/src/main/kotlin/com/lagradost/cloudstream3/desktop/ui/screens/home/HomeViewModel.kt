package com.lagradost.cloudstream3.desktop.ui.screens.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.fixUrlNull
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

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

    // The dominant vibrant color extracted from the currently featured hero image
    val heroExtractedColor = MutableStateFlow<androidx.compose.ui.graphics.Color?>(null)

    // Cache to avoid re-extracting the same URL repeatedly
    private val colorCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.Color>()

    fun updateHeroColor(imageUrl: String?) {
        if (imageUrl == null) {
            heroExtractedColor.value = null
            return
        }
        // Return cached result immediately if available
        colorCache[imageUrl]?.let {
            heroExtractedColor.value = it
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val bytes = app.get(imageUrl).body.bytes()
                val img: BufferedImage = ImageIO.read(bytes.inputStream()) ?: return@launch
                val dominant = sampleDominantColor(img)
                if (dominant != null) {
                    colorCache[imageUrl] = dominant
                    heroExtractedColor.value = dominant
                }
            } catch (e: Exception) {
                AppLogger.w("HeroColor: Failed to extract color from $imageUrl — ${e.message}")
            }
        }
    }

    private fun sampleDominantColor(img: BufferedImage): androidx.compose.ui.graphics.Color? {
        val area = img.width * img.height
        val step = maxOf(1, Math.sqrt(area / 300.0).toInt()) // Sample ~300 pixels evenly across the 2D grid
        val colorBuckets = mutableMapOf<Int, Int>()

        var x = 0
        var pixelCount = 0
        while (x < img.width) {
            var y = 0
            while (y < img.height) {
                val argb = img.getRGB(x, y)
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                // Skip near-white, near-black, and near-gray pixels
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val saturation = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
                val brightness = max / 255f
                if (saturation < 0.25f || brightness < 0.15f || brightness > 0.95f) {
                    y += step
                    pixelCount++
                    continue
                }

                // Quantize to reduce noise: bucket by dividing RGB into 32-step chunks
                val qr = (r / 32) * 32
                val qg = (g / 32) * 32
                val qb = (b / 32) * 32
                val key = (qr shl 16) or (qg shl 8) or qb
                colorBuckets[key] = (colorBuckets[key] ?: 0) + 1
                y += step
                pixelCount++
            }
            x += step
        }

        if (colorBuckets.isEmpty()) return null

        // Pick the most frequently occurring vibrant bucket
        val dominant = colorBuckets.maxByOrNull { it.value }?.key ?: return null
        val r = (dominant shr 16) and 0xFF
        val g = (dominant shr 8) and 0xFF
        val b = dominant and 0xFF
        return androidx.compose.ui.graphics.Color(r, g, b)
    }

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

        // Sync provider data to DesktopUiState for global access
        coroutineScope.launch {
            providers.collect { com.lagradost.cloudstream3.desktop.ui.DesktopUiState.homeProviders.value = it }
        }
        coroutineScope.launch {
            mergedPluginIcons.collect { com.lagradost.cloudstream3.desktop.ui.DesktopUiState.mergedPluginIcons.value = it }
        }
        // Bidirectional sync for selectedProviderName
        coroutineScope.launch {
            selectedProviderName.collect { com.lagradost.cloudstream3.desktop.ui.DesktopUiState.selectedProviderName.value = it }
        }
        coroutineScope.launch {
            com.lagradost.cloudstream3.desktop.ui.DesktopUiState.selectedProviderName.collect { globalName ->
                if (globalName != null && selectedProviderName.value != globalName) {
                    selectedProviderName.value = globalName
                    searchResultsGrouped.value = null
                }
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
                            com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.enrich(raw, history.showUrl, onScreenshotsLoaded = {})
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

                    com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.enrich(dummy, "dummy_${item.url}", onScreenshotsLoaded = {})

                    val backdropUrl = dummy.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                    val logoUrl = dummy.logoUrl?.takeIf { it.isNotBlank() }
                    val title = dummy.name.takeIf { it.isNotBlank() && it != dummyTitle } ?: cleanHeroTitle(item.name)
                    val tags = dummy.tags?.take(4) ?: emptyList()
                    val plot = dummy.plot?.take(200)
                    val score = dummy.score?.toString() // Fallback if toStringNull isn't strictly available here

                    val meta = HeroMeta(title, backdropUrl, logoUrl, tags, plot, score, dummy.year, dummy.type, dummy.contentRating, dummy.duration)
                    HeroCache.cache[cacheKey] = meta
                    heroMetaMap.update { it + (item.url to meta) }
                    
                    // Pre-calculate the dominant color in the background so it's ready instantly
                    updateHeroColor(backdropUrl ?: provider.fixUrlNull(item.posterUrl))
                } else {
                    val meta = HeroMeta(dummyTitle, null, null, emptyList(), null, null, null, null, null, null)
                    HeroCache.cache[cacheKey] = meta
                    heroMetaMap.update { it + (item.url to meta) }
                }

                // SLOW PATH
                if (provider != null) {
                    var details: com.lagradost.cloudstream3.LoadResponse? = null
                    var attempt = 0
                    while (attempt < 3 && details == null) {
                        try {
                            kotlinx.coroutines.delay(if (attempt == 0) 1500L else 2000L)
                            details = if (!com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.cache.containsKey(item.url)) {
                                com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.fetchRaw(provider, item.url)
                            } else {
                                com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.cache[item.url]
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            attempt++
                            if (attempt >= 3) throw e
                        }
                    }

                    if (details != null) {
                        com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache.enrich(details, item.url, onScreenshotsLoaded = {})

                        val currentMeta = heroMetaMap.value[item.url]
                        val newTitle = details.name.takeIf { it.isNotBlank() } ?: currentMeta?.title
                        val newBackdrop = details.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: currentMeta?.backdropUrl
                        val newLogo = details.logoUrl?.takeIf { it.isNotBlank() } ?: currentMeta?.logoUrl
                        val newTags = details.tags?.takeIf { it.isNotEmpty() } ?: currentMeta?.tags ?: emptyList()
                        val newPlot = details.plot?.takeIf { it.isNotBlank() } ?: currentMeta?.plot
                        val newScore = details.score?.toString() ?: currentMeta?.score
                        val newYear = details.year ?: currentMeta?.year
                        val newType = details.type ?: currentMeta?.type
                        val newContentRating = details.contentRating?.takeIf { it.isNotBlank() } ?: currentMeta?.contentRating
                        val newDuration = details.duration ?: currentMeta?.duration

                        val finalMeta = HeroMeta(newTitle, newBackdrop, newLogo, newTags, newPlot, newScore, newYear, newType, newContentRating, newDuration)
                        HeroCache.cache[cacheKey] = finalMeta
                        heroMetaMap.update { it + (item.url to finalMeta) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // If all retries fail, clear the dummy cache so we can attempt fetching again next time they swipe here
                HeroCache.cache.remove(cacheKey)
                heroMetaMap.update { it - item.url }
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
