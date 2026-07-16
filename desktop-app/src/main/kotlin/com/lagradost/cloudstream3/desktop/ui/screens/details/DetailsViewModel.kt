package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns all UI state for the Details screen.
 * Delegates data fetching and TMDB enrichment to [GlobalDetailsCache] in DetailsRepository.kt.
 */
class DetailsViewModel(
    private val viewModelScope: CoroutineScope,
    private val provider: MainAPI,
    private val url: String,
    private val preloadedName: String? = null,
    private val preloadedPoster: String? = null,
    private val preloadedBg: String? = null,
) {

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

    init {
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
                    val rawData = GlobalDetailsCache.fetchRaw(provider, url)
                    if (rawData != null) {
                        _response.value = rawData
                        _isLoading.value = false
                    } else {
                        _fetchFailed.value = true
                        _isLoading.value = false
                        return@launch
                    }
                } catch (e: Throwable) {
                    com.lagradost.common.logging.AppLogger.e("Error loading details", e)
                    _errorMessage.value = e.message
                    _fetchFailed.value = true
                    _isLoading.value = false
                    return@launch
                }
            }

            val currentData = _response.value
            if (currentData != null) {
                if (!preloadedName.isNullOrBlank() && currentData.name.isBlank()) {
                    currentData.name = preloadedName
                }
                // Trigger recompose ONLY after all TMDB fields are fully written
                GlobalDetailsCache.enrich(
                    loaded = currentData,
                    url = url,
                    onScreenshotsLoaded = { images -> _screenshots.value = images },
                    onEnrichmentComplete = { _enrichmentTrigger.value += 1 }
                )
            }
        }
    }

    fun retry() {
        _fetchFailed.value = false
        _isLoading.value = true
        GlobalDetailsCache.cache.remove(url) // Clear any partial/failed cache entry
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
