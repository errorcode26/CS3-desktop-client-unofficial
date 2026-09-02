package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card 1: Navigation & Dock
        AppearanceHubCard(
            icon = Icons.Default.Dashboard,
            title = "Navigation & Dock",
            subtitle = "Dock position, floating island vs edge navbar, draggable dock button reordering, UI zoom scale, and clock formats.",
            badge = "${dockPosition.label} Dock",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_NAV_DOCK) },
        )

        // Card 2: Theme, Colors & Wallpaper
        AppearanceHubCard(
            icon = Icons.Default.Palette,
            title = "Theme, Colors & Wallpaper",
            subtitle = "Light/Dark mode, AMOLED pure black, curated accent swatches, custom hex color picker, typography fonts, ambient glow, and custom wallpaper.",
            badge = if (isLightMode) "Light Theme" else if (amoledMode) "AMOLED Black" else "$themeAccent Accent",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_THEME_WALLPAPER) },
        )

        // Card 3: Posters & Provider Branding
        AppearanceHubCard(
            icon = Icons.Default.Wallpaper,
            title = "Posters & Provider Branding",
            subtitle = "Provider badges on cards, auto-clean messy release titles, 4K/1080p quality tags, SUB/DUB badges, rating star pills, and Poster Workshop Studio.",
            badge = providerBadgeDisplayMode.label,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_POSTERS_BADGES) },
        )

        // Card 4: Home Feed & Cinema
        AppearanceHubCard(
            icon = Icons.Default.PlayArrow,
            title = "Home Feed & Cinema",
            subtitle = "Hero spotlight trending slider, banner layout styles (Cinema/Fullscreen/Filmstrip), auto-slide transitions, and Continue Watching row.",
            badge = if (heroEnabled) "Hero Active" else "Hero Off",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_HOME_FEED) },
        )

        // Card 5: Details Page & Modular Sections
        AppearanceHubCard(
            icon = Icons.Default.Edit,
            title = "Details Page & Modular Sections",
            subtitle = "Modular sections drag reordering, atmospheric backdrop blur & softening, unreleased episode locking, and anti-spoiler mode.",
            badge = "Modular Layout",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.DETAILS_LAYOUT) },
        )
    }
}

@Composable
private fun AppearanceHubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        lineHeight = 18.sp,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
