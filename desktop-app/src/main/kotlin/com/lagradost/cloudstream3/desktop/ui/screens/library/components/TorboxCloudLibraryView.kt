package com.lagradost.cloudstream3.desktop.ui.screens.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxFile
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxStorageType
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxTorrentData
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.library.TorboxCloudLibraryViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.TorboxLibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.TorrentFilter
import com.lagradost.common.storage.WatchHistory

@Composable
fun TorboxCloudLibraryView(
    onNavigate: (Config) -> Unit,
    viewModel: TorboxCloudLibraryViewModel = androidx.compose.runtime.remember { TorboxCloudLibraryViewModel() },
    modifier: Modifier = Modifier,
) {
    val launchPlayer = LocalVideoPlayer.current
    val uiState by viewModel.uiState.collectAsState()
    val apiKey = uiState.apiKey
    val selectedStorageType = uiState.selectedStorageType
    val itemsList = uiState.itemsList
    val isLoading = uiState.isLoading
    val errorMessage = uiState.errorMessage
    val searchQuery = uiState.searchQuery
    val selectedFilter = uiState.selectedFilter
    val isAddingItem = uiState.isAddingItem
    val addItemError = uiState.addItemError
    val isDeleting = uiState.isDeleting
    val resolvingItemId = uiState.resolvingItemId
    val resolvingFileId = uiState.resolvingFileId
    val filteredItems = uiState.filteredItems
    val activeRecentHistory = uiState.recentHistory

    var showAddDialog by remember { mutableStateOf(false) }
    var newLinkInput by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<TorboxTorrentData?>(null) }
    var selectedItemForFiles by remember { mutableStateOf<TorboxTorrentData?>(null) }

    fun refreshItems(force: Boolean = false) {
        viewModel.onEvent(TorboxLibraryUiEvent.OnRefresh(force))
    }

    fun playItemFile(item: TorboxTorrentData, file: TorboxFile?) {
        viewModel.onEvent(TorboxLibraryUiEvent.OnPlayItem(item, file, launchPlayer))
    }

    fun resumeFromHistory(history: WatchHistory) {
        viewModel.onEvent(TorboxLibraryUiEvent.OnResumeFromHistory(history, launchPlayer))
    }

    fun removeTorboxHistory(history: WatchHistory) {
        viewModel.onEvent(TorboxLibraryUiEvent.OnRemoveHistory(history))
    }

    if (apiKey.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Text(
                    text = "Torbox Not Connected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connect your Torbox account in Settings > Accounts to browse, manage, and play your cloud torrent library directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onNavigate(Config.Settings) },
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Accounts Settings")
                }
            }
        }
        return
    }

    val addLabel = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "Add Magnet"
        TorboxStorageType.WEB_DOWNLOADS -> "Add Web Link"
        TorboxStorageType.USENET -> "Add Usenet"
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Storage Type Sub-Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TorboxStorageType.entries.forEach { type ->
                FilterChip(
                    selected = selectedStorageType == type,
                    onClick = {
                        if (selectedStorageType != type) {
                            viewModel.onEvent(TorboxLibraryUiEvent.OnSelectStorageType(type))
                        }
                    },
                    label = {
                        Text(
                            type.label,
                            fontWeight = if (selectedStorageType == type) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }

        // Dedicated Torbox Continue Watching Shelf
        if (searchQuery.isBlank() && activeRecentHistory.isNotEmpty()) {
            TorboxRecentWatchedShelf(
                recentItems = activeRecentHistory,
                itemsList = itemsList,
                onResume = ::resumeFromHistory,
                onRemove = ::removeTorboxHistory,
            )
        }

        // Toolbar: Search Field, Filters, Add, Refresh
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onEvent(TorboxLibraryUiEvent.OnSearchQueryChange(it)) },
                placeholder = {
                    Text(
                        "Search ${selectedStorageType.label.lowercase()}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onEvent(TorboxLibraryUiEvent.OnSearchQueryChange("")) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(52.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TorrentFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { viewModel.onEvent(TorboxLibraryUiEvent.OnFilterChange(filter)) },
                        label = { Text(filter.label) },
                    )
                }
            }

            Button(
                onClick = {
                    newLinkInput = ""
                    viewModel.onEvent(TorboxLibraryUiEvent.OnDismissAddItemError)
                    showAddDialog = true
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(addLabel)
            }

            IconButton(
                onClick = { refreshItems(force = true) },
                enabled = !isLoading,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { refreshItems(force = true) }) {
                        Text("Retry")
                    }
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No items match '$searchQuery'" else "Your Torbox ${selectedStorageType.label.lowercase()} storage is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (searchQuery.isBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Click '$addLabel' to add to your cloud storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(filteredItems, key = { "${it.storageType}_${it.id}" }) { item ->
                    val videoFiles = remember(item.files) {
                        item.files.filter { isVideoFile(it.name) }.sortedWith(naturalNameComparator)
                    }
                    TorboxCloudItemCard(
                        item = item,
                        apiKey = apiKey,
                        isCurrentResolving = resolvingItemId == item.id,
                        onCardClick = {
                            if (videoFiles.size > 1) {
                                selectedItemForFiles = item
                            } else {
                                val targetFile = TorboxApiClient.selectBestVideoFile(item.files)
                                playItemFile(item, targetFile)
                            }
                        },
                        onBrowseFilesClick = {
                            selectedItemForFiles = item
                        },
                        onPlayClick = {
                            if (videoFiles.size > 1) {
                                selectedItemForFiles = item
                            } else {
                                val targetFile = TorboxApiClient.selectBestVideoFile(item.files)
                                playItemFile(item, targetFile)
                            }
                        },
                        onResumeEpisode = { hist ->
                            val target = item.files.find { it.id.toString() == hist.episodeId }
                            playItemFile(item, target)
                        },
                        onDeleteClick = {
                            itemToDelete = item
                        },
                    )
                }
            }
        }
    }

    // Add Item Dialog
    val dialogTitle = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "Add Torrent to Cloud"
        TorboxStorageType.WEB_DOWNLOADS -> "Add Web Download to Cloud"
        TorboxStorageType.USENET -> "Add Usenet Download to Cloud"
    }
    val dialogPrompt = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "Enter a magnet URL or info hash to add to your cloud storage:"
        TorboxStorageType.WEB_DOWNLOADS -> "Enter a direct download link (e.g. Rapidgator, Mega, etc.):"
        TorboxStorageType.USENET -> "Enter an NZB file link or Usenet URL:"
    }
    val dialogPlaceholder = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "magnet:?xt=urn:btih:..."
        TorboxStorageType.WEB_DOWNLOADS -> "https://..."
        TorboxStorageType.USENET -> "https://..."
    }
    val dialogConfirmText = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "Add Torrent"
        TorboxStorageType.WEB_DOWNLOADS -> "Add Web Link"
        TorboxStorageType.USENET -> "Add Usenet"
    }

    CloudstreamAlertDialog(
        show = showAddDialog,
        onDismissRequest = {
            if (!isAddingItem) showAddDialog = false
        },
        title = { Text(dialogTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = dialogPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newLinkInput,
                    onValueChange = {
                        newLinkInput = it
                        viewModel.onEvent(TorboxLibraryUiEvent.OnDismissAddItemError)
                    },
                    placeholder = {
                        Text(
                            dialogPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAddingItem,
                )
                if (addItemError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = addItemError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.onEvent(TorboxLibraryUiEvent.OnAddItem(newLinkInput) {
                        showAddDialog = false
                        newLinkInput = ""
                    })
                },
                enabled = !isAddingItem && newLinkInput.isNotBlank(),
            ) {
                if (isAddingItem) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Adding...")
                } else {
                    Text(dialogConfirmText)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showAddDialog = false },
                enabled = !isAddingItem,
            ) {
                Text("Cancel")
            }
        },
    )

    // Delete Confirmation Dialog
    itemToDelete?.let { target ->
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = {
                if (!isDeleting) itemToDelete = null
            },
            title = { Text("Delete ${target.storageType.label.dropLast(1)}") },
            text = {
                Text(
                    text = "Are you sure you want to remove '${target.name}' from your Torbox ${target.storageType.label.lowercase()} storage?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onEvent(TorboxLibraryUiEvent.OnDeleteItem(target) {
                            itemToDelete = null
                        })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Deleting...")
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToDelete = null },
                    enabled = !isDeleting,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Multi-File Browser Modal
    selectedItemForFiles?.let { item ->
        TorboxFileBrowserDialog(
            item = item,
            apiKey = apiKey,
            resolvingItemId = resolvingItemId,
            resolvingFileId = resolvingFileId,
            onDismissRequest = { selectedItemForFiles = null },
            onPlayFile = ::playItemFile,
        )
    }
}
