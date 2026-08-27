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
import dev.chrisbanes.haze.HazeState
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

        val bgUrl = remember(response?.backgroundPosterUrl, response?.posterUrl, uiState.enrichedBackdropUrl, provider) {
            uiState.enrichedBackdropUrl?.takeIf { it.isNotBlank() }
                ?: provider.fixUrlNull(response?.backgroundPosterUrl)?.takeIf { it.isNotBlank() }
                ?: provider.fixUrlNull(response?.posterUrl)?.takeIf { it.isNotBlank() }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (heroBackgroundBlurEnabled && bgUrl != null) {
                androidx.compose.animation.Crossfade(
                    targetState = bgUrl,
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
            if (isLoading) {
                if (fakeData != null) {
                    DetailsContent(onNavigate, onBack, provider, fakeData, screenshots, enrichmentPhase, isLoading = true, onPlay = handlePlay, onDownload = handleDownload, enableDownloadButtons = !(uiState.autoPlayEnabled ?: true), onToggleWatched = handleToggleWatched, onToggleSeasonWatched = handleToggleSeasonWatched, onRemoveEpisodeWatched = handleRemoveEpisodeWatched, onToggleEpisodesStackedView = handleToggleEpisodesStackedView, onSetEpisodeViewMode = handleSetEpisodeViewMode, dynamicColorEnabled = heroBackgroundBlurEnabled, uiState = uiState, showHistory = showHistory)
                } else {
                    DetailsSkeletonPlaceholder(
                        onBack = onBack,
                        preloadedPoster = viewModel.preloadedPoster,
                        preloadedBg = viewModel.preloadedBg,
                    )
                }
            } else if (response != null) {
                DetailsContent(onNavigate, onBack, provider, response, screenshots, enrichmentPhase, isLoading = false, onPlay = handlePlay, onDownload = handleDownload, enableDownloadButtons = !(uiState.autoPlayEnabled ?: true), onToggleWatched = handleToggleWatched, onToggleSeasonWatched = handleToggleSeasonWatched, onRemoveEpisodeWatched = handleRemoveEpisodeWatched, onToggleEpisodesStackedView = handleToggleEpisodesStackedView, onSetEpisodeViewMode = handleSetEpisodeViewMode, dynamicColorEnabled = heroBackgroundBlurEnabled, uiState = uiState, showHistory = showHistory)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (error != null) "Error: $error" else "Failed to load details.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
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
                val panelWidth = minOf(450.dp, maxWidth * 0.92f)
                val offsetX by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isPanelOpen) 0.dp else panelWidth,
                    animationSpec = tween(300),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = offsetX),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                            .clickable { if (isPanelOpen) viewModel.onEvent(DetailsUiEvent.OnCloseLinksPanel) else viewModel.onEvent(DetailsUiEvent.OnOpenLinksPanel(activeLinkData)) }
                            .padding(horizontal = 12.dp, vertical = 32.dp), // Taller hit area for easier clicking in the middle
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
                            .width(panelWidth)
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
) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

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
            hazeState = hazeState,
            enrichmentPhase = enrichmentPhase,
            modifier = Modifier
                .fillMaxWidth()
                .run {
                    if (isWindowCompact) this.height(minOf(360.dp, viewportHeight * 0.48f))
                    else this.fillMaxHeight()
                },
            dynamicColorEnabled = dynamicColorEnabled,
            uiState = uiState,
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = 32.dp,
            ),
        ) {
            item(key = "HeroAndTabs") {
                Box(
                    modifier = Modifier.fillMaxWidth().run {
                        if (isWindowCompact) this else this.heightIn(min = viewportHeight)
                    },
                    contentAlignment = if (isWindowCompact) Alignment.TopStart else Alignment.BottomStart,
                ) {
                    DetailsMetadata(
                        provider = provider,
                        data = data,
                        hazeState = hazeState,
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
                    )

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

                    val showCurrentTime by AppearanceConfig.detailsShowCurrentTime.collectAsState()
                    val showEndTime by AppearanceConfig.detailsShowEndTime.collectAsState()
                    val clockTimeFormat by AppearanceConfig.clockTimeFormat.collectAsState()

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

                    val remainingSecondsForEnd = remember(latestHistory, data, progress) {
                        if (latestHistory != null && latestHistory.duration > 0) {
                            if (progress > 0f && progress < 1f) {
                                latestHistory.duration - latestHistory.position
                            } else {
                                latestHistory.duration
                            }
                        } else if ((data as? com.lagradost.cloudstream3.MovieLoadResponse)?.duration != null) {
                            (data as com.lagradost.cloudstream3.MovieLoadResponse).duration?.toLong()?.times(60L)
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

                    val progressInfo = remember(latestHistory, progress) {
                        if (latestHistory != null && latestHistory.duration > 0 && progress > 0f && progress < 1f) {
                            val leftSeconds = (latestHistory.duration - latestHistory.position).coerceAtLeast(0)
                            val leftMins = leftSeconds / 60L
                            val hours = leftMins / 60L
                            val mins = leftMins % 60L
                            val timeStr = when {
                                hours > 0 && mins > 0 -> "${hours}h ${mins}m left"
                                hours > 0 -> "${hours}h left"
                                leftMins > 0 -> "${leftMins}m left"
                                else -> "< 1m left"
                            }
                            val pctStr = "${(progress * 100).toInt()}%"
                            "$pctStr watched • $timeStr"
                        } else {
                            null
                        }
                    }

                    val progressLabel = remember(latestHistory) {
                        if (latestHistory != null) {
                            val ep = latestHistory.episode
                            val s = latestHistory.season
                            when {
                                s != null && s > 0 && ep != null && ep > 0 -> "CONTINUE WATCHING S$s: E$ep"
                                ep != null && ep > 0 -> "CONTINUE WATCHING E$ep"
                                else -> "CONTINUE WATCHING"
                            }
                        } else {
                            "CONTINUE WATCHING"
                        }
                    }

                    if (progressInfo != null || showTimePill) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    start = if (viewportWidth < 1100.dp) 24.dp else 64.dp,
                                    end = if (viewportWidth < 1100.dp) 24.dp else 64.dp,
                                    bottom = 64.dp,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (progressInfo != null && progress > 0f && progress < 1f) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = progressLabel,
                                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, letterSpacing = 1.sp),
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = progressInfo,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(2.5.dp), spotColor = Color.Black, ambientColor = Color.Black)
                                            .clip(RoundedCornerShape(2.5.dp))
                                            .background(Color.White.copy(alpha = 0.25f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.5.dp)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progress)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.5.dp))
                                                .background(Color.White),
                                        )
                                    }
                                }

                                if (showTimePill) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .padding(top = 6.dp)
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
                        }
                    }
                }
            }

            detailsSectionOrder.filter { it !in detailsDisabledSections }.forEach { sectionKey ->
                when (sectionKey) {
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.EPISODES -> {
                        if (!isMovieLike) {
                            item(key = "Episodes") {
                                BoxWithConstraints {
                                    val hPadding = if (maxWidth < 1100.dp) 24.dp else 64.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
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

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill(isHome = false, isCompact = isWindowCompact)
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
