package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsDropdownItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSubScreen
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun SettingsHomeFeedScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) {
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()
    val showContinueWatching by AppearanceConfig.showContinueWatching.collectAsState()
    val heroAutoSlideDelaySeconds by AppearanceConfig.heroAutoSlideDelaySeconds.collectAsState()
    val heroBannerStyle by AppearanceConfig.heroBannerStyle.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Continue Watching Feed") {
            SettingsToggleItem(
                label = "Show Continue Watching",
                subtitle = "Display in-progress movies and series at the top of the Home feed",
                checked = showContinueWatching,
                onCheckedChange = { AppearanceConfig.setShowContinueWatching(it) },
            )
        }

        SettingsGroupCard(title = "Hero Spotlight Carousel") {
            SettingsToggleItem(
                label = "Enable Hero Slider",
                subtitle = "Display featured trending media spotlight banner at the top of Home",
                checked = heroEnabled,
                onCheckedChange = { AppearanceConfig.setHeroEnabled(it) },
            )

            if (heroEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Hero Banner Layout Style",
                    subtitle = "Choose between cinema peeking edges, fullscreen banner, and filmstrip",
                    options = listOf(
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.CINEMA_PEEKING to "Cinema (Peeking Rails)",
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.FULLSCREEN_IMMERSIVE to "Fullscreen Immersive",
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.THUMBNAIL_STRIP to "Thumbnail Filmstrip",
                    ),
                    currentValue = heroBannerStyle,
                    onSelectionChanged = { AppearanceConfig.setHeroBannerStyle(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Hero Auto-Slide Delay",
                    subtitle = "How long each spotlight item stays visible before transitioning",
                    options = listOf(
                        0 to "Off (Manual Only)",
                        4 to "4 seconds",
                        6 to "6 seconds",
                        8 to "8 seconds",
                        12 to "12 seconds",
                    ),
                    currentValue = heroAutoSlideDelaySeconds,
                    onSelectionChanged = { AppearanceConfig.setHeroAutoSlideDelaySeconds(it) },
                )
            }
        }
    }
}
