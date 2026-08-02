package com.lagradost.cloudstream3.desktop.init

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.AppUpdater
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.net.URI

@Composable
fun launchPeriodicPluginUpdater() {
    LaunchedEffect(Unit) {
        while (true) {
            delay(30 * 60 * 1000L) // 30 minutes
            DesktopRepositoryManager.autoUpdatePlugins()
        }
    }
}

@Composable
fun AppUpdateDialog() {
    val latestRelease by AppUpdater.latestRelease.collectAsState()
    val release = latestRelease ?: return
    var showUpdateDialog by remember { mutableStateOf(true) }

    com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
        show = showUpdateDialog,
        onDismissRequest = { showUpdateDialog = false },
        title = { Text("Update Available: v${release.tag_name.removePrefix("v")}", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("A new version of CloudStream Desktop is available!", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(release.body ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 10)
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    Desktop.getDesktop().browse(URI(release.html_url))
                } catch (e: Exception) {
                    com.lagradost.common.logging.AppLogger.e("Failed to open update URL", e)
                }
                showUpdateDialog = false
            }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = { showUpdateDialog = false }) {
                Text("Ignore")
            }
        },
    )
}
