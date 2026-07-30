package com.lagradost.cloudstream3.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.lagradost.cloudstream3.desktop.player.ComposeNativeWebPlayer
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEvent
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun EmbeddedVideoPlayer(
    launchData: VideoLaunchData,
    isExiting: Boolean = false,
    onClose: () -> Unit,
    onError: (String) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { EmbeddedPlayerViewModel() }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    LaunchedEffect(launchData) {
        viewModel.onEvent(PlayerUiEvent.OnInit(launchData))
    }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEffect.ShowToast -> {
                    // Ignored for now or use a local toast
                }
                is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEffect.ClosePlayer -> {
                    onClose()
                }
                is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEffect.ShowError -> {
                    onError(effect.message)
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val currentLaunchData = uiState.launchData
    val isLoadingNextEpisode = uiState.isLoadingNextEpisode
    val nextEpisodeError = uiState.nextEpisodeError
    val nextEpisodeLinks = uiState.nextEpisodeLinks
    val targetEpisodeData = uiState.targetEpisodeData

    if (currentLaunchData == null) return

    val actualLaunchData = currentLaunchData!!
    val isScrapingLinks = uiState.isScrapingLinks

    var isInitialLoad by remember(actualLaunchData.history.episodeId) { mutableStateOf(true) }
    var userSkippedScraping by remember(actualLaunchData.history.episodeId) { mutableStateOf(false) }
    var currentLinkIndex by remember(actualLaunchData.history.episodeId) {
        mutableIntStateOf(actualLaunchData.initialIndex)
    }
    var fallbackToBeginning by remember(actualLaunchData.history.episodeId) { mutableStateOf(false) }

    var isLoading by remember(actualLaunchData.history.episodeId) { mutableStateOf(true) }
    var showSources by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var lastPositionSec by remember { mutableStateOf(0L) }
    var lastDurationSec by remember { mutableStateOf(0L) }
    var lastSavedPositionSec by remember { mutableStateOf(0L) }

    var activelyPlayingLink by remember { mutableStateOf<com.lagradost.cloudstream3.utils.ExtractorLink?>(null) }
    var lastLinkIndex by remember { mutableIntStateOf(-1) }
    var lastEpisodeId by remember { mutableStateOf<String?>(null) }

    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen ?: false
    val initialPlacement = remember { windowState?.placement ?: WindowPlacement.Floating }

    // PlayerState is hoisted to top level so it can be reset on episode/source changes
    val playerState = remember { PlayerState() }

    var lastSavedHistory by remember { mutableStateOf(actualLaunchData.history) }

    // Reset all playback state when a new episode loads
    LaunchedEffect(actualLaunchData.history.episodeId) {
        if (lastDurationSec > 0 && lastPositionSec > 0) {
            val screenshotPath = "${PlatformPaths.appDataDir.absolutePath}/screenshots/history_${lastSavedHistory.parentId}.jpg"
            File(screenshotPath).parentFile.mkdirs()
            playerState.takeScreenshot(screenshotPath)

            val updatedHistory = lastSavedHistory.copy(
                position = lastPositionSec,
                duration = lastDurationSec,
                screenshotUrl = "file:///$screenshotPath",
                updateTime = System.currentTimeMillis(),
            )
            viewModel.onEvent(PlayerUiEvent.OnSavePosition(updatedHistory))
        }
        lastSavedHistory = actualLaunchData.history

        lastPositionSec = actualLaunchData.startPositionMs / 1000L
        lastDurationSec = actualLaunchData.history.duration
        lastSavedPositionSec = actualLaunchData.startPositionMs / 1000L
        playerState.reset()
        com.lagradost.player.impl.proxy.LocalStreamProxyState.loadingStatus.value = null
    }

    LaunchedEffect(nextEpisodeError) {
        if (nextEpisodeError != null) {
            onError(nextEpisodeError!!)
            onClose()
        }
    }

    // Reset loading spinner + player state when switching between sources
    LaunchedEffect(activelyPlayingLink) {
        com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: State reset requested due to activelyPlayingLink changing")
        playerState.reset()
        isLoading = true
    }

    DisposableEffect(Unit) {
        onDispose {
            if (lastDurationSec > 0 && lastPositionSec > 0) {
                // Screenshot handling
                val screenshotPath = "${PlatformPaths.appDataDir.absolutePath}/screenshots/history_${actualLaunchData.history.parentId}.jpg"
                File(screenshotPath).parentFile.mkdirs()
                playerState.takeScreenshot(screenshotPath)

                val updatedHistory = actualLaunchData.history.copy(
                    position = lastPositionSec,
                    duration = lastDurationSec,
                    screenshotUrl = "file:///$screenshotPath",
                    updateTime = System.currentTimeMillis(),
                )
                viewModel.onEvent(PlayerUiEvent.OnSavePosition(updatedHistory))
            }
            playerState.detachMpv()
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

            if (!isFinished) {
                var countdownToNextEpisode by remember { mutableStateOf<Int?>(null) }

                LaunchedEffect(countdownToNextEpisode) {
                    if (countdownToNextEpisode != null) {
                        if (countdownToNextEpisode!! > 0) {
                            kotlinx.coroutines.delay(1000)
                            countdownToNextEpisode = countdownToNextEpisode!! - 1
                        } else {
                            countdownToNextEpisode = null
                            viewModel.onEvent(PlayerUiEvent.OnLoadNextEpisode)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // isProbingOverlay: true until onPlaybackReady fires.
                    // Keyed on episodeId so it resets when switching episodes.
                    // NOT tied to isScrapingLinks — background scraping can continue while video plays.
                    var isProbingOverlay by remember(actualLaunchData.history.episodeId) { mutableStateOf(true) }

                    var failedLinks by remember(actualLaunchData.history.episodeId) {
                        mutableStateOf(emptyMap<Int, String>())
                    }

                    // Auto-play the next link if we were waiting for links and a new one arrives
                    LaunchedEffect(actualLaunchData.links.size, failedLinks) {
                        if (failedLinks.size >= actualLaunchData.links.size && isScrapingLinks) {
                            isLoading = true
                        } else if (failedLinks.size < actualLaunchData.links.size) {
                            // Find the next link that hasn't failed
                            val nextIndex = (0 until actualLaunchData.links.size).firstOrNull { it > currentLinkIndex && !failedLinks.containsKey(it) }
                            if (nextIndex != null && (failedLinks.containsKey(currentLinkIndex))) {
                                currentLinkIndex = nextIndex
                                isLoading = true
                            }
                        }
                    }

                    // Keep currentLinkIndex in sync if the list is re-sorted by the ViewModel
                    LaunchedEffect(actualLaunchData.links) {
                        val activeLink = activelyPlayingLink
                        if (activeLink != null && actualLaunchData.links.isNotEmpty()) {
                            // Try to find the exact same object by reference or URL/name match
                            val newIndex = actualLaunchData.links.indexOfFirst { it === activeLink || (it.url == activeLink.url && it.name == activeLink.name) }
                            if (newIndex != -1 && newIndex != currentLinkIndex) {
                                currentLinkIndex = newIndex
                                lastLinkIndex = newIndex
                            }
                        }
                    }

                    val autoPlay = uiState.autoPlayEnabled

                    val waitForLinks = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY_WAIT_FOR_LINKS) ?: true
                    val preferredQuality = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"
                    val targetQualityInt = when (preferredQuality) {
                        "2160p (4K)" -> com.lagradost.cloudstream3.utils.Qualities.P2160.value
                        "1080p" -> com.lagradost.cloudstream3.utils.Qualities.P1080.value
                        "720p" -> com.lagradost.cloudstream3.utils.Qualities.P720.value
                        "480p", "480p / SD" -> com.lagradost.cloudstream3.utils.Qualities.P480.value
                        else -> null
                    }
                    val hasMatchingLink = if (targetQualityInt != null) {
                        actualLaunchData.links.any { it.quality == targetQualityInt }
                    } else {
                        actualLaunchData.links.isNotEmpty()
                    }

                    val shouldWaitForScrape = if (!autoPlay || userSkippedScraping) {
                        !autoPlay && !userSkippedScraping
                    } else if (waitForLinks) {
                        isScrapingLinks
                    } else {
                        isScrapingLinks && !hasMatchingLink
                    }

                    // Lock the playing link so background scraper additions don't interrupt playback
                    if (actualLaunchData.links.isNotEmpty()) {
                        // Only lock once we are no longer waiting for the scrape, to allow the VM to sort the list when finished.
                        if (!shouldWaitForScrape) {
                            if (lastLinkIndex != currentLinkIndex || activelyPlayingLink == null || lastEpisodeId != actualLaunchData.history.episodeId) {
                                lastLinkIndex = currentLinkIndex
                                lastEpisodeId = actualLaunchData.history.episodeId
                                activelyPlayingLink = actualLaunchData.links.getOrNull(currentLinkIndex)
                                com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: Preparing to load link [$currentLinkIndex] - Name: ${activelyPlayingLink?.name} | URL: ${activelyPlayingLink?.url?.take(50)}...")
                            }
                        }
                    } else {
                        if (activelyPlayingLink != null && !isExiting) {
                            com.lagradost.common.logging.AppLogger.d("EmbeddedVideoPlayer: No links available. Clearing activelyPlayingLink.")
                        }
                        activelyPlayingLink = null
                    }

                    val isSwitchingEpisode = isLoadingNextEpisode
                    val safeLink = if (shouldWaitForScrape || isExiting || isSwitchingEpisode) null else activelyPlayingLink

                    val displayTitle = if (targetEpisodeData != null) {
                        buildString {
                            append(actualLaunchData.history.showName)
                            val s = targetEpisodeData?.season
                            val e = targetEpisodeData?.episode
                            if (s != null && e != null) {
                                append(" - S${s}E$e")
                            } else if (e != null) {
                                append(" - E$e")
                            }
                            val name = targetEpisodeData?.name
                            if (!name.isNullOrBlank() && name != "Episode $e") {
                                append(" - $name")
                            }
                        }
                    } else {
                        actualLaunchData.title
                    }

                    val displayEpisodeId = targetEpisodeData?.data ?: actualLaunchData.history.episodeId

                    val episodes = uiState.episodes
                    val backdropUrl = actualLaunchData.loadResponse?.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                        ?: actualLaunchData.loadResponse?.posterUrl
                    val logoUrl = actualLaunchData.loadResponse?.logoUrl

                    val plot = targetEpisodeData?.description ?: actualLaunchData.loadResponse?.plot
                    val year = uiState.launchData?.loadResponse?.year // or actualLaunchData.loadResponse?.year
                    val tags = actualLaunchData.loadResponse?.tags

                    ComposeNativeWebPlayer(
                        link = safeLink,
                        title = displayTitle,
                        seriesPosterUrl = actualLaunchData.loadResponse?.posterUrl,
                        plot = plot,
                        year = year,
                        tags = tags,
                        subtitles = actualLaunchData.subtitles,
                        isExiting = isExiting,
                        startPositionMs = if (fallbackToBeginning) {
                            0L
                        } else if (currentLinkIndex != 0 && lastPositionSec > 0) {
                            lastPositionSec * 1000L
                        } else {
                            actualLaunchData.startPositionMs
                        },
                        shouldPauseForResume = !fallbackToBeginning && isInitialLoad && actualLaunchData.startPositionMs > 0,
                        links = actualLaunchData.links,
                        currentLinkIndex = currentLinkIndex,
                        episodes = episodes,
                        currentEpisodeId = displayEpisodeId,
                        isLoading = isLoading || isLoadingNextEpisode,
                        isProbing = !isExiting && isProbingOverlay,
                        failedLinks = failedLinks,
                        backdropUrl = backdropUrl,
                        logoUrl = logoUrl,
                        onLinkChange = { index ->
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onLinkChange triggered -> new index: $index")
                            playerState.pause()
                            currentLinkIndex = index
                            userSkippedScraping = true
                            isLoading = true
                            isProbingOverlay = true // Show overlay again while switching to a new link
                        },
                        onEpisodeChange = { epId ->
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onEpisodeChange triggered -> new episodeId: $epId")
                            playerState.pause()
                            isLoading = true
                            isProbingOverlay = true
                            val targetEp = episodes.find { it.data == epId }
                            if (targetEp != null) {
                                viewModel.onEvent(PlayerUiEvent.OnLoadEpisode(targetEp))
                            }
                        },
                        onNextEpisode = {
                            playerState.pause()
                            isLoading = true
                            isProbingOverlay = true
                            viewModel.onEvent(PlayerUiEvent.OnLoadNextEpisode)
                        },
                        onReplayEpisode = {
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onReplayEpisode triggered")
                            playerState.pause()
                            isLoading = true
                            isProbingOverlay = true
                            val currentEp = episodes.find { it.data == actualLaunchData.history.episodeId }
                            if (currentEp != null) {
                                viewModel.onEvent(PlayerUiEvent.OnLoadEpisode(currentEp))
                            } else {
                                viewModel.onEvent(PlayerUiEvent.OnInit(actualLaunchData))
                            }
                        },
                        onPlaybackReady = {
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onPlaybackReady received for link index $currentLinkIndex! Video is starting.")
                            isLoading = false
                            isInitialLoad = false
                            isProbingOverlay = false // Video is playing — dismiss the overlay
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
                                viewModel.onEvent(PlayerUiEvent.OnSavePosition(updatedHistory))
                            }
                        },
                        onCloseRequest = {
                            onClose()
                        },
                        onSkipScraping = {
                            userSkippedScraping = true
                            viewModel.onEvent(PlayerUiEvent.OnCancelScraping)
                        },
                        onFinished = {
                            val hasNext = uiState.hasNextEpisode
                            val isAutoPlay = uiState.autoPlayEnabled
                            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("window.showVideoEnded && window.showVideoEnded($hasNext, $isAutoPlay);")
                        },
                        onPlaybackError = { err ->
                            com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: Playback error on link index $currentLinkIndex. Error: $err")
                            val newFailed = failedLinks + (currentLinkIndex to err)
                            failedLinks = newFailed

                            val nextIndex = (0 until actualLaunchData.links.size)
                                .firstOrNull { it > currentLinkIndex && !newFailed.containsKey(it) }

                            when {
                                nextIndex != null -> {
                                    com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: Moving to next link index $nextIndex")
                                    currentLinkIndex = nextIndex
                                    isLoading = true
                                    isProbingOverlay = true // Re-show probing so JS renders updated ✗ and new active spinner
                                }
                                newFailed.size >= actualLaunchData.links.size -> {
                                    if (isScrapingLinks) {
                                        // Wait for more links to arrive (Player stays active, overlay shows waiting)
                                        com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: All current links failed, but still scraping. Waiting...")
                                        isLoading = true
                                        isProbingOverlay = true
                                    } else {
                                        if (!fallbackToBeginning && actualLaunchData.startPositionMs > 0) {
                                            // Stream might not support range requests. Fallback to playing from the beginning.
                                            com.lagradost.common.logging.AppLogger.w("EmbeddedVideoPlayer: All links failed on RESUME. Falling back to startPositionMs = 0")
                                            fallbackToBeginning = true
                                            failedLinks = emptyMap()
                                            currentLinkIndex = 0
                                            isLoading = true
                                            isProbingOverlay = true
                                        } else {
                                            // All sources failed — dismiss overlay and show error
                                            com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: All sources failed completely. Terminating playback.")
                                            isProbingOverlay = false
                                            onError("All sources failed. Please try again later.")
                                            onClose()
                                        }
                                    }
                                }
                                else -> {
                                    val anyUntried = (0 until actualLaunchData.links.size)
                                        .firstOrNull { it !in newFailed }
                                    if (anyUntried != null) {
                                        com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: Moving to untried fallback link index $anyUntried")
                                        currentLinkIndex = anyUntried
                                        isLoading = true
                                        isProbingOverlay = true // Re-show probing for the fallback link
                                    } else {
                                        if (isScrapingLinks) {
                                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: All untried links failed, but still scraping. Waiting...")
                                            isLoading = true
                                            isProbingOverlay = true
                                            // Wait for more links to arrive
                                        } else {
                                            if (!fallbackToBeginning && actualLaunchData.startPositionMs > 0) {
                                                // Fallback to playing from the beginning
                                                com.lagradost.common.logging.AppLogger.w("EmbeddedVideoPlayer: All untried links failed on RESUME. Falling back to startPositionMs = 0")
                                                fallbackToBeginning = true
                                                failedLinks = emptyMap()
                                                currentLinkIndex = 0
                                                isLoading = true
                                                isProbingOverlay = true
                                            } else {
                                                // All sources exhausted — dismiss overlay and show error
                                                com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: All untried sources exhausted. Terminating playback.")
                                                isProbingOverlay = false
                                                onError("All sources failed. Please try again later.")
                                                onClose()
                                            }
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
                        onClose()
                    }) {
                        Text("Close Player")
                    }
                }
            }
        } // end BoxWithConstraints
    } // end Column
} // end EmbeddedVideoPlayer
