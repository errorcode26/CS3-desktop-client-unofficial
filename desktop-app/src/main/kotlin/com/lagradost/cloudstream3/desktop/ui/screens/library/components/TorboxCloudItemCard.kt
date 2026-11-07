@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lagradost.cloudstream3.desktop.ui.screens.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxTorrentData
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TorboxCloudItemCard(
    item: TorboxTorrentData,
    apiKey: String,
    isCurrentResolving: Boolean,
    onCardClick: () -> Unit,
    onBrowseFilesClick: () -> Unit,
    onPlayClick: () -> Unit,
    onResumeEpisode: (WatchHistory) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val videoFiles = remember(item.files) { item.files.filter { isVideoFile(it.name) }.sortedWith(naturalNameComparator) }
    val displayTitle = remember(item.name) { cleanItemTitle(item.name) }
    val parentId = "torbox_${item.storageType.name.lowercase()}_${item.id}"
    val historyTick by DesktopDataStore.historyUpdates.collectAsState()
    val latestHistory = remember(item.id, historyTick) {
        DesktopDataStore.getLastWatched(parentId)
    }

    val cardScope = rememberCoroutineScope()
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    var isCopyingLink by remember { mutableStateOf(false) }

    val isPending = !item.isCompleted && (item.downloadState == "pending" || item.downloadState == "queued" || (item.progress == null && item.downloadState != "downloading"))
    val isDownloading = !item.isCompleted && item.downloadState == "downloading"
    val isSeeding = !item.isCompleted && (item.downloadState == "uploading" || item.downloadState == "seeding")

    val borderColor = if (isHovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    val cardBg = if (isHovered) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 6.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .pointerInput(item.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release) {
                            if (event.button == PointerButton.Secondary) {
                                val pos = event.changes.firstOrNull()?.position ?: androidx.compose.ui.geometry.Offset.Zero
                                contextMenuOffset = with(density) {
                                    DpOffset(pos.x.toDp(), pos.y.toDp())
                                }
                                showContextMenu = true
                            } else if (event.button == PointerButton.Primary) {
                                onCardClick()
                            }
                        }
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = contextMenuOffset,
            ) {
                DropdownMenuItem(
                    text = { Text("Copy Title") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        copyToClipboard(item.name)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (isCopyingLink) "Fetching link..." else "Copy Stream / Download Link") },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    enabled = !isCopyingLink,
                    onClick = {
                        showContextMenu = false
                        cardScope.launch(Dispatchers.IO) {
                            isCopyingLink = true
                            try {
                                val link = TorboxApiClient.requestDownloadLink(apiKey, item, null)
                                if (!link.isNullOrBlank()) {
                                    copyToClipboard(link)
                                }
                            } catch (e: Exception) {
                                AppLogger.e("TorboxCloudLibraryView: Failed to copy stream link", e)
                            } finally {
                                isCopyingLink = false
                            }
                        }
                    },
                )
                if (!item.hash.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy Magnet Link") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showContextMenu = false
                            val encoded = java.net.URLEncoder.encode(item.name, "UTF-8")
                            copyToClipboard("magnet:?xt=urn:btih:${item.hash}&dn=$encoded")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Info Hash") },
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showContextMenu = false
                            copyToClipboard(item.hash)
                        },
                    )
                }
                if (!item.originalUrl.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy Source URL") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showContextMenu = false
                            copyToClipboard(item.originalUrl)
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("Delete from Cloud", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        onDeleteClick()
                    },
                )
            }

            // Top Row: Status badge on left, Formatted size on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusLabel = when {
                    item.isCompleted -> "Completed"
                    item.downloadState == "cached" -> "Cached"
                    item.downloadState == "downloading" -> "Downloading (${item.progressPercent}%)"
                    item.downloadState == "uploading" || item.downloadState == "seeding" -> "Seeding"
                    !item.downloadState.isNullOrBlank() -> item.downloadState.replaceFirstChar { it.uppercase() }
                    else -> "Queued"
                }

                Surface(
                    color = if (item.isCompleted || item.downloadState == "cached") {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (item.isCompleted || item.downloadState == "cached") {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else if (item.downloadState == "downloading") {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.isCompleted || item.downloadState == "cached") {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                    }
                }

                if (item.formattedSize.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Middle Section: 2-line Clean Title with Tooltip showing Full Raw Name
            Column(modifier = Modifier.fillMaxWidth()) {
                TooltipArea(
                    tooltip = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1E28),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier.widthIn(max = 480.dp),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Full Release Name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                        }
                    },
                    delayMillis = 200,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (!item.isCompleted) {
                    Spacer(Modifier.height(6.dp))
                    if (isPending) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Pending • Resolving metadata / queued",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                            if (item.formattedSize.isNotBlank()) {
                                Text(
                                    text = item.formattedSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    } else {
                        val currentProgress = (item.progress?.toFloat() ?: 0f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val progressText = if (item.size > 0L) {
                                "${item.formattedDownloadedSize} / ${item.formattedSize} (${item.progressPercent}%)"
                            } else {
                                "${item.progressPercent}%"
                            }
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            val telemetryList = listOfNotNull(
                                item.formattedSpeed,
                                item.formattedEta,
                                item.seeds?.takeIf { it > 0 }?.let { "$it seeds" },
                            )
                            if (telemetryList.isNotEmpty()) {
                                Text(
                                    text = telemetryList.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                } else if (item.isCompleted && latestHistory != null && latestHistory.duration > 0L) {
                    val watchRatio = (latestHistory.position.toFloat() / latestHistory.duration.toFloat()).coerceIn(0f, 1f)
                    if (watchRatio > 0f && !PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { watchRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }

            // Bottom Row: Sources indicator on left, Actions (Delete, Play/Browse/Resume) on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (videoFiles.size > 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable(onClick = onBrowseFilesClick),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${videoFiles.size} Sources",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = if (item.files.isNotEmpty()) "1 Source" else "Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                        )
                    }

                    if (videoFiles.size > 1) {
                        if (latestHistory != null && latestHistory.position > 0) {
                            IconButton(
                                onClick = onBrowseFilesClick,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = "Browse files",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(
                                onClick = { onResumeEpisode(latestHistory) },
                                enabled = !isCurrentResolving,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                if (isCurrentResolving) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Loading...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    val epText = if (latestHistory.episode != null) "Resume Ep ${latestHistory.episode}" else "Resume"
                                    Text(epText, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onBrowseFilesClick,
                                enabled = !isCurrentResolving,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                if (isCurrentResolving) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Loading...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Browse", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        val isResumeSingle = latestHistory != null && latestHistory.position > 0 && !PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)

                        FilledTonalButton(
                            onClick = onPlayClick,
                            enabled = !isCurrentResolving,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            if (isCurrentResolving) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Loading...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        isResumeSingle -> "Resume"
                                        isPending -> "Pending"
                                        isDownloading -> "Stream"
                                        else -> "Play"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
