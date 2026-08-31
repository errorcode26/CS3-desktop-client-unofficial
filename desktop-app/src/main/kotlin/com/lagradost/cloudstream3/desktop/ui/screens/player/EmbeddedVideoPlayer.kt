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
import com.lagradost.cloudstream3.fixUrlNull

@Composable
fun EmbeddedVideoPlayer(
    launchData: VideoLaunchData,
    viewModel: EmbeddedPlayerViewModel,
    isExiting: Boolean = false,
    onClose: () -> Unit,
    onError: (String) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
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
                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.postMessage(
                        "{\"type\":\"show_toast\",\"message\":\"${effect.message.replace("\"", "\\\"")}\"}",
                    )
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
    val phase = uiState.phase
    val isLoadingNextEpisode = phase is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase.Scraping
    val nextEpisodeError = uiState.nextEpisodeError
    val nextEpisodeLinks = uiState.nextEpisodeLinks
    val targetEpisodeData = uiState.targetEpisodeData

    if (currentLaunchData == null) return

    val actualLaunchData = currentLaunchData!!

    var isLoading by remember(actualLaunchData.history.episodeId) { mutableStateOf(true) }
    var showSources by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }

    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen ?: false
    val initialPlacement = remember { windowState?.placement ?: WindowPlacement.Floating }

    val playerState = viewModel.playerState

    LaunchedEffect(actualLaunchData.history.episodeId) {
        // isLoading is already reset to true by remember(episodeId) above
        playerState.reset()
        com.lagradost.player.impl.proxy.LocalStreamProxyState.loadingStatus.value = null

        // Pre-fetch skip intervals in parallel with stream scraping for 0ms startup delay
        val currentEp = uiState.episodes.find { it.data == actualLaunchData.history.episodeId }
        val epNum = actualLaunchData.history.episode ?: currentEp?.episode ?: 1
        val seasonNum = actualLaunchData.history.season ?: currentEp?.season ?: 1
        val showTitle = (actualLaunchData.history.showName ?: "").ifBlank { actualLaunchData.loadResponse?.name ?: actualLaunchData.title.orEmpty() }
        if (showTitle.isNotBlank()) {
            playerState.loadSkipIntervals(
                title = showTitle,
                episode = epNum,
                season = seasonNum,
                durationSeconds = 0.0
            )
        }
    }

    LaunchedEffect(nextEpisodeError) {
        // Surface the error and release local loading lock
        if (nextEpisodeError != null) {
            isLoading = false
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
                Box(modifier = Modifier.fillMaxSize()) {
                    // activeLink is owned entirely by the ViewModel — no local picking logic here.
                    val activeLink = uiState.activeLink
                    val safeLink = if (isExiting || isLoadingNextEpisode) null else activeLink

                    val currentDisplayLinks = uiState.nextEpisodeLinks.ifEmpty { actualLaunchData.links }
                    val displayLinkIndex = currentDisplayLinks.indexOfFirst { it.url == activeLink?.url }.coerceAtLeast(0)
                    val uiFailedLinks = currentDisplayLinks
                        .mapIndexedNotNull { index, link -> uiState.failedLinks[link.url]?.let { index to it } }
                        .toMap()

                    // When ViewModel clears activeLink after all sources are exhausted, close.
                    LaunchedEffect(phase) {
                        if (phase is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase.Idle && uiState.launchData != null) {
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
                    val provider = actualLaunchData.loadResponse?.apiName?.let { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it) }
                        ?: actualLaunchData.history.apiName?.let { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it) }

                    val rawSeriesPoster = actualLaunchData.loadResponse?.posterUrl?.takeIf { it.isNotBlank() }
                        ?: actualLaunchData.history.posterUrl?.takeIf { it.isNotBlank() }
                        ?: actualLaunchData.loadResponse?.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                    val resolvedSeriesPosterUrl = rawSeriesPoster?.let { raw ->
                        (provider?.fixUrlNull(raw) ?: raw).let { if (it.startsWith("//")) "https:$it" else it }
                    }

                    val rawBackdrop = actualLaunchData.enrichedBackdropUrl?.takeIf { it.isNotBlank() }
                        ?: actualLaunchData.loadResponse?.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                        ?: actualLaunchData.loadResponse?.posterUrl?.takeIf { it.isNotBlank() }
                        ?: actualLaunchData.history.posterUrl?.takeIf { it.isNotBlank() }
                    val resolvedBackdropUrl = rawBackdrop?.let { raw ->
                        (provider?.fixUrlNull(raw) ?: raw).let { if (it.startsWith("//")) "https:$it" else it }
                    }

                    val rawLogo = actualLaunchData.enrichedLogoUrl?.takeIf { it.isNotBlank() }
                        ?: actualLaunchData.loadResponse?.logoUrl?.takeIf { it.isNotBlank() }
                    val resolvedLogoUrl = rawLogo?.let { raw ->
                        (provider?.fixUrlNull(raw) ?: raw).let { if (it.startsWith("//")) "https:$it" else it }
                    }

                    val plot = targetEpisodeData?.description ?: actualLaunchData.loadResponse?.plot
                    val year = uiState.launchData?.loadResponse?.year
                    val tags = actualLaunchData.loadResponse?.tags
                    // Always use the start position from launchData — it is the canonical
                    // source of truth set by the ViewModel. Falling back to playerState.positionMs
                    val computedStartPos = actualLaunchData.startPositionMs
                    val isMidStreamSwitch = phase is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase.Probing && !phase.isInitial
                    val displayLoadingStatus = if (isMidStreamSwitch) {
                        if ((phase as com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase.Probing).isRetry) "Reconnecting..." else "Trying next source..."
                    } else {
                        null
                    }

                    ComposeNativeWebPlayer(
                        link = safeLink,
                        title = displayTitle,
                        seriesPosterUrl = resolvedSeriesPosterUrl,
                        plot = plot,
                        year = year,
                        tags = tags,
                        isLive = actualLaunchData.loadResponse?.type == com.lagradost.cloudstream3.TvType.Live || safeLink?.name?.contains("Live", ignoreCase = true) == true || safeLink?.url?.contains("live", ignoreCase = true) == true,
                        subtitles = actualLaunchData.subtitles,
                        isExiting = isExiting,
                        startPositionMs = computedStartPos,
                        shouldPauseForResume = false,
                        links = currentDisplayLinks,
                        currentLinkIndex = displayLinkIndex,
                        episodes = episodes,
                        currentEpisodeId = displayEpisodeId,
                        isLoading = isLoading || isLoadingNextEpisode,
                        loadingStatusText = displayLoadingStatus,
                        isProbing = !isExiting && (phase is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase.Scraping || (phase is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase.Probing && phase.isInitial)),
                        isScraping = !isExiting && (phase is com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase.Scraping),
                        failedLinks = uiFailedLinks,
                        backdropUrl = resolvedBackdropUrl,
                        logoUrl = resolvedLogoUrl,
                        onLinkChange = { targetUrl ->
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onLinkChange -> $targetUrl")
                            playerState.pause()
                            isLoading = true
                            viewModel.onEvent(PlayerUiEvent.OnLinkChange(targetUrl))
                        },
                        onEpisodeChange = { epId ->
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onEpisodeChange triggered -> new episodeId: $epId")
                            playerState.pause()
                            playerState.reset()
                            isLoading = true
                            val targetEp = episodes.find { it.data == epId }
                            if (targetEp != null) {
                                viewModel.onEvent(PlayerUiEvent.OnLoadEpisode(targetEp))
                            }
                        },
                        onNextEpisode = {
                            playerState.pause()
                            playerState.reset()
                            isLoading = true
                            viewModel.onEvent(PlayerUiEvent.OnLoadNextEpisode)
                        },
                        onReplayEpisode = {
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onReplayEpisode triggered")
                            playerState.pause()
                            playerState.reset()
                            isLoading = true
                            val currentEp = episodes.find { it.data == actualLaunchData.history.episodeId }
                            if (currentEp != null) {
                                viewModel.onEvent(PlayerUiEvent.OnLoadEpisode(currentEp))
                            } else {
                                viewModel.onEvent(PlayerUiEvent.OnInit(actualLaunchData.copy(startPositionMs = 0L)))
                            }
                        },
                        onPlaybackReady = {
                            com.lagradost.common.logging.AppLogger.i("EmbeddedVideoPlayer: onPlaybackReady for link index $displayLinkIndex")
                            isLoading = false
                            viewModel.onEvent(PlayerUiEvent.OnPlaybackReady)
                            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.postMessage("{\"type\":\"dismiss_probing\"}")

                            val currentEp = episodes.find { it.data == actualLaunchData.history.episodeId }
                            val epNum = currentEp?.episode ?: 1
                            val seasonNum = currentEp?.season ?: 1
                            val showTitle = (actualLaunchData.history.showName ?: "").ifBlank { actualLaunchData.loadResponse?.name ?: actualLaunchData.title.orEmpty() }
                            if (playerState.skipIntervals.value.isEmpty() && showTitle.isNotBlank()) {
                                playerState.loadSkipIntervals(
                                    title = showTitle,
                                    episode = epNum,
                                    season = seasonNum,
                                    durationSeconds = playerState.durationMs.value / 1000.0
                                )
                            }
                        },
                        onPositionChange = { posMs, durMs ->
                            playerState.updatePositionFromPlayer(posMs)
                            playerState.updateDurationFromPlayer(durMs)
                            val durSec = durMs / 1000L
                            val posSec = posMs / 1000L
                            if (posSec > 0) {
                                val updatedHistory = actualLaunchData.history.copy(
                                    position = posSec,
                                    duration = if (durSec > 0) durSec else actualLaunchData.history.duration,
                                    updateTime = System.currentTimeMillis(),
                                )
                                viewModel.onEvent(PlayerUiEvent.OnSavePosition(updatedHistory))
                            }
                        },
                        onCloseRequest = {
                            onClose()
                        },
                        onSkipScraping = {
                            viewModel.onEvent(PlayerUiEvent.OnCancelScraping)
                        },
                        onFinished = {
                            // Clear loading first so JS isAppLoading=false before showVideoEnded runs.
                            isLoading = false
                            val hasNext = uiState.hasNextEpisode
                            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("window.showVideoEnded && window.showVideoEnded($hasNext, window.autoPlayEnabled);")
                            viewModel.onEvent(PlayerUiEvent.OnPlaybackFinished)
                        },
                        onPlaybackError = { err ->
                            com.lagradost.common.logging.AppLogger.e("EmbeddedVideoPlayer: Playback error — $err")
                            val failedUrl = uiState.activeLink?.url
                            if (failedUrl != null) {
                                viewModel.onEvent(PlayerUiEvent.OnPlaybackError(failedUrl, reason = err))
                            }
                            isLoading = true
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
