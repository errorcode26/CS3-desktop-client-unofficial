package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.crossfade
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.DesktopThemeColors.*
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.details.*
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.player.impl.PlayerLinkHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDetailsScreen(
    onNavigate: (Config) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailsViewModel,
    autoPlay: Boolean = false,
) {
    val provider = viewModel.provider
    LaunchedEffect(viewModel) {
        viewModel.onEvent(DetailsUiEvent.OnLoad)
    }

    DisposableEffect(viewModel) {
        val unregister = com.lagradost.cloudstream3.desktop.ui.GlobalRefreshHandler.register {
            viewModel.onEvent(DetailsUiEvent.OnRefresh)
        }
        onDispose { unregister() }
    }

    val uiState by viewModel.uiState.collectAsState()
    val fetchFailed = uiState.fetchFailed
    val showHistory = uiState.watchHistory

    val response = uiState.response
    val fakeData = uiState.fakeData
    val isLoading = uiState.isLoading
    val error = uiState.error
    val isPanelOpen = uiState.isPanelOpen
    val enrichmentPhase = uiState.enrichmentPhase
    val activeLinkData = uiState.activeLinkData
    val screenshots = uiState.screenshots
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()

    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current

    val handlePlay: (com.lagradost.cloudstream3.Episode) -> Unit = remember(viewModel) {
        { ep -> viewModel.onEvent(DetailsUiEvent.OnPlayEpisode(ep)) }
    }

    val handleDownload: (com.lagradost.cloudstream3.Episode) -> Unit = remember(viewModel) {
        { ep -> viewModel.onEvent(DetailsUiEvent.OnDownloadEpisode(ep)) }
    }

    val handleToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit = remember(viewModel) {
        { ep, isWatched -> viewModel.onEvent(DetailsUiEvent.OnToggleEpisodeWatched(ep, isWatched)) }
    }

    val handleToggleSeasonWatched: (List<com.lagradost.cloudstream3.Episode>, Boolean) -> Unit = remember(viewModel) {
        { episodes, isWatched -> viewModel.onEvent(DetailsUiEvent.OnToggleSeasonWatched(episodes, isWatched)) }
    }
    val handleRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit = remember(viewModel) {
        { ep -> viewModel.onEvent(DetailsUiEvent.OnRemoveEpisodeWatched(ep)) }
    }
    val handleToggleEpisodesStackedView: (Boolean) -> Unit = remember(viewModel) {
        { isStacked -> viewModel.onEvent(DetailsUiEvent.OnToggleEpisodesStackedView(isStacked)) }
    }
    val handleSetEpisodeViewMode: (Int) -> Unit = remember(viewModel) {
        { viewMode -> viewModel.onEvent(DetailsUiEvent.OnSetEpisodeViewMode(viewMode)) }
    }

    LaunchedEffect(viewModel.effectFlow) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is DetailsUiEffect.NavigateToPlayer -> playVideo(effect.launchData)
                is DetailsUiEffect.ShowErrorDialog -> viewModel.onEvent(DetailsUiEvent.OnShowPlaybackError(effect.message))
                is DetailsUiEffect.ShowToast -> com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(effect.message)
            }
        }
    }

    com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
        show = uiState.playbackError != null,
        onDismissRequest = { viewModel.onEvent(DetailsUiEvent.OnDismissPlaybackError) },
        title = { Text("Playback Failed") },
        text = { Text(uiState.playbackError ?: "Unknown error") },
        confirmButton = {
            TextButton(onClick = { viewModel.onEvent(DetailsUiEvent.OnDismissPlaybackError) }) {
                Text("OK")
            }
        },
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(response, uiState.hasAutoPlayed) {
            if (!uiState.hasAutoPlayed && response != null) {
                if (autoPlay) {
                    viewModel.onEvent(DetailsUiEvent.OnRequestAutoPlay)
                } else {
                    // Mark as handled so it never triggers if the state recombines
                    viewModel.onEvent(DetailsUiEvent.OnMarkAutoPlayHandled)
                }
            }
        }
        val screensaverEnabled by AppearanceConfig.screensaverEnabled.collectAsState()
        val screenshotsList = screenshots ?: emptyList()
        var currentScreenshotIndex by remember { mutableStateOf(-1) }

        LaunchedEffect(screensaverEnabled, screenshotsList) {
            if (screenshotsList.isEmpty()) {
                currentScreenshotIndex = -1
                return@LaunchedEffect
            }
            if (currentScreenshotIndex < 0) {
                currentScreenshotIndex = 0
            }
            if (screensaverEnabled) {
                while (isActive) {
                    kotlinx.coroutines.delay(10_000)
                    currentScreenshotIndex = (currentScreenshotIndex + 1) % screenshotsList.size
                }
            }
        }

        val baseBgUrl = remember(response?.backgroundPosterUrl, response?.posterUrl, uiState.enrichedBackdropUrl, provider) {
            uiState.enrichedBackdropUrl?.takeIf { it.isNotBlank() }
                ?: provider.fixUrlNull(response?.backgroundPosterUrl)?.takeIf { it.isNotBlank() }
                ?: provider.fixUrlNull(response?.posterUrl)?.takeIf { it.isNotBlank() }
        }

        val activeBgUrl = if (currentScreenshotIndex >= 0 && screenshotsList.isNotEmpty()) {
            screenshotsList[currentScreenshotIndex]
        } else {
            baseBgUrl
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (heroBackgroundBlurEnabled && activeBgUrl != null) {
                androidx.compose.animation.Crossfade(
                    targetState = activeBgUrl,
                    animationSpec = androidx.compose.animation.core.tween(2000),
                    label = "global_backdrop_crossfade",
                    modifier = Modifier.fillMaxSize(),
                ) { targetBgUrl ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        coil3.compose.AsyncImage(
                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                .data(targetBgUrl)
                                .size(640, 360)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(heroBackdropBlurRadius.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                        )
                        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = heroBackdropDarkening)))
                    }
                }
            }
            val enableDownloadButtons = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.common.storage.DesktopDataStore.PREF_ENABLE_DOWNLOAD_BUTTONS) ?: true
            val currentStage = remember(isLoading, response, fetchFailed) {
                when {
                    fetchFailed && response == null -> com.lagradost.cloudstream3.desktop.ui.components.ScreenStage.ERROR
                    response != null -> com.lagradost.cloudstream3.desktop.ui.components.ScreenStage.CONTENT
                    isLoading -> com.lagradost.cloudstream3.desktop.ui.components.ScreenStage.LOADING
                    else -> com.lagradost.cloudstream3.desktop.ui.components.ScreenStage.ERROR
                }
            }

            com.lagradost.cloudstream3.desktop.ui.components.ScreenStateCrossfade(
                stage = currentStage,
                modifier = Modifier.fillMaxSize(),
                loadingContent = {
                    com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsLoadingView(
                        onBack = onBack,
                        preloadedName = viewModel.preloadedName,
                        providerName = provider.name,
                    )
                },
                content = {
                    val activeData = response ?: fakeData
                    if (activeData != null) {
                        DetailsContent(
                            onNavigate = onNavigate,
                            onBack = onBack,
                            provider = provider,
                            data = activeData,
                            screenshots = screenshots,
                            enrichmentPhase = enrichmentPhase,
                            isLoading = false,
                            onPlay = handlePlay,
                            onDownload = handleDownload,
                            enableDownloadButtons = enableDownloadButtons,
                            onToggleWatched = handleToggleWatched,
                            onToggleSeasonWatched = handleToggleSeasonWatched,
                            onRemoveEpisodeWatched = handleRemoveEpisodeWatched,
                            onToggleEpisodesStackedView = handleToggleEpisodesStackedView,
                            onSetEpisodeViewMode = handleSetEpisodeViewMode,
                            dynamicColorEnabled = heroBackgroundBlurEnabled,
                            uiState = uiState,
                            showHistory = showHistory,
                            activeBgUrl = activeBgUrl,
                            onEvent = viewModel::onEvent,
                        )
                    }
                },
                errorContent = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .padding(24.dp)
                                .widthIn(max = 560.dp)
                                .fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Failed to load details from ${provider.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                if (!error.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = error,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.onEvent(DetailsUiEvent.OnRefresh) },
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry")
                                    }
                                    Button(
                                        onClick = onBack,
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text("Go Back")
                                    }
                                }
                            }
                        }
                    }
                },
            )

            AnimatedVisibility(
                visible = isPanelOpen,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { viewModel.onEvent(DetailsUiEvent.OnCloseLinksPanel) },
                )
            }

            if (activeLinkData != null) {
                val panelWidth = minOf(620.dp, maxWidth * 0.95f)
                val offsetX by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isPanelOpen) 0.dp else panelWidth + 20.dp,
                    animationSpec = tween(300),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = offsetX)
                        .fillMaxHeight()
                        .width(panelWidth)
                        .shadow(24.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0C0C14).copy(alpha = 0.95f),
                                    Color(0xFF161622).copy(alpha = 0.98f),
                                ),
                            ),
                        ),
                ) {
                    activeLinkData.let { (linkProvider, linkUrl, linkHistory) ->
                        val currentSeason = linkHistory.season ?: uiState.selectedSeason
                        val seasonCast = if (currentSeason != null && currentSeason > 0) {
                            uiState.seasonCredits[currentSeason]
                        } else null
                        val effectiveActors = seasonCast ?: uiState.enrichedActors ?: response?.actors
                        LinksSidePanel(
                            provider = linkProvider,
                            dataUrl = linkUrl,
                            history = linkHistory,
                            loadResponse = response, // Passed from ComposeDetailsScreen
                            enrichedActors = effectiveActors,
                            enrichedLogoUrl = uiState.enrichedLogoUrl,
                            enrichedBackdropUrl = uiState.enrichedBackdropUrl,
                            onClose = { viewModel.onEvent(DetailsUiEvent.OnCloseLinksPanel) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DetailsContent(
    onNavigate: (Config) -> Unit,
    onBack: () -> Unit,
    provider: MainAPI,
    data: LoadResponse,
    screenshots: List<String>?,
    enrichmentPhase: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase,
    isLoading: Boolean = false,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
    onDownload: ((com.lagradost.cloudstream3.Episode) -> Unit)? = null,
    enableDownloadButtons: Boolean = false,
    onToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit,
    onToggleSeasonWatched: (List<com.lagradost.cloudstream3.Episode>, Boolean) -> Unit,
    onRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit,
    onToggleEpisodesStackedView: (Boolean) -> Unit,
    onSetEpisodeViewMode: ((Int) -> Unit)? = null,
    dynamicColorEnabled: Boolean = false,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState? = null,
    showHistory: Map<String, com.lagradost.common.storage.WatchHistory> = emptyMap(),
    activeBgUrl: String? = null,
    onEvent: (DetailsUiEvent) -> Unit = {},
) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val latestHistory = remember(data.url, uiState?.watchHistory) {
        uiState?.watchHistory?.values?.maxByOrNull { it.updateTime }
    }

    var selectedScreenshot by remember { mutableStateOf<String?>(null) }
    var screenshotsExpanded by remember { mutableStateOf(true) }
    var trailersExpanded by remember { mutableStateOf(true) }

    val isMovieLike = remember(data) {
        data is com.lagradost.cloudstream3.MovieLoadResponse || data is com.lagradost.cloudstream3.TorrentLoadResponse || data is com.lagradost.cloudstream3.LiveStreamLoadResponse ||
            (data is com.lagradost.cloudstream3.TvSeriesLoadResponse && data.episodes.size == 1 && data.type == com.lagradost.cloudstream3.TvType.Movie) ||
            (data is com.lagradost.cloudstream3.AnimeLoadResponse && data.episodes.values.sumOf { it.size } == 1 && data.type == com.lagradost.cloudstream3.TvType.AnimeMovie)
    }

    val availableSeasons = remember(data) {
        val list = when (data) {
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> data.episodes.mapNotNull { it.season }.distinct().sorted()
            is com.lagradost.cloudstream3.AnimeLoadResponse -> data.episodes.values.flatten().mapNotNull { it.season }.distinct().sorted()
            else -> emptyList()
        }
        if (list.isEmpty() && (data is com.lagradost.cloudstream3.TvSeriesLoadResponse || data is com.lagradost.cloudstream3.AnimeLoadResponse)) {
            listOf(1)
        } else {
            list
        }
    }
    val currentSeason = uiState?.selectedSeason ?: latestHistory?.season ?: availableSeasons.firstOrNull() ?: 1

    val detailsSectionOrder by AppearanceConfig.detailsSectionOrder.collectAsState()
    val detailsDisabledSections by AppearanceConfig.detailsDisabledSections.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val viewportWidth = maxWidth
        val isWindowCompact = viewportWidth < 600.dp
        DetailsBackdrop(
            provider = provider,
            data = data,
            scrollState = scrollState,
            enrichmentPhase = enrichmentPhase,
            modifier = Modifier.fillMaxSize(),
            dynamicColorEnabled = dynamicColorEnabled,
            uiState = uiState,
            activeBgUrl = activeBgUrl,
        )

        val remoteIcons by com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.remotePluginIcons.collectAsState()

        val heroAction: @Composable (Modifier) -> Unit = { modifier ->
            com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsPlayButton(
                modifier = modifier,
                data = data,
                provider = provider,
                latestHistory = latestHistory,
                onPlay = onPlay,
            )
        }

        val downloadAction: (@Composable (Modifier) -> Unit)? = if (enableDownloadButtons && onDownload != null && isMovieLike) {
            { modifier ->
                com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsDownloadButton(
                    modifier = modifier,
                    data = data,
                    provider = provider,
                    latestHistory = latestHistory,
                    onDownload = onDownload,
                )
            }
        } else {
            null
        }

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { },
            contentPadding = PaddingValues(
                bottom = 32.dp,
            ),
        ) {
            item(key = "HeroAndTabs") {
                val heroMinHeight = if (isWindowCompact) {
                    null
                } else if (isMovieLike) {
                    minOf(740.dp, maxOf(500.dp, viewportHeight * 0.68f))
                } else {
                    minOf(640.dp, maxOf(460.dp, viewportHeight * 0.58f))
                }

                Box(
                    modifier = Modifier.fillMaxWidth().run {
                        if (heroMinHeight != null) this.heightIn(min = heroMinHeight) else this
                    },
                    contentAlignment = if (isWindowCompact) Alignment.TopStart else Alignment.BottomStart,
                ) {
                    DetailsMetadata(
                        provider = provider,
                        data = data,
                        heroAction = heroAction,
                        downloadAction = downloadAction,
                        enrichmentPhase = enrichmentPhase,
                        isLoading = isLoading,
                        uiState = uiState,
                        screenshots = screenshots,
                        onPhotosClick = {
                            coroutineScope.launch { scrollState.animateScrollToItem(1) }
                        },
                        onCastClick = {
                            coroutineScope.launch { scrollState.animateScrollToItem(2) }
                        },
                        onActorClick = { actor ->
                            val searchName = actor.voiceActor?.name?.takeIf { it.isNotBlank() } ?: actor.actor.name
                            onNavigate(Config.Person(name = searchName, image = actor.actor.image, tmdbId = null))
                        },
                        onTrailerClick = { url ->
                            val trailer = uiState?.enrichedTrailers?.find { it.url == url }
                                ?: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData(id = url, name = "${data.name} Official Trailer", url = url)
                            onEvent(DetailsUiEvent.OnSelectTrailer(trailer))
                        },
                        onEvent = onEvent,
                    )
                }
            }

            detailsSectionOrder.filter { it !in detailsDisabledSections }.forEach { sectionKey ->
                when (sectionKey) {
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.EPISODES -> {
                        if (!isMovieLike) {
                            item(key = "Episodes") {
                                BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsEpisodeSection(
                                            provider = provider,
                                            data = data,
                                            showHistory = showHistory,
                                            latestHistory = latestHistory,
                                            isMovieLike = isMovieLike,
                                            isLoading = isLoading,
                                            uiState = uiState,
                                            enableDownloadButtons = enableDownloadButtons,
                                            onPlay = onPlay,
                                            onDownload = onDownload,
                                            onToggleWatched = onToggleWatched,
                                            onToggleSeasonWatched = onToggleSeasonWatched,
                                            onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                                            onToggleEpisodesStackedView = onToggleEpisodesStackedView,
                                            onSetEpisodeViewMode = onSetEpisodeViewMode ?: {},
                                            selectedSeason = currentSeason,
                                            onSeasonChange = { onEvent(DetailsUiEvent.OnSelectSeason(it)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.CAST -> {
                        item(key = "Cast") {
                            BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                    com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCastSection(
                                        data = data,
                                        provider = provider,
                                        uiState = uiState,
                                        onActorClick = { actor ->
                                            val searchName = actor.voiceActor?.name?.takeIf { it.isNotBlank() } ?: actor.actor.name
                                            onNavigate(Config.Person(name = searchName, image = actor.actor.image, tmdbId = null))
                                        },
                                        onNavigate = onNavigate,
                                        horizontalPadding = hPadding,
                                        selectedSeason = uiState?.selectedSeason ?: currentSeason,
                                        seasonCredits = uiState?.seasonCredits,
                                        onSeasonChange = { onEvent(DetailsUiEvent.OnSelectSeason(it)) },
                                        availableSeasons = availableSeasons,
                                    )
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.INFO -> {
                        if (com.lagradost.cloudstream3.desktop.ui.screens.details.hasDetailsStats(uiState, data)) {
                            item(key = "Info") {
                                BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    val isMovie = data.type == TvType.Movie || data.type == TvType.AnimeMovie
                                    val sectionTitle = if (isMovie) "Movie Details" else "Show Details"
                                    Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                        Text(
                                            sectionTitle,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = hPadding).padding(bottom = 16.dp),
                                        )
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsStatsSection(
                                            data = data,
                                            uiState = uiState,
                                            modifier = Modifier.padding(horizontal = hPadding),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.NETWORKS -> {
                        if (com.lagradost.cloudstream3.desktop.ui.screens.details.hasNetworks(uiState)) {
                            item(key = "Networks") {
                                BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsNetworksSection(
                                            uiState = uiState,
                                            modifier = Modifier.padding(horizontal = hPadding),
                                            onCompanyClick = { comp ->
                                                onNavigate(
                                                    Config.Studio(
                                                        name = comp.name,
                                                        companyId = comp.id.takeIf { it > 0 },
                                                        logoUrl = comp.logoUrl,
                                                        originCountry = comp.originCountry,
                                                    )
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.STUDIOS -> {
                        if (com.lagradost.cloudstream3.desktop.ui.screens.details.hasStudios(uiState)) {
                            item(key = "Studios") {
                                BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsStudiosSection(
                                            uiState = uiState,
                                            modifier = Modifier.padding(horizontal = hPadding),
                                            onCompanyClick = { comp ->
                                                onNavigate(
                                                    Config.Studio(
                                                        name = comp.name,
                                                        companyId = comp.id.takeIf { it > 0 },
                                                        logoUrl = comp.logoUrl,
                                                        originCountry = comp.originCountry,
                                                    )
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.TRAILERS -> {
                        val enrichedTrailers = uiState?.enrichedTrailers ?: emptyList()
                        if (enrichedTrailers.isNotEmpty()) {
                            item(key = "Trailers") {
                                BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsTrailersSection(
                                            trailers = enrichedTrailers,
                                            trailersExpanded = trailersExpanded,
                                            onToggleExpand = { trailersExpanded = !trailersExpanded },
                                            onTrailerClick = { url ->
                                                val trailer = enrichedTrailers.find { it.url == url }
                                                    ?: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData(id = url, name = "${data.name} Official Trailer", url = url)
                                                onEvent(DetailsUiEvent.OnSelectTrailer(trailer))
                                            },
                                            horizontalPadding = hPadding,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.SCREENSHOTS -> {
                        if (!screenshots.isNullOrEmpty()) {
                            item(key = "Screenshots") {
                                BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsScreenshotsSection(
                                            screenshots = screenshots,
                                            screenshotsExpanded = screenshotsExpanded,
                                            onToggleExpand = { screenshotsExpanded = !screenshotsExpanded },
                                            onScreenshotClick = { selectedScreenshot = it },
                                            horizontalPadding = hPadding,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.COLLECTION -> {
                        val collName = uiState?.enrichedCollectionName
                        val collBg = uiState?.enrichedCollectionBackdrop
                        val collItems = uiState?.enrichedCollectionItems ?: emptyList()
                        if (!collName.isNullOrBlank()) {
                            item(key = "Collection") {
                                Box(modifier = Modifier.animateItem().fillMaxWidth().padding(top = 48.dp)) {
                                    com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCollectionSection(
                                        collName = collName,
                                        collBg = collBg,
                                        collItems = collItems,
                                        provider = provider,
                                        onNavigate = onNavigate,
                                    )
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.RECOMMENDATIONS -> {
                        val validRecs = data.recommendations?.filterIsInstance<com.lagradost.cloudstream3.SearchResponse>()
                            ?.filter { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it.apiName) != null } ?: emptyList()
                        if (validRecs.isNotEmpty()) {
                            item(key = "Recommendations") {
                                BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsRecommendationsSection(
                                            validRecs = validRecs,
                                            onNavigate = onNavigate,
                                            horizontalPadding = hPadding,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.REVIEWS -> {
                        if (uiState?.enrichedReviews?.isNotEmpty() == true) {
                            item(key = "Reviews") {
                                BoxWithConstraints(modifier = Modifier.animateItem().fillMaxWidth()) {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsReviewsSection(
                                            reviews = uiState.enrichedReviews,
                                            horizontalPadding = hPadding,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "Spacer") {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        // Floating Top Action Layer (Corner-anchored, matching Web Player 1:1 geometry)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Floating Back Button (1:1 with Web Player UI)
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F0F12).copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Floating Window Controls Pill
            com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill(
                isHome = false,
                isCompact = isWindowCompact,
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = selectedScreenshot != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { selectedScreenshot = null },
                contentAlignment = Alignment.Center,
            ) {
                coil3.compose.AsyncImage(
                    model = selectedScreenshot,
                    contentDescription = "Screenshot Full",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                )
                IconButton(
                    onClick = { selectedScreenshot = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }

        if (uiState?.pendingExternalUrl != null) {
            com.lagradost.cloudstream3.desktop.utils.ExternalLinkConfirmationDialog(
                url = uiState.pendingExternalUrl,
                onDismiss = { onEvent(DetailsUiEvent.OnSetPendingExternalUrl(null)) },
            )
        }

        com.lagradost.cloudstream3.desktop.ui.screens.details.dialogs.TrailerPlayerDialog(
            trailer = uiState?.activeTrailer,
            onDismissRequest = { onEvent(DetailsUiEvent.OnSelectTrailer(null)) },
        )
    }
}

