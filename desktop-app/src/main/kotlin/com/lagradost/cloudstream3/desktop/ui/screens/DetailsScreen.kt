package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.DesktopThemeColors.*
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.details.*
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.WatchHistory
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDetailsScreen(navController: NavController, provider: MainAPI, url: String, preloadedName: String? = null, preloadedPoster: String? = null, preloadedBg: String? = null, autoPlay: Boolean = false) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember(url) { DetailsViewModel(provider, url, preloadedName, preloadedPoster, preloadedBg) }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.onEvent(DetailsUiEvent.OnLoad)
    }

    val uiState by viewModel.uiState.collectAsState()
    val fetchFailed = uiState.fetchFailed
    val showHistory = uiState.watchHistory

    val response = uiState.response
    val fakeData = uiState.fakeData
    val isLoading = uiState.isLoading
    val error = uiState.error
    val watchHistory = uiState.watchHistory
    val isPanelOpen = uiState.isPanelOpen
    val enrichmentPhase = uiState.enrichmentPhase
    val activeLinkData = uiState.activeLinkData
    val screenshots = uiState.screenshots
    val heroExtractedColor = uiState.heroColor
    val dynamicColorEnabled by AppearanceConfig.heroDynamicColorEnabled.collectAsState()
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()

    val animatedHeroColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (dynamicColorEnabled && !isLightMode && heroExtractedColor != null) {
            heroExtractedColor
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 800),
        label = "heroBgColor",
    )

    var playbackError by remember { mutableStateOf<String?>(null) }
    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current

    val handlePlay: (com.lagradost.cloudstream3.Episode) -> Unit = { ep ->
        viewModel.onEvent(DetailsUiEvent.OnPlayEpisode(ep))
    }

    val handleToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit = { ep, isWatched ->
        viewModel.onEvent(DetailsUiEvent.OnToggleEpisodeWatched(ep, isWatched))
    }

    LaunchedEffect(viewModel.effectFlow) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is DetailsUiEffect.NavigateToPlayer -> playVideo(effect.launchData)
                is DetailsUiEffect.ShowErrorDialog -> playbackError = effect.message
                is DetailsUiEffect.ShowToast -> {} // Handled elsewhere or not needed
            }
        }
    }

    if (playbackError != null) {
        AlertDialog(
            onDismissRequest = { playbackError = null },
            title = { Text("Playback Failed") },
            text = { Text(playbackError ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { playbackError = null }) {
                    Text("OK")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        var hasAutoPlayed by remember { mutableStateOf(false) }

        LaunchedEffect(response) {
            if (!hasAutoPlayed && response != null) {
                viewModel.onEvent(DetailsUiEvent.OnRequestAutoPlay)
                hasAutoPlayed = true
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
                .drawWithCache {
                    if (dynamicColorEnabled && !isLightMode && animatedHeroColor != androidx.compose.ui.graphics.Color.Transparent) {
                        val flatColor = animatedHeroColor.copy(alpha = 0.28f)
                        val radius1 = size.width.coerceAtLeast(size.height) * 1.5f
                        val brush1 = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(animatedHeroColor.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, 0f),
                            radius = radius1,
                        )
                        val radius2 = size.width.coerceAtLeast(size.height) * 0.9f
                        val brush2 = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(animatedHeroColor.copy(alpha = 0.12f), androidx.compose.ui.graphics.Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.15f),
                            radius = radius2,
                        )
                        onDrawBehind {
                            drawRect(flatColor)
                            drawRect(brush = brush1)
                            drawRect(brush = brush2)
                        }
                    } else {
                        onDrawBehind {}
                    }
                },
        ) {
            if (isLoading) {
                if (fakeData != null) {
                    DetailsContent(navController, provider, fakeData, screenshots, enrichmentPhase, isLoading = true, onPlay = handlePlay, onToggleWatched = handleToggleWatched, dynamicColorEnabled = dynamicColorEnabled, animatedHeroColor = animatedHeroColor, uiState = uiState, showHistory = showHistory)
                } else {
                    DetailsSkeletonPlaceholder(
                        onBack = { navController.goBack() },
                        preloadedPoster = preloadedPoster,
                        preloadedBg = preloadedBg,
                    )
                }
            } else if (response != null) {
                DetailsContent(navController, provider, response, screenshots, enrichmentPhase, isLoading = false, onPlay = handlePlay, onToggleWatched = handleToggleWatched, dynamicColorEnabled = dynamicColorEnabled, animatedHeroColor = animatedHeroColor, uiState = uiState, showHistory = showHistory)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (error != null) "Error: $error" else "Failed to load details.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.goBack() }) {
                            Text("Go Back")
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
                val offsetX by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isPanelOpen) 0.dp else 450.dp,
                    animationSpec = tween(300),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = offsetX),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                            .clickable { if (isPanelOpen) viewModel.onEvent(DetailsUiEvent.OnCloseLinksPanel) else viewModel.onEvent(DetailsUiEvent.OnOpenLinksPanel(activeLinkData)) }
                            .padding(16.dp),
                    ) {
                        Icon(
                            if (isPanelOpen) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = "Toggle links",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(450.dp)
                            .shadow(24.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0C0C14).copy(alpha = 0.75f),
                                        Color(0xFF1A1A24).copy(alpha = 0.85f),
                                    ),
                                ),
                            ),
                    ) {
                        activeLinkData?.let { (linkProvider, linkUrl, linkHistory) ->
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
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DetailsContent(
    navController: NavController,
    provider: MainAPI,
    data: LoadResponse,
    screenshots: List<String>?,
    enrichmentPhase: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase,
    isLoading: Boolean = false,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
    onToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit,
    dynamicColorEnabled: Boolean = false,
    animatedHeroColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState? = null,
    showHistory: Map<String, com.lagradost.common.storage.WatchHistory> = emptyMap(),
) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val backupSeasonHistory = remember { mutableMapOf<String, com.lagradost.common.storage.WatchHistory?>() }
    val hazeState = remember { HazeState() }

    val latestHistory = remember(data.url, uiState?.watchHistory) {
        uiState?.watchHistory?.values?.maxByOrNull { it.position }
    }

    var selectedScreenshot by remember { mutableStateOf<String?>(null) }
    var screenshotsExpanded by remember { mutableStateOf(false) }
    val screenshotsScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val similarScrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    val isMovieLike = remember(data) {
        data is MovieLoadResponse || data is TorrentLoadResponse || data is LiveStreamLoadResponse ||
            (data is TvSeriesLoadResponse && data.episodes.size == 1) ||
            (data is AnimeLoadResponse && data.episodes.values.sumOf { it.size } == 1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val remoteIcons by com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.remotePluginIcons.collectAsState()

        val isLightMode by AppearanceConfig.isLightMode.collectAsState()
        val heroAction: @Composable () -> Unit = {
            com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsPlayButton(
                data = data,
                provider = provider,
                latestHistory = latestHistory,
                onPlay = onPlay,
            )
        }

        LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
            item(key = "HeroSection") {
                val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
                val heroHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                    windowInfo.containerSize.height.toDp()
                }
                Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                    DetailsBackdrop(
                        provider = provider,
                        data = data,
                        scrollState = scrollState,
                        hazeState = hazeState,
                        enrichmentPhase = enrichmentPhase,
                        modifier = Modifier.fillMaxSize(),
                        dynamicColorEnabled = dynamicColorEnabled,
                        animatedHeroColor = animatedHeroColor,
                        uiState = uiState,
                    )
                    DetailsMetadata(provider = provider, data = data, hazeState = hazeState, heroAction = heroAction, enrichmentPhase = enrichmentPhase, isLoading = isLoading, uiState = uiState)
                }
            }

            item(key = "EpisodeSection") {
                com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsEpisodeSection(
                    provider = provider,
                    data = data,
                    showHistory = showHistory,
                    latestHistory = latestHistory,
                    isMovieLike = isMovieLike,
                    isLoading = isLoading,
                    coroutineScope = coroutineScope,
                    onPlay = onPlay,
                    onToggleWatched = onToggleWatched,
                )
            }

            item(key = "StatsSection") {
                com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsStatsSection(
                    uiState = uiState,
                )
            }

            item(key = "CastSection") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCastSection(
                            data = data,
                            provider = provider,
                            onMovieClick = { rec ->
                                val recProvider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(rec.apiName) ?: provider
                                navController.navigate(com.lagradost.cloudstream3.desktop.ui.navigation.Screen.Details(recProvider.name, rec.url, rec.name, rec.posterUrl, null, false))
                            },
                        )
                    }
                }
            }

            val collName = uiState?.enrichedCollectionName
            val collBg = uiState?.enrichedCollectionBackdrop
            val collItems = uiState?.enrichedCollectionItems ?: emptyList()
            if (!collName.isNullOrBlank()) {
                item(key = "CollectionSection") {
                    com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCollectionSection(
                        collName = collName,
                        collBg = collBg,
                        collItems = collItems,
                        provider = provider,
                        onNavigate = { screen -> navController.navigate(screen) },
                    )
                }
            }

            if (!screenshots.isNullOrEmpty()) {
                item(key = "ScreenshotsSection") {
                    com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsScreenshotsSection(
                        screenshots = screenshots,
                        screenshotsExpanded = screenshotsExpanded,
                        onToggleExpand = { screenshotsExpanded = !screenshotsExpanded },
                        onScreenshotClick = { selectedScreenshot = it },
                    )
                }
            }

            val validRecs = data.recommendations?.filterIsInstance<com.lagradost.cloudstream3.SearchResponse>()?.filter { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it.apiName) != null } ?: emptyList()

            if (validRecs.isNotEmpty()) {
                item(key = "RecommendationsSection") {
                    com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsRecommendationsSection(
                        validRecs = validRecs,
                        onNavigate = { screen -> navController.navigate(screen) },
                    )
                }
            }

            item(key = "Spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Back button
        IconButton(
            onClick = { navController.goBack() },
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill(isHome = false)
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
