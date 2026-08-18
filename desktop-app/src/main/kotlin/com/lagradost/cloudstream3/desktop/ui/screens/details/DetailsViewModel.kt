package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
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
    cachedResponse: LoadResponse? = DetailsCache.get(url),
    cachedUiState: DetailsUiState? = EnrichedDetailsCache.get(url),
) : BaseMviViewModel<DetailsUiState, DetailsUiEvent, DetailsUiEffect>(
    initialState = cachedUiState?.copy(
        fetchFailed = false,
        error = null,
    ) ?: DetailsUiState(
        preloadedName = preloadedName,
        response = cachedResponse,
        enrichedLogoUrl = cachedResponse?.logoUrl,
        enrichedBackdropUrl = cachedResponse?.backgroundPosterUrl,
        isLoading = cachedResponse == null,
        fakeData = if (cachedResponse == null && preloadedName != null) {
            @Suppress("DEPRECATION_ERROR", "DEPRECATION")
            MovieLoadResponse(
                name = preloadedName,
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
            val isStacked = DesktopDataStore.getKey<Boolean>("pref_episodes_stacked_view") ?: false
            updateState {
                copy(
                    autoPlayEnabled = autoPlay,
                    isEpisodesStackedView = isStacked,
                )
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.historyUpdates.collect {
                val currentDataUrl = uiState.value.response?.url ?: url
                val currentParentId = DesktopDataStore.watchHistoryId(provider.name, currentDataUrl)
                val fallbackParentId = DesktopDataStore.watchHistoryId(provider.name, url)

                val historyMap = (
                    DesktopDataStore.getWatchHistoryByParent(currentParentId) +
                        DesktopDataStore.getWatchHistoryByParent(fallbackParentId)
                    )
                    .distinctBy { it.episodeId }
                    .filter { it.showUrl == url || it.showUrl == currentDataUrl }
                    .associateBy { it.episodeId ?: "" }
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
            is DetailsUiEvent.OnRequestAutoPlay -> handleAutoPlay()
            is DetailsUiEvent.OnMarkAutoPlayHandled -> updateState { copy(hasAutoPlayed = true) }
            is DetailsUiEvent.OnPlayEpisode -> handlePlayEpisode(event.ep)
            is DetailsUiEvent.OnDownloadEpisode -> handleDownloadEpisode(event.ep)
            is DetailsUiEvent.OnToggleEpisodeWatched -> handleToggleEpisodeWatched(event.ep, event.isWatched)
            is DetailsUiEvent.OnRemoveEpisodeWatched -> handleRemoveEpisodeWatched(event.ep)
            is DetailsUiEvent.OnToggleSeasonWatched -> handleToggleSeasonWatched(event.episodes, event.isWatched)
            is DetailsUiEvent.OnToggleEpisodesStackedView -> handleToggleEpisodesStackedView(event.isStacked)
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

            if (uiState.value.response == null && preloadedName != null) {
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

            GetEnrichedDetailsUseCase(provider, url, preloadedName, preloadedPoster, preloadedBg).collect { update ->
                when (update) {
                    is EnrichmentUpdate.RawData -> {
                        updateState {
                            copy(
                                response = update.response,
                                isLoading = false,
                                fakeData = null,
                                enrichedLogoUrl = update.response.logoUrl,
                                enrichedBackdropUrl = update.response.backgroundPosterUrl,
                                isEnriching = true,
                                enrichmentPhase = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase.InProgress,
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
                            copy(
                                enrichedTagline = update.tagline,
                                enrichedStatus = update.status,
                                enrichedStudios = update.studios,
                                enrichedCollectionName = update.collName,
                                enrichedCollectionBackdrop = update.collBg,
                                enrichedSeasonsCount = update.seasons,
                                enrichedEpisodesCount = update.episodes,
                                enrichedSeasonsMetadata = update.seasonsMetadata ?: emptyList(),
                                enrichedOriginalLanguage = update.lang,
                                enrichedReleaseDate = update.relDate,
                                enrichedCountry = update.country,
                                enrichedCollectionItems = update.collItems,
                                enrichedBudget = update.budget,
                                enrichedRevenue = update.revenue,
                                enrichedNetworks = update.networks ?: emptyList(),
                                enrichedYear = update.year,
                                enrichedDuration = update.duration,
                                enrichedTags = update.tags,
                                enrichedActors = update.actors,
                            )
                        }
                    }
                    is EnrichmentUpdate.FullyEnriched -> {
                        updateState {
                            val newState = copy(isEnriching = false, enrichmentPhase = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase.Complete)
                            EnrichedDetailsCache.put(url, newState)
                            newState.response?.url?.let {
                                if (it != url) EnrichedDetailsCache.put(it, newState)
                            }
                            newState
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

    private fun handleAutoPlay() {
        if (uiState.value.hasAutoPlayed) return
        updateState { copy(hasAutoPlayed = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val resp = uiState.value.response ?: uiState.value.fakeData ?: return@launch

            val allEpisodes = when (resp) {
                is TvSeriesLoadResponse -> resp.episodes
                is AnimeLoadResponse -> resp.episodes.values.flatten()
                else -> emptyList()
            }
            val sortedEpisodes = allEpisodes.sortedWith(
                compareBy<Episode> { it.season ?: 1 }
                    .thenBy { it.episode ?: 1 },
            )

            val latestHistory = uiState.value.watchHistory.values.maxByOrNull { it.updateTime }
            val isLatestCompleted = latestHistory != null && latestHistory.duration > 0 &&
                com.lagradost.player.impl.PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)

            val targetEp = if (latestHistory != null && sortedEpisodes.isNotEmpty()) {
                if (isLatestCompleted) {
                    val currentIdx = sortedEpisodes.indexOfFirst { it.data == latestHistory.episodeId }
                    if (currentIdx != -1 && currentIdx + 1 < sortedEpisodes.size) {
                        sortedEpisodes[currentIdx + 1]
                    } else {
                        sortedEpisodes.find { it.data == latestHistory.episodeId } ?: sortedEpisodes.firstOrNull()
                    }
                } else {
                    sortedEpisodes.find { it.data == latestHistory.episodeId } ?: sortedEpisodes.firstOrNull()
                }
            } else if (sortedEpisodes.isNotEmpty()) {
                sortedEpisodes.firstOrNull()
            } else if (resp is MovieLoadResponse) {
                provider.newEpisode(resp.dataUrl) {
                    this.name = resp.name
                    this.posterUrl = resp.posterUrl
                }
            } else {
                null
            }

            if (targetEp != null) {
                val history = buildWatchHistory(targetEp, resp)
                val patchedData = patchEpisodeData(targetEp, resp)
                handlePlayRequest(Triple(provider, patchedData, history))
            }
        }
    }

    private fun buildWatchHistory(ep: Episode, data: LoadResponse): WatchHistory {
        val parentId = DesktopDataStore.watchHistoryId(
            apiName = provider.name,
            showUrl = data.url,
        )
        val saved = DesktopDataStore.getEpisodeWatched(parentId, ep.data)
        val resumePos = com.lagradost.player.impl.PlayerLinkHandler.resumeStartSeconds(
            saved?.position ?: 0L,
            saved?.duration ?: 0L,
        )
        return WatchHistory(
            parentId = parentId,
            showName = data.name,
            showUrl = data.url,
            apiName = provider.name,
            posterUrl = data.posterUrl,
            episodeThumbnailUrl = ep.posterUrl ?: data.posterUrl,
            screenshotUrl = saved?.screenshotUrl,
            episode = ep.episode,
            season = ep.season,
            episodeId = ep.data,
            position = resumePos,
            duration = saved?.duration ?: 0L,
        )
    }

    private fun patchEpisodeData(ep: Episode, data: LoadResponse): String {
        val patchedData = ep.data
        if (patchedData.startsWith("{") && patchedData.endsWith("}")) {
            return try {
                val map = mapper.readValue(patchedData, object : TypeReference<MutableMap<String, Any>>() {})
                if (!map.containsKey("title")) {
                    map["title"] = data.name
                }
                if (!map.containsKey("tvtype")) {
                    map["tvtype"] = ""
                }
                mapper.writeValueAsString(map)
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("Failed to patch episode data", e)
                patchedData
            }
        }
        return patchedData
    }

    private fun handlePlayEpisode(ep: Episode) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: uiState.value.fakeData ?: return@launch
            val history = buildWatchHistory(ep, data)
            val patchedData = patchEpisodeData(ep, data)
            handlePlayRequest(Triple(provider, patchedData, history), forceAutoPlay = true)
        }
    }

    private fun handleDownloadEpisode(ep: Episode) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: uiState.value.fakeData ?: return@launch
            val history = buildWatchHistory(ep, data)
            val patchedData = patchEpisodeData(ep, data)
            handlePlayRequest(Triple(provider, patchedData, history), forceAutoPlay = false)
        }
    }

    private fun handleRemoveEpisodeWatched(ep: com.lagradost.cloudstream3.Episode) {
        val data = uiState.value.response ?: uiState.value.fakeData ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val parentId = DesktopDataStore.watchHistoryId(provider.name, data.url)
            DesktopDataStore.removeEpisodeWatched(parentId, ep.data)
        }
    }

    private fun handleToggleEpisodeWatched(ep: Episode, isWatched: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: uiState.value.fakeData ?: return@launch
            val parentId = DesktopDataStore.watchHistoryId(
                apiName = provider.name,
                showUrl = data.url,
            )
            val saved = DesktopDataStore.getEpisodeWatched(parentId, ep.data)
            val dur = if (saved != null && saved.duration > 0L) saved.duration else 60L
            val newPos = if (isWatched) dur else 0L
            val history = WatchHistory(
                parentId = parentId,
                showName = data.name,
                showUrl = data.url,
                apiName = provider.name,
                posterUrl = data.posterUrl,
                episodeThumbnailUrl = ep.posterUrl,
                screenshotUrl = saved?.screenshotUrl,
                episode = ep.episode,
                season = ep.season,
                episodeId = ep.data,
                position = newPos,
                duration = dur,
            )
            DesktopDataStore.setLastWatched(history)

            if (isWatched) {
                val allEps = when (data) {
                    is TvSeriesLoadResponse -> data.episodes
                    is AnimeLoadResponse -> data.episodes.values.flatten()
                    else -> emptyList()
                }
                val currentIdx = allEps.indexOfFirst { it.data == ep.data }
                if (currentIdx != -1 && currentIdx + 1 < allEps.size) {
                    val nextEp = allEps[currentIdx + 1]
                    val existingNext = DesktopDataStore.getEpisodeWatched(parentId, nextEp.data)
                    if (existingNext == null) {
                        val nextEpHistory = WatchHistory(
                            parentId = parentId,
                            showName = data.name,
                            showUrl = data.url,
                            apiName = provider.name,
                            posterUrl = data.posterUrl,
                            episodeThumbnailUrl = nextEp.posterUrl ?: data.posterUrl,
                            screenshotUrl = null,
                            episode = nextEp.episode,
                            season = nextEp.season,
                            episodeId = nextEp.data,
                            position = 0,
                            duration = 0,
                            updateTime = System.currentTimeMillis() + 1000,
                        )
                        DesktopDataStore.setLastWatched(nextEpHistory)
                    } else if (existingNext.position < (existingNext.duration * 0.9)) {
                        DesktopDataStore.setLastWatched(
                            existingNext.copy(
                                updateTime = System.currentTimeMillis() + 1000,
                                episodeThumbnailUrl = existingNext.episodeThumbnailUrl ?: nextEp.posterUrl ?: data.posterUrl,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun handleToggleSeasonWatched(episodes: List<Episode>, isWatched: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: uiState.value.fakeData ?: return@launch
            val parentId = DesktopDataStore.watchHistoryId(provider.name, data.url)

            if (isWatched) {
                // Marking as watched. Save backup of current states.
                val newBackupMap = mutableMapOf<String, WatchHistory>()
                val historiesToSave = mutableListOf<WatchHistory>()

                episodes.forEach { ep ->
                    val hist = uiState.value.watchHistory.values.find { (it.episodeId ?: "") == ep.data }
                    if (hist != null) {
                        newBackupMap[ep.data] = hist
                    }
                    val saved = DesktopDataStore.getEpisodeWatched(parentId, ep.data)
                    val dur = if (saved != null && saved.duration > 0L) saved.duration else 60L
                    historiesToSave.add(
                        WatchHistory(
                            parentId = parentId,
                            showName = data.name,
                            showUrl = data.url,
                            apiName = provider.name,
                            posterUrl = data.posterUrl,
                            episodeThumbnailUrl = ep.posterUrl,
                            screenshotUrl = saved?.screenshotUrl,
                            episode = ep.episode,
                            season = ep.season,
                            episodeId = ep.data,
                            position = dur,
                            duration = dur,
                        ),
                    )
                }

                // Advance to next episode after the batch if one exists
                if (episodes.isNotEmpty()) {
                    val allEps = when (data) {
                        is TvSeriesLoadResponse -> data.episodes
                        is AnimeLoadResponse -> data.episodes.values.flatten()
                        else -> emptyList()
                    }
                    val lastWatchedEp = episodes.last()
                    val lastIdx = allEps.indexOfFirst { it.data == lastWatchedEp.data }
                    if (lastIdx != -1 && lastIdx + 1 < allEps.size) {
                        val nextEp = allEps[lastIdx + 1]
                        val existingNext = DesktopDataStore.getEpisodeWatched(parentId, nextEp.data)
                        if (existingNext == null) {
                            historiesToSave.add(
                                WatchHistory(
                                    parentId = parentId,
                                    showName = data.name,
                                    showUrl = data.url,
                                    apiName = provider.name,
                                    posterUrl = data.posterUrl,
                                    episodeThumbnailUrl = nextEp.posterUrl ?: data.posterUrl,
                                    screenshotUrl = null,
                                    episode = nextEp.episode,
                                    season = nextEp.season,
                                    episodeId = nextEp.data,
                                    position = 0,
                                    duration = 0,
                                    updateTime = System.currentTimeMillis() + 1000,
                                ),
                            )
                        } else if (existingNext.position < (existingNext.duration * 0.9)) {
                            historiesToSave.add(
                                existingNext.copy(
                                    updateTime = System.currentTimeMillis() + 1000,
                                    episodeThumbnailUrl = existingNext.episodeThumbnailUrl ?: nextEp.posterUrl ?: data.posterUrl,
                                ),
                            )
                        }
                    }
                }

                DesktopDataStore.setMultipleLastWatched(historiesToSave)
                updateState { copy(backupSeasonHistory = newBackupMap) }
            } else {
                // Unmarking. Restore from backup.
                val historiesToRestore = mutableListOf<WatchHistory>()
                val episodesToRemove = mutableListOf<String>()

                episodes.forEach { ep ->
                    val backup = uiState.value.backupSeasonHistory[ep.data]
                    if (backup != null) {
                        val dur = if (backup.duration > 0L) backup.duration else 60L
                        historiesToRestore.add(
                            WatchHistory(
                                parentId = parentId,
                                showName = data.name,
                                showUrl = data.url,
                                apiName = provider.name,
                                posterUrl = data.posterUrl,
                                episodeThumbnailUrl = ep.posterUrl,
                                screenshotUrl = backup.screenshotUrl,
                                episode = ep.episode,
                                season = ep.season,
                                episodeId = ep.data,
                                position = backup.position,
                                duration = dur,
                            ),
                        )
                    } else {
                        episodesToRemove.add(ep.data)
                    }
                }
                DesktopDataStore.setMultipleLastWatched(historiesToRestore)
                DesktopDataStore.removeMultipleEpisodesWatched(parentId, episodesToRemove)
                updateState { copy(backupSeasonHistory = emptyMap()) }
            }
        }
    }

    private fun handleToggleEpisodesStackedView(isStacked: Boolean) {
        updateState { copy(isEpisodesStackedView = isStacked) }
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey("pref_episodes_stacked_view", isStacked)
        }
    }

    private fun handlePlayRequest(data: Triple<MainAPI, String, WatchHistory>, forceAutoPlay: Boolean? = null) {
        val shouldAutoPlay = forceAutoPlay ?: uiState.value.autoPlayEnabled
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
            val response = uiState.value.response ?: uiState.value.fakeData
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

    fun openLinksPanel(data: Triple<MainAPI, String, WatchHistory>) {
        updateState { copy(activeLinkData = data, isPanelOpen = true) }
    }

    fun closeLinksPanel() {
        updateState { copy(isPanelOpen = false) }
    }
}
