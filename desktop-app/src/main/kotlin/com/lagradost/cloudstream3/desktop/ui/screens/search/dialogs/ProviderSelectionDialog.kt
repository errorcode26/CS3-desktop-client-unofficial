package com.lagradost.cloudstream3.desktop.ui.screens.search.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import java.io.File
import java.net.URI

private val SANITIZE_NAME_REGEX = Regex("[^a-z0-9]")

internal fun fuzzyMatchPluginIcon(providerName: String, pluginIcons: Map<String, String>): String? {
    val pName = providerName.lowercase().replace(SANITIZE_NAME_REGEX, "").replace("provider", "").replace("plugin", "")
    return pluginIcons.entries.firstOrNull { (k, _) ->
        val kName = k.lowercase().replace(SANITIZE_NAME_REGEX, "").replace("provider", "").replace("plugin", "")
        if (kName.length < 3) return@firstOrNull false
        pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
    }?.value
}

@Composable
fun ProviderSelectionDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    providers: List<MainAPI>,
    pluginIcons: Map<String, String>,
    selectedProviderNames: Set<String>,
    onApplySelectedProviders: (Set<String>) -> Unit,
) {
    var providerModalSearch by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<TvType?>(null) }
    val allProviderNames = remember(providers) { providers.map { it.name }.toSet() }
    var localSelected by remember(show, selectedProviderNames, providers) {
        mutableStateOf(if (selectedProviderNames.isEmpty()) allProviderNames else selectedProviderNames)
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .fillMaxHeight(0.85f)
            .heightIn(min = 420.dp, max = 700.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header Row ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Search Sources",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Select which providers to search across simultaneously",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { localSelected = allProviderNames },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Select All", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { localSelected = emptySet() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Clear All", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }

            // ── Search & Filter Controls ───────────────────────────────
            OutlinedTextField(
                value = providerModalSearch,
                onValueChange = { providerModalSearch = it },
                placeholder = { Text("Filter providers...", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (providerModalSearch.isNotEmpty()) {
                        IconButton(onClick = { providerModalSearch = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
            )

            // ── Media Type Category Chips ──────────────────────────────
            val categoryChips = listOf(
                null to "All",
                TvType.Movie to "Movies",
                TvType.TvSeries to "Series",
                TvType.Anime to "Anime",
                TvType.Documentary to "Documentaries",
                TvType.Live to "Live",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                categoryChips.forEach { (type, label) ->
                    val isSelected = selectedCategoryFilter == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = if (isSelected) null else type },
                        label = { Text(label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }

            // ── Matching Providers Grid ────────────────────────────────
            val matchingProviders = remember(providers, providerModalSearch, selectedCategoryFilter) {
                providers
                    .filter { p ->
                        val matchesQuery = providerModalSearch.isBlank() || p.name.contains(providerModalSearch, ignoreCase = true)
                        val matchesCategory = selectedCategoryFilter == null || p.supportedTypes.contains(selectedCategoryFilter)
                        matchesQuery && matchesCategory
                    }
                    .distinctBy { "${it.name}_${it.mainUrl}_${it.sourcePlugin ?: ""}" }
            }

            val duplicateNames = remember(matchingProviders) {
                matchingProviders.groupBy { it.name }.filterValues { it.size > 1 }.keys
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(matchingProviders.size, key = { idx -> "${matchingProviders[idx].name}_${matchingProviders[idx].mainUrl}_${matchingProviders[idx].sourcePlugin ?: ""}" }) { idx ->
                    val provider = matchingProviders[idx]
                    val isChecked = provider.name in localSelected

                    Surface(
                        onClick = {
                            localSelected = if (isChecked) {
                                localSelected - provider.name
                            } else {
                                localSelected + provider.name
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isChecked) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isChecked) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val icon = pluginIcons[provider.name] ?: fuzzyMatchPluginIcon(provider.name, pluginIcons)
                            if (icon != null) {
                                AsyncImage(
                                    model = icon,
                                    contentDescription = null,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White),
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = provider.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                val repoTag = if (provider.name in duplicateNames) {
                                    provider.sourcePlugin?.let {
                                        try {
                                            File(it).parentFile?.name?.replace("_", " ")
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
                                        text = provider.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (!repoTag.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "($repoTag)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                val domain = try {
                                    URI(provider.mainUrl).host ?: provider.mainUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                                } catch (_: Exception) {
                                    provider.mainUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                                }
                                val typesStr = provider.supportedTypes.take(2).joinToString { it.name }
                                val subtitle = if (domain.isNotBlank()) "$typesStr • $domain" else typesStr

                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    localSelected = if (checked) {
                                        localSelected + provider.name
                                    } else {
                                        localSelected - provider.name
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── Footer Controls ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        localSelected.size == allProviderNames.size -> "All ${allProviderNames.size} sources selected"
                        localSelected.isEmpty() -> "No sources selected (will search all)"
                        else -> "${localSelected.size} of ${allProviderNames.size} sources selected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val finalSelected = if (localSelected.size == allProviderNames.size) emptySet() else localSelected
                            onApplySelectedProviders(finalSelected)
                            onDismissRequest()
                        },
                    ) {
                        Text("Apply & Search", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
