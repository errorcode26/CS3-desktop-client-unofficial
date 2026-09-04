package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.formatBytes
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType

@Composable
internal fun PosterContextMenuActions(state: GlobalContextMenuState) {
    val item = state.searchResponse ?: return
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
}

@Composable
internal fun WatchHistoryContextMenuActions(
    state: GlobalContextMenuState,
    progress: Float,
) {
    if (state.watchHistory == null) return
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
}

@Composable
internal fun BookmarkContextMenuActions(state: GlobalContextMenuState) {
    val bm = state.bookmark ?: return
    var isCategoryExpanded by remember { mutableStateOf(false) }

    if (state.provider != null) {
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
    }

    ActionMenuItem(
        text = "Move Category",
        icon = Icons.Default.Bookmark,
        onClick = {
            isCategoryExpanded = !isCategoryExpanded
        },
    )

    AnimatedVisibility(visible = isCategoryExpanded) {
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
                            isSelected = bm.watchType == watchType.id,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                state.dismiss()
                                state.onChangeCategory?.invoke(watchType)
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

    ActionMenuItem(
        text = "Re-link to Provider...",
        icon = Icons.Default.Sync,
        onClick = {
            state.dismiss()
            state.onReLink?.invoke()
        },
    )

    ActionMenuItem(
        text = "Search on Other Providers...",
        icon = Icons.Default.Search,
        onClick = {
            state.dismiss()
            state.onSearchOtherProviders?.invoke()
        },
    )

    ActionMenuItem(
        text = "Remove from Library",
        icon = Icons.Default.Delete,
        color = MaterialTheme.colorScheme.error,
        onClick = {
            state.dismiss()
            state.onRemove?.invoke()
        },
    )
}

@Composable
internal fun DownloadContextMenuActions(state: GlobalContextMenuState) {
    val task = state.downloadTask ?: return
    ActionMenuItem(
        text = "Play",
        icon = Icons.Default.PlayArrow,
        onClick = {
            state.dismiss()
            state.onPlayClick?.invoke()
        },
    )

    if (state.onOpenInExplorer != null) {
        ActionMenuItem(
            text = "Show in File Explorer",
            icon = Icons.Default.FolderOpen,
            onClick = {
                state.dismiss()
                state.onOpenInExplorer?.invoke()
            },
        )
    }

    ActionMenuItem(
        text = "Delete This File (${formatBytes(task.downloadedBytes)})",
        icon = Icons.Default.Delete,
        color = MaterialTheme.colorScheme.error,
        onClick = {
            state.dismiss()
            state.onRemove?.invoke()
        },
    )

    if (state.showTaskCount > 1 && state.onDeleteShow != null) {
        ActionMenuItem(
            text = "Delete Entire Show (${state.showTaskCount} episodes • ${formatBytes(state.showTotalBytes)})",
            icon = Icons.Default.DeleteSweep,
            color = MaterialTheme.colorScheme.error,
            onClick = {
                state.dismiss()
                state.onDeleteShow?.invoke()
            },
        )
    }
}

@Composable
internal fun EpisodeContextMenuActions(
    state: GlobalContextMenuState,
    isWatched: Boolean,
    progress: Float,
) {
    val ep = state.episode ?: return
    val epReleaseStatus = remember(ep.description) {
        com.lagradost.cloudstream3.desktop.ui.screens.details.parseEpisodeReleaseStatus(ep)
    }
    val lockUnreleasedEpisodes by AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
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
    } else if (epReleaseStatus.isMissingFromProvider) {
        ActionMenuItem(
            text = "Unavailable on ${state.provider?.name ?: "this provider"}",
            icon = Icons.Default.CloudOff,
            color = Color(0xFFFFB74D),
            onClick = {
                state.dismiss()
                AppToastManager.showWarning("Episode is not available on ${state.provider?.name ?: "this provider"}.")
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

@Composable
internal fun ActionMenuItem(
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
internal fun LibraryStatusChip(
    text: String,
    icon: ImageVector,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val primary = MaterialTheme.colorScheme.primary

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) primary.copy(alpha = 0.85f) else if (isHovered) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        animationSpec = tween(120),
        label = "chipBg",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, if (isSelected) primary else if (isHovered) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)),
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
                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
