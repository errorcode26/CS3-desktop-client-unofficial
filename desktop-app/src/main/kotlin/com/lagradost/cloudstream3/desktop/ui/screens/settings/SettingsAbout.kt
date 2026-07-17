package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsAbout() {
    Column {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About CloudStream Desktop", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This is an UNOFFICIAL Desktop client. Please use the official CloudStream Android app for the best experience. " +
                        "This application does not ship with any media content or scrapers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Button(onClick = { openUrl("https://discord.gg/5Hus6fM") }) {
                        Text("Join Discord")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { openUrl("https://recloudstream.github.io/csdocs/") }) {
                        Text("CloudStream Wiki")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedButton(onClick = { openUrl("https://github.com/recloudstream/cloudstream") }) {
                        Text("Official Android Repo")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
