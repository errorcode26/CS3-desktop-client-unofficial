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

    CloudstreamCustomDialog(
        show = showUpdateDialog,
        onDismissRequest = { showUpdateDialog = false },
        modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(0.75f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Update Available",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = LocalContentColor.current,
                    )
                    Text(
                        "Version v${release.tag_name.removePrefix("v")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = LocalContentColor.current.copy(alpha = 0.1f))

            // Body / Changelog
            val changelogText = release.body ?: "No changelog provided."
            val lines = changelogText.lines()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
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

            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = LocalContentColor.current.copy(alpha = 0.1f))

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Ignore")
                }
                Spacer(modifier = Modifier.width(16.dp))
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
