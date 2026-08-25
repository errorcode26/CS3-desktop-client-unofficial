package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard
import com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardWide
import com.lagradost.common.storage.WatchHistory

@Composable
fun HomeHistoryRow(
    historyList: List<WatchHistory>,
    providers: List<MainAPI>,
    onClearHistory: () -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onViewAllClick: () -> Unit,
    onItemClick: (MainAPI, WatchHistory) -> Unit,
    onPlayClick: ((MainAPI, WatchHistory) -> Unit)? = null,
) {
    val displayList = remember(historyList) { historyList.ifEmpty { emptyList() } } // We'll hold previous state
    val lastNonEmptyList = remember { mutableStateOf(historyList) }
    if (historyList.isNotEmpty()) {
        lastNonEmptyList.value = historyList
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = historyList.isNotEmpty(),
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
    ) {
        var showClearConfirmDialog by remember { mutableStateOf(false) }

        CloudstreamAlertDialog(
            show = showClearConfirmDialog,
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Watch History?") },
            text = { Text("This will permanently remove all your watch history. You won't be able to resume anything from here.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") }
            },
        )

        val dockPosition by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.dockPosition.collectAsState()
        val continueWatchingStyle by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.continueWatchingStyle.collectAsState()
        val posterWidthDp by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.posterWidthDp.collectAsState()
        val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 88.dp else 22.dp
        val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 88.dp else 22.dp

        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 600.dp
            val effectivePaddingStart = if (isCompact) 8.dp else paddingStart
            val effectivePaddingEnd = if (isCompact) 8.dp else paddingEnd
            val currentList = lastNonEmptyList.value

            CategoryRowWithHeader(
                title = "Continue Watching",
                itemCount = currentList.size,
                onViewAll = onViewAllClick,
                rowContentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = effectivePaddingStart,
                    end = effectivePaddingEnd,
                    top = if (isCompact) 8.dp else 16.dp,
                    bottom = if (isCompact) 8.dp else 16.dp,
                ),
                headerPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = effectivePaddingStart,
                    end = effectivePaddingEnd,
                    top = 12.dp,
                    bottom = 8.dp,
                ),
                trailingHeaderExtra = if (!isCompact) {
                    {
                        TextButton(onClick = { showClearConfirmDialog = true }) {
                            Text("Clear History", color = DesktopUi.TextMuted)
                        }
                    }
                } else null,
            ) {
                items(currentList.size, key = { index -> currentList[index].parentId }) { index ->
                    val history = currentList[index]
                    val provider = providers.find { it.name == history.apiName }
                    
                    if (continueWatchingStyle == com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM) {
                        val cardWidth = if (isCompact) 230.dp else (posterWidthDp * 2.2f).dp
                        val cardHeight = if (isCompact) 125.dp else (posterWidthDp * 1.5f).dp
                        WatchHistoryCardWide(
                            modifier = Modifier.animateItem().width(cardWidth).height(cardHeight),
                            history = history,
                            provider = provider,
                            onRemove = { onRemoveHistoryItem(history.parentId) },
                            onClick = {
                                if (provider != null) {
                                    onItemClick(provider, history)
                                }
                            },
                            onPlayClick = {
                                if (provider != null) {
                                    if (onPlayClick != null) {
                                        onPlayClick(provider, history)
                                    } else {
                                        onItemClick(provider, history)
                                    }
                                }
                            },
                        )
                    } else {
                        val cardWidth = if (isCompact) 230.dp else 380.dp
                        val cardHeight = cardWidth * 9f / 16f
                        WatchHistoryCard(
                            modifier = Modifier.animateItem().width(cardWidth).height(cardHeight),
                            history = history,
                            provider = provider,
                            onRemove = { onRemoveHistoryItem(history.parentId) },
                            onClick = {
                                if (provider != null) {
                                    onItemClick(provider, history)
                                }
                            },
                            onPlayClick = {
                                if (provider != null) {
                                    if (onPlayClick != null) {
                                        onPlayClick(provider, history)
                                    } else {
                                        onItemClick(provider, history)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
