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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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

    var playbackError by remember { mutableStateOf<String?>(null) }
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
                is DetailsUiEffect.ShowErrorDialog -> playbackError = effect.message
                is DetailsUiEffect.ShowToast -> com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(effect.message)
            }
        }
    }

    com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
        show = playbackError != null,
        onDismissRequest = { playbackError = null },
        title = { Text("Playback Failed") },
        text = { Text(playbackError ?: "Unknown error") },
        confirmButton = {
            TextButton(onClick = { playbackError = null }) {
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
                while (true) {
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
            if (isLoading) {
                if (fakeData != null) {
                    DetailsContent(onNavigate, onBack, provider, fakeData, screenshots, enrichmentPhase, isLoading = true, onPlay = handlePlay, onDownload = handleDownload, enableDownloadButtons = enableDownloadButtons, onToggleWatched = handleToggleWatched, onToggleSeasonWatched = handleToggleSeasonWatched, onRemoveEpisodeWatched = handleRemoveEpisodeWatched, onToggleEpisodesStackedView = handleToggleEpisodesStackedView, onSetEpisodeViewMode = handleSetEpisodeViewMode, dynamicColorEnabled = heroBackgroundBlurEnabled, uiState = uiState, showHistory = showHistory, activeBgUrl = activeBgUrl, onEvent = viewModel::onEvent)
                } else {
                    DetailsSkeletonPlaceholder(
                        onBack = onBack,
                        preloadedPoster = viewModel.preloadedPoster,
                        preloadedBg = viewModel.preloadedBg,
                    )
                }
            } else if (response != null) {
                DetailsContent(onNavigate, onBack, provider, response, screenshots, enrichmentPhase, isLoading = false, onPlay = handlePlay, onDownload = handleDownload, enableDownloadButtons = enableDownloadButtons, onToggleWatched = handleToggleWatched, onToggleSeasonWatched = handleToggleSeasonWatched, onRemoveEpisodeWatched = handleRemoveEpisodeWatched, onToggleEpisodesStackedView = handleToggleEpisodesStackedView, onSetEpisodeViewMode = handleSetEpisodeViewMode, dynamicColorEnabled = heroBackgroundBlurEnabled, uiState = uiState, showHistory = showHistory, activeBgUrl = activeBgUrl, onEvent = viewModel::onEvent)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(24.dp)
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.onEvent(DetailsUiEvent.OnRefresh) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry")
                                }
                                Button(
                                    onClick = onBack,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                }
            }

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
                        LinksSidePanel(
                            provider = linkProvider,
                            dataUrl = linkUrl,
                            history = linkHistory,
                            loadResponse = response, // Passed from ComposeDetailsScreen
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
    var activeTrailer by remember { mutableStateOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData?>(null) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }

    val isMovieLike = remember(data) {
        data is com.lagradost.cloudstream3.MovieLoadResponse || data is com.lagradost.cloudstream3.TorrentLoadResponse || data is com.lagradost.cloudstream3.LiveStreamLoadResponse ||
            (data is com.lagradost.cloudstream3.TvSeriesLoadResponse && data.episodes.size == 1) ||
            (data is com.lagradost.cloudstream3.AnimeLoadResponse && data.episodes.values.sumOf { it.size } == 1)
    }

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
                            activeTrailer = trailer
                        },
                        onEvent = onEvent,
                    )

                    DetailsClockPill(
                        latestHistory = latestHistory,
                        data = data,
                        viewportWidth = viewportWidth,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            detailsSectionOrder.filter { it !in detailsDisabledSections }.forEach { sectionKey ->
                when (sectionKey) {
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.EPISODES -> {
                        if (!isMovieLike) {
                            item(key = "Episodes") {
                                BoxWithConstraints {
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
                                        )
                                    }
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.CAST -> {
                        item(key = "Cast") {
                            BoxWithConstraints {
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
                                        horizontalPadding = hPadding,
                                    )
                                }
                            }
                        }
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.INFO -> {
                        if (com.lagradost.cloudstream3.desktop.ui.screens.details.hasDetailsStats(uiState, data)) {
                            item(key = "Info") {
                                BoxWithConstraints {
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
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.STUDIOS -> {
                        if (com.lagradost.cloudstream3.desktop.ui.screens.details.hasStudiosOrNetworks(uiState)) {
                            item(key = "Studios") {
                                BoxWithConstraints {
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
                                BoxWithConstraints {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsTrailersSection(
                                            trailers = enrichedTrailers,
                                            trailersExpanded = trailersExpanded,
                                            onToggleExpand = { trailersExpanded = !trailersExpanded },
                                            onTrailerClick = { url ->
                                                val trailer = enrichedTrailers.find { it.url == url }
                                                    ?: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData(id = url, name = "${data.name} Official Trailer", url = url)
                                                activeTrailer = trailer
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
                                BoxWithConstraints {
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
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
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
                                BoxWithConstraints {
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
                                BoxWithConstraints {
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

        if (pendingExternalUrl != null) {
            com.lagradost.cloudstream3.desktop.utils.ExternalLinkConfirmationDialog(
                url = pendingExternalUrl,
                onDismiss = { pendingExternalUrl = null },
            )
        }

        com.lagradost.cloudstream3.desktop.ui.screens.details.dialogs.TrailerPlayerDialog(
            trailer = activeTrailer,
            onDismissRequest = { activeTrailer = null },
        )
    }
}

@Composable
fun DetailsSkeletonPlaceholder(
    onBack: () -> Unit,
    preloadedPoster: String? = null,
    preloadedBg: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070E)),
    ) {
        // Backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .shimmerBackground(),
        ) {
            if (!preloadedBg.isNullOrEmpty()) {
                coil3.compose.AsyncImage(
                    model = preloadedBg,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }

        // Dark vertical scrim overlay matching actual backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF07070E)),
                    ),
                ),
        )

        // Metadata alignment
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1200.dp)
                    .padding(start = 32.dp, end = 32.dp, top = 100.dp, bottom = 32.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Poster
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .shimmerBackground(),
                    ) {
                        if (!preloadedPoster.isNullOrEmpty()) {
                            coil3.compose.AsyncImage(
                                model = preloadedPoster,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(48.dp))

                    // Shimmering Text Lines
                    Column(modifier = Modifier.weight(1f)) {
                        // Title line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .shimmerBackground(),
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Metadata badges row
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerBackground(),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // Plot lines
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground(),
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Play button row
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .shimmerBackground(),
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .shimmerBackground(),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Cast Section
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerBackground(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(6) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                            Box(modifier = Modifier.size(96.dp).clip(CircleShape).shimmerBackground())
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.width(80.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.width(50.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Content/episodes section title
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerBackground(),
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Shimmering Episode Grid
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) {
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .shimmerBackground(),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground(),
                            )
                        }
                    }
                }
            }
        }

        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .align(Alignment.TopStart),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Window Controls
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill(isHome = false)
        }
    }
}

@Composable
private fun DetailsClockPill(
    latestHistory: com.lagradost.common.storage.WatchHistory?,
    data: LoadResponse,
    viewportWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val showCurrentTime by AppearanceConfig.detailsShowCurrentTime.collectAsState()
    val showEndTime by AppearanceConfig.detailsShowEndTime.collectAsState()
    val clockTimeFormat by AppearanceConfig.clockTimeFormat.collectAsState()

    if (!showCurrentTime && !showEndTime) return

    val currentFormattedTime by produceState(initialValue = "", key1 = clockTimeFormat) {
        val pattern = if (clockTimeFormat.isNotBlank()) clockTimeFormat else "h:mm a"
        while (true) {
            val formatter = try {
                java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            } catch (_: Exception) {
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            }
            value = formatter.format(java.util.Date())
            kotlinx.coroutines.delay(1000L)
        }
    }

    val progress = remember(latestHistory) {
        if (latestHistory != null && latestHistory.duration > 0) {
            if (PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)) {
                1f
            } else {
                (latestHistory.position.toFloat() / latestHistory.duration.toFloat()).coerceIn(0f, 1f)
            }
        } else {
            0f
        }
    }

    val remainingSecondsForEnd = remember(latestHistory, data, progress) {
        if (latestHistory != null && latestHistory.duration > 0) {
            if (progress > 0f && progress < 1f) {
                latestHistory.duration - latestHistory.position
            } else {
                latestHistory.duration
            }
        } else if (data is com.lagradost.cloudstream3.MovieLoadResponse && data.duration != null) {
            data.duration?.toLong()?.times(60L)
        } else {
            null
        }
    }

    val formattedEndTime = remember(remainingSecondsForEnd, currentFormattedTime, clockTimeFormat) {
        remainingSecondsForEnd?.let { secs ->
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.SECOND, secs.toInt())
            val pattern = if (clockTimeFormat.isNotBlank()) clockTimeFormat else "h:mm a"
            val formatter = try {
                java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            } catch (_: Exception) {
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            }
            "Ends at ${formatter.format(calendar.time)}"
        }
    }

    val showTimePill = (showCurrentTime && currentFormattedTime.isNotBlank()) || (showEndTime && formattedEndTime != null)
    if (!showTimePill) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (viewportWidth < 1100.dp) 24.dp else 64.dp,
                end = if (viewportWidth < 1100.dp) 24.dp else 64.dp,
                bottom = 32.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(100.dp),
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                )
                .clip(RoundedCornerShape(100.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(100.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showCurrentTime && currentFormattedTime.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "🕒",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        )
                        Text(
                            text = currentFormattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                            ),
                            color = Color.White.copy(alpha = 0.95f),
                        )
                    }
                }

                if (showCurrentTime && currentFormattedTime.isNotBlank() && showEndTime && formattedEndTime != null) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f)),
                    )
                }

                if (showEndTime && formattedEndTime != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "⏳",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        )
                        Text(
                            text = formattedEndTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}
