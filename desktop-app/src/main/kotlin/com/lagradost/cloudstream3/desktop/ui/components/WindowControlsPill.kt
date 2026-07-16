package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.DesktopUiState
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState

@Composable
fun WindowControlsPill(isHome: Boolean = false) {
    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen?.value ?: (windowState?.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen)

    val theme = LocalDesktopTheme.current
    
    // Fetch provider states for the global pill
    val providers by DesktopUiState.homeProviders.collectAsState()
    val selectedProviderName by DesktopUiState.selectedProviderName.collectAsState()
    val mergedPluginIcons by DesktopUiState.mergedPluginIcons.collectAsState()

    fun fuzzyMatchIcon(providerName: String): String? {
        val pName = providerName.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
        return mergedPluginIcons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
            if (kName.length < 3) return@firstOrNull false
            pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
    }

    Surface(
        shape = CircleShape,
        color = theme.SurfaceElevated.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
        shadowElevation = 8.dp, // Always use elevated shadow
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            if (isHome && providers.isNotEmpty()) {
                val isDropdownExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { isDropdownExpanded.value = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        val pluginIcon = selectedProviderName?.let { p -> mergedPluginIcons[p] ?: fuzzyMatchIcon(p) }
                        if (pluginIcon != null) {
                            coil3.compose.AsyncImage(
                                model = pluginIcon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.White),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = selectedProviderName ?: "Sources",
                            color = theme.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Provider",
                            tint = theme.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    androidx.compose.material.MaterialTheme(
                        colors = androidx.compose.material.MaterialTheme.colors.copy(surface = theme.SurfaceElevated),
                        shapes = androidx.compose.material.MaterialTheme.shapes.copy(medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    ) {
                        androidx.compose.material.DropdownMenu(
                            expanded = isDropdownExpanded.value,
                            onDismissRequest = { isDropdownExpanded.value = false },
                            modifier = Modifier
                                .widthIn(min = 260.dp, max = 380.dp)
                                .heightIn(max = 500.dp)
                        ) {
                        val selectedCategory = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("All") }
                        val categories = androidx.compose.runtime.remember(providers) {
                            val allTypes = providers.flatMap { it.supportedTypes }.map { it.name }.toSet()
                            val cats = mutableListOf("All")
                            if (allTypes.contains("Movie") || allTypes.contains("TvSeries")) cats.add("Movies & TV")
                            if (allTypes.contains("Anime") || allTypes.contains("AnimeMovie") || allTypes.contains("OVA")) cats.add("Anime")
                            if (allTypes.contains("Cartoon")) cats.add("Cartoon")
                            if (allTypes.contains("AsianDrama")) cats.add("Asian Drama")
                            if (allTypes.contains("Live")) cats.add("Live TV")
                            if (allTypes.contains("Documentary")) cats.add("Documentary")
                            if (allTypes.contains("NSFW")) cats.add("NSFW")
                            if (allTypes.contains("Torrent") || allTypes.contains("Others") || allTypes.contains("None")) cats.add("Others")
                            cats
                        }

                        if (categories.size > 1) {
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEach { cat ->
                                    val isSelected = selectedCategory.value == cat
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                        color = if (isSelected) theme.Accent else Color.Transparent,
                                        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else theme.TextMuted.copy(alpha = 0.3f)),
                                        modifier = Modifier.clickable { selectedCategory.value = cat },
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else theme.TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            androidx.compose.material.Divider(color = theme.Divider, modifier = Modifier.padding(bottom = 4.dp))
                        }

                        val filteredProviders = providers.filter { p ->
                            if (selectedCategory.value == "All") return@filter true
                            val types = p.supportedTypes.map { it.name }
                            when (selectedCategory.value) {
                                "Movies & TV" -> types.contains("Movie") || types.contains("TvSeries")
                                "Anime" -> types.contains("Anime") || types.contains("AnimeMovie") || types.contains("OVA")
                                "Cartoon" -> types.contains("Cartoon")
                                "Asian Drama" -> types.contains("AsianDrama")
                                "Live TV" -> types.contains("Live")
                                "Documentary" -> types.contains("Documentary")
                                "NSFW" -> types.contains("NSFW")
                                "Others" -> types.contains("Torrent") || types.contains("Others") || types.contains("None")
                                else -> true
                            }
                        }

                        filteredProviders.forEach { provider ->
                            val pluginIcon = mergedPluginIcons[provider.name] ?: fuzzyMatchIcon(provider.name)
                            androidx.compose.material.DropdownMenuItem(
                                onClick = {
                                    DesktopUiState.selectedProviderName.value = provider.name
                                    isDropdownExpanded.value = false
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (pluginIcon != null) {
                                        coil3.compose.AsyncImage(
                                            model = pluginIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    } else {
                                        Spacer(modifier = Modifier.width(36.dp))
                                    }
                                    Text(provider.name, color = theme.TextPrimary)
                                }
                            }
                        }
                        
                        if (filteredProviders.isEmpty()) {
                            Text(
                                "No providers in this category", 
                                color = theme.TextMuted, 
                                modifier = Modifier.padding(16.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                    }
                }
                
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(1.dp)
                        .background(theme.Divider)
                )
            }

            IconButton(
                onClick = { DesktopUiState.forceProviderRefresh.value += 1 },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.TextPrimary, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = {
                    if (fullscreenController != null) {
                        fullscreenController.toggle()
                    } else {
                        windowState?.placement = if (isFullscreen) {
                            androidx.compose.ui.window.WindowPlacement.Floating
                        } else {
                            androidx.compose.ui.window.WindowPlacement.Fullscreen
                        }
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = theme.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
