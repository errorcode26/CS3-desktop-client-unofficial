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
        // Surface the error but do not auto-close — current playback may still be active
        // and the user can manually select a different source.
        if (nextEpisodeError != null) {
            onError(nextEpisodeError.displayMessage)
        }
    }


    DisposableEffect(Unit) {
        onDispose {
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

                    var currentUrl by remember(actualLaunchData.history.episodeId) { mutableStateOf<String?>(null) }
                    var failedUrls by remember(actualLaunchData.history.episodeId) { mutableStateOf<Set<String>>(emptySet()) }
                    var scrapeStartTime by remember(actualLaunchData.history.episodeId) { mutableStateOf(System.currentTimeMillis()) }

                    val activelyPlayingLink = actualLaunchData.links.find { it.url == currentUrl }

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

                    val isResuming = !fallbackToBeginning && actualLaunchData.startPositionMs > 0

                    fun pickBestLink(candidates: List<com.lagradost.cloudstream3.utils.ExtractorLink>): com.lagradost.cloudstream3.utils.ExtractorLink? {
                        return candidates.minByOrNull { link ->
                            val qualityDelta = if (targetQualityInt != null) kotlin.math.abs(link.quality - targetQualityInt) else 0
                            val seekPenalty = if (isResuming) when {
                                link.isM3u8 || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 -> 1
                                link.isDash || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH -> 2
                                else -> 0 // PROGRESSIVE — seeks via HTTP Range
                            } else 0
                            seekPenalty * 10_000 + qualityDelta
                        }
                    }

                    val canPickLink = activelyPlayingLink == null && !isExiting && !isLoadingNextEpisode
                    
                    if (canPickLink && actualLaunchData.links.isNotEmpty()) {
                        val hasTargetQuality = targetQualityInt != null && actualLaunchData.links.any { it.quality == targetQualityInt }
                        val scrapeTimedOut = (System.currentTimeMillis() - scrapeStartTime) > 10000
                        
                        val shouldPickNow = !autoPlay || userSkippedScraping || !waitForLinks || !isScrapingLinks || hasTargetQuality || scrapeTimedOut
                        
                        if (shouldPickNow) {
                            val availableLinks = actualLaunchData.links.filter { it.url !in failedUrls }
                            val bestLink = pickBestLink(availableLinks)
                            if (bestLink != null) {
                                com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: Selected link URL ${bestLink.url.take(50)}...")
                                currentUrl = bestLink.url
                            }
                        }
                    }

                    val isSwitchingEpisode = isLoadingNextEpisode
                    val safeLink = if (isExiting || isSwitchingEpisode) null else activelyPlayingLink

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
                    val year = uiState.launchData?.loadResponse?.year
                    val tags = actualLaunchData.loadResponse?.tags

                    val displayLinkIndex = actualLaunchData.links.indexOfFirst { it.url == currentUrl }.coerceAtLeast(0)
                    val uiFailedLinks = actualLaunchData.links
                        .mapIndexedNotNull { index, link -> if (link.url in failedUrls) index to "Failed" else null }
                        .toMap()

                    val handlePlaybackError: (String) -> Unit = { err ->
                        com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: Playback error. Error: $err")
                        
                        if (currentUrl != null) {
                            failedUrls = failedUrls + currentUrl!!
                            currentUrl = null // Clears the current link, allowing the loop above to pick the next one
                        }

                        val unfailed = actualLaunchData.links.filter { it.url !in failedUrls }
                        val nextLink = pickBestLink(unfailed)

                        when {
                            nextLink != null -> {
                                com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: Moving to next link (quality=${nextLink.quality})")
                                isLoading = true
                                isProbingOverlay = true
                            }
                            isScrapingLinks -> {
                                com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: All current links failed, but still scraping. Waiting...")
                                isLoading = true
                                isProbingOverlay = true
                            }
                            !fallbackToBeginning && actualLaunchData.startPositionMs > 0 -> {
                                com.lagradost.common.logging.AppLogger.w("EmbeddedVideoPlayer: All links failed on RESUME. Falling back to startPositionMs = 0")
                                fallbackToBeginning = true
                                failedUrls = emptySet()
                                isLoading = true
                                isProbingOverlay = true
                            }
                            else -> {
                                com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: All sources exhausted. Terminating playback.")
                                isProbingOverlay = false
                                actualLaunchData.history.episodeId?.let { LinkCache.remove(it) }
                                onError("All sources failed. Please try again later.")
                                onClose()
                            }
                        }
                    }

                    // Safety-net timeout: MPV fires its own error after 45s on a hung stream.
                    // This 48s fallback catches any edge case where MPV silently hangs without
                    // surfacing an event (e.g. proxy stall, JNA freeze during init).
                    LaunchedEffect(activelyPlayingLink, isProbingOverlay) {
                        if (activelyPlayingLink != null && isProbingOverlay) {
                            kotlinx.coroutines.delay(48000)
                            if (isProbingOverlay) {
                                com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: Link timed out after 48s. Forcing fallback.")
                                handlePlaybackError("Connection timed out")
                            }
                        }
                    }

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
                        } else if (displayLinkIndex != 0 && lastPositionSec > 0) {
                            lastPositionSec * 1000L
                        } else {
                            actualLaunchData.startPositionMs
                        },
                        shouldPauseForResume = false,
                        links = actualLaunchData.links,
                        currentLinkIndex = displayLinkIndex,
                        episodes = episodes,
                        currentEpisodeId = displayEpisodeId,
                        isLoading = isLoading || isLoadingNextEpisode,
                        isProbing = !isExiting && isProbingOverlay,
                        failedLinks = uiFailedLinks,
                        backdropUrl = backdropUrl,
                        logoUrl = logoUrl,
                        onLinkChange = { targetUrl ->
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onLinkChange triggered -> new url: $targetUrl")
                            playerState.pause()
                            
                            currentUrl = targetUrl
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
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onPlaybackReady received for link index $displayLinkIndex! Video is starting.")
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
                        onPlaybackError = handlePlaybackError,
                        onFullscreenToggle = {
                            fullscreenController?.toggle?.invoke()
                        },
                        playerState = playerState,
                    )
                } // end outer Box
            } // end if (!error && !finished)

            if (isFinished) {
                com.lagradost.cloudstream3.desktop.ui.screens.player.components.VideoEndedOverlay(onClose = onClose)
            }
        } // end BoxWithConstraints
    } // end Column
} // end EmbeddedVideoPlayer
