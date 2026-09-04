package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.formatBytes
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.utils.ImageUtils
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun ContextMenuOverlay() {
    val state = GlobalContextMenuState
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = state.isActive

    val isVisible = transitionState.currentState || transitionState.targetState

    LaunchedEffect(transitionState.isIdle, transitionState.currentState) {
        if (transitionState.isIdle && !transitionState.currentState) {
            state.clear()
        }
    }

    if (isVisible) {
        val isEpisode = state.menuType == ContextMenuType.EPISODE ||
            state.menuType == ContextMenuType.WATCH_HISTORY ||
            (state.menuType == ContextMenuType.DOWNLOAD && state.downloadTask?.isMovie == false)

        val posterUrl = if (state.menuType == ContextMenuType.POSTER) {
            state.searchResponse?.posterUrl
        } else if (state.menuType == ContextMenuType.WATCH_HISTORY) {
            val wh = state.watchHistory
            val provider = state.provider
            val raw = provider?.fixUrlNull(wh?.episodeThumbnailUrl) ?: wh?.episodeThumbnailUrl
                ?: wh?.screenshotUrl
                ?: provider?.fixUrlNull(wh?.posterUrl) ?: wh?.posterUrl
            ImageUtils.enhancePosterUrl(raw) ?: raw
        } else if (state.menuType == ContextMenuType.BOOKMARK) {
            state.bookmark?.posterUrl
        } else if (state.menuType == ContextMenuType.DOWNLOAD) {
            state.downloadTask?.posterUrl ?: state.downloadTask?.backdropUrl
        } else {
            state.episode?.posterUrl ?: state.loadResponse?.posterUrl
        }

        val isCleanMode by AppearanceConfig.cleanModeEnabled.collectAsState()
        val hideProviderNames by AppearanceConfig.hideProviderNames.collectAsState()
        val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()

        val rawTitleText = if (state.menuType == ContextMenuType.POSTER) {
            state.searchResponse?.name
        } else if (state.menuType == ContextMenuType.WATCH_HISTORY) {
            state.watchHistory?.showName
        } else if (state.menuType == ContextMenuType.BOOKMARK) {
            state.bookmark?.name
        } else if (state.menuType == ContextMenuType.DOWNLOAD) {
            state.downloadTask?.showName
        } else {
            state.episode?.let { ep ->
                val rawTitle = ep.name ?: "Episode ${ep.episode ?: "?"}"
                val titleCleaned = rawTitle
                    .replace(Regex("^(?i)(E[0-9]+[\\s\\-:]*)+"), "")
                    .replace(Regex("^(?i)(Episode[\\s]*[0-9]+[\\s\\-:]*)+"), "")
                    .trim()
                if (titleCleaned.isBlank()) "Episode ${ep.episode ?: "?"}" else titleCleaned
            }
        }

        val titleText = remember(rawTitleText, autoCleanTitles, isCleanMode) {
            if (rawTitleText == null) null
            else if (autoCleanTitles || isCleanMode) {
                CardTitleSanitizer.sanitize(rawTitleText, autoClean = true).displayTitle
            } else {
                rawTitleText
            }
        }

        val subtitleText = if (state.menuType == ContextMenuType.DOWNLOAD && state.downloadTask != null) {
            val task = state.downloadTask!!
            if (!task.isMovie) {
                val epClean = task.cleanEpisodeTitle
                "S${task.season ?: 1} E${task.episode ?: 1}${if (!epClean.isNullOrBlank()) " • $epClean" else ""}"
            } else {
                "${task.quality}p • ${formatBytes(task.downloadedBytes)}"
            }
        } else if (state.menuType == ContextMenuType.EPISODE && state.episode != null) {
            val ep = state.episode!!
            if (ep.season != null && ep.episode != null) "S${ep.season} E${ep.episode}" else ep.episode?.let { "Episode $it" } ?: ""
        } else if (state.menuType == ContextMenuType.WATCH_HISTORY && state.watchHistory != null) {
            val wh = state.watchHistory!!
            val ep = wh.episode
            val s = wh.season
            val epName = wh.episodeName
            val epPrefix = if (s != null && ep != null) "S${s} E${ep}" else ep?.let { "Episode $it" }
            if (epPrefix != null && !epName.isNullOrBlank()) {
                "$epPrefix • $epName"
            } else if (epPrefix != null) {
                epPrefix
            } else if (!epName.isNullOrBlank()) {
                epName
            } else if (hideProviderNames || isCleanMode) {
                ""
            } else {
                wh.apiName
            }
        } else if (state.menuType == ContextMenuType.BOOKMARK && state.bookmark != null) {
            if (hideProviderNames || isCleanMode) "" else if (state.provider != null) state.provider!!.name else "${state.bookmark!!.apiName} (Missing Provider)"
        } else if (state.menuType == ContextMenuType.POSTER && state.searchResponse != null) {
            val item = state.searchResponse!!
            val year = (item as? com.lagradost.cloudstream3.MovieSearchResponse)?.year
                ?: (item as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year
                ?: (item as? com.lagradost.cloudstream3.AnimeSearchResponse)?.year
                ?: (rawTitleText?.let { CardTitleSanitizer.sanitize(it).year })
            year?.toString() ?: if (hideProviderNames || isCleanMode) "" else state.provider?.name ?: ""
        } else {
            ""
        }

        val progress = if (state.menuType == ContextMenuType.EPISODE && state.episode != null) {
            val history = state.watchHistory
            if (history != null && history.duration > 0) {
                if (PlayerLinkHandler.isCompleted(history.position, history.duration)) {
                    1f
                } else {
                    (history.position.toFloat() / history.duration.toFloat()).coerceIn(0f, 1f)
                }
            } else {
                0f
            }
        } else if (state.menuType == ContextMenuType.WATCH_HISTORY && state.watchHistory != null) {
            val history = state.watchHistory!!
            if (history.duration > 0) {
                if (PlayerLinkHandler.isCompleted(history.position, history.duration)) 1f
                else (history.position.toFloat() / history.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
        } else {
            0f
        }
        val isWatched = progress > 0.9f
        val ep = state.episode

        val epDate = remember(ep?.description) {
            ep?.description?.let { desc ->
                Regex("\\|\\|DATE:(.*?)\\|\\|").find(desc)?.groupValues?.getOrNull(1)
            }
        }
        val epCleanPlot = remember(ep?.description, state.watchHistory?.episodeDescription) {
            val desc = ep?.description ?: state.watchHistory?.episodeDescription
            desc?.replace(Regex("\\|\\|DATE:.*?\\|\\|"), "")?.trim()?.takeIf { it.isNotBlank() }
        }
        val epRuntimeText = remember(ep?.runTime) {
            ep?.runTime?.let { "${it}m" }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            val screenMaxHeight = maxHeight

            // Sizable dimensions adapt dynamically to available window height
            val posterWidth = when {
                screenMaxHeight < 720.dp -> if (isEpisode) 380.dp else 130.dp
                screenMaxHeight < 860.dp -> if (isEpisode) 440.dp else 170.dp
                else -> if (isEpisode) 480.dp else 210.dp
            }
            val posterHeight = if (isEpisode) (posterWidth * 9f / 16f) else (posterWidth * 3f / 2f)
            val actionCardWidth = if (isEpisode) posterWidth else when {
                screenMaxHeight < 720.dp -> 260.dp
                screenMaxHeight < 860.dp -> 280.dp
                else -> 300.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { state.dismiss() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) + scaleIn(
                        animationSpec = tween(160, easing = FastOutSlowInEasing),
                        initialScale = 0.92f,
                    ),
                    exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) + scaleOut(
                        animationSpec = tween(140, easing = FastOutSlowInEasing),
                        targetScale = 0.92f,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(vertical = 24.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 1 & 2. Poster / Thumbnail Surface & Header Presentation
                        ContextMenuCardPreview(
                            isEpisode = isEpisode,
                            posterUrl = posterUrl,
                            epDate = epDate,
                            epRuntimeText = epRuntimeText,
                            isWatched = isWatched,
                            progress = progress,
                            posterWidth = posterWidth,
                            posterHeight = posterHeight,
                            actionCardWidth = actionCardWidth,
                            titleText = titleText,
                            subtitleText = subtitleText,
                            epCleanPlot = epCleanPlot,
                            isAntiSpoiler = state.isAntiSpoiler,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3. Floating Rounded Action List Pill
                        Surface(
                            modifier = Modifier
                                .width(actionCardWidth)
                                .wrapContentHeight(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1C1C1E).copy(alpha = 0.96f),
                            tonalElevation = 6.dp,
                            shadowElevation = 12.dp,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                when (state.menuType) {
                                    ContextMenuType.POSTER -> PosterContextMenuActions(state)
                                    ContextMenuType.WATCH_HISTORY -> WatchHistoryContextMenuActions(state, progress)
                                    ContextMenuType.BOOKMARK -> BookmarkContextMenuActions(state)
                                    ContextMenuType.DOWNLOAD -> DownloadContextMenuActions(state)
                                    ContextMenuType.EPISODE -> EpisodeContextMenuActions(state, isWatched, progress)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
