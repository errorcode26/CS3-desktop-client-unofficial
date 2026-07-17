package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsAbout() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header Info
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "CloudStream Desktop",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        "UNOFFICIAL PRE-ALPHA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "This is an unofficial desktop port in early pre-alpha. It is actively being developed. Expect bugs and missing features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Desktop Source
        Text(
            "This Application",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
        )
        ListItem(
            headlineContent = { Text("Desktop Source Code") },
            supportingContent = { Text("View the GitHub repository for this unofficial Windows port.") },
            modifier = Modifier.clickable { openUrl("https://github.com/errorcode26/CS3-desktop-client-unofficial") }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))

        // Official Links
        Text(
            "The Official Project",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
        )
        ListItem(
            headlineContent = { Text("Official Android App") },
            supportingContent = { Text("View the official CloudStream 3 repository for Android.") },
            modifier = Modifier.clickable { openUrl("https://github.com/recloudstream/cloudstream") }
        )
        ListItem(
            headlineContent = { Text("Official Discord") },
            supportingContent = { Text("Join the official community Discord server.") },
            modifier = Modifier.clickable { openUrl("https://discord.gg/5Hus6fM") }
        )
        ListItem(
            headlineContent = { Text("Wiki & Documentation") },
            supportingContent = { Text("Read the official guides and extension developer wiki.") },
            modifier = Modifier.clickable { openUrl("https://recloudstream.github.io/csdocs/") }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
        
        Text(
            "Disclaimer: This application does not ship with any media content or scrapers. This product uses the TMDB API but is not endorsed or certified by TMDB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun openUrl(url: String) {
    try {
        val uri = java.net.URI(url)
        val desktop = java.awt.Desktop.getDesktop()
        desktop.browse(uri)
    } catch (e: Exception) {
        com.lagradost.common.logging.AppLogger.e("Error opening link $url", e)
    }
}
