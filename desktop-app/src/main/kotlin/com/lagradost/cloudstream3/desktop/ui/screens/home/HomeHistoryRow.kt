package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard
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

    Column(modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp)) {
        CategoryRowWithHeader(
            title = "Continue Watching",
            itemCount = historyList.size,
            trailingHeaderExtra = {
                TextButton(onClick = onClearHistory) {
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
}
