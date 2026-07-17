package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard
import com.lagradost.cloudstream3.desktop.ui.components.AmoledConfirmDialog
import com.lagradost.common.storage.WatchHistory

@Composable
fun HomeHistoryRow(
    historyList: List<WatchHistory>,
    providers: List<MainAPI>,
    onClearHistory: () -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onItemClick: (MainAPI, WatchHistory) -> Unit,
) {
    if (historyList.isEmpty()) return

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    AmoledConfirmDialog(
        show = showClearConfirmDialog,
        title = "Clear Watch History?",
        text = "This will permanently remove all your watch history. You won't be able to resume anything from here.",
        confirmText = "Clear All",
        dismissText = "Cancel",
        isDestructive = true,
        onConfirm = {
            showClearConfirmDialog = false
            onClearHistory()
        },
        onDismiss = { showClearConfirmDialog = false }
    )

    CategoryRowWithHeader(
        title = "Continue Watching",
        itemCount = historyList.size,
        trailingHeaderExtra = {
            TextButton(onClick = { showClearConfirmDialog = true }) {
                Text("Clear History", color = DesktopUi.TextMuted)
            }
        },
    ) {
        items(historyList.size) { index ->
            val history = historyList[index]
            val provider = providers.find { it.name == history.apiName }
            WatchHistoryCard(
                history = history,
                provider = provider,
                onRemove = { onRemoveHistoryItem(history.parentId) },
                onClick = {
                    if (provider != null) {
                        onItemClick(provider, history)
                    }
                },
            )
        }
    }
}
