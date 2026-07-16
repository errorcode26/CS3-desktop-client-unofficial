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
                        "Follow their socials below for more information.\n\n" +
                        "PRE-ALPHA BUILD: This software is provided 'as is'. We do not guarantee that any features will work correctly, " +
                        "and there is no guarantee of future updates or ongoing maintenance.\n\n" +
                        "This application does not ship with any media content or scrapers. By using this software, you agree that you are solely " +
                        "responsible for the third-party extensions downloaded and the networks you connect to.",
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

                Text("TMDB API Attribution", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Legal & DMCA Disclaimer", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "CloudStream Desktop acts strictly as a neutral web-scraping client and video player framework. " +
                        "The developers of this application do NOT host, index, upload, distribute, or control any media files or streams. " +
                        "We hold zero liability for the actions of users or the capabilities of third-party extensions. " +
                        "All DMCA takedown requests must be directed to the actual third-party websites and servers hosting the copyrighted material. " +
                        "This software is provided 'as is', without warranty of any kind.",
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
