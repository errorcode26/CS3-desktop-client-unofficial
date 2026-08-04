package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType
import com.lagradost.common.storage.WatchHistory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

enum class ContextMenuType {
    POSTER, WATCH_HISTORY
}

object GlobalContextMenuState {
    var isActive by mutableStateOf(false)
    var bounds by mutableStateOf(Rect.Zero)
    var menuType by mutableStateOf(ContextMenuType.POSTER)

    var searchResponse: SearchResponse? by mutableStateOf(null)
    var watchHistory: WatchHistory? by mutableStateOf(null)
    var provider: MainAPI? by mutableStateOf(null)

    var onRemove: (() -> Unit)? by mutableStateOf(null)
    var onDetailsClick: (() -> Unit)? by mutableStateOf(null)
    var onPlayClick: (() -> Unit)? by mutableStateOf(null)

    fun dismiss() {
        isActive = false
    }

    fun showForPoster(
        bounds: Rect,
        item: SearchResponse,
        provider: MainAPI?,
        onClick: (() -> Unit)?,
        onPlayClick: (() -> Unit)? = null
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
        onPlayClick: (() -> Unit)? = null
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
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ContextMenuOverlay() {
    val state = GlobalContextMenuState
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = state.isActive

    val isVisible = transitionState.currentState || transitionState.targetState

    if (isVisible) {
        val appThemeBackground by AppearanceConfig.appThemeBackground.collectAsState()
        val isAmoled = appThemeBackground == "Pure Black"

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { state.dismiss() }
                ),
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.95f),
                exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.95f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val cardWidth = if (state.menuType == ContextMenuType.WATCH_HISTORY) 480.dp else 240.dp
                    val cardHeight = if (state.menuType == ContextMenuType.WATCH_HISTORY) (480.dp * 9f / 16f) else (240.dp * 3f / 2f)

                    // Render the scaled-up card dead center
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(cardWidth)
                            .height(cardHeight)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    ) {
                            if (state.menuType == ContextMenuType.POSTER && state.searchResponse != null) {
                                PosterCard(
                                    item = state.searchResponse!!,
                                    provider = state.provider,
                                    itemWidth = 240.dp,
                                    onClick = {
                                        state.dismiss()
                                        state.onDetailsClick?.invoke()
                                    }
                                )
                            } else if (state.menuType == ContextMenuType.WATCH_HISTORY && state.watchHistory != null) {
                                WatchHistoryCard(
                                    history = state.watchHistory!!,
                                    provider = state.provider,
                                    modifier = Modifier.fillMaxSize(),
                                    onRemove = {
                                        state.dismiss()
                                        state.onRemove?.invoke()
                                    },
                                    onClick = {
                                        state.dismiss()
                                        state.onDetailsClick?.invoke()
                                    }
                                )
                            }
                    }

                    // Render the floating menu next to it
                    val menuWidth = 260.dp

                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = (cardWidth / 2) + 32.dp + (menuWidth / 2))
                            .width(menuWidth)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isAmoled) Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)) else Modifier)
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                if (state.menuType == ContextMenuType.POSTER && state.searchResponse != null) {
                                    ContextMenuItem(
                                        text = "Play",
                                        icon = Icons.Default.PlayArrow,
                                        onClick = {
                                            state.dismiss()
                                            if (state.onPlayClick != null) state.onPlayClick?.invoke() else state.onDetailsClick?.invoke()
                                        }
                                    )
                                    ContextMenuItem(
                                        text = "Details",
                                        icon = Icons.Default.Info,
                                        onClick = {
                                            state.dismiss()
                                            state.onDetailsClick?.invoke()
                                        }
                                    )
                                    
                                    val item = state.searchResponse!!
                                    val bookmarkId = if (state.provider != null) "${state.provider!!.name}_${item.url.hashCode()}" else ""
                                    val allBookmarks by BookmarksRepository.bookmarksFlow.collectAsState()
                                    val currentBookmark = if (bookmarkId.isNotEmpty()) allBookmarks[bookmarkId] else null

                                    var isLibraryExpanded by remember { mutableStateOf(false) }

                                    if (currentBookmark != null) {
                                        ContextMenuItem(
                                            text = "Remove from Library",
                                            icon = Icons.Default.Delete,
                                            color = MaterialTheme.colorScheme.error,
                                            onClick = {
                                                state.dismiss()
                                                BookmarksRepository.removeBookmark(bookmarkId)
                                            }
                                        )
                                    } else {
                                        ContextMenuItem(
                                            text = "Add to Library",
                                            icon = Icons.Default.Add,
                                            onClick = {
                                                isLibraryExpanded = !isLibraryExpanded
                                            }
                                        )
                                        
                                        AnimatedVisibility(visible = isLibraryExpanded) {
                                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                                DesktopWatchType.entries.forEach { watchType ->
                                                    ContextMenuItem(
                                                        text = watchType.stringRes,
                                                        icon = if (watchType == DesktopWatchType.WATCHING) Icons.Default.PlayArrow else Icons.Default.Add,
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
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    ContextMenuItem(
                                        text = "Copy Title",
                                        icon = Icons.Default.ContentCopy,
                                        onClick = {
                                            state.dismiss()
                                            val selection = StringSelection(item.name)
                                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                        }
                                    )
                                    
                                } else if (state.menuType == ContextMenuType.WATCH_HISTORY && state.watchHistory != null) {
                                    ContextMenuItem(
                                        text = "Resume Playing",
                                        icon = Icons.Default.PlayArrow,
                                        onClick = {
                                            state.dismiss()
                                            if (state.onPlayClick != null) state.onPlayClick?.invoke() else state.onDetailsClick?.invoke()
                                        }
                                    )
                                    ContextMenuItem(
                                        text = "Details",
                                        icon = Icons.Default.Info,
                                        onClick = {
                                            state.dismiss()
                                            state.onDetailsClick?.invoke()
                                        }
                                    )
                                    ContextMenuItem(
                                        text = "Remove from Continue Watching",
                                        icon = Icons.Default.Delete,
                                        color = MaterialTheme.colorScheme.error,
                                        onClick = {
                                            state.dismiss()
                                            state.onRemove?.invoke()
                                        }
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

@Composable
private fun ContextMenuItem(
    text: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple()) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(18.dp),
            tint = color
        )
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
