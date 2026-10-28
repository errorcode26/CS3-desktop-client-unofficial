package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.explore.ui.ExploreProviderDialog
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.home.*
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun ComposeHomeScreen(
    onNavigate: (Config) -> Unit,
    viewModel: com.lagradost.cloudstream3.desktop.ui.screens.home.DesktopHomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val providers = uiState.providers
    val activeProviders = uiState.activeProviders
    val activeProviderApis = uiState.activeProviderApis
    val historyList = uiState.historyList
    val mergedPluginIcons = uiState.mergedPluginIcons
    val errorSnapshot = uiState.errorSnapshot

    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()
    val showContinueWatching by AppearanceConfig.showContinueWatching.collectAsState()

    DisposableEffect(viewModel) {
        val unregister = com.lagradost.cloudstream3.desktop.ui.GlobalRefreshHandler.register {
            viewModel.onEvent(HomeUiEvent.OnProviderRefresh)
        }
        onDispose { unregister() }
    }

    var currentHeroImageUrl by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (heroBackgroundBlurEnabled && currentHeroImageUrl != null) {
            androidx.compose.animation.Crossfade(
                targetState = currentHeroImageUrl,
                animationSpec = tween(2000),
                label = "home_global_backdrop_crossfade",
                modifier = Modifier.fillMaxSize(),
            ) { targetBgUrl ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val context = coil3.compose.LocalPlatformContext.current
                    val imageRequest = remember(targetBgUrl) {
                        coil3.request.ImageRequest.Builder(context)
                            .data(targetBgUrl)
                            .size(320, 180)
                            .crossfade(true)
                            .build()
                    }
                    coil3.compose.AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(heroBackdropBlurRadius.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = heroBackdropDarkening)))
                }
            }
        }

        // Main content area
        val allPages = remember(activeProviderApis, uiState.disabledCatalogs, uiState.orderedCatalogs, uiState.refreshEpoch) {
            activeProviderApis.flatMap { prov ->
                val disabledForProv = uiState.disabledCatalogs[prov.name] ?: emptySet()
                val orderedForProv = uiState.orderedCatalogs[prov.name] ?: emptyList()
                val enabledCatalogs = prov.mainPage.filter { it.name !in disabledForProv }
                val sortedCatalogs = if (orderedForProv.isNotEmpty()) {
                    val orderMap = orderedForProv.withIndex().associate { it.value to it.index }
                    enabledCatalogs.sortedBy { orderMap[it.name] ?: Int.MAX_VALUE }
                } else {
                    enabledCatalogs
                }
                sortedCatalogs.map { prov to it }
            }
        }

        LaunchedEffect(allPages) {
            allPages.take(3).forEach { (provider, pageData) ->
                viewModel.onEvent(HomeUiEvent.OnLoadCategory(provider, pageData))
            }
        }

        HomeManagementDialog(
            show = uiState.showHomeManagement,
            allProviders = providers,
            activeProviders = activeProviders,
            disabledCatalogs = uiState.disabledCatalogs,
            orderedCatalogs = uiState.orderedCatalogs,
            pluginIcons = mergedPluginIcons,
            onDismissRequest = { viewModel.onEvent(HomeUiEvent.OnShowHomeManagement(false)) },
            onSetSingleProvider = { name -> viewModel.onEvent(HomeUiEvent.OnSetSingleProvider(name)) },
            onToggleProviderActive = { name, isActive -> viewModel.onEvent(HomeUiEvent.OnToggleProviderActive(name, isActive)) },
            onMoveProvider = { from, to -> viewModel.onEvent(HomeUiEvent.OnMoveProvider(from, to)) },
            onToggleCatalog = { prov, cat, enabled -> viewModel.onEvent(HomeUiEvent.OnToggleCatalog(prov, cat, enabled)) },
            onMoveCatalog = { prov, from, to -> viewModel.onEvent(HomeUiEvent.OnMoveCatalog(prov, from, to)) },
            onResetCatalogOrder = { prov -> viewModel.onEvent(HomeUiEvent.OnResetCatalogOrder(prov)) },
        )

        uiState.orphanRecoveryHistory?.let { orphan ->
            val exploreItem = uiState.orphanExploreItem
            if (exploreItem != null) {
                ExploreProviderDialog(
                    item = exploreItem,
                    matches = uiState.matchedResults,
                    isSearching = uiState.isSearchingMatches,
                    watchHistory = orphan,
                    onDismissRequest = { viewModel.onEvent(HomeUiEvent.OnDismissRecoveryModal) },
                    onSelectMatch = { match ->
                        viewModel.onEvent(HomeUiEvent.OnSelectReLinkMatch(orphan, match))
                    },
                    onOpenAddonMode = { item ->
                        viewModel.onEvent(HomeUiEvent.OnSelectReLinkAddon(orphan, item))
                    },
                )
            }
        }

        if (allPages.isNotEmpty()) {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            DisposableEffect(listState) {
                val unregister = com.lagradost.cloudstream3.desktop.ui.GlobalScrollToTopHandler.register(
                    com.lagradost.cloudstream3.desktop.ui.NavScrollTarget.HOME,
                ) {
                    coroutineScope.launch {
                        if (listState.firstVisibleItemIndex > 5) {
                            listState.scrollToItem(5)
                        }
                        listState.animateScrollToItem(0)
                    }
                }
                onDispose { unregister() }
            }

            val safeArea = com.lagradost.cloudstream3.desktop.ui.LocalSafeArea.current
            val hazeState = com.lagradost.cloudstream3.desktop.ui.LocalHazeState.current

            // Extract individual safe padding components
            val safeLeft = safeArea.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            val safeRight = safeArea.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            val safeTop = safeArea.calculateTopPadding()
            val safeBottom = safeArea.calculateBottomPadding()

            val primaryProvider = allPages.firstOrNull()?.first
            val heroCandidates = remember(uiState.categories, allPages) {
                allPages.mapNotNull { (prov, pageData) ->
                    uiState.categories["${prov.name}_${pageData.name}"]?.response?.items
                }.flatten().flatMap { it.list }.distinctBy { it.url }.take(30)
            }

            val isAnyCategoryLoading = remember(uiState.categories, allPages) {
                allPages.isNotEmpty() && allPages.any { (prov, pageData) ->
                    val cat = uiState.categories["${prov.name}_${pageData.name}"]
                    cat == null || cat.isLoading || (cat.response == null && cat.error == null)
                }
            }

            val isHeroShowing = heroEnabled && (heroCandidates.isNotEmpty() || (isAnyCategoryLoading && heroCandidates.isEmpty()))

            LaunchedEffect(isHeroShowing) {
                if (!isHeroShowing) {
                    currentHeroImageUrl = null
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (hazeState != null) {
                                Modifier.graphicsLayer { }.hazeSource(state = hazeState)
                            } else {
                                Modifier
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy(homeVerticalSpacingDp.dp),
                    contentPadding = PaddingValues(
                        top = if (isHeroShowing) 0.dp else safeTop + 8.dp,
                        bottom = safeBottom + 32.dp,
                    ),
                ) {
                    if (isHeroShowing) {
                        item(key = "home_hero_banner_${uiState.refreshEpoch}") {
                            if (heroCandidates.isNotEmpty()) {
                                HomeHeroCarousel(
                                    items = heroCandidates,
                                    provider = primaryProvider,
                                    heroMetaMap = uiState.heroMetaMap,
                                    allBookmarks = uiState.bookmarks,
                                    onPrefetchHeroItem = { prov, item ->
                                        viewModel.onEvent(HomeUiEvent.OnPrefetchHeroItem(prov ?: primaryProvider, item))
                                    },
                                    onHeroBackgroundChanged = { url ->
                                        currentHeroImageUrl = url
                                    },
                                    onItemClick = { item, backdrop, autoPlay ->
                                        val provName = item.apiName.takeIf { it.isNotBlank() } ?: primaryProvider?.name ?: ""
                                        onNavigate(
                                            Config.Details(
                                                providerName = provName,
                                                url = item.url,
                                                preloadedName = item.name,
                                                preloadedPoster = item.posterUrl,
                                                preloadedBg = backdrop,
                                                autoPlay = autoPlay,
                                            ),
                                        )
                                    },
                                )
                            } else {
                                HomeHeroCarouselPlaceholder()
                            }
                        }
                    }

                    if (showContinueWatching && historyList.isNotEmpty()) {
                        item(key = "home_continue_watching") {
                            HomeHistoryRow(
                                historyList = historyList,
                                providers = providers,
                                onClearHistory = { viewModel.onEvent(HomeUiEvent.OnClearHistory) },
                                onRemoveHistoryItem = { viewModel.onEvent(HomeUiEvent.OnRemoveHistoryItem(it)) },
                                onViewAllClick = {
                                    onNavigate(Config.History)
                                },
                                onItemClick = { prov, hist ->
                                    onNavigate(Config.Details(prov.name, hist.showUrl, hist.showName, hist.posterUrl, null, autoPlay = false, targetSeason = hist.season, targetEpisodeId = hist.episodeId))
                                },
                                onPlayClick = { prov, hist ->
                                    onNavigate(Config.Details(prov.name, hist.showUrl, hist.showName, hist.posterUrl, null, autoPlay = true, targetSeason = hist.season, targetEpisodeId = hist.episodeId))
                                },
                                onReLinkClick = { hist ->
                                    viewModel.onEvent(HomeUiEvent.OnStartReLink(hist))
                                },
                            )
                        }
                    }

                    items(allPages.size, key = { index -> "${allPages[index].first.name}_${allPages[index].first.mainUrl}_${allPages[index].second.name}_${uiState.refreshEpoch}_$index" }) { index ->
                        val (currentProvider, pageData) = allPages[index]
                        Box(modifier = Modifier.fillMaxWidth()) {
                            HomeCategorySection(
                                pageData = pageData,
                                provider = currentProvider,
                                categoryState = uiState.categories["${currentProvider.name}_${pageData.name}"],
                                onLoadCategory = {
                                    viewModel.onEvent(HomeUiEvent.OnLoadCategory(currentProvider, pageData))
                                },
                                outerPadding = 20.dp,
                                onViewAll = { provider, title, items ->
                                    CategoryGridCache.put(provider.name, title, items)
                                    onNavigate(Config.CategoryGrid(provider.name, title))
                                },
                                onItemClick = { provider, item, backdrop, autoPlay ->
                                    onNavigate(Config.Details(provider.name, item.url, item.name, item.posterUrl, backdrop, autoPlay))
                                },
                            )
                        }
                    }
                }
            }
        } else if (providers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                        contentDescription = "No providers",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        com.lagradost.cloudstream3.desktop.utils.DesktopStrings.NO_PROVIDERS_FOUND,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        com.lagradost.cloudstream3.desktop.utils.DesktopStrings.PLEASE_INSTALL_PLUGINS,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onNavigate(Config.Extensions(initialTab = 0)) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        ) {
                            Text("Browse Plugins")
                        }
                        OutlinedButton(
                            onClick = { onNavigate(Config.Extensions(initialTab = 3)) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        ) {
                            Text("Install Add-ons")
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeHeroCarouselPlaceholder()
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryRowPlaceholder(title = "Loading...", showLargeHeader = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryRowPlaceholder(title = "Loading...", showLargeHeader = true)
                }
            }
        }
    }
}
