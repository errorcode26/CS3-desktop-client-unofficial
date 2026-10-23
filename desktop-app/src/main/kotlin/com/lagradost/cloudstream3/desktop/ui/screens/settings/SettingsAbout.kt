package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.cloudstream3.desktop.utils.appScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsAbout() {
    var copiedDiagnostics by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }

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
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "An independent, desktop-native client powered by the CloudStream core engine. For desktop support and bug reports, use our Discord community below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("v${AppConfig.APP_VERSION}") },
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Java ${System.getProperty("java.version", "JVM")}") },
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Windows x64") },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val diagnosticInfo = buildString {
                            appendLine("CloudStream Desktop Diagnostic Report")
                            appendLine("Version: ${AppConfig.APP_VERSION}")
                            appendLine("OS: ${System.getProperty("os.name")} (${System.getProperty("os.arch")})")
                            appendLine("Java Runtime: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
                            appendLine("Repository: https://github.com/${AppConfig.GITHUB_REPO}")
                        }
                        try {
                            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                java.awt.datatransfer.StringSelection(diagnosticInfo),
                                null,
                            )
                            copiedDiagnostics = true
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("SettingsAbout: Failed to copy diagnostic info", e)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = if (copiedDiagnostics) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (copiedDiagnostics) "Copied Diagnostic Info!" else "Copy Diagnostic Info")
                }
            }
        }

        SettingsGroupCard(title = "Desktop Community & Support") {
            SettingsNavigationItem(
                label = "Community Discord",
                subtitle = "Join Discord to report bugs or any issue.",
                onClick = { openUrl("https://discord.gg/cU6Fr7Wx4") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsNavigationItem(
                label = "Desktop Source Code",
                subtitle = "View repository, report desktop issues, and inspect release builds.",
                onClick = { openUrl("https://github.com/errorcode26/CS3-desktop-client-unofficial") },
            )
        }

        SettingsGroupCard(title = "Upstream Core & Documentation") {
            SettingsNavigationItem(
                label = "Upstream Android Project",
                subtitle = "The official Android app whose core library powers this desktop client.",
                onClick = { openUrl("https://github.com/recloudstream/cloudstream") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SettingsNavigationItem(
                label = "Wiki & Extension Docs",
                subtitle = "Read plugin development guides and extension APIs documentation.",
                onClick = { openUrl("https://recloudstream.github.io/csdocs/") },
            )
        }

        SettingsGroupCard(title = "Legal Notice & Disclaimers") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Gavel,
                                    contentDescription = "Legal Notice",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "LEGAL STATEMENT & NOMINATIVE FAIR USE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp,
                                )
                            }

                            TextButton(
                                onClick = {
                                    val fullLegalText = """
LEGAL STATEMENT & NOMINATIVE FAIR USE NOTICE

1. Pure Browser Shell & Zero Bundled Media
This software is strictly an open-source, media-neutral desktop browser shell, player harness, and extension sandbox. It does not host, own, create, index, scrape, transmit, cache, or distribute any media files, video streams, torrents, or content of any kind. Out of the box, this application contains no media catalog, no proprietary streaming sources, and zero content.

2. User-Provided Extensions & Third-Party Repositories
All plugins, extensions, addon manifests, external links, and media sources loaded into this application are configured and added entirely by the user at their sole discretion. The developers and contributors do not author, host, maintain, verify, control, or endorse any external repositories, plugins, or third-party streaming sources.

3. Trademarks, Logos & Nominative Identification
All product names, corporate names, logos, service marks, rating badges (e.g. IMDb, TMDB, MAL), and streaming platform identifiers displayed in this software are the intellectual property of their respective owners. Any reference to, or display of, these names, badges, or marks is strictly for descriptive and nominative identification purposes to indicate content categorization. Their display does NOT imply endorsement, sponsorship, affiliation, or partnership with any trademark holder.

4. DMCA & Content Concerns
CloudStream Desktop maintains zero content hosting, scraping, or media distribution infrastructure. Because all media sources originate from user-configured external extensions, copyright inquiries or DMCA notices regarding hosted streams must be directed to the third-party web hosts serving the respective content.

5. Sole User Responsibility & Regulatory Compliance
Users are exclusively and solely responsible for their use of this software. You must ensure that your access, playback, or usage of any media or third-party services complies fully with all applicable local, state, national, and international laws, copyright regulations, and intellectual property rights in your jurisdiction.

6. "As-Is" Software & Limitation of Liability
This software is provided "AS IS", without warranty of any kind, express or implied. Under no circumstances shall the authors, maintainers, or copyright holders be held liable for any claim, damages, copyright infringement, or legal consequences arising from the use, installation, or misuse of this software under GNU General Public License v3.0 (GPLv3).
                                    """.trimIndent()
                                    try {
                                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                            java.awt.datatransfer.StringSelection(fullLegalText),
                                            null,
                                        )
                                        copiedNotice = true
                                    } catch (e: Exception) {
                                        com.lagradost.common.logging.AppLogger.e("SettingsAbout: Failed to copy legal text", e)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (copiedNotice) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (copiedNotice) "Copied!" else "Copy Text",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Text(
                            text = "1. Pure Browser Shell & Zero Bundled Media\n" +
                                "This software is strictly an open-source, media-neutral desktop browser shell, player harness, and extension sandbox. It does NOT host, own, create, index, scrape, transmit, cache, or distribute any media files, video streams, torrents, or content of any kind. Out of the box, this application contains no media catalog, no proprietary streaming sources, and zero content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )

                        Text(
                            text = "2. User-Provided Extensions & Third-Party Repositories\n" +
                                "All plugins, extensions, addon manifests, external links, and media sources loaded into this application are configured and added entirely by the user at their own sole discretion. The developers and contributors do not author, host, maintain, verify, control, or endorse any external repositories, plugins, or third-party streaming sources.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )

                        Text(
                            text = "3. Trademarks, Logos & Nominative Identification\n" +
                                "All product names, company names, logos, service marks, rating badges, and platform identifiers displayed in this software are the intellectual property of their respective owners. Any reference to, or display of, these names or marks is strictly for descriptive and nominative identification purposes. Their display does NOT imply any affiliation, sponsorship, endorsement, or partnership.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )

                        Text(
                            text = "4. DMCA & Content Concerns\n" +
                                "CloudStream Desktop maintains zero content hosting or media distribution infrastructure. Because all media sources originate from user-configured external extensions, copyright inquiries or notices regarding hosted streams must be directed to the third-party web hosts serving the respective content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )

                        Text(
                            text = "5. Sole User Responsibility & Legal Compliance\n" +
                                "Users are exclusively and solely responsible for their use of this software. You must ensure that your access, playback, or usage of any media or third-party services complies fully with all applicable local, state, national, and international laws, copyright regulations, and intellectual property rights in your jurisdiction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )

                        Text(
                            text = "6. \"As-Is\" Software & Limitation of Liability\n" +
                                "This software is provided \"AS IS\", without warranty of any kind, express or implied. Under no circumstances shall the authors, maintainers, or copyright holders be held liable for any claim, damages, copyright infringement, or legal consequences arising from the use, installation, or misuse of this software under GNU General Public License v3.0 (GPLv3).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun openUrl(url: String) {
    appScope.launch(Dispatchers.IO) {
        try {
            val uri = java.net.URI(url)
            val desktop = java.awt.Desktop.getDesktop()
            desktop.browse(uri)
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("Error opening link $url", e)
        }
    }
}

@Composable
fun SettingsAboutScreen() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsAbout()
    }
}

@Composable
fun SettingsAboutAndUpdates(viewModel: SettingsViewModel) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsUpdates(viewModel = viewModel)
        SettingsAbout()
    }
}
