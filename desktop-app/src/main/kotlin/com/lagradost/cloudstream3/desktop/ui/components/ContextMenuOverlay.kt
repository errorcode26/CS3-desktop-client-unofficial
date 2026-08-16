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
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType
import com.lagradost.common.storage.WatchHistory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

enum class ContextMenuType {
    POSTER,
    WATCH_HISTORY,
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

    fun clear() {
        searchResponse = null
        watchHistory = null
        provider = null
        onRemove = null
        onDetailsClick = null
        onPlayClick = null
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
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { state.dismiss() },
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
                    contentAlignment = Alignment.Center,
                ) {
                    val basePosterWidth = if (state.menuType == ContextMenuType.WATCH_HISTORY) 480.dp else 280.dp
                    val basePosterHeight = if (state.menuType == ContextMenuType.WATCH_HISTORY) (480.dp * 9f / 16f) else (280.dp * 3f / 2f)
                    val menuWidth = 300.dp
                    val cardPadding = 16.dp

                    Surface(
                        modifier = Modifier
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent, // Transparent so cinematic background shows
                        tonalElevation = 24.dp, // High elevation for premium feel
                    ) {
                        // Main cinematic container
                        Box(
                            modifier = Modifier
                                .width(basePosterWidth + cardPadding * 2 + menuWidth)
                                .height(basePosterHeight + cardPadding * 2) // Fixed height tightly bound to poster with padding
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        ) {
                            // Ambient Cinematic Background (Blurred poster filling the entire card)
                            val posterUrl = if (state.menuType == ContextMenuType.POSTER) state.searchResponse?.posterUrl else state.watchHistory?.posterUrl
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().blur(48.dp).alpha(0.85f),
                            )
                            // Darken the background for text readability
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))

                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left side: Poster
                                Box(
                                    modifier = Modifier
                                        .width(basePosterWidth + cardPadding * 2)
                                        .fillMaxHeight()
                                        .padding(cardPadding),
                                ) {
                                    if (state.menuType == ContextMenuType.POSTER && state.searchResponse != null) {
                                        PosterCard(
                                            item = state.searchResponse!!,
                                            provider = state.provider,
                                            itemWidth = basePosterWidth,
                                            isHoverEnabled = false,
                                            onClick = {
                                                state.dismiss()
                                                state.onDetailsClick?.invoke()
                                            },
                                        )
                                    } else if (state.menuType == ContextMenuType.WATCH_HISTORY && state.watchHistory != null) {
                                        WatchHistoryCard(
                                            history = state.watchHistory!!,
                                            provider = state.provider,
                                            modifier = Modifier.fillMaxSize(),
                                            isContextMenuEnabled = false,
                                            onRemove = {
                                                state.dismiss()
                                                state.onRemove?.invoke()
                                            },
                                            onClick = {
                                                state.dismiss()
                                                state.onDetailsClick?.invoke()
                                            },
                                        )
                                    }
                                }

                                // Right side: Context Menu (Scrollable, Glassmorphism)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.4f),
                                                    Color.Black.copy(alpha = 0.7f)
                                                ),
                                            ),
                                        )
                                        .padding(vertical = 16.dp, horizontal = 12.dp),
                                ) {
                                    val scrollState = androidx.compose.foundation.rememberScrollState()
                                    Column(
                                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        val titleText = state.searchResponse?.name ?: state.watchHistory?.showName
                                        if (!titleText.isNullOrBlank()) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                                Text(
                                                    text = titleText,
                                                    color = Color.White.copy(alpha = 0.95f),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.5.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .height(1.dp)
                                                        .background(Color.White.copy(alpha = 0.15f))
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }


                                        if (state.menuType == ContextMenuType.POSTER && state.searchResponse != null) {
                                            ContextMenuItem(
                                                text = "Play",
                                                icon = Icons.Default.PlayArrow,
                                                onClick = {
                                                    state.dismiss()
                                                    if (state.onPlayClick != null) state.onPlayClick?.invoke() else state.onDetailsClick?.invoke()
                                                },
                                            )
                                            ContextMenuItem(
                                                text = "Details",
                                                icon = Icons.Default.Info,
                                                onClick = {
                                                    state.dismiss()
                                                    state.onDetailsClick?.invoke()
                                                },
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
                                                    },
                                                )
                                            } else {
                                                ContextMenuItem(
                                                    text = "Add to Library",
                                                    icon = Icons.Default.Add,
                                                    onClick = {
                                                        isLibraryExpanded = !isLibraryExpanded
                                                    },
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
                                                                },
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
                                                },
                                            )
                                        } else if (state.menuType == ContextMenuType.WATCH_HISTORY && state.watchHistory != null) {
                                            ContextMenuItem(
                                                text = "Resume Playing",
                                                icon = Icons.Default.PlayArrow,
                                                onClick = {
                                                    state.dismiss()
                                                    if (state.onPlayClick != null) state.onPlayClick?.invoke() else state.onDetailsClick?.invoke()
                                                },
                                            )
                                            ContextMenuItem(
                                                text = "Details",
                                                icon = Icons.Default.Info,
                                                onClick = {
                                                    state.dismiss()
                                                    state.onDetailsClick?.invoke()
                                                },
                                            )
                                            ContextMenuItem(
                                                text = "Remove from Continue Watching",
                                                icon = Icons.Default.Delete,
                                                color = MaterialTheme.colorScheme.error,
                                                onClick = {
                                                    state.dismiss()
                                                    state.onRemove?.invoke()
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
}

@Composable
private fun ContextMenuItem(
    text: String,
    icon: ImageVector,
    color: Color = Color.White.copy(alpha = 0.9f),
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isHovered) Color.White.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(150),
        label = "menuItemBg",
    )

    val iconOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isHovered) 4f else 0f,
        animationSpec = tween(150),
        label = "menuItemIconOffset",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple()) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier
                .size(20.dp)
                .offset(x = iconOffsetX.dp),
            tint = color,
        )
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}
