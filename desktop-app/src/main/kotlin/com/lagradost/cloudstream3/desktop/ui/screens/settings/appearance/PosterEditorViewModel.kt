package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.data.history.WatchHistoryRepositoryImpl
import com.lagradost.cloudstream3.desktop.domain.history.interactor.GetContinueWatching
import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class PosterEditorUiState(
    val liveMovies: List<SearchResponse> = emptyList(),
    val liveSeries: List<SearchResponse> = emptyList(),
    val activeProvider: MainAPI? = null,
    val continueWatching: List<WatchHistory> = emptyList(),
    val isLoading: Boolean = false,
) : UiState {
    val displayHistory: List<WatchHistory>
        get() = if (continueWatching.isNotEmpty()) {
            continueWatching.take(4)
        } else {
            liveMovies.take(2).mapIndexed { idx, item ->
                WatchHistory(
                    parentId = "preview_hist_$idx",
                    showName = item.name,
                    showUrl = item.url,
                    apiName = item.apiName,
                    posterUrl = item.posterUrl,
                    episodeThumbnailUrl = null,
                    screenshotUrl = null,
                    episode = if (item.type == TvType.TvSeries) idx + 1 else null,
                    season = if (item.type == TvType.TvSeries) 1 else null,
                    episodeId = "preview_ep_$idx",
                    position = 1800L * (idx + 1),
                    duration = 3600L,
                    episodeName = if (item.type == TvType.TvSeries) "Episode ${idx + 1}" else null,
                    episodeDescription = "Preview in-progress stream playback progress indicator.",
                )
            }
        }
}

sealed interface PosterEditorUiEvent : UiEvent {
    data object RefreshPreviews : PosterEditorUiEvent
}

sealed interface PosterEditorUiEffect : UiEffect

class PosterEditorViewModel(
    private val watchHistoryRepo: WatchHistoryRepository = WatchHistoryRepositoryImpl(),
    private val getContinueWatching: GetContinueWatching = GetContinueWatching(watchHistoryRepo),
) : BaseMviViewModel<PosterEditorUiState, PosterEditorUiEvent, PosterEditorUiEffect>(
    initialState = PosterEditorUiState(
        liveMovies = defaultInitialMovies,
        liveSeries = defaultInitialSeries,
    ),
) {
    init {
        viewModelScope.launch {
            getContinueWatching.subscribe().collect { history ->
                updateState { copy(continueWatching = history) }
            }
        }
        loadPreviewContent()
    }

    override fun handleEvent(event: PosterEditorUiEvent) {
        when (event) {
            is PosterEditorUiEvent.RefreshPreviews -> loadPreviewContent()
        }
    }

    private fun loadPreviewContent() {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isLoading = true) }
            try {
                val candidateProviders = ActiveProviderRepository.activeProviders.value
                    .filter { it.hasMainPage && ActiveProviderRepository.isRealContentProvider(it) }
                    .ifEmpty { APIHolder.allProviders.filter { it.hasMainPage && it.name != "NONE" } }

                var loadedProvider: MainAPI? = null
                val allResponses = mutableListOf<SearchResponse>()

                // 1. Query Installed / Active Addons
                for (provider in candidateProviders) {
                    try {
                        if (provider.mainPage.isNotEmpty()) {
                            for (pageData in provider.mainPage.take(4)) {
                                val pageResponse = provider.getMainPage(1, MainPageRequest(pageData.name, pageData.data, pageData.horizontalImages))
                                allResponses.addAll(pageResponse?.items?.flatMap { it.list } ?: emptyList())
                            }
                        } else {
                            val pageResponse = provider.getMainPage(1, MainPageRequest("", "", false))
                            allResponses.addAll(pageResponse?.items?.flatMap { it.list } ?: emptyList())
                        }
                        if (allResponses.isNotEmpty()) {
                            loadedProvider = provider
                            break
                        }
                    } catch (e: Exception) {
                        AppLogger.w("PosterStudio", "Failed loading addon ${provider.name}: ${e.message}")
                    }
                }

                // 2. Query Built-in TMDB MetaProvider
                if (allResponses.isEmpty()) {
                    try {
                        val tmdb = TmdbProvider()
                        val tmdbResponse = tmdb.getMainPage(1, MainPageRequest("", "", false))
                        val items = tmdbResponse.items.flatMap { it.list }
                        if (items.isNotEmpty()) {
                            allResponses.addAll(items)
                            loadedProvider = tmdb
                        }
                    } catch (e: Exception) {
                        AppLogger.w("PosterStudio", "Failed loading from TMDB: ${e.message}")
                    }
                }

                if (allResponses.isNotEmpty()) {
                    val api = loadedProvider ?: placeholderApi
                    // Enrich real titles with diverse badge tokens to test quality and sub/dub switches
                    val enrichedResponses = allResponses.mapIndexed { idx, resp ->
                        val rawTitle = resp.name
                        val enrichedTitle = when (idx % 4) {
                            0 -> "$rawTitle [4K] [HDR] [Dual-Audio]"
                            1 -> "$rawTitle [SUB] [4K] [WEB-DL]"
                            2 -> "$rawTitle [DUB] [1080p.HEVC]"
                            else -> "$rawTitle [SUB] [DUB] [1080p]"
                        }
                        val itemType = resp.type ?: TvType.Movie
                        if (itemType == TvType.TvSeries || itemType == TvType.Anime) {
                            api.newTvSeriesSearchResponse(enrichedTitle, resp.url, itemType, false) {
                                this.posterUrl = resp.posterUrl
                                this.score = resp.score ?: Score.from10(8.5)
                                this.quality = if (idx % 2 == 0) SearchQuality.UHD else SearchQuality.HD
                            }
                        } else {
                            api.newMovieSearchResponse(enrichedTitle, resp.url, itemType, false) {
                                this.posterUrl = resp.posterUrl
                                this.score = resp.score ?: Score.from10(8.5)
                                this.quality = if (idx % 2 == 0) SearchQuality.UHD else SearchQuality.HD
                            }
                        }
                    }

                    val movies = enrichedResponses.filter { it.type == TvType.Movie }
                    val series = enrichedResponses.filter { it.type == TvType.TvSeries || it.type == TvType.Anime }

                    val finalMovies = if (movies.isNotEmpty()) movies.take(16) else enrichedResponses.take(16)
                    val finalSeries = if (series.isNotEmpty()) series.take(16) else enrichedResponses.drop(8).take(16).ifEmpty { enrichedResponses.take(16) }

                    updateState {
                        copy(
                            liveMovies = finalMovies,
                            liveSeries = finalSeries,
                            activeProvider = loadedProvider,
                            isLoading = false,
                        )
                    }
                } else {
                    updateState { copy(isLoading = false) }
                }
            } catch (e: Exception) {
                AppLogger.w("PosterStudio", "Failed in loadPreviewContent: ${e.message}")
                updateState { copy(isLoading = false) }
            }
        }
    }

    companion object {
        private val placeholderApi = object : MainAPI() {
            override var mainUrl = ""
            override var name = "Preview"
            override val hasMainPage = true
        }

        val defaultInitialMovies: List<SearchResponse> = listOf(
            placeholderApi.newMovieSearchResponse("Dune [4K] [HDR] [Dual-Audio]", "preview_m_1", TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(8.6)
            },
            placeholderApi.newMovieSearchResponse("Oppenheimer [4K] [IMAX.x265]", "preview_m_2", TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(8.9)
            },
            placeholderApi.newMovieSearchResponse("The Batman [4K] [HDR10]", "preview_m_3", TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(7.9)
            },
            placeholderApi.newMovieSearchResponse("Interstellar [4K] [Remastered.UHD]", "preview_m_4", TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(8.7)
            },
            placeholderApi.newMovieSearchResponse("Alien: Romulus [1080p.HEVC.Dual-Audio]", "preview_m_5", TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/b33nnKl1vGa6kKK4a3GQU0IO4a.jpg"
                quality = SearchQuality.HD
                score = Score.from10(7.3)
            },
            placeholderApi.newMovieSearchResponse("Deadpool & Wolverine [4K] [HDR]", "preview_m_6", TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(7.8)
            },
        )

        val defaultInitialSeries: List<SearchResponse> = listOf(
            placeholderApi.newTvSeriesSearchResponse("Arcane [SUB] [4K] [WEB-DL.x265]", "preview_s_1", TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/fqldf2t8ztc9aiwn396nlv8g9qc.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(9.1)
            },
            placeholderApi.newTvSeriesSearchResponse("House of the Dragon [4K] [HDR] [Dual-Audio]", "preview_s_2", TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(8.9)
            },
            placeholderApi.newTvSeriesSearchResponse("Shōgun [DUB] [1080p.HEVC.Multi-Audio]", "preview_s_3", TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg"
                quality = SearchQuality.HD
                score = Score.from10(8.8)
            },
            placeholderApi.newTvSeriesSearchResponse("Severance [SUB] [4K] [WEB-DL.Atmos]", "preview_s_4", TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8t4fF2k9YvW1F7yW71c5Mv2M8k7.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(8.7)
            },
            placeholderApi.newTvSeriesSearchResponse("Solo Leveling [SUB] [DUB] [1080p.FHD]", "preview_s_5", TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nx11H.jpg"
                quality = SearchQuality.HD
                score = Score.from10(8.4)
            },
            placeholderApi.newTvSeriesSearchResponse("Breaking Bad [4K] [Remastered.UHD]", "preview_s_6", TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg"
                quality = SearchQuality.UHD
                score = Score.from10(9.5)
            },
        )
    }
}
