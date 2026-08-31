package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ShortcutEntry(
    val keys: List<String>,
    val description: String,
    val context: String,
    val icon: ImageVector? = null,
)

data class ShortcutCategory(
    val title: String,
    val icon: ImageVector,
    val shortcuts: List<ShortcutEntry>,
)

@Composable
fun SettingsShortcutsScreen() {
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember {
        listOf(
            ShortcutCategory(
                title = "Core System & Quick Actions",
                icon = Icons.Default.Bolt,
                shortcuts = listOf(
                    ShortcutEntry(listOf("Ctrl", "Shift", "C"), "Toggle Cinematic Clean Mode (Master Key)", "Global", Icons.Default.AutoAwesome),
                    ShortcutEntry(listOf("F11"), "Toggle True Borderless Fullscreen", "Global", Icons.Default.Fullscreen),
                    ShortcutEntry(listOf("Ctrl", "+"), "Zoom In UI (+10% Scale)", "Global", Icons.Default.ZoomIn),
                    ShortcutEntry(listOf("Ctrl", "-"), "Zoom Out UI (-10% Scale)", "Global", Icons.Default.ZoomOut),
                    ShortcutEntry(listOf("Ctrl", "0"), "Reset UI Zoom to 100%", "Global", Icons.Default.Refresh),
                    ShortcutEntry(listOf("F5"), "Refresh Home Feeds & Catalogs", "Global", Icons.Default.Refresh),
                    ShortcutEntry(listOf("Ctrl", "R"), "Hard Reload Active View", "Global", Icons.Default.Sync),
                    ShortcutEntry(listOf("Ctrl", "O"), "Open Local Video File (.mp4, .mkv, .webm)", "Global", Icons.Default.FolderOpen),
                    ShortcutEntry(listOf("Ctrl", "U"), "Play Network Stream (M3U8 / MPD / Direct URL)", "Global", Icons.Default.Link),
                    ShortcutEntry(listOf("F12"), "Toggle Developer Studio Inspector", "Global", Icons.Default.Code),
                    ShortcutEntry(listOf("Esc"), "Close Overlay / Back / Exit Fullscreen", "Global", Icons.Default.Close),
                    ShortcutEntry(listOf("Alt", "←"), "Navigate Back in Settings Sub-screens", "Settings", Icons.Default.ArrowBack),
                    ShortcutEntry(listOf("F1"), "Open Keyboard Shortcuts Reference", "Global", Icons.Default.Keyboard),
                ),
            ),
            ShortcutCategory(
                title = "Video Player & Playback Engine",
                icon = Icons.Default.PlayCircle,
                shortcuts = listOf(
                    ShortcutEntry(listOf("Space"), "Play / Pause Video", "Player", Icons.Default.PlayArrow),
                    ShortcutEntry(listOf("K"), "Play / Pause Video (YouTube standard)", "Player", Icons.Default.PlayArrow),
                    ShortcutEntry(listOf("←"), "Seek Backward 10s (Silent HUD ripple)", "Player", Icons.Default.FastRewind),
                    ShortcutEntry(listOf("→"), "Seek Forward 10s (Silent HUD ripple)", "Player", Icons.Default.FastForward),
                    ShortcutEntry(listOf("J"), "Seek Backward 10s", "Player", Icons.Default.FastRewind),
                    ShortcutEntry(listOf("L"), "Seek Forward 10s", "Player", Icons.Default.FastForward),
                    ShortcutEntry(listOf("Mouse 4"), "Thumb Back Button (-10s Seek)", "Player", Icons.Default.Mouse),
                    ShortcutEntry(listOf("Mouse 5"), "Thumb Forward Button (+10s Seek)", "Player", Icons.Default.Mouse),
                    ShortcutEntry(listOf("Hold Click"), "2x Turbo Playback Speed Boost", "Player", Icons.Default.Speed),
                    ShortcutEntry(listOf("Double Click"), "Toggle Fullscreen Mode", "Player", Icons.Default.Fullscreen),
                    ShortcutEntry(listOf("↑"), "Volume Up (+5%)", "Player", Icons.Default.VolumeUp),
                    ShortcutEntry(listOf("↓"), "Volume Down (-5%)", "Player", Icons.Default.VolumeDown),
                    ShortcutEntry(listOf("M"), "Mute / Unmute Audio", "Player", Icons.Default.VolumeOff),
                    ShortcutEntry(listOf("S"), "Take Clean Screenshot to Pictures", "Player", Icons.Default.CameraAlt),
                    ShortcutEntry(listOf("C"), "Toggle Subtitles On / Off", "Player", Icons.Default.Subtitles),
                    ShortcutEntry(listOf("["), "Decrease Playback Speed (-0.25x)", "Player", Icons.Default.Speed),
                    ShortcutEntry(listOf("]"), "Increase Playback Speed (+0.25x)", "Player", Icons.Default.Speed),
                    ShortcutEntry(listOf("Backspace"), "Reset Playback Speed to 1.0x Normal", "Player", Icons.Default.RestartAlt),
                    ShortcutEntry(listOf("N"), "Next Episode (TV Series / Anime)", "Player", Icons.Default.SkipNext),
                    ShortcutEntry(listOf("P"), "Previous Episode", "Player", Icons.Default.SkipPrevious),
                    ShortcutEntry(listOf("I"), "Skip Intro / Outro (AniSkip Trigger)", "Player", Icons.Default.FastForward),
                    ShortcutEntry(listOf("D"), "Toggle MPV Performance & Codec Stats", "Player", Icons.Default.Analytics),
                ),
            ),
            ShortcutCategory(
                title = "Search & Navigation",
                icon = Icons.Default.Search,
                shortcuts = listOf(
                    ShortcutEntry(listOf("/"), "Quick Focus Search Input", "Navigation", Icons.Default.Search),
                    ShortcutEntry(listOf("Ctrl", "F"), "Open Global Media Finder", "Navigation", Icons.Default.Search),
                    ShortcutEntry(listOf("Tab"), "Navigate Focus Forward", "Navigation", Icons.Default.KeyboardTab),
                    ShortcutEntry(listOf("Shift", "Tab"), "Navigate Focus Backward", "Navigation", Icons.Default.KeyboardTab),
                    ShortcutEntry(listOf("Enter"), "Activate / Play Selected Item", "Navigation", Icons.Default.Check),
                ),
            ),
        )
    }

    val filteredCategories = remember(searchQuery, categories) {
        if (searchQuery.isBlank()) {
            categories
        } else {
            val q = searchQuery.trim().lowercase()
            categories.mapNotNull { cat ->
                val matching = cat.shortcuts.filter {
                    it.description.lowercase().contains(q) ||
                        it.context.lowercase().contains(q) ||
                        it.keys.any { k -> k.lowercase().contains(q) }
                }
                if (matching.isNotEmpty()) cat.copy(shortcuts = matching) else null
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Hero Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keyboard Shortcuts & Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Master keybindings for lightning-fast playback control, zooming, navigation, and Clean Mode toggling without touching the mouse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Search Filter Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter shortcuts (e.g. clean mode, seek, volume, zoom, screenshot)...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )

        // Shortcuts List
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            filteredCategories.forEach { category ->
                item(key = category.title) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "(${category.shortcuts.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(category.shortcuts, key = { "${category.title}_${it.description}" }) { entry ->
                    ShortcutRowCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun ShortcutRowCard(entry: ShortcutEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (entry.icon != null) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = entry.context,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Keycap Badge Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entry.keys.forEachIndexed { index, key ->
                    KeycapBadge(key = key)
                    if (index < entry.keys.size - 1) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeycapBadge(key: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
        )
    }
}
