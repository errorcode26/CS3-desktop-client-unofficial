package com.lagradost.cloudstream3.desktop.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSearchScreen(
    onNavigate: (Config) -> Unit,
    viewModel: SearchViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingSearch = uiState.isLoadingSearch
    val isGlobalSearchEnabled = uiState.isGlobalSearchEnabled
    val searchResultsGrouped = uiState.searchResultsGrouped
    val selectedProviderName = uiState.selectedProviderName
    val selectedCategories = uiState.selectedCategories
    val pluginIcons = uiState.pluginIcons
    val searchHistory = uiState.searchHistory
    var showProviderDropdown by remember { mutableStateOf(false) }
    // Local filter for the provider picker — does not affect the search itself
    var providerTypeFilter by remember { mutableStateOf(emptySet<TvType>()) }

    fun fuzzyMatchIcon(providerName: String): String? {
        val pName = providerName.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
        return pluginIcons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
            if (kName.length < 3) return@firstOrNull false
            pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
    }

    val categories = listOf(
        TvType.Movie to "Movies",
        TvType.TvSeries to "Series",
        TvType.Anime to "Anime",
        TvType.Documentary to "Documentaries",
        TvType.Live to "Live",
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Search Header ──────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Unified container to perfectly left-align the categories with the search bar
            Column(
                modifier = Modifier.widthIn(max = 660.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                // Search Bar & Plugin Selector Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Search Bar
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )

                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (uiState.searchQuery.isEmpty()) {
                                    Text(
                                        "Search movies, series, anime...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 16.sp,
                                    )
                                }
                                BasicTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.onEvent(SearchUiEvent.OnSearchQueryChange(it)) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        viewModel.onEvent(SearchUiEvent.OnSearch)
                                    }),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                )
                            }

                            AnimatedVisibility(
                                visible = uiState.searchQuery.isNotEmpty(),
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                            ) {
                                IconButton(
                                    onClick = { viewModel.onEvent(SearchUiEvent.OnClearSearch) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Plugin Selector Chip
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                        onClick = { showProviderDropdown = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!isGlobalSearchEnabled && selectedProviderName != null) {
                                val icon = pluginIcons[selectedProviderName] ?: fuzzyMatchIcon(selectedProviderName)
                                if (icon != null) {
                                    coil3.compose.AsyncImage(
                                        model = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White),
                                    )
                                }
                            }
                            Text(
                                text = if (isGlobalSearchEnabled) "All Plugins" else (selectedProviderName ?: "Select Plugin"),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 120.dp),
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } // closes Row

                // ── Provider Selection Modal ─────────────────────────────────
                var providerModalSearch by remember { mutableStateOf("") }
                com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog(
                    show = showProviderDropdown,
                    onDismissRequest = { showProviderDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.65f).fillMaxHeight(0.75f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                                onClick = {
                                    viewModel.onEvent(SearchUiEvent.OnToggleGlobalSearch(true))
                                    showProviderDropdown = false
                                },
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
                        val matchingProviders = remember(uiState.providers, providerModalSearch, providerTypeFilter) {
                            uiState.providers.filter { p ->
                                val matchesQuery = providerModalSearch.isBlank() || p.name.contains(providerModalSearch, ignoreCase = true)
                                val matchesType = providerTypeFilter.isEmpty() || p.supportedTypes.any { it in providerTypeFilter }
                                matchesQuery && matchesType
                            }
                        }

                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 180.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(matchingProviders.size, key = { matchingProviders[it].name }) { idx ->
                                val provider = matchingProviders[idx]
                                val isSelected = !isGlobalSearchEnabled && selectedProviderName == provider.name
                                Surface(
                                    onClick = {
                                        viewModel.onEvent(SearchUiEvent.OnToggleGlobalSearch(false))
                                        viewModel.onEvent(SearchUiEvent.OnProviderSelected(provider.name))
                                        showProviderDropdown = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        val icon = pluginIcons[provider.name] ?: fuzzyMatchIcon(provider.name)
                                        if (icon != null) {
                                            coil3.compose.AsyncImage(
                                                model = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White),
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
                                            Text(
                                                text = provider.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = provider.supportedTypes.take(2).joinToString { it.name },
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
                } // closes dialog

                Spacer(modifier = Modifier.height(14.dp))

                // ── Horizontal Category Filter Chips ──────────────────────────
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // "All" chip — selected when no categories are active
                    item {
                        AnimatedCategoryTab(
                            selected = selectedCategories.isEmpty(),
                            label = "All",
                            onClick = {
                                // Clear all selections → show everything
                                selectedCategories.forEach { cat ->
                                    viewModel.onEvent(SearchUiEvent.OnToggleCategory(cat))
                                }
                            },
                        )
                    }

                    items(categories) { (type, label) ->
                        AnimatedCategoryTab(
                            selected = type in selectedCategories,
                            label = label,
                            onClick = { viewModel.onEvent(SearchUiEvent.OnToggleCategory(type)) },
                        )
                    }
                }
            }
        }

        // ── Content Area ─────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            val hasResults = !searchResultsGrouped.isNullOrEmpty()
            val showEmptyState = !hasResults && !isLoadingSearch
            val showHistory = showEmptyState && uiState.searchQuery.isEmpty() && searchHistory.isNotEmpty()

            if (showHistory) {
                // Search History
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recent Searches",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(onClick = { viewModel.onEvent(SearchUiEvent.OnClearSearchHistory) }) {
                                Text(
                                    "Clear All",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                    items(searchHistory) { query ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered by interactionSource.collectIsHoveredAsState()

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hoverable(interactionSource)
                                .clickable {
                                    viewModel.onEvent(SearchUiEvent.OnSearchQueryChange(query))
                                    viewModel.onEvent(SearchUiEvent.OnSearch)
                                },
                            color = if (isHovered) MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp) else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = query,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = { viewModel.onEvent(SearchUiEvent.OnRemoveSearchHistoryItem(query)) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (showEmptyState) {
                // Empty state (no history or has query but no results yet)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Search your favorite movies, series, or anime",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isGlobalSearchEnabled) {
                            "Searching across all installed plugins."
                        } else {
                            "Searching across your selected plugin."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            } else {
                val resultsList = searchResultsGrouped?.mapNotNull { (providerName, items) ->
                    com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(providerName)?.let { api ->
                        Pair(api, items)
                    }
                }

                SearchResults(
                    searchResultsGrouped = resultsList,
                    selectedCategories = selectedCategories,
                    isLoadingSearch = isLoadingSearch,
                    onViewAll = { provider, title, items ->
                        com.lagradost.cloudstream3.desktop.ui.screens.CategoryGridCache.put(provider.name, title, items)
                        onNavigate(Config.CategoryGrid(provider.name, title))
                    },
                    onItemClick = { provider, item, backdrop, autoPlay ->
                        onNavigate(
                            Config.Details(provider.name, item.url, item.name, item.posterUrl, backdrop, autoPlay),
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun AnimatedCategoryTab(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.height(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
            )
        }
    }
}
