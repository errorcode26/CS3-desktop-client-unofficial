package com.lagradost.cloudstream3.desktop.ui.screens.search.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    selectedProviderName: String?,
    selectedProviderSource: String?,
    isGlobalSearchEnabled: Boolean,
    providerTypeFilter: Set<TvType>,
    onSelectGlobalSearch: () -> Unit,
    onSelectProvider: (name: String, sourcePlugin: String?) -> Unit,
) {
    var providerModalSearch by remember { mutableStateOf("") }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.65f).wrapContentHeight().heightIn(min = 220.dp, max = 620.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Select Provider",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Choose a dedicated provider or search across all installed plugins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilledTonalButton(
                    onClick = onSelectGlobalSearch,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("All Plugins (Global)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            // Search input within modal
            OutlinedTextField(
                value = providerModalSearch,
                onValueChange = { providerModalSearch = it },
                placeholder = { Text("Filter providers...", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
            )

            // Provider Grid
            val matchingProviders = remember(providers, providerModalSearch, providerTypeFilter) {
                providers
                    .filter { p ->
                        val matchesQuery = providerModalSearch.isBlank() || p.name.contains(providerModalSearch, ignoreCase = true)
                        val matchesType = providerTypeFilter.isEmpty() || p.supportedTypes.any { it in providerTypeFilter }
                        matchesQuery && matchesType
                    }
                    .distinctBy { "${it.name}_${it.mainUrl}_${it.sourcePlugin ?: ""}" }
            }

            val duplicateNames = remember(matchingProviders) {
                matchingProviders.groupBy { it.name }.filterValues { it.size > 1 }.keys
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 440.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(matchingProviders.size, key = { idx -> "${matchingProviders[idx].name}_${matchingProviders[idx].mainUrl}_${matchingProviders[idx].sourcePlugin ?: ""}" }) { idx ->
                    val provider = matchingProviders[idx]
                    val isSelected = !isGlobalSearchEnabled && selectedProviderName == provider.name && (selectedProviderSource == null || selectedProviderSource == provider.sourcePlugin)
                    Surface(
                        onClick = {
                            onSelectProvider(provider.name, provider.sourcePlugin)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
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
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.White),
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = provider.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
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
                                        } catch (_: Exception) { null }
                                    }
                                } else null

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
                        }
                    }
                }
            }
        }
    }
}
