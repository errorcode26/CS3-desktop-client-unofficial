package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.metadata.MetadataConfig
import com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsIntegrations() {
    val tmdbEnabled by MetadataConfig.tmdbEnabled.collectAsState()
    val customTmdbKey by MetadataConfig.customTmdbApiKey.collectAsState()
    val anilistEnabled by MetadataConfig.anilistEnabled.collectAsState()
    val kitsuEnabled by MetadataConfig.kitsuEnabled.collectAsState()

    val skipIntervalsEnabled by MetadataConfig.skipIntervalsEnabled.collectAsState()
    val autoSkipIntro by MetadataConfig.autoSkipIntro.collectAsState()
    val autoSkipOutro by MetadataConfig.autoSkipOutro.collectAsState()

    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var editingTmdbKey by remember(customTmdbKey) { mutableStateOf(customTmdbKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Integrations & Services",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Configure external metadata providers, custom Stremio addons, API credentials, and automated opening & ending skip services.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Section 1: Metadata Providers
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Metadata & Artwork Providers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )

            // TMDB Card
            IntegrationServiceCard(
                title = "The Movie Database (TMDB)",
                tag = "Primary: Movies & Series",
                description = "Provides studio-grade 4K backdrops, official transparent logos, cast credits, reviews, and IMDb external IDs.",
                enabled = tmdbEnabled,
                onToggle = { MetadataConfig.setTmdbEnabled(it) },
                icon = Icons.Default.Movie,
                tagColor = Color(0xFF01B4E4),
                uiCardOpacity = uiCardOpacity,
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = editingTmdbKey,
                        onValueChange = { editingTmdbKey = it },
                        label = { Text("Custom TMDB API Key (Optional)") },
                        placeholder = { Text("Leave empty to use built-in key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (editingTmdbKey != customTmdbKey) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            MetadataConfig.setCustomTmdbApiKey(editingTmdbKey)
                                        }
                                    },
                                ) {
                                    Text("Save", fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                    )
                    Text(
                        text = if (customTmdbKey.isBlank()) "Status: Using default built-in API key." else "Status: Using custom user API key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }

            // AniList Card
            IntegrationServiceCard(
                title = "AniList",
                tag = "Primary: Anime & Manga",
                description = "Official anime GraphQL provider delivering voice cast, character artwork, production studios, and MAL ID matching.",
                enabled = anilistEnabled,
                onToggle = { MetadataConfig.setAniListEnabled(it) },
                icon = Icons.Default.AutoAwesome,
                tagColor = Color(0xFF02A9FF),
                uiCardOpacity = uiCardOpacity,
            )

            // Kitsu Card
            IntegrationServiceCard(
                title = "Kitsu",
                tag = "Secondary: Anime Backup",
                description = "Fast fallback anime metadata provider for synopses, age ratings, and poster art.",
                enabled = kitsuEnabled,
                onToggle = { MetadataConfig.setKitsuEnabled(it) },
                icon = Icons.Default.Public,
                tagColor = Color(0xFFF75239),
                uiCardOpacity = uiCardOpacity,
            )
        }

        // Section 2: Custom Stremio Addon
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Custom Stremio Metadata Addon",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "External Addons (Subtitles & Metadata)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Install and manage multi-source subtitle providers and metadata resolvers from the dedicated External Addons hub.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = { SettingsSession.selectedLeaf = LeafTab.ADDONS },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage Addons")
                    }
                }
            }
        }

        // Section 3: Intro & Outro Skipping
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Intro & Outro Skipping",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (skipIntervalsEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Enable Intro & Outro Detection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Discovers anime openings & endings via AniSkip, TV show intros via IntroDB, and embedded chapter markers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = skipIntervalsEnabled,
                            onCheckedChange = { MetadataConfig.setSkipIntervalsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }

                    AnimatedVisibility(visible = skipIntervalsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        text = "Auto-Skip Openings & Intros",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Automatically jumps past intros without requiring a manual button click.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = autoSkipIntro,
                                    onCheckedChange = { MetadataConfig.setAutoSkipIntro(it) },
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        text = "Auto-Skip Endings & Outros",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Automatically jumps past ending credit sequences.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = autoSkipOutro,
                                    onCheckedChange = { MetadataConfig.setAutoSkipOutro(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrationServiceCard(
    title: String,
    tag: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: ImageVector,
    tagColor: Color,
    uiCardOpacity: Float,
    extraContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tagColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tagColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tagColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = tag,
                                color = tagColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            extraContent?.invoke()
        }
    }
}
