package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.DesktopThemeColors.*
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.details.*
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDetailsScreen(navController: NavController, provider: MainAPI, url: String, preloadedName: String? = null, preloadedPoster: String? = null, preloadedBg: String? = null, autoPlay: Boolean = false) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember(url) { DetailsViewModel(coroutineScope, provider, url, preloadedName, preloadedPoster, preloadedBg) }

    val response by viewModel.response.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val fakeData by viewModel.fakeData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val activeLinkData by viewModel.activeLinkData.collectAsState()
    val isPanelOpen by viewModel.isPanelOpen.collectAsState()
    val enrichmentTrigger by viewModel.enrichmentTrigger.collectAsState()
    val screenshots by viewModel.screenshots.collectAsState()
    val heroExtractedColor by viewModel.heroExtractedColor.collectAsState()
    val dynamicColorEnabled by AppearanceConfig.heroDynamicColorEnabled.collectAsState()
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()

    val animatedHeroColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (dynamicColorEnabled && !isLightMode && heroExtractedColor != null) {
            heroExtractedColor!!
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 800),
        label = "heroBgColor",
    )

    var playbackError by remember { mutableStateOf<String?>(null) }
    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current

    val handlePlay: (Triple<MainAPI, String, WatchHistory>) -> Unit = { (linkProvider, linkUrl, linkHistory) ->
        val autoPlay = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
        if (autoPlay) {
            val epTitle = buildString {
                append(linkHistory.showName)
                if (linkHistory.season != null && linkHistory.episode != null) {
                    append(" - S${linkHistory.season}E${linkHistory.episode}")
                } else if (linkHistory.episode != null) {
                    append(" - E${linkHistory.episode}")
                }
            }
            val isLive = (response ?: fakeData)?.type == com.lagradost.cloudstream3.TvType.Live
            val resumeMs = if (isLive) 0L else com.lagradost.player.impl.PlayerLinkHandler.resumeStartSeconds(linkHistory.position, linkHistory.duration) * 1000L
            playVideo(
                com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                    links = emptyList(),
                    initialIndex = 0,
                    title = epTitle,
                    subtitles = emptyList(),
                    startPositionMs = resumeMs,
                    history = linkHistory,
                    loadResponse = response ?: fakeData,
                    onError = { err -> playbackError = err },
                ),
            )
        } else {
            viewModel.openLinksPanel(Triple(linkProvider, linkUrl, linkHistory))
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
            if (autoPlay && !hasAutoPlayed && response != null) {
                val resp = response!!
                val firstEp = if (resp is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                    resp.episodes.firstOrNull()
                } else if (resp is com.lagradost.cloudstream3.AnimeLoadResponse) {
                    resp.episodes.values.firstOrNull()?.firstOrNull()
                } else {
                    null
                }
                if (firstEp != null) {
                    com.lagradost.cloudstream3.desktop.ui.screens.details.navigateToPlay(provider, resp, firstEp, handlePlay)
                    hasAutoPlayed = true
                } else if (resp is com.lagradost.cloudstream3.MovieLoadResponse) {
                    val ep = provider.newEpisode(resp.dataUrl) {
                        this.name = resp.name
                        this.posterUrl = resp.posterUrl
                    }
                    com.lagradost.cloudstream3.desktop.ui.screens.details.navigateToPlay(provider, resp, ep, handlePlay)
                    hasAutoPlayed = true
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
                .drawBehind {
                    if (dynamicColorEnabled && !isLightMode && animatedHeroColor != androidx.compose.ui.graphics.Color.Transparent) {
                        // Uniform 0.28f flat tint — same as HomeScreen so details page matches the same vibrant theme
                        drawRect(animatedHeroColor.copy(alpha = 0.28f))

                        val radius1 = size.width.coerceAtLeast(size.height) * 1.5f
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(animatedHeroColor.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, 0f),
                                radius = radius1,
                            ),
                        )
                        val radius2 = size.width.coerceAtLeast(size.height) * 0.9f
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(animatedHeroColor.copy(alpha = 0.12f), androidx.compose.ui.graphics.Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.15f),
                                radius = radius2,
                            ),
                        )
                    }
                },
        ) {
            if (isLoading) {
                if (fakeData != null) {
                    DetailsContent(navController, provider, fakeData!!, screenshots, enrichmentTrigger, isLoading = true, onPlay = handlePlay, dynamicColorEnabled = dynamicColorEnabled, animatedHeroColor = animatedHeroColor, uiState = uiState)
                } else {
                    DetailsSkeletonPlaceholder(
                        onBack = { navController.goBack() },
                        preloadedPoster = preloadedPoster,
                        preloadedBg = preloadedBg,
                    )
                }
            } else if (response != null) {
                DetailsContent(navController, provider, response!!, screenshots, enrichmentTrigger, isLoading = false, onPlay = handlePlay, dynamicColorEnabled = dynamicColorEnabled, animatedHeroColor = animatedHeroColor, uiState = uiState)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (errorMessage != null) "Error: $errorMessage" else "Failed to load details.",
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
                        .clickable { viewModel.closeLinksPanel() },
                )
            }

            if (activeLinkData != null) {
                val offsetX by animateDpAsState(
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
                            .clickable { if (isPanelOpen) viewModel.closeLinksPanel() else viewModel.openLinksPanel(activeLinkData!!) }
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
                                onClose = { viewModel.closeLinksPanel() },
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
    enrichmentTrigger: Int,
    isLoading: Boolean = false,
    onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit,
    dynamicColorEnabled: Boolean = false,
    animatedHeroColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsUiState? = null,
) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val backupSeasonHistory = remember { mutableMapOf<String, com.lagradost.common.storage.WatchHistory?>() }
    val hazeState = remember { HazeState() }

    val historyUpdatesVal = com.lagradost.common.storage.DesktopDataStore.historyUpdates.collectAsState().value
    val latestHistory = remember(data.url, historyUpdatesVal) {
        com.lagradost.common.storage.DesktopDataStore.getLatestWatchHistoryForShow(data.url)
    }

    val showHistory = remember(data.url, historyUpdatesVal) {
        com.lagradost.common.storage.DesktopDataStore.getAllWatchHistory()
            .filter { it.showUrl == data.url }
            .associateBy { it.episodeId ?: it.parentId }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val historyUpdatesVal = com.lagradost.common.storage.DesktopDataStore.historyUpdates.collectAsState().value
                val latestHistory = remember(data.url, historyUpdatesVal) {
                    com.lagradost.common.storage.DesktopDataStore.getLatestWatchHistoryForShow(data.url)
                }

                val allEpisodes = remember(data) {
                    when (data) {
                        is com.lagradost.cloudstream3.TvSeriesLoadResponse -> data.episodes
                        is com.lagradost.cloudstream3.AnimeLoadResponse -> data.episodes.values.flatten()
                        else -> emptyList()
                    }
                }
                val sortedEpisodes = remember(allEpisodes) {
                    allEpisodes.sortedWith(
                        compareBy<com.lagradost.cloudstream3.Episode> { it.season ?: 1 }
                            .thenBy { it.episode ?: 1 }
                    )
                }
                val targetEp = remember(sortedEpisodes, latestHistory) {
                    if (latestHistory != null && sortedEpisodes.isNotEmpty()) {
                        sortedEpisodes.find { it.data == latestHistory.episodeId } ?: sortedEpisodes.firstOrNull()
                    } else {
                        sortedEpisodes.firstOrNull()
                    }
                }

                val buttonLabel = remember(data, latestHistory, targetEp) {
                    if (latestHistory != null) {
                        if (targetEp?.episode != null) {
                            "Resume E${targetEp.episode}"
                        } else {
                            "Resume"
                        }
                    } else {
                        if (targetEp?.season != null && targetEp.episode != null) {
                            "Play S${targetEp.season} E${targetEp.episode}"
                        } else if (targetEp?.episode != null) {
                            "Play E${targetEp.episode}"
                        } else {
                            "Play"
                        }
                    }
                }

                val onPlayClick = {
                    if (targetEp != null) {
                        com.lagradost.cloudstream3.desktop.ui.screens.details.navigateToPlay(provider, data, targetEp, onPlay)
                    } else {
                        val ep = when (data) {
                            is com.lagradost.cloudstream3.MovieLoadResponse -> provider.newEpisode(data.dataUrl) {
                                name = data.name
                                description = data.plot
                                posterUrl = data.backgroundPosterUrl ?: data.posterUrl
                            }
                            is com.lagradost.cloudstream3.TorrentLoadResponse -> provider.newEpisode(data.torrent ?: data.magnet ?: "") {
                                name = data.name
                                description = data.plot
                                posterUrl = data.posterUrl
                            }
                            is com.lagradost.cloudstream3.LiveStreamLoadResponse -> provider.newEpisode(data.dataUrl) {
                                name = data.name
                                description = data.plot
                                posterUrl = data.backgroundPosterUrl ?: data.posterUrl
                            }
                            else -> null
                        }
                        if (ep != null) {
                            com.lagradost.cloudstream3.desktop.ui.screens.details.navigateToPlay(provider, data, ep, onPlay)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable { onPlayClick() }
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF0F0F0F),
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = buttonLabel,
                            color = Color(0xFF0F0F0F),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
            item {
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
                        enrichmentTrigger = enrichmentTrigger,
                        modifier = Modifier.fillMaxSize(),
                        dynamicColorEnabled = dynamicColorEnabled,
                        animatedHeroColor = animatedHeroColor,
                        uiState = uiState,
                    )
                    DetailsMetadata(provider = provider, data = data, hazeState = hazeState, heroAction = heroAction, enrichmentTrigger = enrichmentTrigger, isLoading = isLoading, uiState = uiState)
                }
            }

            item {
                com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsEpisodeSection(
                    provider = provider,
                    data = data,
                    showHistory = showHistory,
                    latestHistory = latestHistory,
                    isMovieLike = isMovieLike,
                    isLoading = isLoading,
                    coroutineScope = coroutineScope,
                    onPlay = onPlay,
                )
            }

            item {
                com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsStatsSection(
                    uiState = uiState
                )
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCastSection(
                            data = data,
                            provider = provider,
                            onMovieClick = { rec ->
                                val recProvider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(rec.apiName) ?: provider
                                navController.navigate(com.lagradost.cloudstream3.desktop.ui.navigation.Screen.Details(recProvider, rec.url, rec.name, rec.posterUrl, null, false))
                            }
                        )
                    }
                }
            }

            val collName = uiState?.enrichedCollectionName
            val collBg = uiState?.enrichedCollectionBackdrop
            val collItems = uiState?.enrichedCollectionItems ?: emptyList()
            if (!collName.isNullOrBlank()) {
                item {
                    val collScrollState = rememberLazyListState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF161618)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (collBg != null) {
                                coil3.compose.AsyncImage(
                                    model = collBg,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize().blur(16.dp),
                                )
                                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.65f)))
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 18.dp)
                            ) {
                                Text(
                                    text = "COLLECTION / SAGA",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = collName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (collItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    androidx.compose.foundation.lazy.LazyRow(
                                        state = collScrollState,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectHorizontalDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    collScrollState.dispatchRawDelta(-dragAmount)
                                                }
                                            },
                                    ) {
                                        items(collItems) { partItem ->
                                            com.lagradost.cloudstream3.desktop.ui.components.PosterCard(
                                                item = partItem,
                                                provider = provider,
                                                itemWidth = 125.dp,
                                                onClick = {
                                                    navController.navigate(Screen.Details(provider, partItem.url, partItem.name, partItem.posterUrl, null, false))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!screenshots.isNullOrEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(vertical = 16.dp),
                    ) {
                        // Collapsible Screenshots header row
                        Row(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { screenshotsExpanded = !screenshotsExpanded }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "Screenshots",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                ) {
                                    Text(
                                        text = "${screenshots.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = if (screenshotsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.rotate(if (screenshotsExpanded) 180f else 0f),
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = screenshotsExpanded,
                            enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(200)),
                            exit = fadeOut(tween(200)) + androidx.compose.animation.shrinkVertically(tween(200)),
                        ) {
                            Column {
                                androidx.compose.foundation.lazy.LazyRow(
                                    state = screenshotsScrollState,
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                                        detectHorizontalDragGestures { change, dragAmount ->
                                            change.consume()
                                            screenshotsScrollState.dispatchRawDelta(-dragAmount)
                                        }
                                    },
                                ) {
                                    items(screenshots, key = { it }) { imgUrl ->
                                        Surface(
                                            modifier = Modifier
                                                .width(480.dp)
                                                .aspectRatio(16f / 9f)
                                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { selectedScreenshot = imgUrl },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                        ) {
                                            coil3.compose.AsyncImage(
                                                model = imgUrl,
                                                contentDescription = "Screenshot",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ) {
                                        Row {
                                            IconButton(onClick = { coroutineScope.launch { screenshotsScrollState.animateScrollBy(-500f) } }) {
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left", tint = MaterialTheme.colorScheme.onSurface)
                                            }
                                            IconButton(onClick = { coroutineScope.launch { screenshotsScrollState.animateScrollBy(500f) } }) {
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right", tint = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            val validRecs = data.recommendations?.filterIsInstance<com.lagradost.cloudstream3.SearchResponse>()?.filter { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it.apiName) != null } ?: emptyList()

            if (validRecs.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(vertical = 24.dp),
                    ) {
                        Text(
                            text = "Similar Content",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            state = similarScrollState,
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    similarScrollState.dispatchRawDelta(-dragAmount)
                                }
                            },
                        ) {
                            items(validRecs.take(18)) { rec ->
                                val recProvider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(rec.apiName)!!
                                com.lagradost.cloudstream3.desktop.ui.components.PosterCard(
                                    item = rec,
                                    provider = recProvider,
                                    itemWidth = 150.dp,
                                    onClick = {
                                        navController.navigate(Screen.Details(recProvider, rec.url, rec.name, rec.posterUrl, null, false))
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Row {
                                    IconButton(onClick = { coroutineScope.launch { similarScrollState.animateScrollBy(-500f) } }) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(onClick = { coroutineScope.launch { similarScrollState.animateScrollBy(500f) } }) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Back button
        IconButton(
            onClick = { navController.goBack() },
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
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
                Icons.Default.ArrowBack,
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
