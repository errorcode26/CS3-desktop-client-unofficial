package com.lagradost.cloudstream3.desktop.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
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
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Search Header ──────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
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
                        // Unified Search Bar & Plugin Selector Capsule (Sleek Desktop Profile)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSearchFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            ),
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 14.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (isSearchFocused || uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )

                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (uiState.searchQuery.isEmpty()) {
                                        Text(
                                            "Search movies, series, anime...",
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
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                                                    if (uiState.searchQuery.isNotEmpty()) {
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

                                // Subtle vertical separator
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                )

                                // Embedded Plugin Selector Chip (Compact flush pill)
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
                                        if (!isGlobalSearchEnabled && selectedProviderName != null) {
                                            val icon = pluginIcons[selectedProviderName] ?: fuzzyMatchPluginIcon(selectedProviderName, pluginIcons)
                                            if (icon != null) {
                                                AsyncImage(
                                                    model = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White),
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isGlobalSearchEnabled) "All Plugins" else (selectedProviderName ?: "Select Plugin"),
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
                        }

                        // Provider Selection Modal
                        ProviderSelectionDialog(
                            show = showProviderDropdown,
                            onDismissRequest = { showProviderDropdown = false },
                            providers = uiState.providers,
                            pluginIcons = pluginIcons,
                            selectedProviderName = selectedProviderName,
                            selectedProviderSource = uiState.selectedProviderSource,
                            isGlobalSearchEnabled = isGlobalSearchEnabled,
                            providerTypeFilter = uiState.providerTypeFilter,
                            onSelectGlobalSearch = {
                                viewModel.onEvent(SearchUiEvent.OnToggleGlobalSearch(true))
                                showProviderDropdown = false
                            },
                            onSelectProvider = { name, source, mainUrl ->
                                viewModel.onEvent(SearchUiEvent.OnToggleGlobalSearch(false))
                                viewModel.onEvent(SearchUiEvent.OnProviderSelected(name, source, mainUrl))
                                showProviderDropdown = false
                            },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Horizontal Category Filter Chips ──────────────────────────
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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

                    // ── Floating Search Suggestions Dropdown Overlay ──────────────────────
                    SearchSuggestionsOverlay(
                        visible = uiState.showSuggestions && uiState.searchSuggestions.isNotEmpty() && uiState.searchQuery.isNotEmpty(),
                        suggestions = uiState.searchSuggestions,
                        onSelectSuggestion = { title, submit ->
                            viewModel.onEvent(SearchUiEvent.OnSelectSuggestion(title, submitSearch = submit))
                        },
                        onFillSuggestion = { title ->
                            viewModel.onEvent(SearchUiEvent.OnSelectSuggestion(title, submitSearch = false))
                            try { focusRequester.requestFocus() } catch (_: Exception) {}
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
                val hasResults = !searchResultsGrouped.isNullOrEmpty()
                val showEmptyState = !hasResults && !isLoadingSearch
                val showHistory = showEmptyState && uiState.searchQuery.isEmpty() && searchHistory.isNotEmpty()

                if (showHistory) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
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
                            if (uiState.searchQuery.isEmpty()) "Search movies, series, or anime"
                            else "No results found for \"${uiState.searchQuery}\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (uiState.searchQuery.isEmpty()) {
                                if (isGlobalSearchEnabled) "Searching across all installed plugins."
                                else "Searching across your selected plugin."
                            } else {
                                if (isGlobalSearchEnabled) "Try checking your spelling or search a different title."
                                else "Try switching to All Plugins or check your spelling."
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
                        isGlobalSearchEnabled = isGlobalSearchEnabled,
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
    }
