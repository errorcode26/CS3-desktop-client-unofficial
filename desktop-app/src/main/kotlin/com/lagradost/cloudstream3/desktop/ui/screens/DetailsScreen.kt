package com.lagradost.cloudstream3.desktop.ui.screens

// TODO: Yeah I know this is a big ball of mud, but let's refactor this later.
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Switch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu
import com.lagradost.cloudstream3.desktop.ui.components.DesktopThemeColors.*
import com.lagradost.cloudstream3.desktop.ui.screens.details.*
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import dev.chrisbanes.haze.HazeState
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDetailsScreen(navController: NavController, provider: MainAPI, url: String, preloadedName: String? = null, preloadedPoster: String? = null, preloadedBg: String? = null, autoPlay: Boolean = false) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember(url) { DetailsViewModel(coroutineScope, provider, url, preloadedName, preloadedPoster, preloadedBg) }

    val response by viewModel.response.collectAsState()
    val fakeData by viewModel.fakeData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val activeLinkData by viewModel.activeLinkData.collectAsState()
    val isPanelOpen by viewModel.isPanelOpen.collectAsState()
    val enrichmentTrigger by viewModel.enrichmentTrigger.collectAsState()
    val screenshots by viewModel.screenshots.collectAsState()

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
                    onError = { err -> playbackError = err }
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
            } else null
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

    Box(modifier = Modifier.fillMaxSize()) {
            // 1. Details Content
            if (isLoading) {
                if (fakeData != null) {
                    DetailsContent(navController, provider, fakeData!!, screenshots, enrichmentTrigger, isLoading = true, onPlay = handlePlay)
                } else {
                    DetailsSkeletonPlaceholder(
                        onBack = { navController.goBack() },
                        preloadedPoster = preloadedPoster,
                        preloadedBg = preloadedBg
                    )
                }
            } else if (response != null) {
                DetailsContent(navController, provider, response!!, screenshots, enrichmentTrigger, isLoading = false, onPlay = handlePlay)
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

            // 2. Dim Overlay
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

            // 3. Side Panel with Links
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
fun DetailsContent(navController: NavController, provider: MainAPI, data: LoadResponse, screenshots: List<String>?, enrichmentTrigger: Int, isLoading: Boolean = false, onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit) {
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 1. Play / Resume Button ──
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
                val targetEp = remember(allEpisodes, latestHistory) {
                    if (latestHistory != null && allEpisodes.isNotEmpty()) {
                        allEpisodes.find { it.data == latestHistory.episodeId } ?: allEpisodes.firstOrNull()
                    } else {
                        allEpisodes.firstOrNull()
                    }
                }

                val buttonLabel = remember(data, latestHistory, targetEp) {
                    if (latestHistory != null) {
                        if (targetEp?.episode != null) "Resume E${targetEp.episode}"
                        else "Resume"
                    } else {
                        if (targetEp?.season != null && targetEp.episode != null) "Play S${targetEp.season} E${targetEp.episode}"
                        else if (targetEp?.episode != null) "Play E${targetEp.episode}"
                        else "Play"
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable { onPlayClick() }
                        .padding(horizontal = 36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF0F0F0F),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = buttonLabel,
                            color = Color(0xFF0F0F0F),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }

        LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
            item {
                // Hero section — backdrop fills the full window height, metadata overlaid at bottom
                val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
                val heroHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                    windowInfo.containerSize.height.toDp()
                }
                Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                    DetailsBackdrop(provider = provider, data = data, scrollState = scrollState, hazeState = hazeState, enrichmentTrigger = enrichmentTrigger, modifier = Modifier.fillMaxSize())
                    DetailsMetadata(provider = provider, data = data, hazeState = hazeState, heroAction = heroAction, enrichmentTrigger = enrichmentTrigger, isLoading = isLoading)
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
                    onPlay = onPlay
                )
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCastSection(data = data, provider = provider)
                    }
                }
            }

            if (!screenshots.isNullOrEmpty()) {
                item {
                    Text(
                        text = "Screenshots",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(screenshots, key = { it }) { imgUrl ->
                            Surface(
                                modifier = Modifier
                                    .width(280.dp) // Slightly smaller width so it acts as a divider
                                    .aspectRatio(16f / 9f)
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
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // ── Similar Content / Recommendations ──
            val validRecs = data.recommendations?.filterIsInstance<com.lagradost.cloudstream3.SearchResponse>()?.filter { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it.apiName) != null } ?: emptyList()

            if (validRecs.isNotEmpty()) {
                item {
                    Text(
                        text = "Similar Content",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(validRecs) { rec ->
                            val recProvider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(rec.apiName)!!
                            com.lagradost.cloudstream3.desktop.ui.components.PosterCard(
                                item = rec,
                                provider = recProvider,
                                onClick = {
                                    navController.navigate(Screen.Details(recProvider, rec.url, rec.name, rec.posterUrl, null, false))
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Back button ──
        IconButton(
            onClick = { navController.goBack() },
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }

        // ── Window Controls ──
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill(isHome = false)
        }

        // ── Screenshot Overlay ──
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
    preloadedBg: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070E))
    ) {
        // Backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .shimmerBackground()
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
                        colors = listOf(Color.Transparent, Color(0xFF07070E))
                    )
                )
        )
        
        // Metadata alignment
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1200.dp)
                    .padding(start = 32.dp, end = 32.dp, top = 100.dp, bottom = 32.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Poster
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .shimmerBackground()
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
                                .shimmerBackground()
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
                                        .shimmerBackground()
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
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
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
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .shimmerBackground()
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
                        .shimmerBackground()
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
                        .shimmerBackground()
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
                                    .shimmerBackground()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
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
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Window Controls ──
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill(isHome = false)
        }
    }
}
