package com.lagradost.cloudstream3.desktop.init

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.AppUpdater
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
@Composable
fun launchPeriodicPluginUpdater() {
    LaunchedEffect(Unit) {
        // Check for plugin updates early in the boot sequence and periodically thereafter
        delay(45_000L)
        DesktopRepositoryManager.autoUpdatePlugins(force = true)

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
    val scope = rememberCoroutineScope()

    CloudstreamCustomDialog(
        show = showUpdateDialog,
        onDismissRequest = { showUpdateDialog = false },
        modifier = Modifier.width(460.dp).wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Update Available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalContentColor.current,
                    )
                    Text(
                        "Version v${release.tag_name.removePrefix("v")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = LocalContentColor.current.copy(alpha = 0.1f))

            // Body / Changelog
            val changelogText = release.body ?: "No changelog provided."
            val lines = changelogText.lines()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 180.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "What's New",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(lines) { line ->
                    if (line.isBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    } else if (line.startsWith("#")) {
                        val headerText = line.trimStart('#').trim()
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LocalContentColor.current,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    } else {
                        Text(
                            text = parseBasicMarkdown(line),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.9f),
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = LocalContentColor.current.copy(alpha = 0.1f))

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        AppUpdater.ignoreRelease(release.tag_name)
                    }
                    showUpdateDialog = false
                }) {
                    Text(
                        "Don't show again",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Ignore")
                    }
                    Button(onClick = {
                        try {
                            Desktop.getDesktop().browse(URI(release.html_url))
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("Failed to open update URL", e)
                        }
                        showUpdateDialog = false
                    }) {
                        Text("Download Update")
                    }
                }
            }
        }
    }
}

fun parseBasicMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val boldRegex = "\\*\\*(.*?)\\*\\*".toRegex()
        val matches = boldRegex.findAll(text)

        for (match in matches) {
            // Append text before bold
            append(text.substring(currentIndex, match.range.first))
            // Append bold text
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        // Append remaining text
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
