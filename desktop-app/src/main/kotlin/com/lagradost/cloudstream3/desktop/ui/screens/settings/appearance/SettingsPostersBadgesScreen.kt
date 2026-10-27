package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsDropdownItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSliderItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSubScreen
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.PosterMetadataPosition
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTextAlignment

@Composable
fun SettingsPostersBadgesScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) {
    val cleanModeEnabled by AppearanceConfig.cleanModeEnabled.collectAsState()
    val hideProviderNames by AppearanceConfig.hideProviderNames.collectAsState()
    val hideDetailsSource by AppearanceConfig.hideDetailsSource.collectAsState()
    val hideStreamProviders by AppearanceConfig.hideStreamProviders.collectAsState()
    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()

    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()
    val posterMetadataPosition by AppearanceConfig.posterMetadataPosition.collectAsState()
    val posterTextAlignment by AppearanceConfig.posterTextAlignment.collectAsState()
    val posterTitleMaxLines by AppearanceConfig.posterTitleMaxLines.collectAsState()
    val elementShadowsEnabled by AppearanceConfig.elementShadowsEnabled.collectAsState()
    val elementShadowMultiplier by AppearanceConfig.elementShadowMultiplier.collectAsState()
    val textDropShadowEnabled by AppearanceConfig.textDropShadowEnabled.collectAsState()
    val textDropShadowBlur by AppearanceConfig.textDropShadowBlur.collectAsState()
    val posterDepthEffectEnabled by AppearanceConfig.posterDepthEffectEnabled.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Master Clean Mode Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (cleanModeEnabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            ),
            border = if (cleanModeEnabled) {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            } else {
                null
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (cleanModeEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (cleanModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Cinematic Clean Mode (Master)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (cleanModeEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        ) {
                            Text(
                                text = if (cleanModeEnabled) "Active" else "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cleanModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "One-click master switch to strip all plugin/provider names, hide scraper tags, and auto-clean messy release strings across the entire app. Shortcut: Ctrl+Shift+C.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = cleanModeEnabled,
                    onCheckedChange = { AppearanceConfig.setCleanModeEnabled(it) },
                )
            }
        }

        // Poster Workshop Studio Quick Launcher Card (Direct Top Placement)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Poster Workshop Studio (Live Preview)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Adjust card dimensions, corner radius, and spacing with live movie rows in real time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { onNavigateToSubScreen(SettingsSubScreen.POSTER_EDITOR) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Open Studio")
                }
            }
        }

        SettingsGroupCard(title = "Card Metadata & Badges Placement") {
            SettingsDropdownItem(
                label = "Metadata Placement",
                subtitle = when (posterMetadataPosition) {
                    PosterMetadataPosition.BELOW_POSTER -> "Clean artwork; specs & tags row displayed under title"
                    PosterMetadataPosition.ON_POSTER -> "Rating and tags floating in poster top corners"
                    PosterMetadataPosition.HYBRID -> "Star rating on poster top-left; technical specs under title"
                    PosterMetadataPosition.STEALTH_HOVER -> "Artwork 100% clean at rest; badges fade in on hover"
                },
                options = listOf(
                    PosterMetadataPosition.BELOW_POSTER to "Below Poster (Infuse / Apple TV+)",
                    PosterMetadataPosition.ON_POSTER to "On Poster Top Corners",
                    PosterMetadataPosition.HYBRID to "Hybrid (Rating Top-Left, Specs Below)",
                    PosterMetadataPosition.STEALTH_HOVER to "Stealth Hover (Fades In On Mouseover)",
                ),
                currentValue = posterMetadataPosition,
                onSelectionChanged = { AppearanceConfig.setPosterMetadataPosition(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsDropdownItem(
                label = "Title & Metadata Alignment",
                subtitle = "Align titles, tags, and rating stars to the card edge or center",
                options = listOf(
                    PosterTextAlignment.LEFT to "Left Aligned",
                    PosterTextAlignment.CENTER to "Center Aligned",
                ),
                currentValue = posterTextAlignment,
                onSelectionChanged = { AppearanceConfig.setPosterTextAlignment(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsDropdownItem(
                label = "Title Line Clamping",
                subtitle = "How many lines of title to show before truncating with ellipsis",
                options = listOf(
                    1 to "1 Line (Compact & Clean)",
                    2 to "2 Lines (Standard)",
                    3 to "3 Lines (Full Titles)",
                ),
                currentValue = posterTitleMaxLines,
                onSelectionChanged = { AppearanceConfig.setPosterTitleMaxLines(it) },
            )
        }

        SettingsGroupCard(title = "Active Badge Display Modes") {
            SettingsToggleItem(
                label = "Rating Badges (★ Gold Pill)",
                subtitle = "Displays star rating pill when metadata is available",
                checked = showRatingBadges,
                onCheckedChange = { CardMetadataConfig.setShowRatingBadges(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Quality Badges (4K / 1080p)",
                subtitle = "Ultra-HD & Full-HD resolution badges on media cards",
                checked = autoDetectQuality,
                onCheckedChange = { CardMetadataConfig.setAutoDetectQuality(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "SUB / DUB Language Badges",
                subtitle = "Detects subtitle and dubbed audio tracks automatically from stream tags",
                checked = autoDetectSubDub,
                onCheckedChange = { CardMetadataConfig.setAutoDetectSubDub(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsDropdownItem(
                label = "Provider Badges on Cards",
                subtitle = "Shows provider source pills in the top-right corner of movie and series posters",
                options = listOf(
                    com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode.HIDDEN to "Hidden (Clean)",
                    com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode.ICON_ONLY to "Icon Only (Minimal)",
                    com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode.FULL_BADGE to "Full Badge (Icon & Name)",
                ),
                currentValue = providerBadgeDisplayMode,
                onSelectionChanged = { AppearanceConfig.setProviderBadgeDisplayMode(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Auto-Clean Messy Release Titles",
                subtitle = "Automatically strips release tags like [4K], [HDR], [1080p], and codec tags from card titles",
                checked = autoCleanTitles,
                onCheckedChange = { CardMetadataConfig.setAutoCleanTitles(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Hide Details Source Pill",
                subtitle = "Hides provider name tags on the details page hero banner",
                checked = hideDetailsSource,
                onCheckedChange = { AppearanceConfig.setHideDetailsSource(it) },
            )
        }

        SettingsGroupCard(title = "Legibility & Drop Shadows") {
            SettingsToggleItem(
                label = "UI Element Drop Shadows",
                subtitle = "Adds cinematic depth shadows under movie cards, badges, and overlay headers",
                checked = elementShadowsEnabled,
                onCheckedChange = { AppearanceConfig.setElementShadowsEnabled(it) },
            )

            if (elementShadowsEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Shadow Intensity Multiplier",
                    subtitle = "${(elementShadowMultiplier * 10).toInt() / 10.0}x intensity",
                    value = elementShadowMultiplier,
                    onValueChange = { AppearanceConfig.setElementShadowMultiplier(it) },
                    valueRange = 0.5f..2.5f,
                    steps = 8,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Text Legibility Shadows",
                subtitle = "Soft subtle drop shadows behind titles and badges for enhanced readability",
                checked = textDropShadowEnabled,
                onCheckedChange = { AppearanceConfig.setTextDropShadowEnabled(it) },
            )

            if (textDropShadowEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Text Shadow Blur Radius",
                    subtitle = "${textDropShadowBlur.toInt()} dp blur",
                    value = textDropShadowBlur,
                    onValueChange = { AppearanceConfig.setTextDropShadowBlur(it) },
                    valueRange = 2f..24f,
                    steps = 11,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Poster Depth Effect",
                subtitle = "Directional overhead rim light bevel and laminated glass sheen across cards",
                checked = posterDepthEffectEnabled,
                onCheckedChange = { AppearanceConfig.setPosterDepthEffectEnabled(it) },
            )
        }
    }
}
