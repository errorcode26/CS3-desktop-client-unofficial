package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCollectionDetail
import com.lagradost.cloudstream3.desktop.explore.client.ExploreHubClient
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ExploreSearchScope
import com.lagradost.cloudstream3.desktop.explore.models.ExploreShelf
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.models.StreamingPlatform
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchEngine
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchResults
import com.lagradost.cloudstream3.desktop.explore.viewmodel.EXPLORE_YEAR_OPTIONS
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEffect
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEvent
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiState
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreViewModel
import com.lagradost.cloudstream3.desktop.repo.HeroMeta
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.getTextShadow
import com.lagradost.cloudstream3.desktop.ui.components.posterDepthEffect
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.home.HomeHeroCarousel
import com.lagradost.cloudstream3.desktop.ui.screens.home.HomeHeroCarouselPlaceholder
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.PosterMetadataPosition
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTextAlignment
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onNavigate: (Config) -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val uiState by viewModel.uiState.collectAsState()
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    var currentHeroImageUrl by remember { mutableStateOf<String?>(null) }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val catalogListState = rememberLazyListState()
    val shelvesListState = rememberLazyListState()

    DisposableEffect(shelvesListState, gridState) {
        val unregister = com.lagradost.cloudstream3.desktop.ui.GlobalScrollToTopHandler.register(
            com.lagradost.cloudstream3.desktop.ui.NavScrollTarget.EXPLORE,
        ) {
            coroutineScope.launch {
                if (uiState.selectedCollection != null) {
                    viewModel.onEvent(ExploreUiEvent.CloseCollection)
                }
                if (uiState.drilledCatalog != null || uiState.drilledPlatform != null) {
                    viewModel.onEvent(ExploreUiEvent.ReturnToShelves)
                }
                if (uiState.drilledSearchCategory != null) {
                    viewModel.onEvent(ExploreUiEvent.ReturnFromSearchCategory)
                }
                if (shelvesListState.firstVisibleItemIndex > 5) {
                    shelvesListState.scrollToItem(5)
                }
                shelvesListState.animateScrollToItem(0)
                if (gridState.firstVisibleItemIndex > 8) {
                    gridState.scrollToItem(8)
                }
                gridState.animateScrollToItem(0)
            }
        }
        onDispose { unregister() }
    }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is ExploreUiEffect.OpenDetails -> {
                    onNavigate(
                        Config.Details(
                            providerName = effect.providerName,
                            url = effect.url,
                            preloadedName = effect.title,
                        ),
                    )
                }
            }
        }
    }

    // Scroll to top when catalog, genre, year, or query changes
    LaunchedEffect(uiState.selectedCatalog, uiState.selectedGenre, uiState.selectedYear) {
        gridState.scrollToItem(0)
    }

    // Scroll to top when switching Explore category tabs
    LaunchedEffect(uiState.selectedType) {
        shelvesListState.scrollToItem(0)
    }

    // Infinite scrolling / pagination trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 8
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoading && !uiState.isLoadingMore && uiState.canLoadMore) {
            viewModel.onEvent(ExploreUiEvent.LoadMore)
        }
    }

    val isLandscapeGrid = uiState.selectedType.equals("tv", ignoreCase = true) ||
        uiState.selectedType.equals("channel", ignoreCase = true) ||
        uiState.selectedCatalog?.posterShape.equals("landscape", ignoreCase = true) ||
        uiState.displayItems.any { it.posterShape.equals("landscape", ignoreCase = true) || it.type.equals("tv", ignoreCase = true) }

    val minPosterSize = if (isLandscapeGrid) (posterWidthDp.dp * 1.45f) else posterWidthDp.dp

    val hazeState = com.lagradost.cloudstream3.desktop.ui.LocalHazeState.current
    val hazeSourceModifier = if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier
    val focusManager = LocalFocusManager.current

    Box(modifier = Modifier.fillMaxSize()) {
        val handleItemClick: (ExploreItem) -> Unit = { item ->
            if (item.type.equals("collection", ignoreCase = true)) {
                val collId = item.id.removePrefix("collection:").toIntOrNull()
                if (collId != null) {
                    viewModel.onEvent(ExploreUiEvent.OpenCollection(collId))
                }
            } else {
                viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item))
            }
        }
        val unusedHandleItemClick: (ExploreItem) -> Unit = { item ->
            if (item.type.equals("collection", ignoreCase = true)) {
                val collId = item.id.removePrefix("collection:").toIntOrNull()
                if (collId != null) {
                    viewModel.onEvent(ExploreUiEvent.OpenCollection(collId))
                }
            } else {
                val (providerName, url) = if (!item.providerName.isNullOrBlank() && !item.itemUrl.isNullOrBlank()) {
                    item.providerName to item.itemUrl
                } else {
                    "Stremio" to "stremio://${item.type}/${item.id}"
                }
                onNavigate(
                    Config.Details(
                        providerName = providerName,
                        url = url,
                        preloadedName = item.name,
                        preloadedPoster = item.posterUrl,
                        preloadedBg = item.backgroundUrl,
                    ),
                )
            }
        }

        // Main Dynamic & Scrollable Content (Sampled as Haze Source)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(hazeSourceModifier),
        ) {
            if (heroBackgroundBlurEnabled && currentHeroImageUrl != null) {
                androidx.compose.animation.Crossfade(
                    targetState = currentHeroImageUrl,
                    animationSpec = tween(2000),
                    label = "explore_global_backdrop_crossfade",
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

            if (uiState.isInitializing) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = "Discovering catalog addons...",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.TextMuted,
                        )
                    }
                }
            } else if (uiState.availableTypes.isEmpty()) {
                // Empty state if no catalog addon is enabled
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
                    EmptyCatalogState(onNavigateToSettings = { onNavigate(Config.Extensions(initialTab = 3)) })
                }
            } else if (uiState.searchQuery.isNotBlank() || uiState.drilledSearchCategory != null) {
                // Fast live search results view across Cinemeta, Stremio Addons & TMDB
                ExploreSearchResultsView(
                    searchQuery = uiState.searchQuery,
                    searchResults = uiState.searchResults,
                    isSearching = uiState.isLiveSearching,
                    isShelvesMode = uiState.isShelvesMode,
                    drilledCategory = uiState.drilledSearchCategory,
                    recommendations = uiState.heroItems,
                    watchHistoryMap = uiState.watchHistoryMap,
                    autoCleanTitles = autoCleanTitles,
                    onDrillCategory = { cat -> viewModel.onEvent(ExploreUiEvent.DrillIntoSearchCategory(cat)) },
                    onReturnFromDrill = { viewModel.onEvent(ExploreUiEvent.ReturnFromSearchCategory) },
                    onClearSearch = { viewModel.onEvent(ExploreUiEvent.ClearSearchQuery) },
                    onItemClick = handleItemClick,
                )
            } else {
                // Content Area: Platform Shelves vs Collection View vs Main Shelves vs Grid View
                val currentPlatform = uiState.drilledPlatform
                val selectedCollection = uiState.selectedCollection
                if (currentPlatform != null) {
                    if (uiState.isPlatformShelvesLoading && uiState.platformShelves.all { it.items.isEmpty() }) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                            )
                        }
                    } else {
                        PlatformShelvesView(
                            platform = currentPlatform,
                            shelves = uiState.platformShelves,
                            isPlatformShelvesLoading = uiState.isPlatformShelvesLoading,
                            searchQuery = uiState.searchQuery,
                            watchHistoryMap = uiState.watchHistoryMap,
                            autoCleanTitles = autoCleanTitles,
                            onHeroBackgroundChanged = { url -> currentHeroImageUrl = url },
                            onViewAll = { catalog -> viewModel.onEvent(ExploreUiEvent.DrillIntoCatalog(catalog)) },
                            onItemClick = handleItemClick,
                        )
                    }
                } else if (selectedCollection != null) {
                    CollectionDetailView(
                        collection = selectedCollection,
                        watchHistoryMap = uiState.watchHistoryMap,
                        autoCleanTitles = autoCleanTitles,
                        onBack = { viewModel.onEvent(ExploreUiEvent.CloseCollection) },
                        onItemClick = handleItemClick,
                        onOpenDetails = { item ->
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
                        dockPosition = dockPosition,
                    )
                } else if (uiState.drilledCatalog == null) {
                    ExploreShelvesView(
                        shelves = uiState.shelves,
                        heroItems = uiState.heroItems,
                        isShelvesLoading = uiState.isShelvesLoading,
                        searchQuery = uiState.searchQuery,
                        watchHistoryMap = uiState.watchHistoryMap,
                        autoCleanTitles = autoCleanTitles,
                        selectedType = uiState.selectedType,
                        state = shelvesListState,
                        onHeroBackgroundChanged = { url -> currentHeroImageUrl = url },
                        onViewAll = { catalog -> viewModel.onEvent(ExploreUiEvent.DrillIntoCatalog(catalog)) },
                        onItemClick = handleItemClick,
                        onSelectPlatform = { platform -> viewModel.onEvent(ExploreUiEvent.DrillIntoPlatform(platform)) },
                        onNavigate = onNavigate,
                    )
                } else {
                    // Focused Drilled Catalog Grid View (padded down so it does not collide with floating controls)
                    val gridTopPadding = 118.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = gridTopPadding),
                    ) {
                        ExploreDrilledCatalogView(
                            dockPosition = dockPosition,
                            viewModel = viewModel,
                            uiState = uiState,
                            gridState = gridState,
                            minPosterSize = minPosterSize,
                            isLandscapeGrid = isLandscapeGrid,
                            autoCleanTitles = autoCleanTitles,
                            onItemClick = handleItemClick,
                        )
                    }
                }
            }
        }

        // Floating Header Controls Overlay (Permanently at root level so it is ALWAYS visible)
        val dockPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
        val dockPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp
        if (uiState.selectedCollection == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(10f),
            ) {
                // Subtle top gradient scrim for crisp contrast over light backdrops
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.20f),
                                0.5f to Color.Black.copy(alpha = 0.05f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                )

                // Header Controls Row
                ExploreHeaderControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    dockPaddingStart = dockPaddingStart,
                    dockPaddingEnd = dockPaddingEnd,
                    onNavigate = onNavigate,
                    modifier = Modifier.padding(top = 52.dp),
                )
            }
        }

        if (false) {
            ExploreCatalogSettingsDialog(
                show = true,
                catalogs = uiState.allCatalogs.filter { !it.isBuiltIn },
                onDismissRequest = { viewModel.onEvent(ExploreUiEvent.CloseCustomizeDialog) },
            )
        }

        // Multi-Provider Match Resolver Dialog
        val selectedWatchHistory = uiState.selectedItemForMatch?.let { item ->
            uiState.watchHistoryMap[item.id]
                ?: uiState.watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]
        }
        ExploreProviderDialog(
            item = uiState.selectedItemForMatch,
            matches = uiState.providerMatches,
            isSearching = uiState.isSearchingProviders,
            watchHistory = selectedWatchHistory,
            onDismissRequest = { viewModel.onEvent(ExploreUiEvent.CloseProviderPicker) },
            onSelectMatch = { match ->
                viewModel.onEvent(ExploreUiEvent.SelectProviderMatch(match))
            },
            onOpenDetails = { providerName, url, title ->
                viewModel.onEvent(ExploreUiEvent.CloseProviderPicker)
                onNavigate(
                    Config.Details(
                        providerName = providerName,
                        url = url,
                        preloadedName = title,
                    ),
                )
            },
            onNavigateToExtensions = { tab ->
                viewModel.onEvent(ExploreUiEvent.CloseProviderPicker)
                onNavigate(Config.Extensions(initialTab = tab))
            },
            onOpenAddonMode = { item ->
                viewModel.onEvent(ExploreUiEvent.CloseProviderPicker)
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
