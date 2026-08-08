package com.lagradost.cloudstream3.desktop.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

object ExternalLinkHandler {
    fun isExternalBrowserAllowed(): Boolean {
        return DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER) ?: true
    }

    fun copyToClipboard(text: String): Boolean {
        return try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            true
        } catch (e: Exception) {
            AppLogger.e("ExternalLinkHandler", "Failed to copy to clipboard: ${e.message}")
            false
        }
    }

    fun launchSystemBrowser(url: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
                true
            } else {
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("win") -> Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url))
                    os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", url))
                    os.contains("nix") || os.contains("nux") -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
                }
                true
            }
        } catch (e: Exception) {
            AppLogger.e("ExternalLinkHandler", "Failed to open link '$url' in browser: ${e.message}")
            copyToClipboard(url)
            false
        }
    }

    fun openOrPrompt(url: String, onPromptNeeded: (String) -> Unit) {
        if (isExternalBrowserAllowed()) {
            launchSystemBrowser(url)
        } else {
            onPromptNeeded(url)
        }
    }
}

@Composable
fun ExternalLinkConfirmationDialog(
    url: String?,
    onDismiss: () -> Unit,
) {
    if (url.isNullOrBlank()) return

    CloudstreamAlertDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "External Link",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Opening external links is disabled in Settings. Choose how you would like to handle this link:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        ExternalLinkHandler.copyToClipboard(url)
                        onDismiss()
                    },
                ) {
                    Text("Copy Link")
                }
                Button(
                    onClick = {
                        ExternalLinkHandler.launchSystemBrowser(url)
                        onDismiss()
                    },
                ) {
                    Text("Open Once")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
