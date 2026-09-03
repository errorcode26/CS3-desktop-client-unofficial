package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor.GetBookmarks
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
    cachedResponse: LoadResponse? = DetailsCache.get(url),
    cachedUiState: DetailsUiState? = EnrichedDetailsCache.get(url),
    private val getWatchHistory: GetWatchHistory = AppContainerHolder.container.getWatchHistory,
    private val upsertWatchHistory: UpsertWatchHistory = AppContainerHolder.container.upsertWatchHistory,
    private val removeWatchHistory: RemoveWatchHistory = AppContainerHolder.container.removeWatchHistory,
    private val getBookmarks: GetBookmarks = AppContainerHolder.container.getBookmarks,
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
            val viewMode = DesktopDataStore.getKey<Int>("pref_episodes_view_mode") ?: if (isStacked) 1 else 0
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
                updateState { copy(watchHistory = historyMap) }
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
            val resp = uiState.value.response ?: return@launch

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
                    this.posterUrl = resp.backgroundPosterUrl ?: resp.posterUrl
                    this.description = resp.plot
                }
            } else {
                null
            }

            if (targetEp != null) {
                val patchedData = patchEpisodeData(targetEp, resp)
                val history = buildWatchHistory(targetEp, resp).copy(episodeId = patchedData)
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
            ?: if (data is MovieLoadResponse && ep.data != data.url) {
                DesktopDataStore.getEpisodeWatched(parentId, data.url)?.also { corrupted ->
                    DesktopDataStore.setLastWatched(
                        corrupted.copy(episodeId = ep.data),
                    )
                    DesktopDataStore.removeEpisodeWatched(parentId, data.url)
                }
            } else {
                null
            }
        val resumePos = com.lagradost.player.impl.PlayerLinkHandler.resumeStartSeconds(
            saved?.position ?: 0L,
            saved?.duration ?: 0L,
        )
        val isMovie = data is MovieLoadResponse
        return WatchHistory(
            parentId = parentId,
            showName = data.name,
            showUrl = data.url,
            apiName = provider.name,
            posterUrl = data.posterUrl,
            episodeThumbnailUrl = ep.posterUrl ?: data.posterUrl,
            screenshotUrl = saved?.screenshotUrl,
            episode = if (isMovie) null else ep.episode,
            season = if (isMovie) null else ep.season,
            episodeId = ep.data,
            position = resumePos,
            duration = saved?.duration ?: 0L,
            episodeName = if (isMovie) null else ep.name,
            episodeDescription = ep.description ?: data.plot,
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
            val data = uiState.value.response ?: return@launch
            val patchedData = patchEpisodeData(ep, data)
            val history = buildWatchHistory(ep, data).copy(episodeId = patchedData)
            handlePlayRequest(Triple(provider, patchedData, history))
        }
    }

    private fun handleDownloadEpisode(ep: Episode) {
        val data = uiState.value.response ?: return
        val patchedData = patchEpisodeData(ep, data)
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
            val currentDataUrl = data.url
            val currentParentId = DesktopDataStore.watchHistoryId(provider.name, currentDataUrl)
            val fallbackParentId = DesktopDataStore.watchHistoryId(provider.name, url)

            removeWatchHistory.awaitByEpisode(currentParentId, ep.data)
            if (fallbackParentId != currentParentId) {
                removeWatchHistory.awaitByEpisode(fallbackParentId, ep.data)
            }
        }
    }

    private fun handleToggleEpisodeWatched(ep: Episode, isWatched: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: return@launch
            val currentDataUrl = data.url
            val currentParentId = DesktopDataStore.watchHistoryId(
                apiName = provider.name,
                showUrl = currentDataUrl,
            )
            val fallbackParentId = DesktopDataStore.watchHistoryId(
                apiName = provider.name,
                showUrl = url,
            )

            if (!isWatched) {
                DesktopDataStore.removeEpisodeWatched(currentParentId, ep.data)
                if (fallbackParentId != currentParentId) {
                    DesktopDataStore.removeEpisodeWatched(fallbackParentId, ep.data)
                }
                return@launch
            }

            val saved = DesktopDataStore.getEpisodeWatched(currentParentId, ep.data)
                ?: DesktopDataStore.getEpisodeWatched(fallbackParentId, ep.data)
            val dur = if (saved != null && saved.duration > 0L) saved.duration else 60L
            val isMovie = data is MovieLoadResponse
            val history = WatchHistory(
                parentId = currentParentId,
                showName = data.name,
                showUrl = data.url,
                apiName = provider.name,
                posterUrl = data.posterUrl,
                episodeThumbnailUrl = ep.posterUrl,
                screenshotUrl = saved?.screenshotUrl,
                episode = if (isMovie) null else ep.episode,
                season = if (isMovie) null else ep.season,
                episodeId = ep.data,
                position = dur,
                duration = dur,
                episodeName = if (isMovie) null else ep.name,
                episodeDescription = ep.description ?: data.plot,
            )
            DesktopDataStore.setLastWatched(history)

            val allEps = when (data) {
                is TvSeriesLoadResponse -> data.episodes
                is AnimeLoadResponse -> data.episodes.values.flatten()
                else -> emptyList()
            }
            val currentIdx = allEps.indexOfFirst { it.data == ep.data }
            if (currentIdx != -1 && currentIdx + 1 < allEps.size) {
                val nextEp = allEps[currentIdx + 1]
                val existingNext = DesktopDataStore.getEpisodeWatched(currentParentId, nextEp.data)
                    ?: DesktopDataStore.getEpisodeWatched(fallbackParentId, nextEp.data)
                if (existingNext == null) {
                    val nextEpHistory = WatchHistory(
                        parentId = currentParentId,
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
                        episodeName = nextEp.name,
                        episodeDescription = nextEp.description ?: data.plot,
                    )
                    DesktopDataStore.setLastWatched(nextEpHistory)
                } else if (existingNext.position < (existingNext.duration * 0.9)) {
                    DesktopDataStore.setLastWatched(
                        existingNext.copy(
                            updateTime = System.currentTimeMillis() + 1000,
                            episodeThumbnailUrl = existingNext.episodeThumbnailUrl ?: nextEp.posterUrl ?: data.posterUrl,
                            episodeName = nextEp.name,
                            episodeDescription = nextEp.description ?: data.plot,
                        ),
                    )
                }
            }
        }
    }

    private fun handleToggleSeasonWatched(episodes: List<Episode>, isWatched: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: return@launch
            val currentDataUrl = data.url
            val currentParentId = DesktopDataStore.watchHistoryId(provider.name, currentDataUrl)
            val fallbackParentId = DesktopDataStore.watchHistoryId(provider.name, url)

            if (isWatched) {
                // Marking as watched. Save backup of current states.
                val newBackupMap = mutableMapOf<String, WatchHistory>()
                val historiesToSave = mutableListOf<WatchHistory>()

                episodes.forEach { ep ->
                    val hist = uiState.value.watchHistory.values.find { (it.episodeId ?: "") == ep.data }
                    if (hist != null) {
                        newBackupMap[ep.data] = hist
                    }
                    val saved = DesktopDataStore.getEpisodeWatched(currentParentId, ep.data)
                        ?: DesktopDataStore.getEpisodeWatched(fallbackParentId, ep.data)
                    val dur = if (saved != null && saved.duration > 0L) saved.duration else 60L
                    val isMovie = data is MovieLoadResponse
                    historiesToSave.add(
                        WatchHistory(
                            parentId = currentParentId,
                            showName = data.name,
                            showUrl = data.url,
                            apiName = provider.name,
                            posterUrl = data.posterUrl,
                            episodeThumbnailUrl = ep.posterUrl,
                            screenshotUrl = saved?.screenshotUrl,
                            episode = if (isMovie) null else ep.episode,
                            season = if (isMovie) null else ep.season,
                            episodeId = ep.data,
                            position = dur,
                            duration = dur,
                            episodeName = if (isMovie) null else ep.name,
                            episodeDescription = ep.description ?: data.plot,
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
                        val existingNext = DesktopDataStore.getEpisodeWatched(currentParentId, nextEp.data)
                            ?: DesktopDataStore.getEpisodeWatched(fallbackParentId, nextEp.data)
                        if (existingNext == null) {
                            historiesToSave.add(
                                WatchHistory(
                                    parentId = currentParentId,
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
                                    episodeName = nextEp.name,
                                    episodeDescription = nextEp.description ?: data.plot,
                                ),
                            )
                        } else if (existingNext.position < (existingNext.duration * 0.9)) {
                            historiesToSave.add(
                                existingNext.copy(
                                    updateTime = System.currentTimeMillis() + 1000,
                                    episodeThumbnailUrl = existingNext.episodeThumbnailUrl ?: nextEp.posterUrl ?: data.posterUrl,
                                    episodeName = nextEp.name,
                                    episodeDescription = nextEp.description ?: data.plot,
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
                        val isMovie = data is MovieLoadResponse
                        historiesToRestore.add(
                            WatchHistory(
                                parentId = currentParentId,
                                showName = data.name,
                                showUrl = data.url,
                                apiName = provider.name,
                                posterUrl = data.posterUrl,
                                episodeThumbnailUrl = ep.posterUrl,
                                screenshotUrl = backup.screenshotUrl,
                                episode = if (isMovie) null else ep.episode,
                                season = if (isMovie) null else ep.season,
                                episodeId = ep.data,
                                position = backup.position,
                                duration = dur,
                                episodeName = backup.episodeName ?: if (isMovie) null else ep.name,
                                episodeDescription = backup.episodeDescription ?: ep.description ?: data.plot,
                            ),
                        )
                    } else {
                        episodesToRemove.add(ep.data)
                    }
                }
                if (historiesToRestore.isNotEmpty()) {
                    DesktopDataStore.setMultipleLastWatched(historiesToRestore)
                }
                if (episodesToRemove.isNotEmpty()) {
                    DesktopDataStore.removeMultipleEpisodesWatched(currentParentId, episodesToRemove)
                    if (fallbackParentId != currentParentId) {
                        DesktopDataStore.removeMultipleEpisodesWatched(fallbackParentId, episodesToRemove)
                    }
                }
                updateState { copy(backupSeasonHistory = emptyMap()) }
            }
        }
    }

    private fun handleToggleEpisodesStackedView(isStacked: Boolean) {
        val viewMode = if (isStacked) 1 else 0
        updateState { copy(isEpisodesStackedView = isStacked, episodeViewMode = viewMode) }
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey("pref_episodes_stacked_view", isStacked)
            DesktopDataStore.setKey("pref_episodes_view_mode", viewMode)
        }
    }

    private fun handleSetEpisodeViewMode(viewMode: Int) {
        val isStacked = viewMode != 0
        updateState { copy(episodeViewMode = viewMode, isEpisodesStackedView = isStacked) }
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey("pref_episodes_view_mode", viewMode)
            DesktopDataStore.setKey("pref_episodes_stacked_view", isStacked)
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
                        history = linkHistory.copy(episodeId = data.second),
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
