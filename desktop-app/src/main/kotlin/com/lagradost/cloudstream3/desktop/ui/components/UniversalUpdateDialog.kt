package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager
import com.lagradost.cloudstream3.desktop.updates.UpdateType

@Composable
fun UniversalUpdateDialog() {
    val update by UnifiedUpdateManager.activeDialogUpdate.collectAsState()
    val activeUpdate = update ?: return

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = { UnifiedUpdateManager.dismissDialog() },
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
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
                            imageVector = when (activeUpdate.type) {
                                UpdateType.APP_CLIENT -> Icons.Default.SystemUpdate
                                UpdateType.TORRENT_ENGINE -> Icons.Default.Download
                                UpdateType.STREAM_RESOLVER -> Icons.Default.Download
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "${activeUpdate.title} Update",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = LocalContentColor.current,
                    )
                    Text(
                        "New version: ${activeUpdate.newVersion} (Installed: ${activeUpdate.currentVersion})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = LocalContentColor.current.copy(alpha = 0.1f),
            )

            // Body / Changelog
            val changelogText = activeUpdate.releaseNotes ?: "No release notes provided."
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
                itemsIndexed(lines, key = { index, line -> "$index-${line.hashCode()}" }) { _, line ->
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

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = LocalContentColor.current.copy(alpha = 0.1f),
            )

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { UnifiedUpdateManager.dismissDialog() }) {
                    Text("Later")
                }
                Spacer(modifier = Modifier.width(16.dp))
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
