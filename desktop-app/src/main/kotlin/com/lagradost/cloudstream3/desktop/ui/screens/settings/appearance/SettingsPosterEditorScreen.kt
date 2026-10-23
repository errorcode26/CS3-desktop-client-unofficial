package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.data.history.WatchHistoryRepositoryImpl
import com.lagradost.cloudstream3.desktop.domain.history.interactor.GetContinueWatching
import com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard
import com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardDetailed
import com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardWide
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle
import com.lagradost.cloudstream3.desktop.ui.theme.PosterMetadataPosition
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTextAlignment
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition
import com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsPosterEditorScreen(onBack: () -> Unit = {}) {
    val theme = LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val posterRoundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val posterTitlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val posterMetadataPosition by AppearanceConfig.posterMetadataPosition.collectAsState()
    val posterTextAlignment by AppearanceConfig.posterTextAlignment.collectAsState()
    val posterTitleMaxLines by AppearanceConfig.posterTitleMaxLines.collectAsState()
    val continueWatchingStyle by AppearanceConfig.continueWatchingStyle.collectAsState()
    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()

    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()

    var isInspectorOpen by remember { mutableStateOf(false) }
    var activeControlTab by remember { mutableStateOf(0) }

    val placeholderApi = remember {
        object : MainAPI() {
            override var mainUrl = ""
            override var name = "Preview"
            override val hasMainPage = true
        }
    }

    val initialMovies = remember {
        listOf(
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
    }

    val initialSeries = remember {
        listOf(
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

    val watchHistoryRepo = remember { WatchHistoryRepositoryImpl() }
    val getContinueWatching = remember(watchHistoryRepo) { GetContinueWatching(watchHistoryRepo) }
    val realHistory by getContinueWatching.subscribe().collectAsState(initial = emptyList())

    var liveMovies by remember { mutableStateOf<List<SearchResponse>>(initialMovies) }
    var liveSeries by remember { mutableStateOf<List<SearchResponse>>(initialSeries) }
    var activeProvider by remember { mutableStateOf<MainAPI?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
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
                    com.lagradost.common.logging.AppLogger.w("PosterStudio", "Failed loading addon ${provider.name}: ${e.message}")
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
                    com.lagradost.common.logging.AppLogger.w("PosterStudio", "Failed loading from TMDB: ${e.message}")
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

                liveMovies = if (movies.isNotEmpty()) movies.take(16) else enrichedResponses.take(16)
                liveSeries = if (series.isNotEmpty()) series.take(16) else enrichedResponses.drop(8).take(16).ifEmpty { enrichedResponses.take(16) }
                activeProvider = loadedProvider
            }
        }
    }

    // Prepare Continue Watching items (real user items if available, or generated preview cards)
    val displayHistory: List<WatchHistory> = remember(realHistory, liveMovies) {
        if (realHistory.isNotEmpty()) {
            realHistory.take(4)
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

    val currentSpacing = homeSpacingDp.dp
    val currentWidth = posterWidthDp.dp
    val currentVerticalSpacing = homeVerticalSpacingDp.dp

    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 88.dp else 22.dp
    val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 88.dp else 22.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableWidth = this.maxWidth
        val isCompact = availableWidth < 600.dp
        val effectivePaddingStart = if (isCompact) 8.dp else paddingStart
        val effectivePaddingEnd = if (isCompact) 8.dp else paddingEnd
        val spacingDp = if (isCompact) 8.dp else currentSpacing

        val optimalItemWidth = if (isCompact) {
            115.dp
        } else {
            val baseWidth = currentWidth
            val netWidth = availableWidth - effectivePaddingStart - effectivePaddingEnd - 20.dp
            val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
            val columns = exactColumns.toInt().coerceAtLeast(1)
            ((netWidth + spacingDp) / columns) - spacingDp
        }

        // 1. Full-Width Scrollable Canvas
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 70.dp, bottom = 48.dp)
                .padding(start = effectivePaddingStart, end = effectivePaddingEnd),
            verticalArrangement = Arrangement.spacedBy(currentVerticalSpacing),
        ) {
            // Row 1: Continue Watching Shelf
            if (displayHistory.isNotEmpty()) {
                CategoryRowWithHeader(
                    title = "Continue Watching",
                    itemCount = displayHistory.size,
                    rowContentPadding = PaddingValues(
                        horizontal = if (isCompact) 4.dp else 10.dp,
                        vertical = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                    ),
                    headerPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                        bottom = 4.dp,
                    ),
                    itemSpacing = spacingDp,
                ) {
                    items(displayHistory.size) { index ->
                        val history = displayHistory[index]
                        when (continueWatchingStyle) {
                            ContinueWatchingStyle.PREMIUM -> {
                                val cardWidth = if (isCompact) 280.dp else 340.dp
                                val cardHeight = if (isCompact) 125.dp else 140.dp
                                WatchHistoryCardWide(
                                    modifier = Modifier.width(cardWidth).height(cardHeight),
                                    history = history,
                                    provider = activeProvider,
                                    onRemove = {},
                                    onClick = {},
                                    onPlayClick = {},
                                )
                            }
                            ContinueWatchingStyle.DETAILED -> {
                                val cardWidth = if (isCompact) 280.dp else 350.dp
                                val cardHeight = if (isCompact) 105.dp else 115.dp
                                WatchHistoryCardDetailed(
                                    modifier = Modifier.width(cardWidth).height(cardHeight),
                                    history = history,
                                    provider = activeProvider,
                                    onRemove = {},
                                    onClick = {},
                                    onPlayClick = {},
                                )
                            }
                            ContinueWatchingStyle.THUMBNAIL -> {
                                val cardWidth = if (isCompact) 230.dp else 380.dp
                                val cardHeight = cardWidth * 9f / 16f
                                WatchHistoryCard(
                                    modifier = Modifier.width(cardWidth).height(cardHeight),
                                    history = history,
                                    provider = activeProvider,
                                    onRemove = {},
                                    onClick = {},
                                    onPlayClick = {},
                                )
                            }
                        }
                    }
                }
            }

            // Row 2: Trending / Popular Shelves
            if (liveMovies.isNotEmpty()) {
                CategoryRowWithHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = if (activeProvider != null) "Featured from ${activeProvider?.name}" else "Trending Content",
                    itemCount = liveMovies.size,
                    rowContentPadding = PaddingValues(
                        horizontal = if (isCompact) 4.dp else 10.dp,
                        vertical = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                    ),
                    headerPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                        bottom = 4.dp,
                    ),
                    itemSpacing = spacingDp,
                ) {
                    items(liveMovies.size) { index ->
                        PosterCard(
                            item = liveMovies[index],
                            provider = activeProvider,
                            itemWidth = optimalItemWidth,
                            onClick = {},
                            onPlayClick = {},
                        )
                    }
                }
            }

            if (liveSeries.isNotEmpty()) {
                CategoryRowWithHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Popular Series & Shows",
                    itemCount = liveSeries.size,
                    rowContentPadding = PaddingValues(
                        horizontal = if (isCompact) 4.dp else 10.dp,
                        vertical = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                    ),
                    headerPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                        bottom = 4.dp,
                    ),
                    itemSpacing = spacingDp,
                ) {
                    items(liveSeries.size) { index ->
                        PosterCard(
                            item = liveSeries[index],
                            provider = activeProvider,
                            itemWidth = optimalItemWidth,
                            onClick = {},
                            onPlayClick = {},
                        )
                    }
                }
            }
        }

        // 2. Top Floating Glass Header (Presets & Fast Controls)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    start = effectivePaddingStart + 10.dp,
                    end = effectivePaddingEnd + 10.dp,
                    top = 10.dp,
                )
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = theme.SurfaceElevated.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.6f)),
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.SurfaceCard.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onBack() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.TextPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Back",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        "Poster Workshop Studio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "Live",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))
                VerticalDivider(modifier = Modifier.height(20.dp), color = theme.Divider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = posterTextAlignment == PosterTextAlignment.LEFT &&
                            posterMetadataPosition == PosterMetadataPosition.BELOW_POSTER &&
                            posterTitleMaxLines == 2,
                        onClick = {
                            AppearanceConfig.setPosterTextAlignment(PosterTextAlignment.LEFT)
                            AppearanceConfig.setPosterMetadataPosition(PosterMetadataPosition.BELOW_POSTER)
                            AppearanceConfig.setPosterTitlePosition(PosterTitlePosition.BELOW)
                            AppearanceConfig.setPosterTitleMaxLines(2)
                            AppearanceConfig.setPosterRoundingDp(10)
                            CardMetadataConfig.setAutoCleanTitles(true)
                        },
                        label = { Text("🍎 Infuse Clean", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )

                    FilterChip(
                        selected = posterTextAlignment == PosterTextAlignment.CENTER &&
                            posterMetadataPosition == PosterMetadataPosition.BELOW_POSTER,
                        onClick = {
                            AppearanceConfig.setPosterTextAlignment(PosterTextAlignment.CENTER)
                            AppearanceConfig.setPosterMetadataPosition(PosterMetadataPosition.BELOW_POSTER)
                            AppearanceConfig.setPosterTitlePosition(PosterTitlePosition.BELOW)
                        },
                        label = { Text("🎬 Centered", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )

                    FilterChip(
                        selected = posterMetadataPosition == PosterMetadataPosition.ON_POSTER,
                        onClick = {
                            AppearanceConfig.setPosterMetadataPosition(PosterMetadataPosition.ON_POSTER)
                            AppearanceConfig.setPosterTitlePosition(PosterTitlePosition.BELOW)
                        },
                        label = { Text("🏷️ On Poster", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )

                    FilterChip(
                        selected = posterTitleMaxLines == 1 && posterTextAlignment == PosterTextAlignment.LEFT,
                        onClick = {
                            AppearanceConfig.setPosterTextAlignment(PosterTextAlignment.LEFT)
                            AppearanceConfig.setPosterTitleMaxLines(1)
                            AppearanceConfig.setPosterMetadataPosition(PosterMetadataPosition.BELOW_POSTER)
                            AppearanceConfig.setPosterTitlePosition(PosterTitlePosition.BELOW)
                            AppearanceConfig.setPosterWidthDp(135)
                        },
                        label = { Text("⚡ Ultra Compact", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                FilterChip(
                    selected = isInspectorOpen,
                    onClick = { isInspectorOpen = !isInspectorOpen },
                    leadingIcon = {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    label = {
                        Text(
                            if (isInspectorOpen) "Close Sliders" else "Fine-Tune Sliders",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        AppearanceConfig.setPosterWidthDp(160)
                        AppearanceConfig.setHomeSpacingDp(12)
                        AppearanceConfig.setHomeVerticalSpacingDp(16)
                        AppearanceConfig.setPosterRoundingDp(12)
                        AppearanceConfig.setPosterTitlePosition(PosterTitlePosition.BELOW)
                        AppearanceConfig.setPosterMetadataPosition(PosterMetadataPosition.BELOW_POSTER)
                        AppearanceConfig.setPosterTextAlignment(PosterTextAlignment.LEFT)
                        AppearanceConfig.setPosterTitleMaxLines(2)
                        AppearanceConfig.setContinueWatchingStyle(ContinueWatchingStyle.PREMIUM)
                        AppearanceConfig.setProviderBadgeDisplayMode(ProviderBadgeDisplayMode.HIDDEN)
                        AppearanceConfig.setPosterHoverGlowEnabled(true)
                        CardMetadataConfig.setAutoCleanTitles(true)
                        CardMetadataConfig.setAutoDetectSubDub(true)
                        CardMetadataConfig.setAutoDetectQuality(true)
                        CardMetadataConfig.setShowRatingBadges(true)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 3. Right Collapsible Inspector Drawer (Zero Poster Occlusion)
        androidx.compose.animation.AnimatedVisibility(
            visible = isInspectorOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .padding(top = 70.dp, bottom = 14.dp, end = 14.dp),
                shape = RoundedCornerShape(16.dp),
                color = theme.SurfaceElevated,
                border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.6f)),
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    // Header of Inspector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Studio Inspector",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                            )
                        }
                        IconButton(
                            onClick = { isInspectorOpen = false },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Inspector",
                                tint = theme.TextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Segmented Tabs: Dimensions | Style | Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = activeControlTab == 0,
                            onClick = { activeControlTab = 0 },
                            label = { Text("Sizes", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = activeControlTab == 1,
                            onClick = { activeControlTab = 1 },
                            label = { Text("Style", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = activeControlTab == 2,
                            onClick = { activeControlTab = 2 },
                            label = { Text("Badges", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = theme.Divider.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable Inspector Body
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (activeControlTab) {
                            0 -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    // Poster Width
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Poster Width", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("$posterWidthDp dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = posterWidthDp.toFloat(),
                                            onValueChange = { AppearanceConfig.setPosterWidthDp(it.toInt()) },
                                            valueRange = 100f..250f,
                                            steps = 29,
                                        )
                                    }

                                    // Card Spacing
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Card Spacing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("$homeSpacingDp dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = homeSpacingDp.toFloat(),
                                            onValueChange = { AppearanceConfig.setHomeSpacingDp(it.toInt()) },
                                            valueRange = 0f..32f,
                                            steps = 15,
                                        )
                                    }

                                    // Row Spacing
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Row Spacing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("$homeVerticalSpacingDp dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = homeVerticalSpacingDp.toFloat(),
                                            onValueChange = { AppearanceConfig.setHomeVerticalSpacingDp(it.toInt()) },
                                            valueRange = 0f..64f,
                                            steps = 31,
                                        )
                                    }

                                    // Corner Radius
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Corner Radius", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("$posterRoundingDp dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = posterRoundingDp.toFloat(),
                                            onValueChange = { AppearanceConfig.setPosterRoundingDp(it.toInt()) },
                                            valueRange = 0f..24f,
                                            steps = 23,
                                        )
                                    }
                                }
                            }
                            1 -> {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    // Poster Title Position
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Poster Title Position", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            listOf(
                                                PosterTitlePosition.BELOW to "Below",
                                                PosterTitlePosition.INSIDE to "Hover",
                                                PosterTitlePosition.HIDDEN to "Hidden",
                                            ).forEach { (pos, label) ->
                                                FilterChip(
                                                    selected = posterTitlePosition == pos,
                                                    onClick = { AppearanceConfig.setPosterTitlePosition(pos) },
                                                    label = {
                                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }

                                    // Poster Metadata Position
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Poster Metadata Position", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                        val metaPositions = listOf(
                                            PosterMetadataPosition.BELOW_POSTER to "Below",
                                            PosterMetadataPosition.ON_POSTER to "On Card",
                                            PosterMetadataPosition.HYBRID to "Hybrid",
                                            PosterMetadataPosition.STEALTH_HOVER to "Hover",
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            metaPositions.take(2).forEach { (pos, label) ->
                                                FilterChip(
                                                    selected = posterMetadataPosition == pos,
                                                    onClick = { AppearanceConfig.setPosterMetadataPosition(pos) },
                                                    label = {
                                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            metaPositions.drop(2).forEach { (pos, label) ->
                                                FilterChip(
                                                    selected = posterMetadataPosition == pos,
                                                    onClick = { AppearanceConfig.setPosterMetadataPosition(pos) },
                                                    label = {
                                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }

                                    // Poster Text Alignment
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Poster Text Alignment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            listOf(
                                                PosterTextAlignment.LEFT to "Left (Infuse)",
                                                PosterTextAlignment.CENTER to "Center",
                                            ).forEach { (align, label) ->
                                                FilterChip(
                                                    selected = posterTextAlignment == align,
                                                    onClick = { AppearanceConfig.setPosterTextAlignment(align) },
                                                    label = {
                                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }

                                    // Title Line Clamping
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Title Line Clamping", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            listOf(
                                                1 to "1 Line",
                                                2 to "2 Lines",
                                            ).forEach { (lines, label) ->
                                                FilterChip(
                                                    selected = posterTitleMaxLines == lines,
                                                    onClick = { AppearanceConfig.setPosterTitleMaxLines(lines) },
                                                    label = {
                                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }

                                    // Continue Watching Style
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Continue Watching Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            listOf(
                                                ContinueWatchingStyle.PREMIUM to "Wide",
                                                ContinueWatchingStyle.DETAILED to "Detailed",
                                                ContinueWatchingStyle.THUMBNAIL to "Classic",
                                            ).forEach { (style, label) ->
                                                FilterChip(
                                                    selected = continueWatchingStyle == style,
                                                    onClick = { AppearanceConfig.setContinueWatchingStyle(style) },
                                                    label = {
                                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }

                                    // Hover Ambient Glow
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Hover Glow", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("Ambient light beneath cards", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Switch(
                                                checked = posterHoverGlowEnabled,
                                                onCheckedChange = { AppearanceConfig.setPosterHoverGlowEnabled(it) },
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Smart Title Sanitization
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Clean Titles", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("Strip [4K], release tags", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Switch(
                                                checked = autoCleanTitles,
                                                onCheckedChange = { CardMetadataConfig.setAutoCleanTitles(it) },
                                            )
                                        }
                                    }

                                    // Rating Badges
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Rating Badges (★)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("Show score pill", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Switch(
                                                checked = showRatingBadges,
                                                onCheckedChange = { CardMetadataConfig.setShowRatingBadges(it) },
                                            )
                                        }
                                    }

                                    // Quality Badges
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Quality Badges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("4K, UHD, 1080p tags", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Switch(
                                                checked = autoDetectQuality,
                                                onCheckedChange = { CardMetadataConfig.setAutoDetectQuality(it) },
                                            )
                                        }
                                    }

                                    // SUB / DUB Badges
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("SUB / DUB Badges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("Audio & subtitle flags", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Switch(
                                                checked = autoDetectSubDub,
                                                onCheckedChange = { CardMetadataConfig.setAutoDetectSubDub(it) },
                                            )
                                        }
                                    }

                                    // Provider Badges
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text("Provider Badges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf(
                                                    ProviderBadgeDisplayMode.HIDDEN to "Hidden",
                                                    ProviderBadgeDisplayMode.ICON_ONLY to "Icon",
                                                    ProviderBadgeDisplayMode.FULL_BADGE to "Full",
                                                ).forEach { (mode, label) ->
                                                    FilterChip(
                                                        selected = providerBadgeDisplayMode == mode,
                                                        onClick = { AppearanceConfig.setProviderBadgeDisplayMode(mode) },
                                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                                        shape = RoundedCornerShape(8.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
