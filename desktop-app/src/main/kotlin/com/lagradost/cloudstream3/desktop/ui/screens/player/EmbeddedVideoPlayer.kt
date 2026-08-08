package com.lagradost.cloudstream3.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    var isLoading by remember(actualLaunchData.history.episodeId) { mutableStateOf(true) }
    var showSources by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }

    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen ?: false
    val initialPlacement = remember { windowState?.placement ?: WindowPlacement.Floating }

    val playerState = viewModel.playerState

    LaunchedEffect(actualLaunchData.history.episodeId) {
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
                    var isProbingOverlay by remember(actualLaunchData.history.episodeId) { mutableStateOf(true) }
                    // Once onPlaybackReady fires, we never show the probing overlay again for this episode.
                    var hasPlaybackStarted by remember(actualLaunchData.history.episodeId) { mutableStateOf(false) }

                    // activeLink is owned entirely by the ViewModel — no local picking logic here.
                    val activeLink = uiState.activeLink
                    val safeLink = if (isExiting || isLoadingNextEpisode) null else activeLink

                    val displayLinkIndex = actualLaunchData.links.indexOfFirst { it.url == activeLink?.url }.coerceAtLeast(0)
                    val uiFailedLinks = actualLaunchData.links
                        .mapIndexedNotNull { index, link -> if (link.url in uiState.failedLinks) index to "Failed" else null }
                        .toMap()

                    // Safety-net timeout: MPV fires its own error after 45s on a hung stream.
                    // This 48s fallback catches any edge case where MPV silently hangs without
                    // surfacing an event (e.g. proxy stall, JNA freeze during init).
                    // Keyed on activeLink ONLY — isProbingOverlay must NOT be a key or every
                    // overlay-dismiss from onPlaybackReady would restart the 48s timer.
                    LaunchedEffect(activeLink) {
                        if (activeLink != null && isProbingOverlay) {
                            kotlinx.coroutines.delay(48000)
                            if (isProbingOverlay) {
                                com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: Link timed out after 48s. Forcing fallback.")
                                activeLink.url.let { viewModel.onEvent(PlayerUiEvent.OnPlaybackError(it)) }
                                isLoading = true
                                if (!hasPlaybackStarted) isProbingOverlay = true
                            }
                        }
                    }

                    // When ViewModel clears activeLink after all sources are exhausted, close.
                    LaunchedEffect(activeLink, uiState.isScrapingLinks) {
                        if (activeLink == null && !uiState.isScrapingLinks && !isLoadingNextEpisode && uiState.launchData != null) {
                            val hasEverHadLinks = actualLaunchData.links.isNotEmpty()
                            if (hasEverHadLinks && uiState.failedLinks.isNotEmpty()) {
                                com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: All sources exhausted. Closing.")
                                actualLaunchData.history.episodeId?.let { LinkCache.remove(it) }
                                onError("All sources failed. Please try again later.")
                                onClose()
                            }
                        }
                    }

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
                    ComposeNativeWebPlayer(
                        link = safeLink,
                        title = displayTitle,
                        seriesPosterUrl = actualLaunchData.loadResponse?.posterUrl,
                        plot = plot,
                        year = year,
                        tags = tags,
                        subtitles = actualLaunchData.subtitles,
                        isExiting = isExiting,
                        startPositionMs = if (displayLinkIndex != 0 && playerState.positionMs.value > 0) {
                            playerState.positionMs.value
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
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onLinkChange -> $targetUrl")
                            playerState.pause()
                            isLoading = true
                            isProbingOverlay = true
                            viewModel.onEvent(PlayerUiEvent.OnLinkChange(targetUrl))
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
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onPlaybackReady for link index $displayLinkIndex")
                            isLoading = false
                            isProbingOverlay = false
                            hasPlaybackStarted = true
                        },
                        onPositionChange = { posMs, durMs ->
                            playerState.updatePositionFromPlayer(posMs)
                            playerState.updateDurationFromPlayer(durMs)
                        },
                        onCloseRequest = {
                            onClose()
                        },
                        onSkipScraping = {
                            viewModel.onEvent(PlayerUiEvent.OnCancelScraping)
                        },
                        onFinished = {
                            val hasNext = uiState.hasNextEpisode
                            val isAutoPlay = uiState.autoPlayEnabled
                            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("window.showVideoEnded && window.showVideoEnded($hasNext, $isAutoPlay);")
                        },
                        onPlaybackError = { err ->
                            com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: Playback error — $err")
                            val failedUrl = activeLink?.url
                            if (failedUrl != null) {
                                viewModel.onEvent(PlayerUiEvent.OnPlaybackError(failedUrl))
                            }
                            isLoading = true
                            // Only re-show the probing screen if playback never actually started.
                            // If the video was already playing, silently attempt the next link
                            // without flashing the overlay at the user.
                            if (!hasPlaybackStarted) isProbingOverlay = true
                        },
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
