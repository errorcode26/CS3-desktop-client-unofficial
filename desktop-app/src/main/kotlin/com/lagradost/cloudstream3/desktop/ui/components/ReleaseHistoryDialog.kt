package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.cloudstream3.desktop.updates.GitHubApiRelease
import com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

@Composable
fun ReleaseHistoryDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    val scope = rememberCoroutineScope()
    val releaseHistory by UnifiedUpdateManager.releaseHistory.collectAsState()
    val isFetching by UnifiedUpdateManager.isFetchingHistory.collectAsState()

    var pendingRollbackRelease by remember { mutableStateOf<GitHubApiRelease?>(null) }
    var expandedReleaseTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (releaseHistory.isEmpty()) {
            UnifiedUpdateManager.fetchReleaseHistory()
        }
    }

    val currentVersion = remember { AppConfig.APP_VERSION }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .fillMaxHeight(0.85f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Release History & Rollback",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Browse official GitHub releases. Rollback to any prior release or update forward.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                UnifiedUpdateManager.fetchReleaseHistory(force = true)
                            }
                        },
                        enabled = !isFetching,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh releases",
                        )
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(16.dp))

            // Body
            if (isFetching && releaseHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            "Fetching releases from GitHub...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (releaseHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Could not retrieve release history from GitHub.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    UnifiedUpdateManager.fetchReleaseHistory(force = true)
                                }
                            },
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(releaseHistory, key = { it.tag_name }) { release ->
                        val remoteVersion = release.tag_name.removePrefix("v").substringBefore('-')
                        val localVersion = currentVersion.removePrefix("v").substringBefore('-')
                        val isCurrent = remoteVersion == localVersion || release.tag_name.equals("v$currentVersion", ignoreCase = true)
                        val semverCmp = UnifiedUpdateManager.compareSemVer(remoteVersion, localVersion)
                        val isNewer = semverCmp > 0
                        val isOlder = semverCmp < 0
                        val isExpanded = expandedReleaseTag == release.tag_name

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                isNewer -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    isNewer -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = release.name.ifBlank { release.tag_name },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )

                                            // Badge
                                            when {
                                                isCurrent -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)),
                                                    ) {
                                                        Text(
                                                            text = "INSTALLED",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2E7D32),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                                isNewer -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                                    ) {
                                                        Text(
                                                            text = "NEWER",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                                isOlder -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                    ) {
                                                        Text(
                                                            text = "PREVIOUS",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Tag: ${release.tag_name} • Published: ${release.published_at.substringBefore('T')}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    // Action buttons
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    Desktop.getDesktop().browse(URI(release.html_url))
                                                } catch (e: Exception) {
                                                    AppLogger.e("Failed to open release on GitHub", e)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "View on GitHub",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        when {
                                            isCurrent -> {
                                                FilledTonalButton(
                                                    onClick = {},
                                                    enabled = false,
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Current", fontSize = 13.sp)
                                                }
                                            }
                                            isNewer -> {
                                                Button(
                                                    onClick = {
                                                        UnifiedUpdateManager.installRelease(release)
                                                        onDismissRequest()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Update", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            isOlder -> {
                                                OutlinedButton(
                                                    onClick = {
                                                        pendingRollbackRelease = release
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.error,
                                                    ),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                                ) {
                                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Rollback", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Release Notes expander
                                if (!release.body.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                expandedReleaseTag = if (isExpanded) null else release.tag_name
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isExpanded) "Hide release notes" else "Show release notes",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        ) {
                                            Text(
                                                text = release.body,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 18.sp,
                                                modifier = Modifier.padding(12.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rollback Confirmation Dialog
    if (pendingRollbackRelease != null) {
        val targetRelease = pendingRollbackRelease!!
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { pendingRollbackRelease = null },
            title = {
                Text(
                    text = "Confirm Version Rollback",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to rollback to CloudStream Desktop ${targetRelease.tag_name}?\n\nThis will download the installer for ${targetRelease.tag_name} and launch it to replace the current version. Your settings, bookmarks, and watch history in AppData will be preserved.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        UnifiedUpdateManager.installRelease(targetRelease)
                        pendingRollbackRelease = null
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Download & Rollback", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRollbackRelease = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
