package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.download.DownloadTask
import com.lagradost.cloudstream3.desktop.download.TaskStatus
import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrServerBinary
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import java.util.Locale

@Composable
fun SettingsUpdates(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val availableUpdates by UnifiedUpdateManager.availableUpdates.collectAsState()
    val downloadTasks by AppDownloadManager.tasks.collectAsState()
    val isChecking = uiState.updateCheckState.isChecking
    val showCheckedFeedback = uiState.updateCheckState.showCheckedFeedback

    val scope = rememberCoroutineScope()
    val torrBinary = remember { DesktopTorrServerBinary() }
    val ytdlBinary = remember { DesktopYtDlpBinary() }

    var torrInstalled by remember { mutableStateOf(torrBinary.isInstalled()) }
    var ytdlInstalled by remember { mutableStateOf(ytdlBinary.isInstalled()) }
    var torrFileSize by remember { mutableStateOf(torrBinary.getFileSizeMB()) }
    var ytdlFileSize by remember { mutableStateOf(ytdlBinary.getFileSizeMB()) }

    var showDeleteTorrConfirm by remember { mutableStateOf(false) }
    var showDeleteYtdlConfirm by remember { mutableStateOf(false) }

    val torrTask = downloadTasks.firstOrNull { it.id == "torrserver" }
    val ytdlTask = downloadTasks.firstOrNull { it.id == "ytdl" }

    LaunchedEffect(torrTask?.status) {
        torrInstalled = torrBinary.isInstalled()
        torrFileSize = torrBinary.getFileSizeMB()
    }

    LaunchedEffect(ytdlTask?.status) {
        ytdlInstalled = ytdlBinary.isInstalled()
        ytdlFileSize = ytdlBinary.getFileSizeMB()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Card 1: Application Updates
        SettingsGroupCard(title = "Application Updates") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CS3 Desktop Client",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Installed: v${AppConfig.APP_VERSION}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            viewModel.onEvent(SettingsUiEvent.CheckUpdates(force = true))
                        },
                        enabled = !isChecking,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Check for updates",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isChecking) "Checking..." else "Check for Updates",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (availableUpdates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(4.dp))

                    availableUpdates.forEach { update ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "${update.title}: ${update.newVersion}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Current: ${update.currentVersion}${if (update.publishedAt != null) " • Published: ${update.publishedAt}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Button(
                                    onClick = { UnifiedUpdateManager.showDialogForUpdate(update) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View & Update", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (showCheckedFeedback) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Everything is up to date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Card 2: External Binary Packages & Transparency
        SettingsGroupCard(title = "External Streaming Packages") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Streaming packages run locally in the background to extract streams and cache P2P content. For security and transparency, binaries are downloaded directly from their official upstream GitHub releases into your local user data directory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )

                // Package 1: TorrServer Engine
                BinaryPackageItem(
                    name = "TorrServer Streaming Engine",
                    description = "Torrent streaming & memory-caching daemon for P2P playback",
                    sourceUrl = "https://github.com/YouROK/TorrServer",
                    sourceDisplayName = "github.com/YouROK/TorrServer",
                    installPath = torrBinary.getBinaryFile().absolutePath,
                    isInstalled = torrInstalled,
                    installedVersion = UnifiedUpdateManager.getTorrServerInstalledVersion(),
                    fileSizeMB = torrFileSize,
                    downloadTask = torrTask,
                    onDownloadClick = {
                        torrBinary.downloadWithManager {
                            torrInstalled = torrBinary.isInstalled()
                            torrFileSize = torrBinary.getFileSizeMB()
                            AppToastManager.showSuccess("TorrServer installed successfully")
                        }
                    },
                    onOpenFolderClick = {
                        try {
                            val dir = torrBinary.getBinaryFile().parentFile
                            if (dir.exists()) {
                                Desktop.getDesktop().open(dir)
                            } else {
                                dir.mkdirs()
                                Desktop.getDesktop().open(dir)
                            }
                        } catch (e: Exception) {
                            AppLogger.e("Failed to open folder", e)
                        }
                    },
                    onDeleteClick = {
                        showDeleteTorrConfirm = true
                    },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                // Package 2: yt-dlp Stream Resolver
                BinaryPackageItem(
                    name = "yt-dlp Stream Resolver",
                    description = "Command-line media extractor for web links and online video services",
                    sourceUrl = "https://github.com/yt-dlp/yt-dlp",
                    sourceDisplayName = "github.com/yt-dlp/yt-dlp",
                    installPath = ytdlBinary.getBinaryFile().absolutePath,
                    isInstalled = ytdlInstalled,
                    installedVersion = UnifiedUpdateManager.getYtDlpInstalledVersion(),
                    fileSizeMB = ytdlFileSize,
                    downloadTask = ytdlTask,
                    onDownloadClick = {
                        ytdlBinary.downloadWithManager {
                            ytdlInstalled = ytdlBinary.isInstalled()
                            ytdlFileSize = ytdlBinary.getFileSizeMB()
                            AppToastManager.showSuccess("yt-dlp installed successfully")
                        }
                    },
                    onOpenFolderClick = {
                        try {
                            val dir = ytdlBinary.getBinaryFile().parentFile
                            if (dir.exists()) {
                                Desktop.getDesktop().open(dir)
                            } else {
                                dir.mkdirs()
                                Desktop.getDesktop().open(dir)
                            }
                        } catch (e: Exception) {
                            AppLogger.e("Failed to open folder", e)
                        }
                    },
                    onDeleteClick = {
                        showDeleteYtdlConfirm = true
                    },
                )
            }
        }
    }

    // Delete Confirmation Dialogs (Using CloudstreamAlertDialog adhering to Rule 9)
    CloudstreamAlertDialog(
        show = showDeleteTorrConfirm,
        onDismissRequest = { showDeleteTorrConfirm = false },
        title = {
            Text(
                text = "Delete TorrServer Engine?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "This will delete the TorrServer executable from your disk (%AppData%/CloudStream/torrserver/bin/). Torrent streams will not be playable until re-downloaded.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    showDeleteTorrConfirm = false
                    scope.launch(Dispatchers.IO) {
                        val deleted = torrBinary.deleteBinary()
                        torrInstalled = torrBinary.isInstalled()
                        torrFileSize = torrBinary.getFileSizeMB()
                        if (deleted) {
                            AppToastManager.showInfo("TorrServer removed from disk")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete Binary", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { showDeleteTorrConfirm = false }) {
                Text("Cancel")
            }
        },
    )

    CloudstreamAlertDialog(
        show = showDeleteYtdlConfirm,
        onDismissRequest = { showDeleteYtdlConfirm = false },
        title = {
            Text(
                text = "Delete yt-dlp Resolver?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "This will delete the yt-dlp executable from your disk (%AppData%/CloudStream/ytdl/bin/). Web extractor streams will auto-download it again on demand.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    showDeleteYtdlConfirm = false
                    scope.launch(Dispatchers.IO) {
                        val deleted = ytdlBinary.deleteBinary()
                        ytdlInstalled = ytdlBinary.isInstalled()
                        ytdlFileSize = ytdlBinary.getFileSizeMB()
                        if (deleted) {
                            AppToastManager.showInfo("yt-dlp removed from disk")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete Binary", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { showDeleteYtdlConfirm = false }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun BinaryPackageItem(
    name: String,
    description: String,
    sourceUrl: String,
    sourceDisplayName: String,
    installPath: String,
    isInstalled: Boolean,
    installedVersion: String,
    fileSizeMB: Float,
    downloadTask: DownloadTask?,
    onDownloadClick: () -> Unit,
    onOpenFolderClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val isDownloading = downloadTask?.status == TaskStatus.RUNNING

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Source Transparency Link
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            try {
                                Desktop.getDesktop().browse(URI(sourceUrl))
                            } catch (_: Exception) {}
                        },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Source: $sourceDisplayName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Local Install Path
                Text(
                    text = "Path: $installPath",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    fontSize = 11.sp,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status Badge & Size
                if (isInstalled) {
                    Text(
                        text = "Status: Installed ($installedVersion) • ${String.format(Locale.ROOT, "%.1f", fileSizeMB)} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = "Status: Not Installed (0.0 MB)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Action Buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!isInstalled && !isDownloading) {
                    FilledTonalButton(
                        onClick = onDownloadClick,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download & Install", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (isInstalled && !isDownloading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onOpenFolderClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Folder", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onDeleteClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Live Download Progress Bar
        if (downloadTask?.status == TaskStatus.RUNNING) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Downloading... ${downloadTask.downloadedMB} / ${downloadTask.totalMB} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = downloadTask.speedFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (downloadTask.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { downloadTask.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsUpdatesScreen(viewModel: SettingsViewModel) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsUpdates(viewModel = viewModel)
    }
}
