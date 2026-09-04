package com.lagradost.cloudstream3.desktop.ui.screens.downloads

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager
import com.lagradost.cloudstream3.desktop.downloader.DownloadStatus
import com.lagradost.cloudstream3.desktop.downloader.DownloadTask
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons
import com.lagradost.cloudstream3.desktop.utils.ImageUtils
import java.awt.Desktop
import java.io.File

import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.dialogs.DownloadSettingsDialog

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onPlayOffline: (DownloadTask) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var taskPendingDelete by remember { mutableStateOf<DownloadTask?>(null) }
    var showPendingDeleteShow by remember { mutableStateOf<String?>(null) }
    var showCancelAllDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (uiState.totalActiveSpeed > 0L) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF10B981), CircleShape),
                            )
                            Text(
                                text = "↓ ${formatSpeed(uiState.totalActiveSpeed)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search downloads...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.width(240.dp).height(48.dp),
                )

                FilledTonalButton(
                    onClick = { com.lagradost.cloudstream3.desktop.ui.GlobalMediaLauncher.openLocalFileDialog() },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play Local File", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                FilledTonalButton(
                    onClick = { com.lagradost.cloudstream3.desktop.ui.GlobalMediaLauncher.showNetworkStreamDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stream URL", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                FilledTonalIconButton(
                    onClick = { viewModel.cleanOrphanedJunk() },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Clean Orphaned Junk", modifier = Modifier.size(20.dp))
                }

                FilledTonalIconButton(
                    onClick = { viewModel.openSettings() },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Download Settings", modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = {
                        try {
                            Desktop.getDesktop().open(DesktopDownloadManager.downloadsDir)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open Downloads Folder", modifier = Modifier.size(22.dp))
                }
            }
        }

        // Tab Filters & Batch Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadsTab.values().forEach { tab ->
                    val selected = uiState.activeTab == tab
                    val badgeCount = if (tab == DownloadsTab.ACTIVE_QUEUE) uiState.activeTasks.size else null

                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setTab(tab) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(tab.title)
                                if (badgeCount != null && badgeCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            text = badgeCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            if (uiState.activeTasks.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasActive = uiState.activeTasks.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
                    if (hasActive) {
                        OutlinedButton(
                            onClick = { viewModel.pauseAll() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause All", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    val hasPaused = uiState.activeTasks.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
                    if (hasPaused) {
                        Button(
                            onClick = { viewModel.resumeAll() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume All", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedButton(
                        onClick = { showCancelAllDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Main Content Area
        if (uiState.activeTab == DownloadsTab.ACTIVE_QUEUE) {
            ActiveQueueView(
                tasks = uiState.activeTasks,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onCancel = { taskId ->
                    val task = uiState.tasks.find { it.id == taskId }
                    if (task != null) taskPendingDelete = task
                },
            )
        } else {
            // Show Active Queue Card at the top if there are downloading items
            if (uiState.activeTasks.isNotEmpty() && uiState.activeTab == DownloadsTab.ALL) {
                ActiveQueueCard(
                    tasks = uiState.activeTasks,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onCancel = { taskId ->
                        val task = uiState.tasks.find { it.id == taskId }
                        if (task != null) taskPendingDelete = task
                    },
                    onViewAll = { viewModel.setTab(DownloadsTab.ACTIVE_QUEUE) },
                )
            }

            val completed = uiState.filteredCompletedTasks
            if (completed.isEmpty() && uiState.activeTasks.isEmpty()) {
                EmptyDownloadsPlaceholder()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(completed, key = { it.id }) { task ->
                        DownloadedItemCard(
                            task = task,
                            allCompletedTasks = completed,
                            onPlay = { onPlayOffline(task) },
                            onDelete = { taskPendingDelete = task },
                            onDeleteShow = { showPendingDeleteShow = task.showName },
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (taskPendingDelete != null) {
        val target = taskPendingDelete!!
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { taskPendingDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Delete Download?")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete \"${target.displayTitle}\"?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (target.downloadedBytes > 0) {
                        Text(
                            text = "This will remove the file from your computer and free up ${formatBytes(target.downloadedBytes)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = target
                        taskPendingDelete = null
                        viewModel.delete(toDelete, deleteFile = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskPendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Delete Entire Show Confirmation Dialog
    if (showPendingDeleteShow != null) {
        val targetShow = showPendingDeleteShow!!
        val matchingTasks = uiState.tasks.filter { it.showName.equals(targetShow, ignoreCase = true) }
        val totalBytes = matchingTasks.sumOf { it.downloadedBytes }
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { showPendingDeleteShow = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Delete Entire Show?")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete all ${matchingTasks.size} downloaded episodes of \"$targetShow\"?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (totalBytes > 0) {
                        Text(
                            text = "This will remove all downloaded files for this show and free up ${formatBytes(totalBytes)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val showToDelete = targetShow
                        showPendingDeleteShow = null
                        viewModel.deleteShow(showToDelete, deleteFiles = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete All ${matchingTasks.size} Episodes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPendingDeleteShow = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Cancel All Confirmation Dialog
    if (showCancelAllDialog) {
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { showCancelAllDialog = false },
            title = { Text("Cancel All Downloads?") },
            text = { Text("Are you sure you want to cancel all active and queued downloads? Any temporary files in progress will be cleaned up.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelAllDialog = false
                        viewModel.cancelAll()
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("Cancelled all downloads.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Cancel All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelAllDialog = false }) {
                    Text("Keep Downloading")
                }
            },
        )
    }

    // In-Tab Download Settings Dialog
    DownloadSettingsDialog(
        show = uiState.isSettingsOpen,
        onDismiss = viewModel::closeSettings,
        downloadPath = uiState.downloadPath,
        downloadThreads = uiState.downloadThreads,
        maxConcurrent = uiState.maxConcurrent,
        onUpdatePath = viewModel::updateDownloadPath,
        onUpdateThreads = viewModel::updateDownloadThreads,
        onUpdateMaxConcurrent = viewModel::updateMaxConcurrent,
        onCleanJunk = viewModel::cleanOrphanedJunk,
    )

    // Junk Cleanup Result Alert
    if (uiState.reclaimedBytesMessage != null) {
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = viewModel::dismissJunkMessage,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Storage Maintenance")
                }
            },
            text = {
                Text(
                    text = uiState.reclaimedBytesMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = viewModel::dismissJunkMessage) {
                    Text("Done")
                }
            },
        )
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DownloadedItemCard(
    task: DownloadTask,
    allCompletedTasks: List<DownloadTask>,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onDeleteShow: () -> Unit,
) {
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val isContextMenuEnabled = true
    val showTasks = remember(task.showName, allCompletedTasks) {
        allCompletedTasks.filter { it.showName.equals(task.showName, ignoreCase = true) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onGloballyPositioned { coordinates ->
                val newBounds = Rect(
                    offset = coordinates.positionInWindow(),
                    size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                )
                if (bounds != newBounds) {
                    bounds = newBounds
                }
            }
            .pointerInput(isContextMenuEnabled) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Release) {
                            if (isContextMenuEnabled && event.button == androidx.compose.ui.input.pointer.PointerButton.Secondary) {
                                com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState.showForDownload(
                                    bounds = bounds,
                                    task = task,
                                    allShowTasks = showTasks,
                                    onPlay = onPlay,
                                    onDelete = onDelete,
                                    onDeleteShow = onDeleteShow,
                                    onOpenInExplorer = {
                                        try {
                                            val file = task.file
                                            if (file.exists()) {
                                                if (System.getProperty("os.name").lowercase().contains("win")) {
                                                    Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,", file.absolutePath))
                                                } else {
                                                    Desktop.getDesktop().open(file.parentFile ?: file)
                                                }
                                            }
                                        } catch (_: Exception) {
                                            try {
                                                Desktop.getDesktop().open(task.file.parentFile ?: task.file)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                )
                            } else if (event.button == androidx.compose.ui.input.pointer.PointerButton.Primary) {
                                onPlay()
                            }
                        }
                    }
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                AsyncImage(
                    model = task.posterUrl ?: task.backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 100f,
                            )
                        ),
                )

                // Quality Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                ) {
                    Text(
                        text = "${task.quality}p",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Play Button
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimary)
                }

                // File Size Badge
                Text(
                    text = formatBytes(task.downloadedBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(8.dp).align(Alignment.BottomStart),
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(4.dp).size(28.dp).align(Alignment.BottomEnd),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                }
            }

            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = task.showName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!task.isMovie) {
                    val epClean = task.cleanEpisodeTitle
                    Text(
                        text = "S${task.season ?: 1} E${task.episode ?: 1}${if (!epClean.isNullOrBlank()) " • $epClean" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadsPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                text = "No Downloads Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Download movies and episodes to watch offline anytime with zero buffering.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}

internal fun formatSpeed(bytesPerSec: Long): String {
    val mb = bytesPerSec.toDouble() / (1024 * 1024)
    return if (mb >= 1.0) "%.1f MB/s".format(mb) else "%.0f KB/s".format(bytesPerSec.toDouble() / 1024)
}

internal fun formatEta(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }
}
