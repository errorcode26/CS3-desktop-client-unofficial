package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState
import com.lagradost.cloudstream3.desktop.utils.ImageColorExtractor
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias DetailsUiState = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState

class DetailsViewModel(
    private val provider: MainAPI,
    private val url: String,
    private val preloadedName: String? = null,
    private val preloadedPoster: String? = null,
    private val preloadedBg: String? = null,
) : BaseMviViewModel<DetailsUiState, DetailsUiEvent, DetailsUiEffect>(
    initialState = DetailsUiState(
        response = DetailsCache.get(url),
        enrichedLogoUrl = DetailsCache.get(url)?.logoUrl,
        enrichedBackdropUrl = DetailsCache.get(url)?.backgroundPosterUrl,
        isLoading = DetailsCache.get(url) == null
    )
) {
    private val _isInitialized = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.historyUpdates.collect {
                val historyMap = DesktopDataStore.getAllWatchHistory()
                    .filter { it.showUrl == url }
                    .associateBy { it.episodeId ?: it.parentId }
                updateState { copy(watchHistory = historyMap) }
            }
        }
        viewModelScope.launch {
            BookmarksRepository.bookmarksFlow.collect { bookmarks ->
                updateState { copy(bookmarks = bookmarks) }
            }
        }
    }

    override fun handleEvent(event: DetailsUiEvent) {
        when (event) {
            is DetailsUiEvent.OnLoad -> load()
            is DetailsUiEvent.OnRetry -> retry()
            is DetailsUiEvent.OnOpenLinksPanel -> openLinksPanel(event.data)
            is DetailsUiEvent.OnCloseLinksPanel -> closeLinksPanel()
        }
    }

    fun load() {
        if (_isInitialized.value) return
        _isInitialized.value = true
        val currentResp = uiState.value.response
        extractColor(preloadedBg ?: preloadedPoster ?: currentResp?.backgroundPosterUrl ?: currentResp?.posterUrl)
        loadDetails()
    }

    private fun extractColor(imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        ImageColorExtractor.getCachedColor(imageUrl)?.let { color ->
            updateState { copy(heroColor = color) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val color = ImageColorExtractor.extractDominantColorFromUrl(imageUrl)
            if (color != null) {
                updateState { copy(heroColor = color) }
            }
        }
    }

    fun loadDetails() {
        viewModelScope.launch {
            updateState { copy(fetchFailed = false) }
            if (uiState.value.response == null) {
                if (preloadedName != null) {
                    val fake = provider.newMovieLoadResponse(
                        name = preloadedName,
                        url = url,
                        type = TvType.Movie,
                        dataUrl = url,
                    ) {
                        this.posterUrl = preloadedPoster
                        this.backgroundPosterUrl = preloadedBg
                    }
                    updateState { copy(fakeData = fake) }
                }

                try {
                    val rawData = DetailsRepository.fetchRaw(provider, url, fallbackName = preloadedName)
                    if (rawData != null) {
                        updateState {
                            copy(
                                response = rawData,
                                isLoading = false,
                                enrichedLogoUrl = rawData.logoUrl,
                                enrichedBackdropUrl = rawData.backgroundPosterUrl,
                                isEnriching = true
                            )
                        }
                        extractColor(rawData.backgroundPosterUrl ?: rawData.posterUrl ?: preloadedBg ?: preloadedPoster)
                    } else {
                        updateState {
                            copy(
                                fetchFailed = true,
                                isLoading = false,
                                error = "Failed to fetch raw details",
                                errorMessage = "Failed to fetch raw details"
                            )
                        }
                        return@launch
                    }
                } catch (e: Throwable) {
                    AppLogger.e("Error loading details", e)
                    updateState {
                        copy(
                            fetchFailed = true,
                            isLoading = false,
                            error = e.message,
                            errorMessage = e.message
                        )
                    }
                    return@launch
                }
            }

            val currentData = uiState.value.response
            if (currentData != null) {
                if (!preloadedName.isNullOrBlank() && currentData.name.isBlank()) {
                    withContext(Dispatchers.Main.immediate) {
                        currentData.name = preloadedName
                    }
                }
                if (uiState.value.heroColor == null) {
                    extractColor(currentData.backgroundPosterUrl ?: currentData.posterUrl ?: preloadedBg ?: preloadedPoster)
                }
                updateState { copy(isEnriching = true) }
                val targetEnrichUrl = if (currentData.url.isNotBlank() && !currentData.url.contains("themoviedb.org")) currentData.url else url
                TmdbEnrichmentService.enrich(
                    loaded = currentData,
                    url = targetEnrichUrl,
                    onScreenshotsLoaded = { images ->
                        updateState { copy(screenshots = images) }
                    },
                    onMetadataLoaded = { tagline, status, studios, collName, collBg, seasons, episodes, lang, relDate, country, collItems, budget, revenue, networks ->
                        updateState {
                            copy(
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
                                enrichedNetworks = networks ?: emptyList()
                            )
                        }
                    },
                    onEnrichmentComplete = {
                        extractColor(currentData.backgroundPosterUrl ?: currentData.posterUrl ?: preloadedBg ?: preloadedPoster)
                        updateState {
                            copy(
                                enrichmentTrigger = enrichmentTrigger + 1,
                                response = currentData,
                                enrichedLogoUrl = currentData.logoUrl,
                                enrichedBackdropUrl = currentData.backgroundPosterUrl,
                                isEnriching = false
                            )
                        }
                    },
                )
            }
        }
    }

    fun retry() {
        updateState { copy(fetchFailed = false, isLoading = true) }
        DetailsCache.remove(url)
        loadDetails()
    }

    fun openLinksPanel(data: Triple<MainAPI, String, WatchHistory>) {
        updateState { copy(activeLinkData = data, isPanelOpen = true) }
    }

    fun closeLinksPanel() {
        updateState { copy(isPanelOpen = false) }
    }
}
