package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsHubCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSubScreen
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun SettingsAppearanceScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) {
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val themeAccent by AppearanceConfig.themeAccent.collectAsState()
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()
    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val scrollState = rememberScrollState()

    val themeBadge = when {
        isLightMode -> "Light Theme"
        amoledMode -> "AMOLED Black"
        else -> "$themeAccent Accent"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card 1: Poster Workshop Studio (Promoted to Top for Real-Time Preview)
        SettingsHubCard(
            icon = Icons.Default.Tune,
            title = "Poster Workshop Studio",
            subtitle = "Interactive real-time preview canvas with live movie shelves and Continue Watching cards. Drag sliders to adjust poster width, corner radius, and card spacing live.",
            badge = "Live Studio",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.POSTER_EDITOR) },
        )

        // Card 2: Details Page Layout & Sections (Promoted to Top-Level Sane Position)
        SettingsHubCard(
            icon = Icons.Default.Layers,
            title = "Details Page Layout & Sections",
            subtitle = "Modular drag-and-drop section ordering, show or hide trailers, cast, watch history, and content recommendations.",
            badge = "Modular Sections",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.DETAILS_LAYOUT) },
        )

        // Card 3: Explore & Catalog Shelves
        SettingsHubCard(
            icon = Icons.Default.Tune,
            title = "Explore & Catalog Shelves",
            subtitle = "Modular drag-and-drop shelf ordering for installed add-ons, toggle categories on or off, and customize shelf titles.",
            badge = "Modular Shelves",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.EXPLORE_CATALOGS) },
        )

        // Card 3: Theme, Colors & Wallpaper
        SettingsHubCard(
            icon = Icons.Default.Palette,
            title = "Theme, Colors & Wallpaper",
            subtitle = "Light/Dark mode, AMOLED pure black, curated accent swatches, custom hex color picker, typography fonts, ambient glow, and custom wallpaper.",
            badge = themeBadge,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_THEME_WALLPAPER) },
        )

        // Card 4: Posters & Provider Badges
        SettingsHubCard(
            icon = Icons.Default.Wallpaper,
            title = "Posters & Provider Badges",
            subtitle = "Master Clean Mode, provider badges, auto-clean messy release titles, 4K/1080p quality tags, SUB/DUB badges, rating stars, and legibility shadows.",
            badge = providerBadgeDisplayMode.label,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_POSTERS_BADGES) },
        )

        // Card 5: Home Feed & Cinema
        SettingsHubCard(
            icon = Icons.Default.PlayArrow,
            title = "Home Feed & Cinema",
            subtitle = "Hero spotlight trending slider, banner layout styles, dynamic backdrop blur, auto-slide transitions, and Continue Watching row.",
            badge = if (heroEnabled) "Hero Active" else "Hero Off",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_HOME_FEED) },
        )

        // Card 6: Navigation & Dock
        SettingsHubCard(
            icon = Icons.Default.Dashboard,
            title = "Navigation & Dock",
            subtitle = "Dock position, floating island vs edge navbar, draggable dock button reordering, UI zoom scale, and clock formats.",
            badge = "${dockPosition.label} Dock",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_NAV_DOCK) },
        )
    }
}
