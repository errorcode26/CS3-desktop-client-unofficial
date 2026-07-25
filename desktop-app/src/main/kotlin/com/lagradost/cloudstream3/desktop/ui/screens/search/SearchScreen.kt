package com.lagradost.cloudstream3.desktop.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEvent

@Composable
fun ComposeSearchScreen(
    navController: NavController,
    viewModel: SearchViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingSearch = uiState.isLoadingSearch
    val isGlobalSearchEnabled = uiState.isGlobalSearchEnabled
    val searchResultsGrouped = uiState.searchResultsGrouped
    val selectedProviderName = uiState.selectedProviderName
    val selectedCategory = uiState.selectedCategory
    val pluginIcons = uiState.pluginIcons
    var showProviderDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    fun fuzzyMatchIcon(providerName: String): String? {
        val pName = providerName.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
        return pluginIcons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
            if (kName.length < 3) return@firstOrNull false
            pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
    }

    val categories = listOf(
        null to "All",
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
        // ── Unified Search Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            // Search Bar (Fixed width, forced to absolute center)
            Surface(
                modifier = Modifier.width(500.dp).align(Alignment.Center),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            onValueChange = { newText ->
                                viewModel.onEvent(SearchUiEvent.OnSearchQueryChange(newText))
                            },
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }

                    // Clear Button
                    AnimatedVisibility(
                        visible = uiState.searchQuery.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.onEvent(SearchUiEvent.OnClearSearch)
                            },
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

            // Wrap Dropdowns in a Row anchored to the right side of the screen
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category Selector Button
                Box {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                        onClick = { showCategoryDropdown = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val activeLabel = categories.find { it.first == selectedCategory }?.second ?: "All"
                            Text(
                                text = activeLabel,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false },
                        modifier = Modifier.heightIn(max = 400.dp),
                    ) {
                        categories.forEach { (type, label) ->
                            val isSelected = selectedCategory == type
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = {
                                    viewModel.onEvent(SearchUiEvent.OnCategorySelected(type))
                                    showCategoryDropdown = false
                                },
                            )
                        }
                    }
                }

                // Plugin Selector Button
                Box {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                        onClick = { showProviderDropdown = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
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
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 140.dp),
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
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
                        androidx.compose.material3.HorizontalDivider()
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
                }
            } // End Dropdowns Row
        } // End Header Box

        // ── Results Area ─────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            val hasResults = !searchResultsGrouped.isNullOrEmpty()
            val showEmptyState = !hasResults && !isLoadingSearch

            if (showEmptyState) {
                // Empty state
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
                    selectedCategory = selectedCategory,
                    isLoadingSearch = isLoadingSearch,
                    onViewAll = { provider, title, items ->
                        navController.navigate(Screen.CategoryGrid(provider.name, title, items))
                    },
                    onItemClick = { provider, item, backdrop ->
                        navController.navigate(
                            Screen.Details(provider.name, item.url, item.name, item.posterUrl, backdrop, false),
                        )
                    },
                )
            }
        }
    }
}
