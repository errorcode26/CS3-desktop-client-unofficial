package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsMenuButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsAppearanceMainScreen(onNavigate: (SettingsSubScreen) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Appearance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        
        SettingsMenuButton(
            icon = Icons.Default.Palette,
            title = "Theme & Colors",
            subtitle = "Amoled mode, color themes, and general app look.",
            onClick = { onNavigate(SettingsSubScreen.THEME) }
        )
        SettingsMenuButton(
            icon = Icons.Default.Dashboard,
            title = "Display & Layout",
            subtitle = "Main screen layouts, home sections, and overall display.",
            onClick = { onNavigate(SettingsSubScreen.LAYOUT) }
        )
        SettingsMenuButton(
            icon = Icons.Default.ViewAgenda,
            title = "Details Page Layout",
            subtitle = "Episodes list style, poster styles, and title presentation.",
            onClick = { onNavigate(SettingsSubScreen.DETAILS_LAYOUT) }
        )
        SettingsMenuButton(
            icon = Icons.Default.AutoAwesome,
            title = "Effects & Blur",
            subtitle = "Toggle background blur, animations, and visual flair.",
            onClick = { onNavigate(SettingsSubScreen.EFFECTS) }
        )
    }
}

@Composable
fun SettingsPlayerMainScreen(onNavigate: (SettingsSubScreen) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Player & Media", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        
        SettingsMenuButton(
            icon = Icons.Default.PlayCircle,
            title = "Player & Controls",
            subtitle = "Skip buttons, gestures, hardware acceleration, and player behavior.",
            onClick = { onNavigate(SettingsSubScreen.PLAYER_CONTROLS) }
        )
        SettingsMenuButton(
            icon = Icons.Default.Subtitles,
            title = "Subtitle Styling",
            subtitle = "Subtitle colors, size, font, and background opacity.",
            onClick = { onNavigate(SettingsSubScreen.SUBTITLES) }
        )
    }
}

@Composable
fun SettingsServicesMainScreen(onNavigate: (SettingsSubScreen) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Services & Sync", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        
        SettingsMenuButton(
            icon = Icons.Default.Sync,
            title = "Accounts & Trackers",
            subtitle = "MAL, AniList, SIMKL sync and tracking integrations.",
            onClick = { onNavigate(SettingsSubScreen.TRACKERS) }
        )
        SettingsMenuButton(
            icon = Icons.Default.Hub,
            title = "Metadata & Services",
            subtitle = "Discord RPC, OpenSubtitles, and TMDB settings.",
            onClick = { onNavigate(SettingsSubScreen.INTEGRATIONS) }
        )
        SettingsMenuButton(
            icon = Icons.Default.Extension,
            title = "Extensions & Plugins",
            subtitle = "Manage installed sources and repository configurations.",
            onClick = { onNavigate(SettingsSubScreen.EXTENSIONS) }
        )
    }
}

@Composable
fun SettingsSystemMainScreen(onNavigate: (SettingsSubScreen) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("System & Help", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        
        SettingsMenuButton(
            icon = Icons.Default.Wifi,
            title = "Network & Connection",
            subtitle = "DNS over HTTPS, proxy settings, and API behaviors.",
            onClick = { onNavigate(SettingsSubScreen.NETWORK) }
        )
        SettingsMenuButton(
            icon = Icons.Default.Storage,
            title = "Storage & Advanced",
            subtitle = "Cache limits, app data backup, and advanced tweaks.",
            onClick = { onNavigate(SettingsSubScreen.ADVANCED) }
        )
        SettingsMenuButton(
            icon = Icons.Default.Code,
            title = "Developer & Logs",
            subtitle = "Error logs, test menus, and experimental features.",
            onClick = { onNavigate(SettingsSubScreen.DEVELOPER) }
        )
        SettingsMenuButton(
            icon = Icons.Default.Update,
            title = "Updates",
            subtitle = "Check for app updates, pre-releases, and patch notes.",
            onClick = { onNavigate(SettingsSubScreen.UPDATES) }
        )
        SettingsMenuButton(
            icon = Icons.Default.Info,
            title = "About",
            subtitle = "App version, license, and community links.",
            onClick = { onNavigate(SettingsSubScreen.ABOUT) }
        )
    }
}
