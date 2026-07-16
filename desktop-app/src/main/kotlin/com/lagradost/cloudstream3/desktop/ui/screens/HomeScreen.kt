package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache
import com.lagradost.cloudstream3.desktop.ui.screens.home.*
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
// haze imports removed

@Composable
fun ComposeHomeScreen(
    navController: NavController,
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { HomeViewModel(coroutineScope) }

    val providers by viewModel.providers.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResultsGrouped by viewModel.searchResultsGrouped.collectAsState()
    val isLoadingSearch by viewModel.isLoadingSearch.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val mergedPluginIcons by viewModel.mergedPluginIcons.collectAsState()
    val errorSnapshot by viewModel.errorSnapshot.collectAsState()

    val hasUnreadUpdates by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.hasUnreadUpdates() }
        .collectAsState(initial = DesktopDataStore.hasUnreadUpdates())

    val updatesHistory by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.getUpdatesHistory() }
        .collectAsState(initial = DesktopDataStore.getUpdatesHistory())

    var isProviderDropdownExpanded by remember { mutableStateOf(false) }



    Box(modifier = Modifier.fillMaxSize()) {

        if (searchQuery.isNotBlank() || searchResultsGrouped != null) {
            HomeSearchResults(
                searchResultsGrouped = searchResultsGrouped,
                isLoadingSearch = isLoadingSearch,
                onViewAll = { provider, title, items ->
                    navController.navigate(Screen.CategoryGrid(provider, title, items))
                },
                onItemClick = { provider, item, backdrop ->
                    navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl, backdrop))
                },
            )
        } else if (selectedProvider != null && selectedProvider!!.hasMainPage && selectedProvider!!.mainPage.isNotEmpty()) {
            val currentProvider = selectedProvider!!
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()

            val searchBarMode by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.searchBarMode.collectAsState()

            var isSearchForced by remember { mutableStateOf(false) }
            val searchTrigger by com.lagradost.cloudstream3.desktop.ui.DesktopUiState.searchFocusTrigger.collectAsState()

            LaunchedEffect(searchTrigger) {
                if (searchTrigger > 0) {
                    isSearchForced = true
                    listState.animateScrollToItem(0)
                }
            }

            val currentScrollOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }
            val currentItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

            LaunchedEffect(currentScrollOffset, currentItemIndex) {
                if (isSearchForced && (currentItemIndex > 0 || currentScrollOffset > 50)) {
                    isSearchForced = false
                }
            }

            val isTopBarVisible = when {
                searchBarMode == "Always Visible" -> true
                else -> isSearchForced
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    if (currentProvider.mainPage.isNotEmpty()) {
                        item {
                                HomeCategorySection(
                                    pageData = currentProvider.mainPage[0],
                                    provider = currentProvider,
                                    isFirstPage = true,
                                    parentScope = coroutineScope,
                                    viewModel = viewModel,
                                    afterHeroContent = {
                                        HomeHistoryRow(
                                            historyList = historyList,
                                            providers = providers,
                                            onClearHistory = { viewModel.clearHistory() },
                                            onRemoveHistoryItem = { viewModel.removeHistoryItem(it) },
                                            onItemClick = { prov, hist ->
                                                navController.navigate(Screen.Details(prov, hist.showUrl, hist.showName, hist.posterUrl, null))
                                            },
                                        )
                                    },
                                    onViewAll = { provider, title, items ->
                                        navController.navigate(Screen.CategoryGrid(provider, title, items))
                                    },
                                    onItemClick = { provider, item, backdrop, autoPlay ->
                                        navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl, backdrop, autoPlay))
                                    },
                                )
                        }
                    }

                    if (currentProvider.mainPage.size > 1) {
                        items(currentProvider.mainPage.size - 1, key = { index -> currentProvider.mainPage[index + 1].name }) { index ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                HomeCategorySection(
                                    pageData = currentProvider.mainPage[index + 1],
                                    provider = currentProvider,
                                    isFirstPage = false,
                                    parentScope = coroutineScope,
                                    viewModel = viewModel,
                                    onViewAll = { provider, title, items ->
                                        navController.navigate(Screen.CategoryGrid(provider, title, items))
                                    },
                                    onItemClick = { provider, item, backdrop, autoPlay ->
                                        navController.navigate(Screen.Details(provider, item.url, item.name, item.posterUrl, backdrop, autoPlay))
                                    },
                                )
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isTopBarVisible,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    HomeTopBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.searchQuery.value = it },
                        onSearch = { viewModel.search() },
                        providers = providers,
                        selectedProvider = selectedProvider,
                        onProviderSelected = {
                            viewModel.selectedProviderName.value = it
                            viewModel.searchResultsGrouped.value = null
                        },
                        mergedPluginIcons = mergedPluginIcons,
                        onProviderSelectClick = { isProviderDropdownExpanded = true }
                    )
                }
            }
        } else if (providers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                        contentDescription = "No providers",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Providers Found",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please go to the Extensions tab to install some plugins.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { navController.navigate(Screen.Extensions) }) {
                        Text("Go to Extensions")
                    }
                }
            }

            HomeTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.searchQuery.value = it },
                onSearch = { viewModel.search() },
                providers = providers,
                selectedProvider = selectedProvider,
                onProviderSelected = {
                    viewModel.selectedProviderName.value = it
                    viewModel.searchResultsGrouped.value = null
                },
                mergedPluginIcons = mergedPluginIcons,
                onProviderSelectClick = { isProviderDropdownExpanded = true }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeHeroCarouselPlaceholder()
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryRowPlaceholder(title = "Loading...", maxWidthConstraint = 1400.dp, showLargeHeader = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryRowPlaceholder(title = "Loading...", maxWidthConstraint = 1400.dp, showLargeHeader = true)
                }
            }

            HomeTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.searchQuery.value = it },
                onSearch = { viewModel.search() },
                providers = providers,
                selectedProvider = selectedProvider,
                onProviderSelected = {
                    viewModel.selectedProviderName.value = it
                    viewModel.searchResultsGrouped.value = null
                },
                mergedPluginIcons = mergedPluginIcons,
                onProviderSelectClick = { isProviderDropdownExpanded = true }
            )
        }
    }

    if (searchQuery.isNotBlank() || searchResultsGrouped != null) {
        // When searching, top bar is always visible overlaying everything
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            HomeTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.searchQuery.value = it },
                onSearch = { viewModel.search() },
                providers = providers,
                selectedProvider = selectedProvider,
                onProviderSelected = {
                    viewModel.selectedProviderName.value = it
                    viewModel.searchResultsGrouped.value = null
                },
                mergedPluginIcons = mergedPluginIcons,
                onProviderSelectClick = { isProviderDropdownExpanded = true }
            )
        }
    }

    if (isProviderDropdownExpanded) {
        ProviderSelectionOverlay(
            providers = providers,
            selectedProvider = selectedProvider,
            onProviderSelected = {
                viewModel.selectedProviderName.value = it
                viewModel.searchResultsGrouped.value = null
                isProviderDropdownExpanded = false
            },
            mergedPluginIcons = mergedPluginIcons,
            onDismiss = { isProviderDropdownExpanded = false }
        )
    }
}
