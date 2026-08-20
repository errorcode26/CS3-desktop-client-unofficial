package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

enum class ContextMenuType {
    POSTER,
    WATCH_HISTORY,
    EPISODE,
}

object GlobalContextMenuState {
    var isActive by mutableStateOf(false)
    var bounds by mutableStateOf(Rect.Zero)
    var menuType by mutableStateOf(ContextMenuType.POSTER)

    var searchResponse: SearchResponse? by mutableStateOf(null)
    var watchHistory: WatchHistory? by mutableStateOf(null)
    var provider: MainAPI? by mutableStateOf(null)

    var episode: Episode? by mutableStateOf(null)
    var loadResponse: LoadResponse? by mutableStateOf(null)
    var isAntiSpoiler: Boolean by mutableStateOf(false)
    var enableDownloadButtons: Boolean by mutableStateOf(false)

    var onRemove: (() -> Unit)? by mutableStateOf(null)
    var onDetailsClick: (() -> Unit)? by mutableStateOf(null)
    var onPlayClick: (() -> Unit)? by mutableStateOf(null)

    var onPlayEpisode: ((Episode) -> Unit)? by mutableStateOf(null)
    var onDownloadEpisode: ((Episode) -> Unit)? by mutableStateOf(null)
    var onToggleWatched: ((Episode, Boolean) -> Unit)? by mutableStateOf(null)
    var onRemoveEpisodeWatched: ((Episode) -> Unit)? by mutableStateOf(null)
    var onMarkPreviousWatched: ((Episode) -> Unit)? by mutableStateOf(null)

    fun dismiss() {
        isActive = false
    }

    fun clear() {
        searchResponse = null
        watchHistory = null
        provider = null
        episode = null
        loadResponse = null
        isAntiSpoiler = false
        enableDownloadButtons = false
        onRemove = null
        onDetailsClick = null
        onPlayClick = null
        onPlayEpisode = null
        onDownloadEpisode = null
        onToggleWatched = null
        onRemoveEpisodeWatched = null
        onMarkPreviousWatched = null
    }

    fun showForPoster(
        bounds: Rect,
        item: SearchResponse,
        provider: MainAPI?,
        onClick: (() -> Unit)?,
        onPlayClick: (() -> Unit)? = null,
    ) {
        this.bounds = bounds
        this.searchResponse = item
        this.provider = provider
        this.onDetailsClick = onClick
        this.onPlayClick = onPlayClick
        this.menuType = ContextMenuType.POSTER
        this.isActive = true
    }

    fun showForWatchHistory(
        bounds: Rect,
        history: WatchHistory,
        provider: MainAPI?,
        onRemove: () -> Unit,
        onClick: (() -> Unit)?,
        onPlayClick: (() -> Unit)? = null,
    ) {
        this.bounds = bounds
        this.watchHistory = history
        this.provider = provider
        this.onRemove = onRemove
        this.onDetailsClick = onClick
        this.onPlayClick = onPlayClick
        this.menuType = ContextMenuType.WATCH_HISTORY
        this.isActive = true
    }

    fun showForEpisode(
        episode: Episode,
        loadResponse: LoadResponse,
        history: WatchHistory?,
        provider: MainAPI?,
        isAntiSpoiler: Boolean = false,
        enableDownloadButtons: Boolean = false,
        onPlay: (Episode) -> Unit,
        onDownload: ((Episode) -> Unit)? = null,
        onToggleWatched: (Episode, Boolean) -> Unit,
        onRemoveEpisodeWatched: (Episode) -> Unit,
        onMarkPreviousWatched: ((Episode) -> Unit)? = null,
    ) {
        this.episode = episode
        this.loadResponse = loadResponse
        this.watchHistory = history
        this.provider = provider
        this.isAntiSpoiler = isAntiSpoiler
        this.enableDownloadButtons = enableDownloadButtons
        this.onPlayEpisode = onPlay
        this.onDownloadEpisode = onDownload
        this.onToggleWatched = onToggleWatched
        this.onRemoveEpisodeWatched = onRemoveEpisodeWatched
        this.onMarkPreviousWatched = onMarkPreviousWatched
        this.menuType = ContextMenuType.EPISODE
        this.isActive = true
    }
}

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
        val isEpisode = state.menuType == ContextMenuType.EPISODE

        val posterUrl = if (state.menuType == ContextMenuType.POSTER) {
            state.searchResponse?.posterUrl
        } else if (state.menuType == ContextMenuType.WATCH_HISTORY) {
            state.watchHistory?.posterUrl
        } else {
            state.episode?.posterUrl ?: state.loadResponse?.posterUrl
        }

        val titleText = if (state.menuType == ContextMenuType.POSTER) {
            state.searchResponse?.name
        } else if (state.menuType == ContextMenuType.WATCH_HISTORY) {
            state.watchHistory?.showName
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

        val subtitleText = if (state.menuType == ContextMenuType.EPISODE && state.episode != null) {
            val ep = state.episode!!
            if (ep.season != null && ep.episode != null) "S${ep.season} E${ep.episode}" else ep.episode?.let { "Episode $it" } ?: ""
        } else if (state.menuType == ContextMenuType.WATCH_HISTORY && state.watchHistory != null) {
            val ep = state.watchHistory!!.episode
            val s = state.watchHistory!!.season
            if (s != null && ep != null) "S${s} E${ep}" else ep?.let { "Episode $it" } ?: state.watchHistory!!.apiName
        } else if (state.menuType == ContextMenuType.POSTER && state.searchResponse != null) {
            val item = state.searchResponse!!
            val year = (item as? com.lagradost.cloudstream3.MovieSearchResponse)?.year
                ?: (item as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year
                ?: (item as? com.lagradost.cloudstream3.AnimeSearchResponse)?.year
            year?.toString() ?: state.provider?.name ?: ""
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            val screenMaxHeight = maxHeight

            // Sizable dimensions adapt dynamically to available window height
            val posterWidth = when {
                screenMaxHeight < 720.dp -> if (isEpisode) 280.dp else 130.dp
                screenMaxHeight < 860.dp -> if (isEpisode) 360.dp else 170.dp
                else -> if (isEpisode) 440.dp else 210.dp
            }
            val posterHeight = if (isEpisode) (posterWidth * 9f / 16f) else (posterWidth * 3f / 2f)
            val actionCardWidth = when {
                screenMaxHeight < 720.dp -> if (isEpisode) 300.dp else 260.dp
                else -> if (isEpisode) 340.dp else 280.dp
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
                    enter = fadeIn(tween(260)) + scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        initialScale = 0.82f,
                    ),
                    exit = fadeOut(tween(180)) + scaleOut(
                        animationSpec = tween(180, easing = EaseInCubic),
                        targetScale = 0.82f,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(vertical = 24.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    // 1. Poster / Thumbnail Surface
                    Surface(
                        modifier = Modifier
                            .width(posterWidth)
                            .height(posterHeight),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF18181A),
                        tonalElevation = 24.dp,
                        shadowElevation = 32.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (!posterUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = posterUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // Watched Check Badge (Top-Right)
                            if (isWatched) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE24A4A)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Watched",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }

                            // Progress bar at bottom
                            if (progress > 0f && !isWatched) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Standalone Centered Title & Subtitle (outside action card)
                    if (!titleText.isNullOrBlank()) {
                        Text(
                            text = titleText,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 20.sp,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = posterWidth.coerceAtLeast(actionCardWidth)).padding(horizontal = 8.dp),
                        )
                        if (subtitleText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = subtitleText,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Floating Rounded Action List Pill
                    Surface(
                        modifier = Modifier
                            .width(actionCardWidth)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1C1C1E).copy(alpha = 0.96f),
                        tonalElevation = 16.dp,
                        shadowElevation = 20.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            if (state.menuType == ContextMenuType.POSTER && state.searchResponse != null) {
                                val item = state.searchResponse!!
                                val bookmarkId = if (state.provider != null) "${state.provider!!.name}_${item.url.hashCode()}" else ""
                                val allBookmarks by BookmarksRepository.bookmarksFlow.collectAsState()
                                val currentBookmark = if (bookmarkId.isNotEmpty()) allBookmarks[bookmarkId] else null
                                var isLibraryExpanded by remember { mutableStateOf(false) }

                                if (currentBookmark != null) {
                                    ActionMenuItem(
                                        text = "Remove from library",
                                        icon = Icons.Default.Delete,
                                        color = MaterialTheme.colorScheme.error,
                                        onClick = {
                                            state.dismiss()
                                            BookmarksRepository.removeBookmark(bookmarkId)
                                        },
                                    )
                                } else {
                                    ActionMenuItem(
                                        text = "Add to library",
                                        icon = Icons.Default.Add,
                                        onClick = {
                                            isLibraryExpanded = !isLibraryExpanded
                                        },
                                    )

                                    AnimatedVisibility(visible = isLibraryExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            val chunks = DesktopWatchType.entries.chunked(2)
                                            chunks.forEach { rowTypes ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    rowTypes.forEach { watchType ->
                                                        val icon = when (watchType) {
                                                            DesktopWatchType.WATCHING -> Icons.Default.PlayArrow
                                                            DesktopWatchType.COMPLETED -> Icons.Default.Check
                                                            DesktopWatchType.ONHOLD -> Icons.Default.Pause
                                                            DesktopWatchType.DROPPED -> Icons.Default.Close
                                                            DesktopWatchType.PLANTOWATCH -> Icons.Default.Bookmark
                                                            DesktopWatchType.REWATCHING -> Icons.Default.Refresh
                                                        }
                                                        LibraryStatusChip(
                                                            text = watchType.stringRes,
                                                            icon = icon,
                                                            modifier = Modifier.weight(1f),
                                                            onClick = {
                                                                state.dismiss()
                                                                if (state.provider != null) {
                                                                    val newBookmark = DesktopBookmark(
                                                                        id = bookmarkId,
                                                                        name = item.name,
                                                                        url = item.url,
                                                                        apiName = state.provider!!.name,
                                                                        posterUrl = item.posterUrl,
                                                                        watchType = watchType.id,
                                                                    )
                                                                    BookmarksRepository.addBookmark(newBookmark)
                                                                }
                                                            },
                                                        )
                                                    }
                                                    if (rowTypes.size == 1) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                ActionMenuItem(
                                    text = "Play",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = {
                                        state.dismiss()
                                        if (state.onPlayClick != null) state.onPlayClick?.invoke() else state.onDetailsClick?.invoke()
                                    },
                                )

                                ActionMenuItem(
                                    text = "Details",
                                    icon = Icons.Default.Info,
                                    onClick = {
                                        state.dismiss()
                                        state.onDetailsClick?.invoke()
                                    },
                                )
                            } else if (state.menuType == ContextMenuType.WATCH_HISTORY && state.watchHistory != null) {
                                val isUpNext = state.watchHistory?.duration == 0L && state.watchHistory?.position == 0L

                                ActionMenuItem(
                                    text = if (isUpNext) "Play next episode" else if (progress > 0f && progress < 0.9f) "Resume playing" else "Play",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = {
                                        state.dismiss()
                                        if (state.onPlayClick != null) state.onPlayClick?.invoke() else state.onDetailsClick?.invoke()
                                    },
                                )

                                ActionMenuItem(
                                    text = "Details",
                                    icon = Icons.Default.Info,
                                    onClick = {
                                        state.dismiss()
                                        state.onDetailsClick?.invoke()
                                    },
                                )

                                ActionMenuItem(
                                    text = "Remove from Continue Watching",
                                    icon = Icons.Default.Delete,
                                    color = MaterialTheme.colorScheme.error,
                                    onClick = {
                                        state.dismiss()
                                        state.onRemove?.invoke()
                                    },
                                )
                            } else if (state.menuType == ContextMenuType.EPISODE && state.episode != null) {
                                val ep = state.episode!!
                                val epReleaseStatus = remember(ep.description) {
                                    com.lagradost.cloudstream3.desktop.ui.screens.details.parseEpisodeReleaseStatus(ep)
                                }
                                val lockUnreleasedEpisodes by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
                                val isEpisodeLocked = epReleaseStatus.isUnreleased && lockUnreleasedEpisodes

                                ActionMenuItem(
                                    text = if (isWatched) "Mark as unwatched" else "Mark as watched",
                                    icon = if (isWatched) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                    onClick = {
                                        state.dismiss()
                                        if (isWatched) {
                                            state.onRemoveEpisodeWatched?.invoke(ep)
                                        } else {
                                            state.onToggleWatched?.invoke(ep, true)
                                        }
                                    },
                                )

                                val season = ep.season
                                if (season != null) {
                                    ActionMenuItem(
                                        text = "Mark Season $season as watched",
                                        icon = Icons.Default.DoneAll,
                                        onClick = {
                                            state.dismiss()
                                            state.onMarkPreviousWatched?.invoke(ep)
                                        },
                                    )
                                } else if (state.onMarkPreviousWatched != null) {
                                    ActionMenuItem(
                                        text = "Mark previous as watched",
                                        icon = Icons.Default.DoneAll,
                                        onClick = {
                                            state.dismiss()
                                            state.onMarkPreviousWatched?.invoke(ep)
                                        },
                                    )
                                }

                                if (isEpisodeLocked) {
                                    ActionMenuItem(
                                        text = "Locked (${epReleaseStatus.statusBadgeText ?: "Unreleased"})",
                                        icon = Icons.Default.Lock,
                                        color = Color(0xFFFFB74D),
                                        onClick = {
                                            // Locked — cannot play
                                        },
                                    )
                                } else {
                                    ActionMenuItem(
                                        text = if (progress > 0f && progress < 0.9f) "Resume episode" else "Play episode",
                                        icon = Icons.Default.PlayArrow,
                                        onClick = {
                                            state.dismiss()
                                            state.onPlayEpisode?.invoke(ep)
                                        },
                                    )
                                }

                                if (!isEpisodeLocked && progress > 0f) {
                                    ActionMenuItem(
                                        text = "Clear watch progress",
                                        icon = Icons.Default.Refresh,
                                        color = Color.White.copy(alpha = 0.7f),
                                        onClick = {
                                            state.dismiss()
                                            state.onRemoveEpisodeWatched?.invoke(ep)
                                        },
                                    )
                                }

                                if (!isEpisodeLocked && state.enableDownloadButtons && state.onDownloadEpisode != null) {
                                    ActionMenuItem(
                                        text = "Download episode",
                                        icon = Icons.Default.Download,
                                        onClick = {
                                            state.dismiss()
                                            state.onDownloadEpisode?.invoke(ep)
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
}
}

@Composable
private fun ActionMenuItem(
    text: String,
    icon: ImageVector,
    color: Color = Color.White.copy(alpha = 0.9f),
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isHovered) Color.White.copy(alpha = 0.09f) else Color.Transparent,
        animationSpec = tween(120),
        label = "actionItemBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = ripple()) { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(17.dp),
            tint = color,
        )
    }
}

@Composable
private fun LibraryStatusChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isHovered) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        animationSpec = tween(120),
        label = "chipBg",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, if (isHovered) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)),
        modifier = modifier.height(34.dp),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
