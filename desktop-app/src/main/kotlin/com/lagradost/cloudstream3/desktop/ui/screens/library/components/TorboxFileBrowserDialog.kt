@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lagradost.cloudstream3.desktop.ui.screens.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxFile
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxTorrentData
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun TorboxFileBrowserDialog(
    item: TorboxTorrentData,
    apiKey: String,
    resolvingItemId: Int?,
    resolvingFileId: Int?,
    onDismissRequest: () -> Unit,
    onPlayFile: (TorboxTorrentData, TorboxFile) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val historyTick by DesktopDataStore.historyUpdates.collectAsState()

    val videoFiles = remember(item.files) {
        item.files.filter { isVideoFile(it.name) }.sortedWith(naturalNameComparator)
    }
    var showAllFiles by remember { mutableStateOf(videoFiles.isEmpty()) }
    val displayFiles = if (!showAllFiles && videoFiles.isNotEmpty()) videoFiles else item.files

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = cleanItemTitle(item.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${displayFiles.size} sources available (${item.formattedSize})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (videoFiles.isNotEmpty() && item.files.size > videoFiles.size) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showAllFiles,
                        onClick = { showAllFiles = false },
                        label = { Text("Video Streams (${videoFiles.size})") },
                    )
                    FilterChip(
                        selected = showAllFiles,
                        onClick = { showAllFiles = true },
                        label = { Text("All Files (${item.files.size})") },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayFiles, key = { it.id }) { file ->
                    val isFileResolving = resolvingItemId == item.id && resolvingFileId == file.id
                    val ext = file.name.substringAfterLast('.', "").uppercase().take(4)
                    val isVideo = isVideoFile(file.name)
                    val parentId = "torbox_${item.storageType.name.lowercase()}_${item.id}"
                    val epWatched = remember(file.id, historyTick) {
                        DesktopDataStore.getEpisodeWatched(parentId, file.id.toString())
                    }
                    val fileRatio = if (epWatched != null && epWatched.duration > 0L) {
                        (epWatched.position.toFloat() / epWatched.duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val isEpCompleted = epWatched != null && (fileRatio >= 0.85f || (epWatched.duration > 0L && PlayerLinkHandler.isCompleted(epWatched.position, epWatched.duration)))

                    val interactionSource = remember { MutableInteractionSource() }
                    val isRowHovered by interactionSource.collectIsHoveredAsState()
                    var showFileMenu by remember { mutableStateOf(false) }
                    var fileMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
                    val density = LocalDensity.current

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRowHovered) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                            },
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isRowHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .hoverable(interactionSource)
                            .pointerInput(file.id) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Release) {
                                            if (event.button == PointerButton.Secondary) {
                                                val pos = event.changes.firstOrNull()?.position ?: androidx.compose.ui.geometry.Offset.Zero
                                                fileMenuOffset = with(density) {
                                                    DpOffset(pos.x.toDp(), pos.y.toDp())
                                                }
                                                showFileMenu = true
                                            } else if (event.button == PointerButton.Primary) {
                                                onPlayFile(item, file)
                                            }
                                        }
                                    }
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DropdownMenu(
                                    expanded = showFileMenu,
                                    onDismissRequest = { showFileMenu = false },
                                    offset = fileMenuOffset,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Copy File Name") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showFileMenu = false
                                            copyToClipboard(file.name)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy Stream / Download Link") },
                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showFileMenu = false
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val link = TorboxApiClient.requestDownloadLink(apiKey, item, file.id)
                                                    if (!link.isNullOrBlank()) {
                                                        copyToClipboard(link)
                                                    }
                                                } catch (e: Exception) {
                                                    AppLogger.e("TorboxCloudLibraryView: Failed to copy file stream link", e)
                                                }
                                            }
                                        },
                                    )
                                }

                                if (ext.isNotBlank()) {
                                    Surface(
                                        color = if (isVideo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Text(
                                            text = ext,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isVideo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (isEpCompleted) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(11.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    )
                                                    Text(
                                                        text = "Watched",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (fileRatio > 0f && !isEpCompleted) {
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { fileRatio },
                                            modifier = Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                                        )
                                    }
                                    if (file.formattedFileSize.isNotBlank() || (fileRatio > 0f && !isEpCompleted)) {
                                        Spacer(Modifier.height(3.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (file.formattedFileSize.isNotBlank()) {
                                                Text(
                                                    text = file.formattedFileSize,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (fileRatio > 0f && !isEpCompleted) {
                                                Text(
                                                    text = "${(fileRatio * 100).toInt()}% watched",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { onPlayFile(item, file) },
                                enabled = !isFileResolving,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                if (isFileResolving) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Loading...", style = MaterialTheme.typography.labelMedium)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    val btnLabel = when {
                                        isEpCompleted -> "Replay"
                                        fileRatio > 0f -> "Resume"
                                        else -> "Play"
                                    }
                                    Text(btnLabel, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
