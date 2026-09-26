package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSliderItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSubScreen
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun SettingsPostersBadgesScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) {
    val cleanModeEnabled by AppearanceConfig.cleanModeEnabled.collectAsState()
    val hideProviderNames by AppearanceConfig.hideProviderNames.collectAsState()
    val hideDetailsSource by AppearanceConfig.hideDetailsSource.collectAsState()
    val hideStreamProviders by AppearanceConfig.hideStreamProviders.collectAsState()
    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val continueWatchingStyle by AppearanceConfig.continueWatchingStyle.collectAsState()
    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()
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
            } else null,
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

        SettingsGroupCard(title = "Customizable Clean Mode & Provider Branding") {
            SettingsToggleItem(
                label = "Hide Provider & Scraper Names Everywhere",
                subtitle = "Suppresses provider names in context menus, cards, and list subtitles",
                checked = hideProviderNames,
                onCheckedChange = { AppearanceConfig.setHideProviderNames(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Auto-Clean Messy Release Titles",
                subtitle = "Strips raw release tags (WEB-DL, Dual Audio, codecs) to show pure titles across the app",
                checked = autoCleanTitles,
                onCheckedChange = { CardMetadataConfig.setAutoCleanTitles(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Hide Source on Details Page",
                subtitle = "Omits the 'Source' provider name from the auto-hiding technical specs sidebar on Details screens",
                checked = hideDetailsSource,
                onCheckedChange = { AppearanceConfig.setHideDetailsSource(it) },
            )
        }

        SettingsGroupCard(title = "Depth & Shadow Enhancements") {
            SettingsToggleItem(
                label = "UI Element Drop Shadows",
                subtitle = "Adds visual depth and elevation shadows under cards and floating panels",
                checked = elementShadowsEnabled,
                onCheckedChange = { AppearanceConfig.setElementShadowsEnabled(it) },
            )

            if (elementShadowsEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Shadow Intensity Multiplier",
                    subtitle = "%.1fx strength".format(elementShadowMultiplier),
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

        SettingsGroupCard(title = "Visual Poster Studio") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Poster Visual Editor", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Fine-tune poster card dimensions, corner rounding, and card spacing in real-time with live preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { onNavigateToSubScreen(SettingsSubScreen.POSTER_EDITOR) }, shape = RoundedCornerShape(8.dp)) {
                    Text("Open Studio ➔")
                }
            }
        }
    }
}
