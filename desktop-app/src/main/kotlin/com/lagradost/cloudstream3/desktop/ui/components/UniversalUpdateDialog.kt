package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.download.TaskStatus
import com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager
import com.lagradost.cloudstream3.desktop.updates.UpdateType
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

@Composable
fun UniversalUpdateDialog() {
    val scope = rememberCoroutineScope()
    val update by UnifiedUpdateManager.activeDialogUpdate.collectAsState()
    val activeUpdate = update ?: return

    val downloadTasks by AppDownloadManager.tasks.collectAsState()
    val appTask = if (activeUpdate.type == UpdateType.APP_CLIENT) {
        downloadTasks.firstOrNull { it.id == "app_update" }
    } else {
        null
    }

    val isDownloaded = remember(activeUpdate, appTask?.status) {
        activeUpdate.type == UpdateType.APP_CLIENT && UnifiedUpdateManager.isAppInstallerDownloaded(activeUpdate)
    }

    val displayVersion = remember(activeUpdate.newVersion) {
        if (activeUpdate.newVersion.startsWith("v", ignoreCase = true)) {
            activeUpdate.newVersion
        } else {
            "v${activeUpdate.newVersion}"
        }
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = { UnifiedUpdateManager.dismissDialog() },
        modifier = Modifier
            .width(680.dp)
            .wrapContentHeight()
            .heightIn(max = 580.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (activeUpdate.type) {
                                    UpdateType.APP_CLIENT -> Icons.Default.SystemUpdate
                                    UpdateType.TORRENT_ENGINE -> Icons.Default.Download
                                    UpdateType.STREAM_RESOLVER -> Icons.Default.Download
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${activeUpdate.title} Update",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = LocalContentColor.current,
                                maxLines = 1,
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = displayVersion,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            "Installed: ${activeUpdate.currentVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.65f),
                        )
                    }
                }

                if (activeUpdate.releaseHtmlUrl != null) {
                    OutlinedButton(
                        onClick = {
                            try {
                                Desktop.getDesktop().browse(URI(activeUpdate.releaseHtmlUrl))
                            } catch (e: Exception) {
                                AppLogger.e("Failed to open release URL", e)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "View on GitHub",
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View on GitHub", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                color = LocalContentColor.current.copy(alpha = 0.08f),
            )

            // Dynamic Download / Installation Status Card
            if (appTask != null && appTask.status == TaskStatus.RUNNING) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 10.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Downloading Setup Installer...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${appTask.percent}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (appTask.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { appTask.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${appTask.downloadedMB} / ${appTask.totalMB}  •  ${appTask.speedFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (appTask.etaFormatted.isNotBlank()) {
                                Text(
                                    appTask.etaFormatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else if (isDownloaded) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                "Installer Downloaded & Ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "CloudStream will close automatically so the installer can update your files without interruption.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (appTask != null && appTask.status == TaskStatus.FAILED) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                "Download Interrupted",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                appTask.errorMessage ?: "Network connection interrupted. You can retry or download directly from GitHub.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // Body / Changelog Section
            val changelogText = activeUpdate.releaseNotes ?: "No release notes provided."
            val lines = changelogText.lines()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            ) {
                Text(
                    "What's New",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp, max = 260.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(lines, key = { index, line -> "$index-${line.hashCode()}" }) { _, line ->
                            if (line.isBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            } else if (line.startsWith("#")) {
                                val headerText = line.trimStart('#').trim()
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalContentColor.current,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                )
                            } else {
                                Text(
                                    text = parseBasicMarkdown(line),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.9f),
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                color = LocalContentColor.current.copy(alpha = 0.08f),
            )

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            UnifiedUpdateManager.ignoreUpdate(activeUpdate)
                            AppToastManager.showInfo("Skipped ${activeUpdate.title} ${activeUpdate.newVersion}")
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Skip this version",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        isDownloaded && activeUpdate.type == UpdateType.APP_CLIENT -> {
                            OutlinedButton(
                                onClick = {
                                    val file = UnifiedUpdateManager.getAppInstallerFile(activeUpdate.newVersion)
                                    try {
                                        ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                                    } catch (_: Exception) {
                                        Desktop.getDesktop().open(file.parentFile)
                                    }
                                },
                            ) {
                                Text("Open in Folder")
                            }
                            Button(
                                onClick = {
                                    val file = UnifiedUpdateManager.getAppInstallerFile(activeUpdate.newVersion)
                                    UnifiedUpdateManager.launchInstallerAndExit(file)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install & Restart")
                            }
                        }

                        appTask != null && appTask.status == TaskStatus.RUNNING -> {
                            TextButton(
                                onClick = {
                                    AppDownloadManager.cancelDownload("app_update")
                                },
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    UnifiedUpdateManager.dismissDialog()
                                },
                            ) {
                                Text("Background")
                            }
                        }

                        appTask != null && appTask.status == TaskStatus.FAILED -> {
                            TextButton(onClick = { UnifiedUpdateManager.dismissDialog() }) {
                                Text("Close")
                            }
                            Button(
                                onClick = {
                                    UnifiedUpdateManager.downloadAppUpdate(activeUpdate)
                                },
                            ) {
                                Text("Retry Download")
                            }
                        }

                        else -> {
                            TextButton(onClick = { UnifiedUpdateManager.dismissDialog() }) {
                                Text("Later")
                            }
                            Button(onClick = { UnifiedUpdateManager.installUpdate(activeUpdate) }) {
                                Text(
                                    when (activeUpdate.type) {
                                        UpdateType.APP_CLIENT -> "Download Update"
                                        UpdateType.TORRENT_ENGINE -> "Update Engine Now"
                                        UpdateType.STREAM_RESOLVER -> "Update Resolver Now"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val BOLD_REGEX = "\\*\\*(.*?)\\*\\*".toRegex()

internal fun parseBasicMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val matches = BOLD_REGEX.findAll(text)

        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
