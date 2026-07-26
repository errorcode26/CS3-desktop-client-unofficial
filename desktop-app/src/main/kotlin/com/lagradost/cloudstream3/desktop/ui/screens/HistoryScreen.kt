package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ComposeHistoryScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val updates by DesktopDataStore.historyUpdates.collectAsState()

    val historyList = remember(updates) {
        DesktopDataStore.getAllWatchHistory()
            .sortedByDescending { it.updateTime }
            .distinctBy { it.parentId }
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    CloudstreamAlertDialog(
        show = showClearConfirmDialog,
        onDismissRequest = { showClearConfirmDialog = false },
        title = { Text("Clear Watch History?") },
        text = { Text("This will permanently remove all your watch history. You won't be able to resume anything.") },
        confirmButton = {
            TextButton(
                onClick = {
                    showClearConfirmDialog = false
                    coroutineScope.launch(Dispatchers.IO) {
                        DesktopDataStore.clearAllWatchHistory()
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Clear All") }
        },
        dismissButton = {
            TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") }
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (historyList.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Your watch history is empty",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Button(onClick = { navController.navigate(Screen.Home) }) {
                    Text("Browse Home")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with title and "Clear History" button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Continue Watching",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = { showClearConfirmDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Clear All")
                    }
                }

                // Grid of 16:9 History cards
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(historyList, key = { it.parentId }) { history ->
                        val provider = APIHolder.getApiFromNameNull(history.apiName)
                        WatchHistoryCard(
                            history = history,
                            provider = provider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            onRemove = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    DesktopDataStore.removeWatchHistory(history.parentId)
                                }
                            },
                            onClick = {
                                if (provider != null) {
                                    navController.navigate(
                                        Screen.Details(
                                            providerName = provider.name,
                                            url = history.showUrl,
                                            preloadedName = history.showName,
                                            preloadedPoster = history.posterUrl,
                                            preloadedBg = null,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
