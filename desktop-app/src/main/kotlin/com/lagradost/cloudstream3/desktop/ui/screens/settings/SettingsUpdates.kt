package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.cloudstream3.desktop.AppUpdater
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

@Composable
fun SettingsUpdates() {
    val coroutineScope = rememberCoroutineScope()
    val latestRelease by AppUpdater.latestRelease.collectAsState()
    var isChecking by remember { mutableStateOf(false) }
    var showCheckedFeedback by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsGroupCard(title = "Updates & Version") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Installed Version",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "v${AppConfig.APP_VERSION}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            isChecking = true
                            showCheckedFeedback = false
                            coroutineScope.launch {
                                AppUpdater.checkForUpdates(force = true)
                                isChecking = false
                                if (AppUpdater.latestRelease.value == null) {
                                    showCheckedFeedback = true
                                }
                            }
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

                if (latestRelease != null) {
                    val release = latestRelease!!
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
                                    text = "Update Available: ${release.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Published: ${release.published_at}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Button(
                                onClick = {
                                    try {
                                        Desktop.getDesktop().browse(URI(release.html_url))
                                    } catch (e: Exception) {
                                        com.lagradost.common.logging.AppLogger.e("Error opening link ${release.html_url}", e)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (showCheckedFeedback) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "You are on the latest version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
