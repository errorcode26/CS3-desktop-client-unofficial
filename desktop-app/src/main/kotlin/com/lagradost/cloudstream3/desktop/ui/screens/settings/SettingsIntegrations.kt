package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Metadata & Integrations Hub.
 * Features 3 high-level dashboard cards that drill down into dedicated Sub-Screens.
 */
@Composable
fun SettingsIntegrations(
    onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {},
) {
    val tmdbEnabled by MetadataConfig.tmdbEnabled.collectAsState()
    val customTmdbKey by MetadataConfig.customTmdbApiKey.collectAsState()
    val tmdbLanguage by MetadataConfig.tmdbLanguage.collectAsState()

    val anilistEnabled by MetadataConfig.anilistEnabled.collectAsState()
    val kitsuEnabled by MetadataConfig.kitsuEnabled.collectAsState()
    val animeTitleLang by MetadataConfig.animeTitleLanguage.collectAsState()
    val animePrimary by MetadataConfig.animePrimaryProvider.collectAsState()

    val stremioAddons by StremioAddonManager.addons.collectAsState()
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var inputManifestUrl by remember { mutableStateOf("") }
    var isInstallingManifest by remember { mutableStateOf(false) }

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
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Metadata & Integrations Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Manage native artwork engines, anime GraphQL providers, and community Stremio catalogs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 1. TMDB Engine Studio Card
        IntegrationHubCard(
            title = "The Movie Database (TMDB)",
            subtitle = "Provides backdrops, logos, actors, movie financials, TV networks, and trailers.",
            icon = Icons.Default.Movie,
            enabled = tmdbEnabled,
            onToggle = { MetadataConfig.setTmdbEnabled(it) },
            badges = listOf(
                if (customTmdbKey.isNotBlank()) "Custom API Key" else "Built-in API Key",
                "Lang: $tmdbLanguage",
            ),
            buttonLabel = "Configure TMDB Studio",
            onConfigureClick = { onNavigateToSubScreen(SettingsSubScreen.INTEGRATIONS_TMDB) },
        )

        // 2. Anime Engines Studio Card (AniList & Kitsu)
        val animeActive = anilistEnabled || kitsuEnabled
        IntegrationHubCard(
            title = "Anime Engines (AniList & Kitsu)",
            subtitle = "Japanese voice actors, character photos, studio badges, and simulcast air schedules.",
            icon = Icons.Default.Animation,
            enabled = animeActive,
            onToggle = {
                MetadataConfig.setAniListEnabled(it)
                MetadataConfig.setKitsuEnabled(it)
            },
            badges = listOf(
                "Primary: ${if (animePrimary == "anilist") "AniList" else "Kitsu"}",
                "Title: ${animeTitleLang.replaceFirstChar { it.uppercase() }}",
            ),
            buttonLabel = "Configure Anime Studio",
            onConfigureClick = { onNavigateToSubScreen(SettingsSubScreen.INTEGRATIONS_ANIME) },
        )

        // 3. Stremio Addons Ecosystem Card
        val metadataAddons = remember(stremioAddons) { stremioAddons.filter { it.providesMetadata } }
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stremio Community Addons",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Connect public community manifests (Cinemeta, CyberFlix, Torrentio, Anime Kitsu).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Quick Installer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = inputManifestUrl,
                        onValueChange = { inputManifestUrl = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("https://example.com/manifest.json", fontSize = 13.sp) },
                        label = { Text("Install Manifest URL") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                    Button(
                        onClick = {
                            val url = inputManifestUrl.trim()
                            if (url.isBlank()) return@Button
                            isInstallingManifest = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val result = StremioAddonManager.addAddon(url)
                                withContext(Dispatchers.Main) {
                                    isInstallingManifest = false
                                    if (result.isSuccess) {
                                        inputManifestUrl = ""
                                        AppToastManager.showSuccess("Stremio Addon installed successfully")
                                    } else {
                                        AppToastManager.showError("Failed to install: ${result.exceptionOrNull()?.message ?: "Invalid manifest"}")
                                    }
                                }
                            }
                        },
                        enabled = inputManifestUrl.isNotBlank() && !isInstallingManifest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(56.dp),
                    ) {
                        if (isInstallingManifest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Install")
                        }
                    }
                }

                // Status & Addons Manager Shortcut
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Active Addons: ${stremioAddons.size} installed (${metadataAddons.size} metadata resolvers)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    OutlinedButton(
                        onClick = { SettingsSession.selectedLeaf = LeafTab.ADDONS },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage All Addons", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Sub-Screen 1: Dedicated TMDB Studio.
 */
@Composable
fun SettingsTmdbScreen() {
    val tmdbEnabled by MetadataConfig.tmdbEnabled.collectAsState()
    val customTmdbKey by MetadataConfig.customTmdbApiKey.collectAsState()
    val tmdbLanguage by MetadataConfig.tmdbLanguage.collectAsState()
    val tmdbImageLanguage by MetadataConfig.tmdbImageLanguage.collectAsState()
    val tmdbIncludeAdult by MetadataConfig.tmdbIncludeAdult.collectAsState()
    val showFinancials by MetadataConfig.showFinancials.collectAsState()
    val separateNetworks by MetadataConfig.separateNetworks.collectAsState()
    val maxTrailers by MetadataConfig.maxTrailers.collectAsState()

    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()
    var editingTmdbKey by remember(customTmdbKey) { mutableStateOf(customTmdbKey) }

    val languages = listOf(
        "en-US" to "English (United States)",
        "es-ES" to "Español (España)",
        "es-MX" to "Español (Latinoamérica)",
        "fr-FR" to "Français (France)",
        "de-DE" to "Deutsch (Deutschland)",
        "it-IT" to "Italiano (Italia)",
        "pt-BR" to "Português (Brasil)",
        "ja-JP" to "日本語 (Japanese)",
        "ko-KR" to "한국어 (Korean)",
        "zh-CN" to "中文 (简体中文)",
        "ru-RU" to "Русский (Russian)",
        "hi-IN" to "हिन्दी (Hindi)",
        "ar-SA" to "العربية (Arabic)",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Master Toggle Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable TMDB Metadata Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Enriches movies and TV series with high-resolution backdrops, logos, cast, and trailers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = tmdbEnabled, onCheckedChange = { MetadataConfig.setTmdbEnabled(it) })
            }
        }

        if (tmdbEnabled) {
            // API Key Card
            SettingsGroupCard(title = "TMDB API Key") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editingTmdbKey,
                        onValueChange = { editingTmdbKey = it },
                        label = { Text("Custom V3 API Key (Optional)") },
                        placeholder = { Text("Leave blank to use CloudStream's high-speed default key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            if (editingTmdbKey != customTmdbKey) {
                                Button(
                                    onClick = {
                                        MetadataConfig.setCustomTmdbApiKey(editingTmdbKey)
                                        AppToastManager.showSuccess("TMDB API Key updated")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 4.dp),
                                ) {
                                    Text("Save", fontSize = 12.sp)
                                }
                            }
                        },
                    )
                    Text(
                        text = if (customTmdbKey.isBlank()) "✓ Using built-in high-speed TMDB API key." else "✓ Using your custom user TMDB API key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Localization Card
            SettingsGroupCard(title = "Localization & Artwork") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Metadata Language
                    SettingDropdownRow(
                        label = "Overview & Title Language",
                        subtitle = "Preferred language for plot summaries, taglines, and episode titles.",
                        options = languages,
                        currentValue = tmdbLanguage,
                        onSelectionChanged = { MetadataConfig.setTmdbLanguage(it) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Artwork Language Priority
                    SettingDropdownRow(
                        label = "Poster & Backdrop Language Priority",
                        subtitle = "Order of preference for poster and backdrop image languages.",
                        options = listOf(
                            "en,en-US,null" to "International English (en, en-US, no text)",
                            "null,en" to "Clean Textless Artwork First (null, en)",
                            "auto" to "Match Localized Metadata Language",
                        ),
                        currentValue = tmdbImageLanguage,
                        onSelectionChanged = { MetadataConfig.setTmdbImageLanguage(it) },
                    )
                }
            }

            // Display Features Card
            SettingsGroupCard(title = "Display & Details Features") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingToggleRow(
                        label = "Show Movie Financials (Budget & Box Office)",
                        subtitle = "Display production budget and worldwide box office earnings in the hero specs.",
                        checked = showFinancials,
                        onCheckedChange = { MetadataConfig.setShowFinancials(it) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingToggleRow(
                        label = "Separate TV Networks & Production Studios",
                        subtitle = "Split broadcaster logos (HBO, Netflix, Apple TV+) from production companies.",
                        checked = separateNetworks,
                        onCheckedChange = { MetadataConfig.setSeparateNetworks(it) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingToggleRow(
                        label = "Include Adult (18+) Content in TMDB Search",
                        subtitle = "Allows matching against 18+ adult titles in TMDB catalog queries.",
                        checked = tmdbIncludeAdult,
                        onCheckedChange = { MetadataConfig.setTmdbIncludeAdult(it) },
                    )
                }
            }

            // TVmaze Secondary TV Engine Card
            val tvmazeEnabled by MetadataConfig.tvmazeEnabled.collectAsState()
            SettingsGroupCard(title = "TVmaze TV Engine (Precision Schedules & Fallback)") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingToggleRow(
                        label = "TVmaze TV Series Metadata",
                        subtitle = "Zero-key secondary engine. Enriches streaming web channels (Prime Video, Apple TV+), exact upcoming air dates, and missing episode stills.",
                        checked = tvmazeEnabled,
                        onCheckedChange = { MetadataConfig.setTvmazeEnabled(it) },
                    )
                }
            }

            // Media & Trailers Card
            SettingsGroupCard(title = "Video Trailers & Clips") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    SettingDropdownRow(
                        label = "Maximum Trailers to Fetch",
                        subtitle = "Limits the number of YouTube video clips loaded to keep details instant.",
                        options = listOf(
                            5 to "5 Trailers & Teasers",
                            10 to "10 Trailers & Clips (Recommended)",
                            15 to "15 Video Clips",
                            25 to "25 Video Clips (Deep Library)",
                        ),
                        currentValue = maxTrailers,
                        onSelectionChanged = { MetadataConfig.setMaxTrailers(it) },
                    )
                }
            }
        }
    }
}

/**
 * Sub-Screen 2: Dedicated Anime Engines Studio (AniList & Kitsu).
 */
@Composable
fun SettingsAnimeScreen() {
    val anilistEnabled by MetadataConfig.anilistEnabled.collectAsState()
    val kitsuEnabled by MetadataConfig.kitsuEnabled.collectAsState()
    val animeTitleLang by MetadataConfig.animeTitleLanguage.collectAsState()
    val animeVoiceCast by MetadataConfig.animeVoiceCast.collectAsState()
    val animeSimulcast by MetadataConfig.animeSimulcast.collectAsState()
    val animeStudios by MetadataConfig.animeStudios.collectAsState()
    val animePrimary by MetadataConfig.animePrimaryProvider.collectAsState()

    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Engine Priority & Providers Card
        SettingsGroupCard(title = "Anime Metadata Providers") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingToggleRow(
                    label = "AniList (Official GraphQL Engine)",
                    subtitle = "High-res banner artwork, Japanese voice actors with photos, and simulcast schedules.",
                    checked = anilistEnabled,
                    onCheckedChange = { MetadataConfig.setAniListEnabled(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                SettingToggleRow(
                    label = "Kitsu (REST API Fallback Engine)",
                    subtitle = "Secondary anime catalog for Romaji and alternative English title resolution.",
                    checked = kitsuEnabled,
                    onCheckedChange = { MetadataConfig.setKitsuEnabled(it) },
                )

                if (anilistEnabled && kitsuEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingDropdownRow(
                        label = "Primary Anime Metadata Engine",
                        subtitle = "Select which engine resolves anime metadata first.",
                        options = listOf(
                            "anilist" to "AniList (GraphQL) — Recommended",
                            "kitsu" to "Kitsu (REST API)",
                        ),
                        currentValue = animePrimary,
                        onSelectionChanged = { MetadataConfig.setAnimePrimaryProvider(it) },
                    )
                }
            }
        }

        // Title Naming Preferences
        SettingsGroupCard(title = "Anime Title Naming") {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SettingDropdownRow(
                    label = "Preferred Title Language",
                    subtitle = "Format used for anime series titles across details and player.",
                    options = listOf(
                        "romaji" to "Romaji (e.g. Sousou no Frieren, Kimetsu no Yaiba)",
                        "english" to "English (e.g. Frieren: Beyond Journey's End, Demon Slayer)",
                        "native" to "Native Japanese (e.g. 葬送のフリーレン, 鬼滅の刃)",
                    ),
                    currentValue = animeTitleLang,
                    onSelectionChanged = { MetadataConfig.setAnimeTitleLanguage(it) },
                )
            }
        }

        // Anime Rich Metadata Features
        SettingsGroupCard(title = "Anime Rich Features") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingToggleRow(
                    label = "Japanese Voice Cast & Character Photos",
                    subtitle = "Display anime character portraits linked with Japanese voice actor cards.",
                    checked = animeVoiceCast,
                    onCheckedChange = { MetadataConfig.setAnimeVoiceCast(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                SettingToggleRow(
                    label = "Simulcast Schedules & Airing Countdowns",
                    subtitle = "Synthesize locked episode cards with real-time countdowns for airing seasonal anime.",
                    checked = animeSimulcast,
                    onCheckedChange = { MetadataConfig.setAnimeSimulcast(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                SettingToggleRow(
                    label = "Animation Studio Badges",
                    subtitle = "Display official animation studio badges (MAPPA, Ufotable, Madhouse, Bones).",
                    checked = animeStudios,
                    onCheckedChange = { MetadataConfig.setAnimeStudios(it) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Component Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IntegrationHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    badges: List<String>,
    buttonLabel: String,
    onConfigureClick: () -> Unit,
) {
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    badges.forEach { badge ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Button(
                    onClick = onConfigureClick,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(buttonLabel, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> SettingDropdownRow(
    label: String,
    subtitle: String,
    options: List<Pair<T, String>>,
    currentValue: T,
    onSelectionChanged: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = options.find { it.first == currentValue }?.second ?: currentValue.toString(),
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, name) ->
                    DropdownMenuItem(
                        text = { Text(name, fontSize = 13.sp, fontWeight = if (value == currentValue) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onSelectionChanged(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
