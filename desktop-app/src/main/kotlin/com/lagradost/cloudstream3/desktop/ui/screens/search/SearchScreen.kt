package com.lagradost.cloudstream3.desktop.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.explore.ui.ExploreProviderDialog
import com.lagradost.cloudstream3.desktop.explore.ui.ExploreSearchResultsView
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.CategoryGridCache
import com.lagradost.cloudstream3.desktop.ui.screens.search.components.AnimatedCategoryTab
import com.lagradost.cloudstream3.desktop.ui.screens.search.components.SearchHistoryView
import com.lagradost.cloudstream3.desktop.ui.screens.search.components.SearchSuggestionsOverlay
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.search.dialogs.ProviderSelectionDialog
import com.lagradost.cloudstream3.desktop.ui.screens.search.dialogs.fuzzyMatchPluginIcon

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

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        viewModel.onEvent(SearchUiEvent.OnEnterSearchScreen)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val safeArea = com.lagradost.cloudstream3.desktop.ui.LocalSafeArea.current
    val topPadding = safeArea.calculateTopPadding().coerceAtLeast(64.dp) + 8.dp
    val dockPosition by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.dockPosition.collectAsState()
    val dockPaddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 88.dp else 24.dp
    val dockPaddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 88.dp else 24.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (uiState.showSuggestions) {
                    viewModel.onEvent(SearchUiEvent.OnDismissSuggestions)
                }
            },
    ) {
        // ── Search Header ──────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = dockPaddingStart, end = dockPaddingEnd, top = topPadding, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().zIndex(50f),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ── Centered Search Bar Capsule (Wide Desktop Profile with Integrated Controls) ──
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        color = Color(0xFF14141A).copy(alpha = 0.75f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            0.5.dp,
                            if (isSearchFocused) {
                                Color.White.copy(alpha = 0.35f)
                            } else {
                                Color.White.copy(alpha = 0.12f)
                            },
                        ),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isSearchFocused || uiState.searchQuery.isNotEmpty()) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(18.dp),
                            )

                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (uiState.searchQuery.isEmpty()) {
                                    Text(
                                        if (uiState.isAddonMode) "Search catalog addons (movies, series, anime)..." else "Search movies, series, anime...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontSize = 13.5.sp,
                                    )
                                }
                                BasicTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.onEvent(SearchUiEvent.OnSearchQueryChange(it)) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        viewModel.onEvent(SearchUiEvent.OnSearch)
                                    }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { isSearchFocused = it.isFocused }
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                                if (uiState.showSuggestions) {
                                                    viewModel.onEvent(SearchUiEvent.OnDismissSuggestions)
                                                    true
                                                } else if (uiState.searchQuery.isNotEmpty()) {
                                                    viewModel.onEvent(SearchUiEvent.OnClearSearch)
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else {
                                                false
                                            }
                                        },
                                )
                            }

                            AnimatedVisibility(
                                visible = uiState.searchQuery.isNotEmpty(),
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                            ) {
                                IconButton(
                                    onClick = { viewModel.onEvent(SearchUiEvent.OnClearSearch) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            if (!uiState.isAddonMode) {
                                // Subtle vertical separator
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                )

                                // Embedded Multi-Source Selector Chip (Matches Upstream SearchFilter)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    onClick = { showProviderDropdown = true },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        val selectedNames = uiState.selectedProviderNames
                                        val totalCount = uiState.providers.size
                                        if (selectedNames.size == 1) {
                                            val singleName = selectedNames.first()
                                            val icon = pluginIcons[singleName] ?: fuzzyMatchPluginIcon(singleName, pluginIcons)
                                            if (icon != null) {
                                                AsyncImage(
                                                    model = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White),
                                                )
                                            }
                                        } else {
                                            Icon(
                                                Icons.Default.Tune,
                                                contentDescription = "Filter Sources",
                                                modifier = Modifier.size(15.dp),
                                                tint = Color.White.copy(alpha = 0.75f),
                                            )
                                        }

                                        val label = when {
                                            selectedNames.isEmpty() || (totalCount > 0 && selectedNames.size == totalCount) -> "All Sources"
                                            selectedNames.size == 1 -> selectedNames.first()
                                            else -> "${selectedNames.size} Sources"
                                        }

                                        Text(
                                            text = label,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            modifier = Modifier.widthIn(max = 115.dp),
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // Subtle vertical separator between search controls and mode toggle
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            )

                            // Mode Switcher Segmented Pill (Addons vs Extensions)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Addons Pill
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (uiState.isAddonMode) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                            .then(if (uiState.isAddonMode) Modifier.border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)), RoundedCornerShape(8.dp)) else Modifier)
                                            .clickable {
                                                if (!uiState.isAddonMode) {
                                                    viewModel.onEvent(SearchUiEvent.OnToggleSearchMode(true))
                                                }
                                            }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.Explore,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (uiState.isAddonMode) Color.White else Color.White.copy(alpha = 0.55f),
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "Addons",
                                                fontSize = 12.sp,
                                                letterSpacing = 0.2.sp,
                                                fontWeight = if (uiState.isAddonMode) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (uiState.isAddonMode) Color.White else Color.White.copy(alpha = 0.55f),
                                            )
                                        }
                                    }

                                    // Extensions Pill
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (!uiState.isAddonMode) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                            .then(if (!uiState.isAddonMode) Modifier.border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)), RoundedCornerShape(8.dp)) else Modifier)
                                            .clickable {
                                                if (uiState.isAddonMode) {
                                                    viewModel.onEvent(SearchUiEvent.OnToggleSearchMode(false))
                                                }
                                            }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.Extension,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (!uiState.isAddonMode) Color.White else Color.White.copy(alpha = 0.55f),
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "Extensions",
                                                fontSize = 12.sp,
                                                letterSpacing = 0.2.sp,
                                                fontWeight = if (!uiState.isAddonMode) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (!uiState.isAddonMode) Color.White else Color.White.copy(alpha = 0.55f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Upstream-Aligned Multi-Source Selection Modal
                    ProviderSelectionDialog(
                        show = showProviderDropdown,
                        onDismissRequest = { showProviderDropdown = false },
                        providers = uiState.providers,
                        pluginIcons = pluginIcons,
                        selectedProviderNames = uiState.selectedProviderNames,
                        onApplySelectedProviders = { selectedSet ->
                            viewModel.onEvent(SearchUiEvent.OnApplySelectedProviders(selectedSet))
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Horizontal Category Filter Chips (Dynamic Across Both Modes) ──
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uiState.isAddonMode) {
                            val currentDrilled = uiState.drilledSearchCategory
                            items(uiState.availableAddonCategories, key = { it }) { category ->
                                val isSelected = if (category == "All") currentDrilled == null else currentDrilled == category
                                AnimatedCategoryTab(
                                    selected = isSelected,
                                    label = category,
                                    onClick = {
                                        val target = if (category == "All") null else category
                                        viewModel.onEvent(SearchUiEvent.OnDrillSearchCategory(target))
                                    },
                                )
                            }
                        } else {
                            item {
                                AnimatedCategoryTab(
                                    selected = selectedCategories.isEmpty(),
                                    label = "All",
                                    onClick = {
                                        viewModel.onEvent(SearchUiEvent.OnClearCategories)
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
                    }
                }

                // ── Floating Search Suggestions Dropdown Overlay ──────────────────────
                SearchSuggestionsOverlay(
                    visible = uiState.showSuggestions && uiState.searchSuggestions.isNotEmpty() && uiState.searchQuery.isNotEmpty(),
                    suggestions = uiState.searchSuggestions,
                    modifier = Modifier.fillMaxWidth(),
                    onSelectSuggestion = { title, submit ->
                        viewModel.onEvent(SearchUiEvent.OnSelectSuggestion(title, submitSearch = submit))
                    },
                    onFillSuggestion = { title ->
                        viewModel.onEvent(SearchUiEvent.OnSelectSuggestion(title, submitSearch = false))
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {}
                    },
                )
            }
        }

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
                },
        ) {
            if (uiState.isAddonMode && (uiState.searchQuery.isNotBlank() || uiState.drilledSearchCategory != null)) {
                ExploreSearchResultsView(
                    searchQuery = uiState.searchQuery,
                    searchResults = uiState.addonSearchResults,
                    isSearching = uiState.isLoadingSearch,
                    isShelvesMode = true,
                    drilledCategory = uiState.drilledSearchCategory,
                    recommendations = emptyList(),
                    watchHistoryMap = emptyMap(),
                    autoCleanTitles = true,
                    onDrillCategory = { cat -> viewModel.onEvent(SearchUiEvent.OnDrillSearchCategory(cat)) },
                    onReturnFromDrill = { viewModel.onEvent(SearchUiEvent.OnDrillSearchCategory(null)) },
                    onClearSearch = { viewModel.onEvent(SearchUiEvent.OnClearSearch) },
                    onItemClick = { item -> viewModel.onEvent(SearchUiEvent.OnOpenProviderPicker(item)) },
                    contentTopPadding = 12.dp,
                )
            } else {
                val hasResults = !searchResultsGrouped.isNullOrEmpty()
                val showEmptyState = !hasResults && !isLoadingSearch
                val showHistory = showEmptyState && uiState.searchQuery.isEmpty() && searchHistory.isNotEmpty()

                if (showHistory) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(start = dockPaddingStart, end = dockPaddingEnd)) {
                            SearchHistoryView(
                                searchHistory = searchHistory,
                                onSelectHistoryItem = { item ->
                                    viewModel.onEvent(SearchUiEvent.OnSearchQueryChange(item))
                                    viewModel.onEvent(SearchUiEvent.OnSearch)
                                },
                                onRemoveHistoryItem = { item ->
                                    viewModel.onEvent(SearchUiEvent.OnRemoveSearchHistoryItem(item))
                                },
                                onClearAll = {
                                    viewModel.onEvent(SearchUiEvent.OnClearSearchHistory)
                                },
                            )
                        }
                    }
                } else if (showEmptyState) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = 72.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (uiState.searchQuery.isEmpty()) {
                                if (uiState.isAddonMode) "Search catalog addons" else "Search movies, series, or anime"
                            } else {
                                "No results found for \"${uiState.searchQuery}\""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (uiState.searchQuery.isEmpty()) {
                                if (uiState.isAddonMode) {
                                    "Search across catalogs with multi-extension stream matching."
                                } else if (uiState.selectedProviderNames.isEmpty()) {
                                    "Searching across all installed plugins."
                                } else {
                                    "Searching across ${uiState.selectedProviderNames.size} selected sources."
                                }
                            } else {
                                if (uiState.isAddonMode) {
                                    "Try checking your spelling or search a different title."
                                } else if (uiState.selectedProviderNames.isEmpty()) {
                                    "Try checking your spelling or search a different title."
                                } else {
                                    "Try selecting more sources or check your spelling."
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
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
                        isGlobalSearchEnabled = isGlobalSearchEnabled || uiState.selectedProviderNames.isEmpty() || uiState.selectedProviderNames.size > 1,
                        onLoadMore = { viewModel.onEvent(SearchUiEvent.OnLoadMore) },
                        onViewAll = { provider, title, items ->
                            CategoryGridCache.put(provider.name, title, items)
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

        // Multi-Provider Match Resolver Dialog for Addon Mode
        if (uiState.selectedItemForMatch != null) {
            ExploreProviderDialog(
                item = uiState.selectedItemForMatch,
                matches = uiState.providerMatches,
                isSearching = uiState.isSearchingProviders,
                watchHistory = null,
                onDismissRequest = { viewModel.onEvent(SearchUiEvent.OnCloseProviderPicker) },
                onNavigateToExtensions = {
                    viewModel.onEvent(SearchUiEvent.OnCloseProviderPicker)
                    onNavigate(Config.Extensions(initialTab = 0))
                },
                onSelectMatch = { match ->
                    viewModel.onEvent(SearchUiEvent.OnCloseProviderPicker)
                    onNavigate(
                        Config.Details(
                            providerName = match.providerName,
                            url = match.searchResponse.url,
                            preloadedName = match.displayTitle,
                            preloadedPoster = match.searchResponse.posterUrl,
                        ),
                    )
                },
                onOpenDetails = { providerName, url, title ->
                    viewModel.onEvent(SearchUiEvent.OnCloseProviderPicker)
                    onNavigate(
                        Config.Details(
                            providerName = providerName,
                            url = url,
                            preloadedName = title,
                        ),
                    )
                },
                onOpenAddonMode = { item ->
                    viewModel.onEvent(SearchUiEvent.OnCloseProviderPicker)
                    onNavigate(
                        Config.Details(
                            providerName = "Stremio",
                            url = "stremio://${item.type}/${item.id}",
                            preloadedName = item.name,
                            preloadedPoster = item.posterUrl,
                            preloadedBg = item.backgroundUrl,
                        ),
                    )
                },
            )
        }
    }
}
