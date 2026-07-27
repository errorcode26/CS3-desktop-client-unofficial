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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

typealias DetailsUiStateAlias = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState

class DetailsViewModel(
    private val provider: MainAPI,
    private val url: String,
    private val preloadedName: String? = null,
    private val preloadedPoster: String? = null,
    private val preloadedBg: String? = null,
) : BaseMviViewModel<DetailsUiStateAlias, DetailsUiEvent, DetailsUiEffect>(
    initialState = DetailsUiStateAlias(
        preloadedName = preloadedName,
        response = DetailsCache.get(url),
        enrichedLogoUrl = DetailsCache.get(url)?.logoUrl,
        enrichedBackdropUrl = DetailsCache.get(url)?.backgroundPosterUrl,
        isLoading = DetailsCache.get(url) == null,
        autoPlayEnabled = DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true,
        isEpisodesStackedView = DesktopDataStore.getKey<Boolean>("pref_episodes_stacked_view") ?: false,
    ),
) {
    private val isInitialized = MutableStateFlow(false)
    private val backupSeasonHistory = java.util.concurrent.ConcurrentHashMap<String, WatchHistory>()

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
            is DetailsUiEvent.OnRequestAutoPlay -> handleAutoPlay()
            is DetailsUiEvent.OnPlayEpisode -> handlePlayEpisode(event.ep)
            is DetailsUiEvent.OnDownloadEpisode -> handleDownloadEpisode(event.ep)
            is DetailsUiEvent.OnToggleEpisodeWatched -> handleToggleEpisodeWatched(event.ep, event.isWatched)
            is DetailsUiEvent.OnRemoveEpisodeWatched -> handleRemoveEpisodeWatched(event.ep)
            is DetailsUiEvent.OnToggleSeasonWatched -> handleToggleSeasonWatched(event.episodes, event.isWatched)
            is DetailsUiEvent.OnToggleEpisodesStackedView -> handleToggleEpisodesStackedView(event.isStacked)
        }
    }

    fun load() {
        if (isInitialized.value) return
        isInitialized.value = true
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
                                enrichedLogoUrl = update.response.logoUrl,
                                enrichedBackdropUrl = update.response.backgroundPosterUrl,
                                isEnriching = true,
                                enrichmentPhase = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase.InProgress,
                            )
                        }
                    }
                    is EnrichmentUpdate.ExtractedColor -> {
                        updateState { copy(heroColor = androidx.compose.ui.graphics.Color(update.color.toULong())) }
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
                    is EnrichmentUpdate.ActorsLoaded -> {
                        updateState { copy(enrichedActors = update.actors) }
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
                        updateState { copy(isEnriching = false, enrichmentPhase = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase.Complete) }
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
        viewModelScope.launch(Dispatchers.IO) {
            val resp = uiState.value.response ?: uiState.value.fakeData ?: return@launch

            val firstEp = if (resp is TvSeriesLoadResponse) {
                resp.episodes.firstOrNull()
            } else if (resp is AnimeLoadResponse) {
                resp.episodes.values.firstOrNull()?.firstOrNull()
            } else if (resp is MovieLoadResponse) {
                provider.newEpisode(resp.dataUrl) {
                    this.name = resp.name
                    this.posterUrl = resp.posterUrl
                }
            } else {
                null
            }

            if (firstEp != null) {
                val history = buildWatchHistory(firstEp, resp)
                val patchedData = patchEpisodeData(firstEp, resp)
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
            episodeThumbnailUrl = ep.posterUrl,
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
            val dur = if (saved != null && saved.duration > 0L) saved.duration else 60_000L
            val newPos = if (isWatched) 0L else dur
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
        }
    }

    private fun handleToggleSeasonWatched(episodes: List<Episode>, isWatched: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = uiState.value.response ?: uiState.value.fakeData ?: return@launch
            val parentId = DesktopDataStore.watchHistoryId(provider.name, data.url)

            if (!isWatched) {
                // Marking as watched. Save backup of current states.
                backupSeasonHistory.clear()
                episodes.forEach { ep ->
                    val hist = uiState.value.watchHistory.values.find { it.episodeId == ep.data }
                    if (hist != null) {
                        backupSeasonHistory[ep.data] = hist
                    }
                    val saved = DesktopDataStore.getEpisodeWatched(parentId, ep.data)
                    val dur = if (saved != null && saved.duration > 0L) saved.duration else 60_000L
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
                        position = dur,
                        duration = dur,
                    )
                    DesktopDataStore.setLastWatched(history)
                }
            } else {
                // Unmarking. Restore from backup.
                episodes.forEach { ep ->
                    val backup = backupSeasonHistory[ep.data]
                    if (backup != null) {
                        DesktopDataStore.setLastWatched(backup)
                    } else {
                        DesktopDataStore.removeEpisodeWatched(parentId, ep.data)
                    }
                }
                backupSeasonHistory.clear()
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
