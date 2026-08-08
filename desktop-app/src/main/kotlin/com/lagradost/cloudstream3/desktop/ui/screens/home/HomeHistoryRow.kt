package com.lagradost.cloudstream3.desktop.ui.screens.home

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
import com.lagradost.common.storage.WatchHistory

@Composable
fun HomeHistoryRow(
    historyList: List<WatchHistory>,
    providers: List<MainAPI>,
    onClearHistory: () -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onViewAllClick: () -> Unit,
    onItemClick: (MainAPI, WatchHistory) -> Unit,
) {
    if (historyList.isEmpty()) return

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
    val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 88.dp else 22.dp
    val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 88.dp else 22.dp

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.padding(start = paddingStart, end = paddingEnd),
    ) {
        CategoryRowWithHeader(
            title = "Continue Watching",
            itemCount = historyList.size,
            onViewAll = onViewAllClick,
            trailingHeaderExtra = {
                TextButton(onClick = { showClearConfirmDialog = true }) {
                    Text("Clear History", color = DesktopUi.TextMuted)
                }
            },
        ) {
            items(historyList.size, key = { index -> historyList[index].parentId }) { index ->
                val history = historyList[index]
                val provider = providers.find { it.name == history.apiName }
                WatchHistoryCard(
                    modifier = Modifier.animateItem().width(380.dp).height(380.dp * 9f / 16f),
                    history = history,
                    provider = provider,
                    onRemove = { onRemoveHistoryItem(history.parentId) },
                    onClick = {
                        if (provider != null) {
                            onItemClick(provider, history)
                        }
                    },
                    onPlayClick = {
                        // For watch history, onClick already resumes playback.
                        if (provider != null) {
                            onItemClick(provider, history)
                        }
                    },
                )
            }
        }
    }
}
