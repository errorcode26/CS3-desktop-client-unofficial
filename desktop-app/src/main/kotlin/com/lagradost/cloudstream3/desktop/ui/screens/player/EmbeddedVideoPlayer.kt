package com.lagradost.cloudstream3.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.lagradost.cloudstream3.desktop.player.ComposeNativeWebPlayer
import com.lagradost.cloudstream3.desktop.ui.screens.player.components.PlayerLoadingOverlay
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun EmbeddedVideoPlayer(
    launchData: VideoLaunchData,
    isExiting: Boolean = false,
    onClose: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { EmbeddedPlayerViewModel(coroutineScope) }

    LaunchedEffect(launchData) {
        viewModel.init(launchData)
    }

    val currentLaunchData by viewModel.launchData.collectAsState()
    val isLoadingNextEpisode by viewModel.isLoadingNextEpisode.collectAsState()
    val nextEpisodeError by viewModel.nextEpisodeError.collectAsState()
    val nextEpisodeLinks by viewModel.nextEpisodeLinks.collectAsState()
    val targetEpisodeData by viewModel.targetEpisodeData.collectAsState()

    if (currentLaunchData == null) return

    val actualLaunchData = currentLaunchData!!
    val isScrapingLinks by viewModel.isScrapingLinks.collectAsState()

    var isInitialLoad by remember(actualLaunchData.history.episodeId) { mutableStateOf(true) }
    var userSkippedScraping by remember(actualLaunchData.history.episodeId) { mutableStateOf(false) }
    var currentLinkIndex by remember(actualLaunchData.history.episodeId) {
        mutableIntStateOf(actualLaunchData.initialIndex)
    }

    var isLoading by remember { mutableStateOf(true) }
    var failedLinks by remember { mutableStateOf(setOf<Int>()) }
    var showSources by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var lastPositionSec by remember { mutableStateOf(0L) }
    var lastDurationSec by remember { mutableStateOf(0L) }
    var lastSavedPositionSec by remember { mutableStateOf(0L) }

    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen?.value ?: false
    val initialPlacement = remember { windowState?.placement ?: WindowPlacement.Floating }

    // PlayerState is hoisted to top level so it can be reset on episode/source changes
    val playerState = remember { PlayerState() }

    var lastSavedHistory by remember { mutableStateOf(actualLaunchData.history) }

    // Reset all playback state when a new episode loads
    LaunchedEffect(actualLaunchData) {
        if (lastDurationSec > 0 && lastPositionSec > 0) {
            val updatedHistory = lastSavedHistory.copy(
                position = lastPositionSec,
                duration = lastDurationSec,
                updateTime = System.currentTimeMillis(),
            )
            DesktopDataStore.setLastWatched(updatedHistory)
        }
        lastSavedHistory = actualLaunchData.history

        isLoading = true
        lastPositionSec = actualLaunchData.startPositionMs / 1000L
        lastDurationSec = actualLaunchData.history.duration
        lastSavedPositionSec = actualLaunchData.startPositionMs / 1000L
        playerState.reset()
        com.lagradost.player.impl.proxy.LocalStreamProxyState.loadingStatus.value = null
    }

    LaunchedEffect(nextEpisodeError) {
        if (nextEpisodeError != null) {
            actualLaunchData.onError?.invoke(nextEpisodeError!!)
            onClose()
        }
    }

    // Reset loading spinner + player state when switching between sources
    LaunchedEffect(currentLinkIndex) {
        isLoading = true
        playerState.reset()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (lastDurationSec > 0 && lastPositionSec > 0) {
                val updatedHistory = actualLaunchData.history.copy(
                    position = lastPositionSec,
                    duration = lastDurationSec,
                    updateTime = System.currentTimeMillis(),
                )
                DesktopDataStore.setLastWatched(updatedHistory)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val playerMaxWidth = maxWidth
            val playerMaxHeight = maxHeight

            // --- Main Video + Controls ---
            if (!isFinished) {
                var countdownToNextEpisode by remember { mutableStateOf<Int?>(null) }

                LaunchedEffect(countdownToNextEpisode) {
                    if (countdownToNextEpisode != null) {
                        if (countdownToNextEpisode!! > 0) {
                            kotlinx.coroutines.delay(1000)
                            countdownToNextEpisode = countdownToNextEpisode!! - 1
                        } else {
                            countdownToNextEpisode = null
                            viewModel.loadNextEpisode()
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    var activelyPlayingLink by remember { mutableStateOf<com.lagradost.cloudstream3.utils.ExtractorLink?>(null) }
                    var lastLinkIndex by remember { mutableStateOf(-1) }
                    var lastEpisodeId by remember { mutableStateOf<String?>(null) }

                    // isProbingOverlay: true until onPlaybackReady fires.
                    // Keyed on episodeId so it resets when switching episodes.
                    // NOT tied to isScrapingLinks — background scraping can continue while video plays.
                    var isProbingOverlay by remember(actualLaunchData.history.episodeId) { mutableStateOf(true) }

                    var failedLinks by remember(actualLaunchData.history.episodeId) {
                        mutableStateOf(setOf<Int>())
                    }

                    // Auto-play the next link if we were waiting for links and a new one arrives
                    LaunchedEffect(actualLaunchData.links.size, failedLinks) {
                        if (failedLinks.size >= actualLaunchData.links.size && isScrapingLinks) {
                            // Do nothing, still waiting
                        } else if (failedLinks.size < actualLaunchData.links.size) {
                            // If we have an untried link that is higher than currentLinkIndex, or any untried link if we ran out
                            val nextIndex = (0 until actualLaunchData.links.size).firstOrNull { it > currentLinkIndex && it !in failedLinks }
                            if (nextIndex != null && (currentLinkIndex in failedLinks)) {
                                currentLinkIndex = nextIndex
                                isLoading = true
                            }
                        }
                    }

                    // Lock the playing link so background scraper additions don't interrupt playback
                    if (actualLaunchData.links.isNotEmpty()) {
                        if (lastLinkIndex != currentLinkIndex || activelyPlayingLink == null || lastEpisodeId != actualLaunchData.history.episodeId) {
                            lastLinkIndex = currentLinkIndex
                            lastEpisodeId = actualLaunchData.history.episodeId
                            activelyPlayingLink = actualLaunchData.links.getOrNull(currentLinkIndex)
                        }
                    } else {
                        activelyPlayingLink = null
                    }

                    val autoPlay = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
                    val shouldWaitForScrape = isScrapingLinks && !userSkippedScraping && !autoPlay
                    val safeLink = if (shouldWaitForScrape || isExiting) null else activelyPlayingLink

                    val episodes = viewModel.getEpisodesList()
                    val backdropUrl = actualLaunchData.loadResponse?.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                        ?: actualLaunchData.loadResponse?.posterUrl
                    val logoUrl = actualLaunchData.loadResponse?.logoUrl

                    ComposeNativeWebPlayer(
                        link = safeLink,
                        title = actualLaunchData.title,
                        seriesPosterUrl = actualLaunchData.loadResponse?.posterUrl,
                        subtitles = actualLaunchData.subtitles,
                        isExiting = isExiting,
                        startPositionMs = if (currentLinkIndex != 0 && lastPositionSec > 0) lastPositionSec * 1000L else actualLaunchData.startPositionMs,
                        shouldPauseForResume = isInitialLoad && actualLaunchData.startPositionMs > 0,
                        links = actualLaunchData.links,
                        currentLinkIndex = currentLinkIndex,
                        episodes = episodes,
                        currentEpisodeId = actualLaunchData.history.episodeId,
                        isLoading = isLoading || viewModel.isLoadingNextEpisode.value,
                        isProbing = !isExiting && isProbingOverlay,
                        failedLinks = failedLinks,
                        backdropUrl = backdropUrl,
                        logoUrl = logoUrl,
                            onLinkChange = { index ->
                                playerState.pause()
                                currentLinkIndex = index
                                isLoading = true
                                isProbingOverlay = true  // Show overlay again while switching to a new link
                            },
                            onEpisodeChange = { epId ->
                                playerState.pause()
                                val targetEp = episodes.find { it.data == epId }
                                if (targetEp != null) {
                                    viewModel.loadEpisode(targetEp)
                                }
                            },
                            onPlaybackReady = {
                                isLoading = false
                                isInitialLoad = false
                                isProbingOverlay = false  // Video is playing — dismiss the overlay
                            },
                            onPositionChange = { posMs, durMs ->
                                val currentPosSec = posMs / 1000L
                                val currentDurSec = durMs / 1000L
                                lastPositionSec = currentPosSec
                                lastDurationSec = currentDurSec

                                playerState.updatePositionFromPlayer(posMs)
                                playerState.updateDurationFromPlayer(durMs)

                                if (kotlin.math.abs(currentPosSec - lastSavedPositionSec) >= 5) {
                                    lastSavedPositionSec = currentPosSec
                                    val updatedHistory = actualLaunchData.history.copy(
                                        position = currentPosSec,
                                        duration = currentDurSec,
                                        updateTime = System.currentTimeMillis(),
                                    )
                                    saveJob?.cancel()
                                    saveJob = coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        delay(2000)
                                        DesktopDataStore.setLastWatched(updatedHistory)
                                    }
                                }
                            },
                            onCloseRequest = {
                                actualLaunchData.onClosed?.invoke()
                                onClose()
                            },
                            onSkipScraping = {
                                userSkippedScraping = true
                            },
                            onFinished = {
                                if (viewModel.hasNextEpisode()) {
                                    countdownToNextEpisode = 5
                                } else {
                                    isFinished = true
                                }
                            },
                            onPlaybackError = { err ->
                                val newFailed = failedLinks + currentLinkIndex
                                failedLinks = newFailed

                                val nextIndex = (0 until actualLaunchData.links.size)
                                    .firstOrNull { it > currentLinkIndex && it !in newFailed }

                                when {
                                    nextIndex != null -> {
                                        currentLinkIndex = nextIndex
                                        isLoading = true
                                    }
                                    newFailed.size >= actualLaunchData.links.size -> {
                                        if (isScrapingLinks) {
                                            // Wait for more links to arrive (Player stays active, overlay shows waiting)
                                            isLoading = true
                                        } else {
                                            // All sources failed — dismiss overlay and show error
                                            isProbingOverlay = false
                                            actualLaunchData.onError?.invoke("All sources failed. Please try again later.")
                                            onClose()
                                        }
                                    }
                                    else -> {
                                        val anyUntried = (0 until actualLaunchData.links.size)
                                            .firstOrNull { it !in newFailed }
                                        if (anyUntried != null) {
                                            currentLinkIndex = anyUntried
                                            isLoading = true
                                        } else {
                                            if (isScrapingLinks) {
                                                isLoading = true
                                                // Wait for more links to arrive
                                            } else {
                                                // All sources exhausted — dismiss overlay and show error
                                                isProbingOverlay = false
                                                actualLaunchData.onError?.invoke("All sources failed. Please try again later.")
                                                onClose()
                                            }
                                        }
                                    }
                                }
                            },
                            onFullscreenToggle = {
                                fullscreenController?.toggle?.invoke()
                            },
                            playerState = playerState,
                        )
                } // end outer Box
            } // end if (!error && !finished)



            // --- Finished State ---
            if (isFinished) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Video Ended",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All episodes watched.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        actualLaunchData.onClosed?.invoke()
                        onClose()
                    }) {
                        Text("Close Player")
                    }
                }
            }
        } // end BoxWithConstraints
    } // end Column
} // end EmbeddedVideoPlayer
