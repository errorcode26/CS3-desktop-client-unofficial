package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Updates & Version",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Current Version: v${AppConfig.APP_VERSION}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (latestRelease != null) {
                    val release = latestRelease!!
                    Text(
                        text = "New Update Available: ${release.name}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Published at: ${release.published_at}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            try {
                                Desktop.getDesktop().browse(URI(release.html_url))
                            } catch (e: Exception) {
                                com.lagradost.common.logging.AppLogger.e("Error opening link ${release.html_url}", e)
                            }
                        }
                    ) {
                        Text("Download Update")
                    }
                } else {
                    Text(
                        text = "You are on the latest version.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isChecking = true
                        coroutineScope.launch {
                            AppUpdater.checkForUpdates(force = true)
                            isChecking = false
                        }
                    },
                    enabled = !isChecking
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Check for updates",
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                    )
                    Text(if (isChecking) "Checking..." else "Check for Updates")
                }
            }
        }
    }
}
