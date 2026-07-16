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
    private val viewModelScope: CoroutineScope,
    private val provider: MainAPI,
    private val url: String,
    private val preloadedName: String? = null,
    private val preloadedPoster: String? = null,
    private val preloadedBg: String? = null,
) {

    private val _uiState = MutableStateFlow(
        DetailsUiState(
            response = GlobalDetailsCache.cache[url],
            enrichedLogoUrl = GlobalDetailsCache.cache[url]?.logoUrl,
            enrichedBackdropUrl = GlobalDetailsCache.cache[url]?.backgroundPosterUrl,
        ),
    )
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _response = MutableStateFlow<LoadResponse?>(GlobalDetailsCache.cache[url])
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

    private fun sampleDominantColor(img: BufferedImage): androidx.compose.ui.graphics.Color? {
        val area = img.width * img.height
        // Sample only 300 pixels because reading the whole image takes 2 seconds and makes the fan spin
        val step = maxOf(1, Math.sqrt(area / 300.0).toInt())
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

                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val saturation = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
                val brightness = max / 255f
                if (saturation < 0.25f || brightness < 0.15f || brightness > 0.95f) {
                    y += step
                    pixelCount++
                    continue
                }

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

        val dominant = colorBuckets.maxByOrNull { entry ->
            val key = entry.key
            val count = entry.value
            val r = (key shr 16) and 0xFF
            val g = (key shr 8) and 0xFF
            val b = key and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val sat = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
            count * (sat * sat)
        }?.key ?: return null

        val r = (dominant shr 16) and 0xFF
        val g = (dominant shr 8) and 0xFF
        val b = dominant and 0xFF
        return androidx.compose.ui.graphics.Color(r, g, b)
    }

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
                val color = sampleDominantColor(img)
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

    init {
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
                    val rawData = GlobalDetailsCache.fetchRaw(provider, url, fallbackName = preloadedName)
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
                    currentData.name = preloadedName
                }
                if (_heroExtractedColor.value == null) {
                    extractColor(currentData.backgroundPosterUrl ?: currentData.posterUrl ?: preloadedBg ?: preloadedPoster)
                }
                _uiState.update { it.copy(isEnriching = true) }
                val targetEnrichUrl = if (currentData.url.isNotBlank() && !currentData.url.contains("themoviedb.org")) currentData.url else url
                GlobalDetailsCache.enrich(
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
        GlobalDetailsCache.cache.remove(url)
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
