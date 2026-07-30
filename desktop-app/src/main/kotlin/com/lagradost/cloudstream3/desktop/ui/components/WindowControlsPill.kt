package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState

@Composable
fun WindowControlsPill(
    isHome: Boolean = false,
    homeUiState: com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState? = null,
    homeActionDispatcher: ((com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent) -> Unit)? = null,
) {
    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen ?: (windowState?.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen)

    val theme = LocalDesktopTheme.current

    // Fetch provider states for the global pill
    val providers = homeUiState?.providers ?: emptyList()
    val selectedProviderName = homeUiState?.selectedProviderName
    val mergedPluginIcons = homeUiState?.mergedPluginIcons ?: emptyMap()

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
        shadowElevation = 8.dp.applyShadowMultiplier(), // Always use elevated shadow
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            if (isHome && providers.isNotEmpty()) {
                val isDropdownExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { isDropdownExpanded.value = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        val pluginIcon = selectedProviderName?.let { p -> mergedPluginIcons[p] ?: fuzzyMatchIcon(p) }
                        if (pluginIcon != null) {
                            coil3.compose.AsyncImage(
                                model = pluginIcon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.White),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else if (selectedProviderName != null) {
                            com.lagradost.cloudstream3.desktop.ui.components.PluginPlaceholderAvatar(
                                name = selectedProviderName,
                                internalName = selectedProviderName,
                                modifier = Modifier.size(20.dp).clip(CircleShape),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = selectedProviderName ?: "Sources",
                            color = theme.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Provider",
                            tint = theme.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    androidx.compose.material.MaterialTheme(
                        colors = androidx.compose.material.MaterialTheme.colors.copy(surface = theme.SurfaceElevated),
                        shapes = androidx.compose.material.MaterialTheme.shapes.copy(medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    ) {
                        androidx.compose.material.DropdownMenu(
                            expanded = isDropdownExpanded.value,
                            onDismissRequest = { isDropdownExpanded.value = false },
                            modifier = Modifier
                                .widthIn(min = 420.dp, max = 500.dp)
                                .heightIn(max = 500.dp),
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

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (categories.size > 1) {
                                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                    androidx.compose.foundation.layout.FlowRow(
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                
                                IconButton(
                                    onClick = {
                                        homeActionDispatcher?.invoke(com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent.OnShowCatalogSettings(true))
                                        isDropdownExpanded.value = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Manage Catalogs",
                                        tint = theme.TextMuted
                                    )
                                }
                            }
                            androidx.compose.material.Divider(color = theme.Divider, modifier = Modifier.padding(bottom = 4.dp))

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

                            if (filteredProviders.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    filteredProviders.chunked(2).forEach { rowProviders ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            rowProviders.forEach { provider ->
                                                val pluginIcon = mergedPluginIcons[provider.name] ?: fuzzyMatchIcon(provider.name)
                                                androidx.compose.material.DropdownMenuItem(
                                                    onClick = {
                                                        homeActionDispatcher?.invoke(com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent.OnSelectProvider(provider.name))
                                                        isDropdownExpanded.value = false
                                                    },
                                                    modifier = Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (pluginIcon != null) {
                                                            coil3.compose.AsyncImage(
                                                                model = pluginIcon,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White),
                                                            )
                                                        } else {
                                                            com.lagradost.cloudstream3.desktop.ui.components.PluginPlaceholderAvatar(
                                                                name = provider.name,
                                                                internalName = provider.name,
                                                                modifier = Modifier.size(24.dp).clip(CircleShape),
                                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(provider.name, color = theme.TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                            if (rowProviders.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            if (filteredProviders.isEmpty()) {
                                Text(
                                    "No providers in this category",
                                    color = theme.TextMuted,
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(1.dp)
                        .background(theme.Divider),
                )
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
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = theme.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
