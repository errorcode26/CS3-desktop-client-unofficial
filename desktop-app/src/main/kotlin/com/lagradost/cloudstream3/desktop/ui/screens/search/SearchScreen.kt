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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEvent

private val SANITIZE_NAME_REGEX = Regex("[^a-z0-9]")

private val SEARCH_CATEGORIES = listOf(
    TvType.Movie to "Movies",
    TvType.TvSeries to "Series",
    TvType.Anime to "Anime",
    TvType.Documentary to "Documentaries",
    TvType.Live to "Live",
)

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
    var isSearchFocused by remember { mutableStateOf(false) }
    // Local filter for the provider picker — does not affect the search itself
    var providerTypeFilter by remember { mutableStateOf(emptySet<TvType>()) }

    fun fuzzyMatchIcon(providerName: String): String? {
        val pName = providerName.lowercase().replace(SANITIZE_NAME_REGEX, "").replace("provider", "").replace("plugin", "")
        return pluginIcons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(SANITIZE_NAME_REGEX, "").replace("provider", "").replace("plugin", "")
            if (kName.length < 3) return@firstOrNull false
            pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
    }

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
            // Unified container to perfectly center the search capsule and categories
            Box(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().zIndex(50f),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Unified Search Bar & Plugin Selector Capsule
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    "Search movies, series, anime...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 15.sp,
                                )
                            }
                            BasicTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.onEvent(SearchUiEvent.OnSearchQueryChange(it)) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    viewModel.onEvent(SearchUiEvent.OnSearch)
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isSearchFocused = it.isFocused },
                            )
                        }

                        AnimatedVisibility(
                            visible = uiState.searchQuery.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            IconButton(
                                onClick = { viewModel.onEvent(SearchUiEvent.OnClearSearch) },
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Subtle vertical separator
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        )

                        // Embedded Plugin Selector Chip
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                            onClick = { showProviderDropdown = true },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (!isGlobalSearchEnabled && selectedProviderName != null) {
                                    val icon = pluginIcons[selectedProviderName] ?: fuzzyMatchIcon(selectedProviderName)
                                    if (icon != null) {
                                        coil3.compose.AsyncImage(
                                            model = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White),
                                        )
                                    }
                                }
                                Text(
                                    text = if (isGlobalSearchEnabled) "All Plugins" else (selectedProviderName ?: "Select Plugin"),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
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
                    }
                } // closes outer Surface

                // ── Provider Selection Modal ─────────────────────────────────
                var providerModalSearch by remember { mutableStateOf("") }
                com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog(
                    show = showProviderDropdown,
                    onDismissRequest = { showProviderDropdown = false },
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
                            uiState.providers
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

                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 180.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 440.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(matchingProviders.size, key = { idx -> "${matchingProviders[idx].name}_${matchingProviders[idx].mainUrl}_${matchingProviders[idx].sourcePlugin ?: ""}_$idx" }) { idx ->
                                val provider = matchingProviders[idx]
                                val isSelected = !isGlobalSearchEnabled && selectedProviderName == provider.name && (uiState.selectedProviderSource == null || uiState.selectedProviderSource == provider.sourcePlugin)
                                Surface(
                                    onClick = {
                                        viewModel.onEvent(SearchUiEvent.OnToggleGlobalSearch(false))
                                        viewModel.onEvent(SearchUiEvent.OnProviderSelected(provider.name, provider.sourcePlugin))
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
                                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
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
                                            val repoTag = if (provider.name in duplicateNames) {
                                                provider.sourcePlugin?.let {
                                                    try {
                                                        java.io.File(it).parentFile?.name?.replace("_", " ")
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
                                                java.net.URI(provider.mainUrl).host ?: provider.mainUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
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
                } // closes dialog

                Spacer(modifier = Modifier.height(14.dp))

                // ── Horizontal Category Filter Chips ──────────────────────────
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
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

                    items(SEARCH_CATEGORIES, key = { it.first.name }) { (type, label) ->
                        AnimatedCategoryTab(
                            selected = type in selectedCategories,
                            label = label,
                            onClick = { viewModel.onEvent(SearchUiEvent.OnToggleCategory(type)) },
                        )
                    }
                }
            } // closes inner Column

            // ── Floating Search Suggestions Dropdown Overlay ──────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.showSuggestions && uiState.searchSuggestions.isNotEmpty() && uiState.searchQuery.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .padding(top = 54.dp)
                    .fillMaxWidth()
                    .zIndex(100f),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        uiState.searchSuggestions.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onEvent(SearchUiEvent.OnSelectSuggestion(item.title, submitSearch = true))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        if (item.isHistory) Icons.Default.History else Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (item.isHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.isHistory) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.year != null) {
                                        Text(
                                            text = "(${item.year})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                }

                                // Fill Arrow (Sets search query without submitting immediately)
                                IconButton(
                                    onClick = {
                                        viewModel.onEvent(SearchUiEvent.OnSelectSuggestion(item.title, submitSearch = false))
                                        try { focusRequester.requestFocus() } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Fill search text",
                                        modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 135f),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }

                            if (index < uiState.searchSuggestions.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                )
                            }
                        }
                    }
                }
            }
        } // closes outer Box
    } // closes Search Header Column

    // ── Content Area ─────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (uiState.showSuggestions) {
                    viewModel.onEvent(SearchUiEvent.OnDismissSuggestions)
                }
            }
    ) {
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recent Searches",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(
                                onClick = { viewModel.onEvent(SearchUiEvent.OnClearSearchHistory) },
                            ) {
                                Text("Clear All", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            }
                        }
                    }

                    items(searchHistory) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                            onClick = {
                                viewModel.onEvent(SearchUiEvent.OnSearchQueryChange(item))
                                viewModel.onEvent(SearchUiEvent.OnSearch)
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.onEvent(SearchUiEvent.OnRemoveSearchHistoryItem(item)) },
                                    modifier = Modifier.size(24.dp),
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
                    modifier = Modifier.fillMaxSize().padding(top = 56.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
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
                val resultsList = searchResultsGrouped?.values?.toList()

                SearchResults(
                    searchResultsGrouped = resultsList,
                    selectedCategories = selectedCategories,
                    isLoadingSearch = isLoadingSearch,
                    isLoadingMore = uiState.isLoadingMore,
                    canPaginate = uiState.canPaginate,
                    isGlobalSearchEnabled = isGlobalSearchEnabled,
                    onLoadMore = { viewModel.onEvent(SearchUiEvent.OnLoadMore) },
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
