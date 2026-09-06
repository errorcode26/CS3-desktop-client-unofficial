package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor.GetBookmarks
import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository
import com.lagradost.cloudstream3.desktop.domain.history.interactor.GetWatchHistory
import com.lagradost.cloudstream3.desktop.domain.history.interactor.RemoveWatchHistory
import com.lagradost.cloudstream3.desktop.domain.history.interactor.UpsertWatchHistory
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DetailsViewModel(
    val provider: MainAPI,
    val url: String,
    val preloadedName: String? = null,
    val preloadedPoster: String? = null,
    val preloadedBg: String? = null,
    val initialSeason: Int? = null,
    val targetEpisodeId: String? = null,
    cachedResponse: LoadResponse? = DetailsCache.get(url),
    cachedUiState: DetailsUiState? = EnrichedDetailsCache.get(url),
    private val getWatchHistory: GetWatchHistory = AppContainerHolder.container.getWatchHistory,
    private val upsertWatchHistory: UpsertWatchHistory = AppContainerHolder.container.upsertWatchHistory,
    private val removeWatchHistory: RemoveWatchHistory = AppContainerHolder.container.removeWatchHistory,
    private val getBookmarks: GetBookmarks = AppContainerHolder.container.getBookmarks,
    private val bookmarksRepository: BookmarksRepository = AppContainerHolder.container.bookmarksRepository,
) : BaseMviViewModel<DetailsUiState, DetailsUiEvent, DetailsUiEffect>(
    initialState = cachedUiState?.copy(
        fetchFailed = false,
        error = null,
        selectedSeason = initialSeason ?: cachedUiState.selectedSeason,
    ) ?: DetailsUiState(
        preloadedName = preloadedName,
        response = cachedResponse,
        selectedSeason = initialSeason,
        enrichedLogoUrl = cachedResponse?.logoUrl,
        enrichedBackdropUrl = cachedResponse?.backgroundPosterUrl,
        isLoading = cachedResponse == null,
        fakeData = if (cachedResponse == null) {
            @Suppress("DEPRECATION_ERROR", "DEPRECATION")
            MovieLoadResponse(
                name = preloadedName ?: "",
                url = url,
                apiName = provider.name,
                type = TvType.Movie,
                dataUrl = url,
                posterUrl = preloadedPoster,
            ).apply {
                this.backgroundPosterUrl = preloadedBg
            }
        } else {
            null
        },
    ),
) {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val autoPlay = DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
            val isStacked = DesktopDataStore.getKey<Boolean>(PreferenceKeys.PREF_EPISODES_STACKED_VIEW) ?: false
            val viewMode = DesktopDataStore.getKey<Int>(PreferenceKeys.PREF_EPISODES_VIEW_MODE) ?: if (isStacked) 1 else 0
            updateState {
                copy(
                    autoPlayEnabled = autoPlay,
                    isEpisodesStackedView = isStacked,
                    episodeViewMode = viewMode,
                )
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            getWatchHistory.subscribeAll().collect {
                val currentDataUrl = uiState.value.response?.url ?: url
                val currentParentId = DesktopDataStore.watchHistoryId(provider.name, currentDataUrl)
                val fallbackParentId = DesktopDataStore.watchHistoryId(provider.name, url)

                val historyMap = (
                    getWatchHistory.awaitByParent(currentParentId) +
                        getWatchHistory.awaitByParent(fallbackParentId)
                    )
                    .distinctBy { it.episodeId }
                    .filter { it.showUrl == url || it.showUrl == currentDataUrl }
                    .associateBy { it.episodeId ?: "" }
                val latestSeason = historyMap.values.maxByOrNull { it.updateTime }?.season
                updateState {
                    copy(
                        watchHistory = historyMap,
                        selectedSeason = selectedSeason ?: latestSeason,
                    )
                }
            }
        }
        viewModelScope.launch {
            getBookmarks.subscribeAll().collect { bookmarks ->
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
            is DetailsUiEvent.OnRequestAutoPlay -> handleAutoPlay()
            is DetailsUiEvent.OnMarkAutoPlayHandled -> updateState { copy(hasAutoPlayed = true) }
            is DetailsUiEvent.OnPlayEpisode -> handlePlayEpisode(event.ep)
            is DetailsUiEvent.OnDownloadEpisode -> handleDownloadEpisode(event.ep)
            is DetailsUiEvent.OnToggleEpisodeWatched -> handleToggleEpisodeWatched(event.ep, event.isWatched)
            is DetailsUiEvent.OnRemoveEpisodeWatched -> handleRemoveEpisodeWatched(event.ep)
            is DetailsUiEvent.OnToggleSeasonWatched -> handleToggleSeasonWatched(event.episodes, event.isWatched)
            is DetailsUiEvent.OnToggleEpisodesStackedView -> handleToggleEpisodesStackedView(event.isStacked)
            is DetailsUiEvent.OnSetEpisodeViewMode -> handleSetEpisodeViewMode(event.viewMode)
            is DetailsUiEvent.OnRefresh -> refresh()
            is DetailsUiEvent.OnAddBookmark -> {
                viewModelScope.launch(Dispatchers.IO) {
                    bookmarksRepository.addBookmark(event.bookmark)
                }
            }
            is DetailsUiEvent.OnRemoveBookmark -> {
                viewModelScope.launch(Dispatchers.IO) {
                    bookmarksRepository.removeBookmark(event.id)
                }
            }
            is DetailsUiEvent.OnSelectSeason -> selectSeason(event.season)
        }
    }

    fun load() {
        if (uiState.value.isInitialized) return
        updateState { copy(isInitialized = true) }
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(fetchFailed = false, isLoading = true, error = null) }

            if (uiState.value.response == null && uiState.value.fakeData == null) {
                val fake = provider.newMovieLoadResponse(
                    name = preloadedName ?: "",
                    url = url,
                    type = TvType.Movie,
                    dataUrl = url,
                ) {
                    this.posterUrl = preloadedPoster
                    this.backgroundPosterUrl = preloadedBg
                }
                updateState { copy(fakeData = fake) }
            }

            GetEnrichedDetailsUseCase(provider, url, preloadedName, preloadedPoster, preloadedBg).collect { update ->
                when (update) {
                    is EnrichmentUpdate.RawData -> {
                        val rawTmdbId = update.response.syncData["tmdb"]?.toIntOrNull()
                        updateState {
                            copy(
                                response = update.response,
                                isLoading = false,
                                fakeData = null,
                                enrichedLogoUrl = update.response.logoUrl,
                                enrichedBackdropUrl = update.response.backgroundPosterUrl,
                                isEnriching = true,
                                enrichmentPhase = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase.InProgress,
                                tmdbId = rawTmdbId ?: tmdbId,
                            )
                        }
                    }
                    is EnrichmentUpdate.LogoLoaded -> {
                        updateState { copy(enrichedLogoUrl = update.url) }
                    }
                    is EnrichmentUpdate.BackdropLoaded -> {
                        updateState { copy(enrichedBackdropUrl = update.url) }
                    }
                    is EnrichmentUpdate.ScreenshotsLoaded -> {
                        updateState { copy(screenshots = update.urls) }
                    }
                    is EnrichmentUpdate.ExtractedColor -> {
                        // Ignored, color extraction removed
                    }
                    is EnrichmentUpdate.ActorsLoaded -> {
                        updateState { copy(enrichedActors = update.actors) }
                    }
                    is EnrichmentUpdate.TrailersLoaded -> {
                        updateState { copy(enrichedTrailers = update.trailers, enrichedTrailerUrl = update.trailers.firstOrNull()?.url) }
                    }
                    is EnrichmentUpdate.ReviewsLoaded -> {
                        updateState { copy(enrichedReviews = update.reviews) }
                    }
                    is EnrichmentUpdate.EpisodeThumbnailsEnriched -> {
                        updateState { copy(episodeThumbnailVersion = episodeThumbnailVersion + 1) }
                    }
                    is EnrichmentUpdate.RatingsLoaded -> {
                        updateState {
                            copy(
                                enrichedImdbRating = update.imdb ?: enrichedImdbRating,
                                enrichedTmdbRating = update.tmdb ?: enrichedTmdbRating,
                                enrichedAniListRating = update.anilist ?: enrichedAniListRating,
                            )
                        }
                    }
                    is EnrichmentUpdate.MetadataLoaded -> {
                        updateState {
                            val mergedProdCompanies = if (update.productionCompanies != null) {
                                val current = enrichedProductionCompanies.toMutableList()
                                update.productionCompanies.forEach { newComp ->
                                    val existingIdx = current.indexOfFirst { it.name.trim().equals(newComp.name.trim(), ignoreCase = true) }
                                    if (existingIdx >= 0) {
                                        val existing = current[existingIdx]
                                        if (existing.logoUrl.isNullOrBlank() && !newComp.logoUrl.isNullOrBlank()) {
                                            current[existingIdx] = newComp
                                        }
                                    } else {
                                        current.add(newComp)
                                    }
                                }
                                current
                            } else enrichedProductionCompanies

                            val mergedNetCompanies = if (update.networkCompanies != null) {
                                val current = enrichedNetworksList.toMutableList()
                                update.networkCompanies.forEach { newComp ->
                                    val existingIdx = current.indexOfFirst { it.name.trim().equals(newComp.name.trim(), ignoreCase = true) }
                                    if (existingIdx >= 0) {
                                        val existing = current[existingIdx]
                                        if (existing.logoUrl.isNullOrBlank() && !newComp.logoUrl.isNullOrBlank()) {
                                            current[existingIdx] = newComp
                                        }
                                    } else {
                                        current.add(newComp)
                                    }
                                }
                                current
                            } else enrichedNetworksList

                            copy(
                                enrichedTagline = update.tagline ?: enrichedTagline,
                                enrichedStatus = update.status ?: enrichedStatus,
                                enrichedStudios = if (update.studios.isNotEmpty()) update.studios else enrichedStudios,
                                enrichedProductionCompanies = mergedProdCompanies,
                                enrichedNetworksList = mergedNetCompanies,
                                enrichedCollectionName = update.collName ?: enrichedCollectionName,
                                enrichedCollectionBackdrop = update.collBg ?: enrichedCollectionBackdrop,
                                enrichedSeasonsCount = update.seasons ?: enrichedSeasonsCount,
                                enrichedEpisodesCount = update.episodes ?: enrichedEpisodesCount,
                                enrichedSeasonsMetadata = if (!update.seasonsMetadata.isNullOrEmpty()) update.seasonsMetadata else enrichedSeasonsMetadata,
                                enrichedOriginalLanguage = update.lang ?: enrichedOriginalLanguage,
                                enrichedReleaseDate = update.relDate ?: enrichedReleaseDate,
                                enrichedCountry = update.country ?: enrichedCountry,
                                enrichedCollectionItems = if (update.collItems.isNotEmpty()) update.collItems else enrichedCollectionItems,
                                enrichedBudget = update.budget ?: enrichedBudget,
                                enrichedRevenue = update.revenue ?: enrichedRevenue,
                                enrichedNetworks = if (!update.networks.isNullOrEmpty()) update.networks else enrichedNetworks,
                                enrichedYear = update.year ?: enrichedYear,
                                enrichedDuration = update.duration ?: enrichedDuration,
                                enrichedTags = update.tags ?: enrichedTags,
                                enrichedActors = update.actors ?: enrichedActors,
                            )
                        }
                    }
                    is EnrichmentUpdate.FullyEnriched -> {
                        updateState {
                            val resolvedTmdbId = tmdbId ?: response?.syncData?.get("tmdb")?.toIntOrNull()
                            val newState = copy(
                                isEnriching = false,
                                enrichmentPhase = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase.Complete,
                                tmdbId = resolvedTmdbId ?: tmdbId,
                            )
                            EnrichedDetailsCache.put(url, newState)
                            newState.response?.url?.let {
                                if (it != url) EnrichedDetailsCache.put(it, newState)
                            }
                            newState
                        }
                        val currentSeason = uiState.value.selectedSeason
                        if (currentSeason != null && currentSeason > 0) {
                            loadSeasonCredits(currentSeason)
                        }
                    }
                    is EnrichmentUpdate.Error -> {
                        AppLogger.e("DetailsViewModel", "Error loading details: ${update.message}")
                        updateState {
                            copy(
                                fetchFailed = true,
                                isLoading = false,
                                error = update.message,
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectSeason(season: Int?) {
        updateState { copy(selectedSeason = season) }
        if (season != null && season > 0) {
            loadSeasonCredits(season)
        }
    }

    fun loadSeasonCredits(season: Int) {
        val state = uiState.value
        if (season <= 0) return
        if (state.seasonCredits.containsKey(season)) return
        val tmdbId = state.tmdbId ?: state.response?.syncData?.get("tmdb")?.toIntOrNull() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val credits = TmdbEnrichmentService.fetchSeasonCredits(tmdbId, season)
            if (credits.isNotEmpty()) {
                updateState {
                    copy(
                        seasonCredits = seasonCredits + (season to credits),
                    )
                }
            }
        }
    }

    private fun handleAutoPlay() {
        if (uiState.value.hasAutoPlayed) return
        updateState { copy(hasAutoPlayed = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val resp = uiState.value.response ?: return@launch
            val targetEp = DetailsWatchCoordinator.determineAutoPlayTarget(
                provider = provider,
                resp = resp,
                watchHistory = uiState.value.watchHistory,
                targetEpisodeId = targetEpisodeId,
            )
            if (targetEp != null) {
                val patchedData = DetailsWatchCoordinator.patchEpisodeData(targetEp, resp)
                val history = DetailsWatchCoordinator.buildWatchHistory(provider.name, targetEp, resp)
                handlePlayRequest(Triple(provider, patchedData, history))
            }
        }
    }

    private fun handlePlayEpisode(ep: Episode) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: return@launch
            val patchedData = DetailsWatchCoordinator.patchEpisodeData(ep, data)
            val history = DetailsWatchCoordinator.buildWatchHistory(provider.name, ep, data)
            handlePlayRequest(Triple(provider, patchedData, history))
        }
    }

    private fun handleDownloadEpisode(ep: Episode) {
        val data = uiState.value.response ?: return
        val patchedData = DetailsWatchCoordinator.patchEpisodeData(ep, data)
        val isMovie = data is MovieLoadResponse
        val history = WatchHistory(
            parentId = data.url,
            showName = data.name,
            showUrl = data.url,
            apiName = provider.name,
            posterUrl = ep.posterUrl ?: data.posterUrl,
            episodeThumbnailUrl = ep.posterUrl ?: data.posterUrl,
            screenshotUrl = null,
            episode = if (isMovie) null else ep.episode,
            season = if (isMovie) null else ep.season,
            episodeId = ep.data,
            position = 0L,
            duration = 0L,
            updateTime = System.currentTimeMillis(),
            episodeName = if (isMovie) null else ep.name,
            episodeDescription = ep.description ?: data.plot,
        )
        openLinksPanel(Triple(provider, patchedData, history))
    }

    private fun handleRemoveEpisodeWatched(ep: com.lagradost.cloudstream3.Episode) {
        val data = uiState.value.response ?: return

        viewModelScope.launch(Dispatchers.IO) {
            DetailsWatchCoordinator.removeEpisodeWatched(
                providerName = provider.name,
                currentDataUrl = data.url,
                fallbackUrl = url,
                epData = ep.data,
                removeWatchHistory = removeWatchHistory,
            )
        }
    }

    private fun handleToggleEpisodeWatched(ep: Episode, isWatched: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: return@launch
            DetailsWatchCoordinator.toggleEpisodeWatched(
                providerName = provider.name,
                data = data,
                fallbackUrl = url,
                ep = ep,
                isWatched = isWatched,
            )
        }
    }

    private fun handleToggleSeasonWatched(episodes: List<Episode>, isWatched: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: return@launch
            val newBackup = DetailsWatchCoordinator.toggleSeasonWatched(
                providerName = provider.name,
                data = data,
                fallbackUrl = url,
                episodes = episodes,
                isWatched = isWatched,
                currentWatchHistory = uiState.value.watchHistory,
                backupSeasonHistory = uiState.value.backupSeasonHistory,
            )
            updateState { copy(backupSeasonHistory = newBackup) }
        }
    }

    private fun handleToggleEpisodesStackedView(isStacked: Boolean) {
        val viewMode = if (isStacked) 1 else 0
        updateState { copy(isEpisodesStackedView = isStacked, episodeViewMode = viewMode) }
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PreferenceKeys.PREF_EPISODES_STACKED_VIEW, isStacked)
            DesktopDataStore.setKey(PreferenceKeys.PREF_EPISODES_VIEW_MODE, viewMode)
        }
    }

    private fun handleSetEpisodeViewMode(viewMode: Int) {
        val isStacked = viewMode != 0
        updateState { copy(episodeViewMode = viewMode, isEpisodesStackedView = isStacked) }
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PreferenceKeys.PREF_EPISODES_VIEW_MODE, viewMode)
            DesktopDataStore.setKey(PreferenceKeys.PREF_EPISODES_STACKED_VIEW, isStacked)
        }
    }

    private fun handlePlayRequest(data: Triple<MainAPI, String, WatchHistory>, forceAutoPlay: Boolean? = null) {
        val isTorrent = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isTorrentProvider(data.first)
        val isP2pOn = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isP2pEnabled
        val shouldAutoPlay = (forceAutoPlay ?: uiState.value.autoPlayEnabled) && (!isTorrent || isP2pOn)
        if (shouldAutoPlay) {
            val linkHistory = data.third
            val epTitle = buildString {
                append(linkHistory.showName)
                if (linkHistory.season != null && linkHistory.episode != null) {
                    append(" - S${linkHistory.season}E${linkHistory.episode}")
                } else if (linkHistory.episode != null) {
                    append(" - E${linkHistory.episode}")
                }
            }
            val response = uiState.value.response
            val isLive = response?.type == TvType.Live
            val resumeMs = if (isLive) 0L else com.lagradost.player.impl.PlayerLinkHandler.resumeStartSeconds(linkHistory.position, linkHistory.duration) * 1000L

            sendEffect(
                DetailsUiEffect.NavigateToPlayer(
                    com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                        links = emptyList(),
                        initialIndex = 0,
                        title = epTitle,
                        subtitles = emptyList(),
                        startPositionMs = resumeMs,
                        history = linkHistory,
                        loadResponse = response,
                        enrichedLogoUrl = uiState.value.enrichedLogoUrl,
                        enrichedBackdropUrl = uiState.value.enrichedBackdropUrl,
                    ),
                ),
            )
        } else {
            handleEvent(DetailsUiEvent.OnOpenLinksPanel(data))
        }
    }

    fun retry() {
        updateState { copy(fetchFailed = false, isLoading = true) }
        DetailsCache.remove(url)
        loadDetails()
    }

    fun refresh() {
        DetailsCache.remove(url)
        uiState.value.response?.url?.let { DetailsCache.remove(it) }
        EnrichedDetailsCache.remove(url)
        uiState.value.response?.url?.let { EnrichedDetailsCache.remove(it) }
        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("Refreshing details...")
        updateState {
            copy(
                isInitialized = true,
                isLoading = true,
                fetchFailed = false,
                error = null,
                response = null,
                fakeData = null,
                isEnriching = false,
                enrichmentPhase = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase.Idle,
            )
        }
        loadDetails()
    }

    fun openLinksPanel(data: Triple<MainAPI, String, WatchHistory>) {
        updateState { copy(activeLinkData = data, isPanelOpen = true) }
    }

    fun closeLinksPanel() {
        updateState { copy(isPanelOpen = false) }
    }
}
