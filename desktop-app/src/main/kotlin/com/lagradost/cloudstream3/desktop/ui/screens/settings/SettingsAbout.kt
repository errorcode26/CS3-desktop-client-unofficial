package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.AppConfig

@Composable
fun SettingsAbout() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsGroupCard(title = "CloudStream Desktop") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                ) {
                    Text(
                        "UNOFFICIAL DESKTOP CLIENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Independent, desktop-native streaming client for Windows, macOS, and Linux.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        SettingsGroupCard(title = "Desktop Source & Development") {
            SettingsNavigationItem(
                label = "Desktop Source Code",
                subtitle = "View repository, report desktop issues, and inspect release builds.",
                onClick = { openUrl("https://github.com/errorcode26/CS3-desktop-client-unofficial") },
            )
        }

        SettingsGroupCard(title = "Upstream Community & Documentation") {
            SettingsNavigationItem(
                label = "Official Android Repository",
                subtitle = "View upstream CloudStream core source code and releases.",
                onClick = { openUrl("https://github.com/recloudstream/cloudstream") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsNavigationItem(
                label = "Community Discord",
                subtitle = "Join the community Discord server for discussions and announcements.",
                onClick = { openUrl("https://discord.gg/5Hus6fM") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsNavigationItem(
                label = "Wiki & Extension Docs",
                subtitle = "Read plugin development guides and extension APIs documentation.",
                onClick = { openUrl("https://recloudstream.github.io/csdocs/") },
            )
        }

        SettingsGroupCard(title = "Legal & Disclaimer") {
            Text(
                "This application is a media browser shell and does not host, scrape, or distribute media content directly. All metadata is provided by third-party APIs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            )
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

@Composable
fun SettingsAboutAndUpdates() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsUpdates()
        SettingsAbout()
    }
}
