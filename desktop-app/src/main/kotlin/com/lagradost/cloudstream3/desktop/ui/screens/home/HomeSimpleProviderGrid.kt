package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager

@Composable
internal fun HomeSimpleProviderGrid(
    allProviders: List<MainAPI>,
    activeProviders: List<String>,
    providerTypeFilter: Set<TvType>,
    pluginIcons: Map<String, String>,
    onSelectProvider: (provider: MainAPI, pKey: String) -> Unit,
    onCustomizeProvider: (provider: MainAPI) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredProviders = if (providerTypeFilter.isEmpty()) {
        allProviders
    } else {
        allProviders.filter { p -> p.supportedTypes.any { it in providerTypeFilter } }
    }
    val sortedProviders = filteredProviders.sortedBy { it.name }
    val duplicateHomeNames = remember(sortedProviders) {
        sortedProviders.groupBy { it.name }.filterValues { it.size > 1 }.keys
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 250.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sortedProviders.size, key = { index -> "${sortedProviders[index].name}_${sortedProviders[index].mainUrl}_${sortedProviders[index].sourcePlugin}_$index" }) { index ->
            val provider = sortedProviders[index]
            val pKey = if (provider.sourcePlugin != null && provider.sourcePlugin != "built-in") {
                "${java.io.File(provider.sourcePlugin).parentFile?.name ?: ""}::${provider.name}"
            } else {
                provider.name
            }
            val isSingleNameUnique = sortedProviders.count { it.name == provider.name } == 1
            val isActive = activeProviders.contains(pKey) || (isSingleNameUnique && activeProviders.contains(provider.name))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable {
                        onSelectProvider(provider, pKey)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val url = fuzzyMatchPluginIcon(provider.name, pluginIcons)
                if (url != null) {
                    coil3.compose.AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    val repoTag = if (provider.name in duplicateHomeNames) {
                        provider.sourcePlugin?.let {
                            try {
                                java.io.File(it).parentFile?.name?.replace("_", " ")
                            } catch (_: Exception) {
                                null
                            }
                        }
                    } else {
                        null
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            provider.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!repoTag.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "($repoTag)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isActive && provider.mainPage.isEmpty()) {
                            Text(
                                "Select to load catalogs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        } else {
                            Text(
                                "${provider.mainPage.size} catalogs",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (DesktopTorrentEngine.isTorrentProvider(provider)) {
                            val isHybrid = DesktopTorrentEngine.isHybridTorrentProvider(provider)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = (if (isHybrid) Color(0xFF2563EB) else Color(0xFFF59E0B)).copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = if (isHybrid) "⚡ Hybrid" else "⚡ Torrent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isHybrid) Color(0xFF60A5FA) else Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = {
                    if (!isActive && provider.mainPage.isEmpty()) {
                        AppToastManager.showInfo("Select this plugin first to load its catalogs.")
                    } else {
                        onCustomizeProvider(provider)
                    }
                }) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Customize Catalogs",
                        tint = if (!isActive && provider.mainPage.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        } else if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
