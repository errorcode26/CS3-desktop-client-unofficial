package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

data class DetailsUiState(
    val response: LoadResponse? = null,
    val enrichedLogoUrl: String? = null,
    val enrichedBackdropUrl: String? = null,
    val enrichedTagline: String? = null,
    val enrichedStatus: String? = null,
    val enrichedStudios: List<String> = emptyList(),
    val enrichedCollectionName: String? = null,
    val enrichedCollectionBackdrop: String? = null,
    val enrichedSeasonsCount: Int? = null,
    val enrichedEpisodesCount: Int? = null,
    val enrichedOriginalLanguage: String? = null,
    val enrichedReleaseDate: String? = null,
    val enrichedCountry: String? = null,
    val enrichedCollectionItems: List<com.lagradost.cloudstream3.SearchResponse> = emptyList(),
    val heroColor: androidx.compose.ui.graphics.Color? = null,
    val isEnriching: Boolean = false,
    val error: String? = null,
    val enrichedBudget: Long? = null,
    val enrichedRevenue: Long? = null,
    val enrichedNetworks: List<String> = emptyList(),
)

class DetailsViewModel(
    private val provider: MainAPI,
    private val url: String,
    private val preloadedName: String? = null,
    private val preloadedPoster: String? = null,
    private val preloadedBg: String? = null,
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    fun dispose() {
        viewModelScope.cancel()
    }

    private val _uiState = MutableStateFlow(
        DetailsUiState(
            response = DetailsCache.get(url),
            enrichedLogoUrl = DetailsCache.get(url)?.logoUrl,
            enrichedBackdropUrl = DetailsCache.get(url)?.backgroundPosterUrl,
        ),
    )
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _watchHistory = MutableStateFlow<Map<String, WatchHistory>>(emptyMap())
    val watchHistory: StateFlow<Map<String, WatchHistory>> = _watchHistory.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.historyUpdates.collect {
                val historyMap = com.lagradost.common.storage.DesktopDataStore.getAllWatchHistory()
                    .filter { it.showUrl == url }
                    .associateBy { it.episodeId ?: it.parentId }
                _watchHistory.value = historyMap
            }
        }
    }

    private val _response = MutableStateFlow<LoadResponse?>(DetailsCache.get(url))
    val response: StateFlow<LoadResponse?> = _response.asStateFlow()

    private val _enrichmentTrigger = MutableStateFlow(0)
    val enrichmentTrigger: StateFlow<Int> = _enrichmentTrigger.asStateFlow()

    private val _fakeData = MutableStateFlow<LoadResponse?>(null)
    val fakeData: StateFlow<LoadResponse?> = _fakeData.asStateFlow()

    private val _isLoading = MutableStateFlow(_response.value == null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _fetchFailed = MutableStateFlow(false)
    val fetchFailed: StateFlow<Boolean> = _fetchFailed.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeLinkData = MutableStateFlow<Triple<MainAPI, String, WatchHistory>?>(null)
    val activeLinkData: StateFlow<Triple<MainAPI, String, WatchHistory>?> = _activeLinkData.asStateFlow()

    private val _isPanelOpen = MutableStateFlow(false)
    val isPanelOpen: StateFlow<Boolean> = _isPanelOpen.asStateFlow()

    private val _screenshots = MutableStateFlow<List<String>?>(null)
    val screenshots: StateFlow<List<String>?> = _screenshots.asStateFlow()

    private val _heroExtractedColor = MutableStateFlow<androidx.compose.ui.graphics.Color?>(null)
    val heroExtractedColor: StateFlow<androidx.compose.ui.graphics.Color?> = _heroExtractedColor.asStateFlow()



    companion object {
        private val detailsColorCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.Color>()
    }

    private fun extractColor(imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        detailsColorCache[imageUrl]?.let {
            _heroExtractedColor.value = it
            _uiState.update { state -> state.copy(heroColor = it) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = app.get(imageUrl).body.bytes()
                val img = ImageIO.read(bytes.inputStream()) ?: return@launch
                val color = com.lagradost.cloudstream3.desktop.utils.ImageColorExtractor.sampleDominantColor(img)
                if (color != null) {
                    detailsColorCache[imageUrl] = color
                    _heroExtractedColor.value = color
                    _uiState.update { state -> state.copy(heroColor = color) }
                }
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.w("DetailsColor: Failed to extract color from $imageUrl — ${e.message}")
            }
        }
    }

    private val _isInitialized = MutableStateFlow(false)

    fun load() {
        if (_isInitialized.value) return
        _isInitialized.value = true
        extractColor(preloadedBg ?: preloadedPoster ?: _response.value?.backgroundPosterUrl ?: _response.value?.posterUrl)
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _fetchFailed.value = false
            if (_response.value == null) {
                if (preloadedName != null) {
                    _fakeData.value = provider.newMovieLoadResponse(
                        name = preloadedName,
                        url = url,
                        type = TvType.Movie,
                        dataUrl = url,
                    ) {
                        this.posterUrl = preloadedPoster
                        this.backgroundPosterUrl = preloadedBg
                    }
                }

                try {
                    val rawData = DetailsRepository.fetchRaw(provider, url, fallbackName = preloadedName)
                    if (rawData != null) {
                        _response.value = rawData
                        _isLoading.value = false
                        _uiState.update {
                            it.copy(
                                response = rawData,
                                enrichedLogoUrl = rawData.logoUrl,
                                enrichedBackdropUrl = rawData.backgroundPosterUrl,
                                isEnriching = true,
                            )
                        }
                        extractColor(rawData.backgroundPosterUrl ?: rawData.posterUrl ?: preloadedBg ?: preloadedPoster)
                    } else {
                        _fetchFailed.value = true
                        _isLoading.value = false
                        _uiState.update { it.copy(error = "Failed to fetch raw details") }
                        return@launch
                    }
                } catch (e: Throwable) {
                    com.lagradost.common.logging.AppLogger.e("Error loading details", e)
                    _errorMessage.value = e.message
                    _fetchFailed.value = true
                    _isLoading.value = false
                    _uiState.update { it.copy(error = e.message) }
                    return@launch
                }
            }

            val currentData = _response.value
            if (currentData != null) {
                if (!preloadedName.isNullOrBlank() && currentData.name.isBlank()) {
                    withContext(Dispatchers.Main.immediate) {
                        currentData.name = preloadedName
                    }
                }
                if (_heroExtractedColor.value == null) {
                    extractColor(currentData.backgroundPosterUrl ?: currentData.posterUrl ?: preloadedBg ?: preloadedPoster)
                }
                _uiState.update { it.copy(isEnriching = true) }
                val targetEnrichUrl = if (currentData.url.isNotBlank() && !currentData.url.contains("themoviedb.org")) currentData.url else url
                TmdbEnrichmentService.enrich(
                    loaded = currentData,
                    url = targetEnrichUrl,
                    onScreenshotsLoaded = { images -> _screenshots.value = images },
                    onMetadataLoaded = { tagline, status, studios, collName, collBg, seasons, episodes, lang, relDate, country, collItems, budget, revenue, networks ->
                        _uiState.update {
                            it.copy(
                                enrichedTagline = tagline,
                                enrichedStatus = status,
                                enrichedStudios = studios,
                                enrichedCollectionName = collName,
                                enrichedCollectionBackdrop = collBg,
                                enrichedSeasonsCount = seasons,
                                enrichedEpisodesCount = episodes,
                                enrichedOriginalLanguage = lang,
                                enrichedReleaseDate = relDate,
                                enrichedCountry = country,
                                enrichedCollectionItems = collItems,
                                enrichedBudget = budget,
                                enrichedRevenue = revenue,
                                enrichedNetworks = networks ?: emptyList(),
                            )
                        }
                    },
                    onEnrichmentComplete = {
                        _enrichmentTrigger.value++
                        extractColor(currentData.backgroundPosterUrl ?: currentData.posterUrl ?: preloadedBg ?: preloadedPoster)
                        _uiState.update {
                            it.copy(
                                response = currentData,
                                enrichedLogoUrl = currentData.logoUrl,
                                enrichedBackdropUrl = currentData.backgroundPosterUrl,
                                heroColor = _heroExtractedColor.value,
                                isEnriching = false,
                            )
                        }
                    },
                )
            }
        }
    }

    fun retry() {
        _fetchFailed.value = false
        _isLoading.value = true
        DetailsCache.remove(url)
        loadDetails()
    }

    fun openLinksPanel(data: Triple<MainAPI, String, WatchHistory>) {
        _activeLinkData.value = data
        _isPanelOpen.value = true
    }

    fun closeLinksPanel() {
        _isPanelOpen.value = false
    }
}
