package com.lagradost.cloudstream3.desktop.ui.screens.search

import androidx.compose.animation.*
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
            // Search Bar Area
            Box(modifier = Modifier.fillMaxWidth()) {
                // Search Bar (Mathematically perfectly centered on screen)
                Surface(
                    modifier = Modifier.width(500.dp).align(Alignment.Center),
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

                // Plugin Selector positioned exactly 16dp to the right of the Search Bar
                // We do this by taking the right half of the screen and offsetting by half the search bar width (250dp)
                Row(
                    modifier = Modifier.fillMaxWidth(0.5f).align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(266.dp)) // 250dp (half search bar) + 16dp (gap)

                    // Plugin Selector Chip
                    Box {
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

                    DropdownMenu(
                        expanded = showProviderDropdown,
                        onDismissRequest = { showProviderDropdown = false },
                        modifier = Modifier.heightIn(max = 400.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Plugins", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                viewModel.onEvent(SearchUiEvent.OnToggleGlobalSearch(true))
                                showProviderDropdown = false
                            },
                        )
                        HorizontalDivider()
                        uiState.providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                leadingIcon = {
                                    val icon = pluginIcons[provider.name] ?: fuzzyMatchIcon(provider.name)
                                    if (icon != null) {
                                        coil3.compose.AsyncImage(
                                            model = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White),
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(24.dp))
                                    }
                                },
                                onClick = {
                                    viewModel.onEvent(SearchUiEvent.OnToggleGlobalSearch(false))
                                    viewModel.onEvent(SearchUiEvent.OnProviderSelected(provider.name))
                                    showProviderDropdown = false
                                },
                            )
                        }
                    }
                } // closes inner Box
                } // closes Row
            } // closes outer Box

            Spacer(modifier = Modifier.height(14.dp))

            // ── Horizontal Category Filter Chips ──────────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "All" chip — selected when no categories are active
                item {
                    FilterChip(
                        selected = selectedCategories.isEmpty(),
                        onClick = {
                            // Clear all selections → show everything
                            selectedCategories.forEach { cat ->
                                viewModel.onEvent(SearchUiEvent.OnToggleCategory(cat))
                            }
                        },
                        label = { Text("All", fontWeight = if (selectedCategories.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategories.isEmpty(),
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                    )
                }

                items(categories) { (type, label) ->
                    val isSelected = type in selectedCategories
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onEvent(SearchUiEvent.OnToggleCategory(type)) },
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                    )
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
                        onNavigate(Config.CategoryGrid(provider.name, title, items))
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
