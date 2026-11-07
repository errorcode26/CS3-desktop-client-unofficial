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

@Composable
private fun ExploreHeaderControls(
    uiState: ExploreUiState,
    viewModel: ExploreViewModel,
    dockPaddingStart: androidx.compose.ui.unit.Dp,
    dockPaddingEnd: androidx.compose.ui.unit.Dp,
    onNavigate: (Config) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val hazeState = com.lagradost.cloudstream3.desktop.ui.LocalHazeState.current
    val isAmoled = theme.isAmoled
    val isLightMode = theme.isLightMode
    val performanceModeEnabled by AppearanceConfig.performanceModeEnabled.collectAsState()
    val barShape = RoundedCornerShape(16.dp)

    val hazeModifier = if (hazeState != null && !isAmoled && !performanceModeEnabled) {
        Modifier
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = if (isLightMode) Color(0xFFF0F0F4).copy(alpha = 0.50f) else Color(0xFF14141A).copy(alpha = 0.35f),
                    tint = HazeTint(if (isLightMode) Color.White.copy(alpha = 0.35f) else Color(0xFF0F0F14).copy(alpha = 0.40f)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.05f,
                ),
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isLightMode) 0.15f else 0.06f),
                        Color.White.copy(alpha = if (isLightMode) 0.05f else 0.02f),
                    ),
                ),
            )
    } else {
        Modifier.background(
            if (isAmoled) {
                Color.Black.copy(alpha = 0.94f)
            } else if (isLightMode) {
                theme.SurfaceElevated.copy(alpha = 0.92f)
            } else {
                Color(0xFF121218).copy(alpha = 0.88f)
            },
        )
    }

    val drilledCat = uiState.drilledCatalog
    val drilledPlat = uiState.drilledPlatform
    val drilledSearchCat = uiState.drilledSearchCategory
    val selectedCollection = uiState.selectedCollection

    // ── Header Controls (Left Catalog Capsule + Centered Search Bar) ──
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dockPaddingStart, end = dockPaddingEnd),
    ) {
        val isHeaderWide = this.maxWidth >= 840.dp
        val leftMaxW = this.maxWidth.coerceAtLeast(120.dp)
        val borderGradient = Brush.linearGradient(
            listOf(
                if (isLightMode) theme.Divider.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f),
                if (isLightMode) theme.Divider.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
            ),
        )

        // ── Left: Navigation & Catalog Filter Tabs Capsule ──
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = leftMaxW)
                .wrapContentWidth()
                .height(48.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = barShape,
                    clip = false,
                    spotColor = Color.Black.copy(alpha = 0.35f),
                    ambientColor = Color.Transparent,
                ),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = leftMaxW)
                    .wrapContentWidth()
                    .height(48.dp)
                    .clip(barShape)
                    .then(hazeModifier)
                    .border(1.dp, borderGradient, barShape),
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = leftMaxW)
                        .wrapContentWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (drilledSearchCat != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onEvent(ExploreUiEvent.ReturnFromSearchCategory) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "Back",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Search  ›  ${uiState.searchQuery}  ›  $drilledSearchCat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = drilledSearchCat,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                            )
                        }
                    } else if (selectedCollection != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onEvent(ExploreUiEvent.CloseCollection) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "Back",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Explore  ›  Collections  ›  ${selectedCollection.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = selectedCollection.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                            )
                        }
                    } else if (drilledCat != null || drilledPlat != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onEvent(ExploreUiEvent.ReturnToShelves) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "Back",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        val headerTitle = drilledPlat?.let { "${it.displayName} Hub" } ?: drilledCat?.name ?: "Explore"
                        Column {
                            Text(
                                text = "Explore  ›  $headerTitle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                            )
                        }

                        val isDrillWide = isHeaderWide

                        if (drilledPlat == null && drilledCat != null && !isDrillWide && drilledCat.genres.isNotEmpty()) {
                            GenreDropdown(
                                selectedGenre = uiState.selectedGenre,
                                genres = drilledCat.genres,
                                onSelectGenre = { viewModel.onEvent(ExploreUiEvent.SelectGenre(it)) },
                            )
                        }

                        if (drilledPlat == null && (!isDrillWide || drilledCat == null)) {
                            YearDropdown(
                                selectedYear = uiState.selectedYear,
                                onSelectYear = { viewModel.onEvent(ExploreUiEvent.SelectYear(it)) },
                            )
                        }
                    } else {
                        // Landing: Brand + Category Pills
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp),
                            )
                            Text(
                                text = "Explore",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                                fontSize = 15.sp,
                            )
                        }

                        // Sleek vertical separator between brand and category pills
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(18.dp)
                                .background(if (isLightMode) theme.Divider else Color.White.copy(alpha = 0.15f)),
                        )

                        // Explore primary category pills (My Add-ons, Streaming Hubs, Franchise Sagas, Anime, Movies, TV Shows)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    0.5.dp,
                                    if (isLightMode) theme.Divider.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp),
                                ),
                            color = if (isLightMode) theme.SurfaceElevated.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                uiState.availableTypes.forEach { type ->
                                    val isSelected = uiState.selectedType.equals(type, ignoreCase = true)
                                    val title = viewModel.formatTypeTitle(type)

                                    Box(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            )
                                            .clickable { viewModel.onEvent(ExploreUiEvent.SelectType(type)) }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.Black else theme.TextMuted,
                                            letterSpacing = 0.2.sp,
                                        )
                                    }
                                    if (false) {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .width(1.dp)
                                                .height(16.dp)
                                                .background(if (isLightMode) theme.Divider else Color.White.copy(alpha = 0.15f)),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .height(30.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.onEvent(ExploreUiEvent.OpenCustomizeDialog) }
                                            .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = "Customize Shelves",
                                                    tint = theme.TextMuted,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                                Text(
                                                    text = "Customize",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = theme.TextMuted,
                                                )
                                            }
                                        }
                                    }
                                    if (false) {
                                        Text(""
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreNativeHero(
    heroItems: List<ExploreItem>,
    onHeroBackgroundChanged: (String?) -> Unit,
    onItemClick: (ExploreItem) -> Unit,
) {
    if (heroItems.isEmpty()) return

    val resolvedLogos = remember(heroItems) {
        mutableStateMapOf<String, String>().apply {
            heroItems.forEach { item ->
                if (item.id.startsWith("tmdb:")) {
                    val tmdbId = item.id.removePrefix("tmdb:").toIntOrNull()
                    if (tmdbId != null) {
                        val isTv = item.type.equals("series", ignoreCase = true) || item.type.equals("tv", ignoreCase = true)
                        ExploreHubClient.getCachedLogoForTmdb(tmdbId, isTv)?.let { cachedLogo ->
                            put(item.id, cachedLogo)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(heroItems) {
        heroItems.forEach { item ->
            if (item.logoUrl == null && item.id.startsWith("tmdb:")) {
                val tmdbId = item.id.removePrefix("tmdb:").toIntOrNull()
                if (tmdbId != null && !resolvedLogos.containsKey(item.id)) {
                    launch(Dispatchers.IO) {
                        val isTv = item.type.equals("series", ignoreCase = true) || item.type.equals("tv", ignoreCase = true)
                        val logo = ExploreHubClient.fetchLogoForTmdb(tmdbId, isTv)
                        if (!logo.isNullOrBlank()) {
                            withContext(Dispatchers.Main) {
                                resolvedLogos[item.id] = logo
                            }
                        }
                    }
                }
            }
        }
    }

    val searchResponses = remember(heroItems) {
        @Suppress("DEPRECATION_ERROR")
        heroItems.map { item ->
            MovieSearchResponse(
                name = item.name,
                url = item.id,
                apiName = "Explore",
                type = if (item.type.equals("series", ignoreCase = true) || item.type.equals("tv", ignoreCase = true)) TvType.TvSeries else TvType.Movie,
                posterUrl = item.posterUrl,
                year = item.releaseYear?.toIntOrNull(),
                id = null,
                quality = null,
                posterHeaders = null,
            )
        }
    }

    val heroMetaMap = remember(heroItems, resolvedLogos.toMap()) {
        heroItems.associate { item ->
            val logo = item.logoUrl ?: resolvedLogos[item.id]
            item.id to HeroMeta(
                title = item.name,
                backdropUrl = item.backgroundUrl ?: item.posterUrl,
                logoUrl = logo,
                tags = item.genres,
                plot = item.description,
                score = item.rating?.let { if (it > 0.0) String.format(Locale.US, "%.1f", it) else null },
                year = item.releaseYear?.toIntOrNull(),
                type = if (item.type.equals("series", ignoreCase = true) || item.type.equals("tv", ignoreCase = true)) TvType.TvSeries else TvType.Movie,
                contentRating = null,
                duration = null,
            )
        }
    }

    HomeHeroCarousel(
        items = searchResponses,
        provider = null,
        heroMetaMap = heroMetaMap,
        allBookmarks = emptyMap(),
        onPrefetchHeroItem = { _, _ -> },
        onHeroBackgroundChanged = onHeroBackgroundChanged,
        onItemClick = { resp, _, _ ->
            val original = heroItems.find { it.id == resp.url }
            if (original != null) {
                onItemClick(original)
            }
        },
    )
}

@Composable
private fun ExploreShelvesView(
    shelves: List<ExploreShelf>,
    heroItems: List<ExploreItem>,
    isShelvesLoading: Boolean = false,
    searchQuery: String,
    watchHistoryMap: Map<String, WatchHistory>,
    autoCleanTitles: Boolean,
    selectedType: String = "movie",
    state: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onHeroBackgroundChanged: (String?) -> Unit,
    onViewAll: (ManifestCatalogDescriptor) -> Unit,
    onItemClick: (ExploreItem) -> Unit,
    onSelectPlatform: (StreamingPlatform) -> Unit,
    onNavigate: (Config) -> Unit = {},
) {
    val theme = LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()

    val filteredShelves = remember(shelves) {
        shelves.map { shelf ->
            if (shelf.items.isEmpty()) {
                shelf
            } else {
                val uniqueItems = shelf.items.distinctBy { it.id }
                shelf.copy(items = uniqueItems)
            }
        }
    }

    if (filteredShelves.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selectedType.equals("addons", ignoreCase = true)) {
"" } else { "" }, modifier = Modifier.size(0.dp)
            )
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    text = if (selectedType.equals("addons", ignoreCase = true)) {
                        "No Add-on Catalogs Installed"
                    } else {
                        "No Catalogs Loaded"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = theme.TextPrimary,
                )
                Text(
                    text = if (selectedType.equals("addons", ignoreCase = true)) {
                        "Install catalog add-ons in Extensions to populate your explore feed with movies, series, and anime."
                    } else {
                        "Configure catalog sources or add-ons in Extensions to discover titles here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 440.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedType.equals("addons", ignoreCase = true)) {
                        Button(
                            onClick = { onNavigate(Config.Extensions(initialTab = 3)) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Install Add-ons", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    } else {
                        Button(
                            onClick = { onNavigate(Config.Extensions(initialTab = 0)) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Browse Plugins", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                        OutlinedButton(
                            onClick = { onNavigate(Config.Extensions(initialTab = 3)) },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Install Add-ons", fontSize = 12.5.sp)
                        }
                    }
                }
            }
            if (false) Text(
                text = if (true) { ""
                } else {
                    "No catalogs loaded."
                },
                color = theme.TextMuted,
                fontSize = 13.5.sp,
            )
        }
        return
    }

    val isHeroVisible = searchQuery.isBlank() && (heroItems.isNotEmpty() || isShelvesLoading)
    val shouldPadTop = !isHeroVisible
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
        contentPadding = PaddingValues(
            top = if (shouldPadTop) 124.dp else 0.dp,
            bottom = 32.dp,
        ),
    ) {
        // Hero Spotlight Banner
        if (isHeroVisible) {
            item(key = "explore_hero_banner") {
                if (heroItems.isNotEmpty()) {
                    ExploreNativeHero(
                        heroItems = heroItems.take(6),
                        onHeroBackgroundChanged = onHeroBackgroundChanged,
                        onItemClick = onItemClick,
                    )
                } else {
                    HomeHeroCarouselPlaceholder()
                }
            }
        }

        // Streaming Platform Hub (Netflix, Disney+, Prime Video, Apple TV+, Hulu, Max, Paramount+, Crunchyroll)
        if (searchQuery.isBlank() && selectedType.equals("platforms", ignoreCase = true)) {
            val shelfPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
            val shelfPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp
            item(key = "explore_streaming_platforms") {
                StreamingPlatformRow(
                    onSelectPlatform = onSelectPlatform,
                    modifier = Modifier.padding(
                        start = shelfPaddingStart,
                        end = shelfPaddingEnd,
                        bottom = 8.dp,
                    ),
                )
            }
        }

        // Horizontal Shelves
        items(
            count = filteredShelves.size,
            key = { index -> "${filteredShelves[index].catalog.key}_$index" },
        ) { index ->
            val shelf = filteredShelves[index]
            val isLandscape = shelf.catalog.posterShape.equals("landscape", ignoreCase = true) ||
                shelf.catalog.type.equals("tv", ignoreCase = true) ||
                shelf.catalog.type.equals("channel", ignoreCase = true)

            if (shelf.isLoading && shelf.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 140.dp else 225.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (shelf.items.isEmpty()) {
                if (shelf.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = shelf.error,
                            fontSize = 12.sp,
                            color = theme.TextMuted,
                        )
                    }
                }
            } else {
                val shelfPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 22.dp
                val shelfPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 22.dp

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = shelfPaddingStart, end = shelfPaddingEnd),
                ) {
                    val availableWidth = this.maxWidth
                    val isCompact = availableWidth < 600.dp
                    val spacingDp = if (isCompact) 8.dp else homeSpacingDp.dp

                    val optimalItemWidth = if (isCompact) {
                        if (isLandscape) 160.dp else 115.dp
                    } else {
                        val baseWidth = if (isLandscape) (posterWidthDp.dp * 1.45f) else posterWidthDp.dp
                        val netWidth = availableWidth - 20.dp
                        val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
                        val columns = exactColumns.toInt().coerceAtLeast(1)
                        ((netWidth + spacingDp) / columns) - spacingDp
                    }

                    val rowVerticalPadding = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp)

                    val typeLabel = when (shelf.catalog.type.lowercase(java.util.Locale.US)) {
                        "series", "tv" -> "Series"
                        "movie" -> "Movies"
                        "anime" -> "Anime"
                        "collections" -> "Collections"
                        else -> ""
                    }
                    val withType = if (typeLabel.isNotBlank() && !shelf.catalog.name.contains(typeLabel, ignoreCase = true)) {
                        "${shelf.catalog.name} ($typeLabel)"
                    } else {
                        shelf.catalog.name
                    }
                    val rowTitle = if (shelf.catalog.addonName.isNotBlank() && !shelf.catalog.name.contains(shelf.catalog.addonName, ignoreCase = true)) {
                        "$withType  •  ${shelf.catalog.addonName}"
                    } else {
                        withType
                    }

                    CategoryRowWithHeader(
                        modifier = Modifier.fillMaxWidth(),
                        title = rowTitle,
                        itemCount = shelf.items.size,
                        onViewAll = { onViewAll(shelf.catalog) },
                        rowContentPadding = PaddingValues(
                            horizontal = if (isCompact) 4.dp else 10.dp,
                            vertical = rowVerticalPadding,
                        ),
                        headerPadding = PaddingValues(
                            start = 10.dp,
                            end = 10.dp,
                            top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                            bottom = 4.dp,
                        ),
                        itemSpacing = spacingDp,
                    ) {
                        items(
                            count = shelf.items.size,
                            key = { i -> "${shelf.catalog.id}_${shelf.items[i].id}_$i" },
                        ) { i ->
                            val item = shelf.items[i]
                            val watchHistory = watchHistoryMap[item.id]
                                ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                            Box(modifier = Modifier.width(optimalItemWidth)) {
                                ExplorePosterCard(
                                    item = item,
                                    watchHistory = watchHistory,
                                    autoCleanTitles = autoCleanTitles,
                                    isCatalogLandscape = isLandscape,
                                    onClick = { onItemClick(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformShelvesView(
    platform: StreamingPlatform,
    shelves: List<ExploreShelf>,
    isPlatformShelvesLoading: Boolean = false,
    searchQuery: String,
    watchHistoryMap: Map<String, WatchHistory>,
    autoCleanTitles: Boolean,
    onHeroBackgroundChanged: (String?) -> Unit,
    onViewAll: (ManifestCatalogDescriptor) -> Unit,
    onItemClick: (ExploreItem) -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()

    val filteredShelves = remember(shelves, searchQuery) {
        if (searchQuery.isBlank()) {
            shelves
        } else {
            val q = searchQuery.trim().lowercase(Locale.US)
            shelves.mapNotNull { shelf ->
                val matching = shelf.items.filter { it.name.lowercase(Locale.US).contains(q) }
                if (matching.isNotEmpty()) shelf.copy(items = matching) else null
            }
        }
    }

    if (filteredShelves.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (searchQuery.isNotBlank()) "No items match \"$searchQuery\" on ${platform.displayName}." else "No platform titles loaded.",
                color = theme.TextMuted,
                fontSize = 13.5.sp,
            )
        }
        return
    }

    val platformHeroes = remember(shelves) {
        shelves.flatMap { it.items }.distinctBy { it.id }.take(6)
    }

    val isHeroVisible = searchQuery.isBlank() && (platformHeroes.isNotEmpty() || isPlatformShelvesLoading)
    val shouldPadTop = !isHeroVisible
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
        contentPadding = PaddingValues(
            top = if (shouldPadTop) 124.dp else 0.dp,
            bottom = 32.dp,
        ),
    ) {
        // Spotlight Hero Carousel for this platform
        if (isHeroVisible) {
            item(key = "platform_hero_banner_${platform.id}") {
                if (platformHeroes.isNotEmpty()) {
                    ExploreNativeHero(
                        heroItems = platformHeroes,
                        onHeroBackgroundChanged = onHeroBackgroundChanged,
                        onItemClick = onItemClick,
                    )
                } else {
                    HomeHeroCarouselPlaceholder()
                }
            }
        }

        // Horizontal Category Shelves
        items(
            count = filteredShelves.size,
            key = { index -> "${filteredShelves[index].catalog.key}_$index" },
        ) { index ->
            val shelf = filteredShelves[index]
            val isLandscape = shelf.catalog.posterShape.equals("landscape", ignoreCase = true) ||
                shelf.catalog.type.equals("tv", ignoreCase = true) ||
                shelf.catalog.type.equals("channel", ignoreCase = true)

            if (shelf.isLoading && shelf.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 140.dp else 225.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (shelf.items.isEmpty()) {
                if (shelf.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = shelf.error,
                            fontSize = 12.sp,
                            color = theme.TextMuted,
                        )
                    }
                }
            } else {
                val shelfPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 22.dp
                val shelfPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 22.dp

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = shelfPaddingStart, end = shelfPaddingEnd),
                ) {
                    val availableWidth = this.maxWidth
                    val isCompact = availableWidth < 600.dp
                    val spacingDp = if (isCompact) 8.dp else homeSpacingDp.dp

                    val optimalItemWidth = if (isCompact) {
                        if (isLandscape) 160.dp else 115.dp
                    } else {
                        val baseWidth = if (isLandscape) (posterWidthDp.dp * 1.45f) else posterWidthDp.dp
                        val netWidth = availableWidth - 20.dp
                        val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
                        val columns = exactColumns.toInt().coerceAtLeast(1)
                        ((netWidth + spacingDp) / columns) - spacingDp
                    }

                    val rowVerticalPadding = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp)

                    CategoryRowWithHeader(
                        modifier = Modifier.fillMaxWidth(),
                        title = shelf.catalog.name,
                        itemCount = shelf.items.size,
                        onViewAll = { onViewAll(shelf.catalog) },
                        rowContentPadding = PaddingValues(
                            horizontal = if (isCompact) 4.dp else 10.dp,
                            vertical = rowVerticalPadding,
                        ),
                        headerPadding = PaddingValues(
                            start = 10.dp,
                            end = 10.dp,
                            top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                            bottom = 4.dp,
                        ),
                        itemSpacing = spacingDp,
                    ) {
                        items(
                            count = shelf.items.size,
                            key = { i -> "${shelf.catalog.id}_${shelf.items[i].id}_$i" },
                        ) { i ->
                            val item = shelf.items[i]
                            val watchHistory = watchHistoryMap[item.id]
                                ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                            Box(modifier = Modifier.width(optimalItemWidth)) {
                                ExplorePosterCard(
                                    item = item,
                                    watchHistory = watchHistory,
                                    autoCleanTitles = autoCleanTitles,
                                    isCatalogLandscape = isLandscape,
                                    onClick = { onItemClick(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionDetailView(
    collection: ExploreCollectionDetail,
    watchHistoryMap: Map<String, WatchHistory>,
    autoCleanTitles: Boolean,
    onBack: () -> Unit,
    onItemClick: (ExploreItem) -> Unit,
    onOpenDetails: (ExploreItem) -> Unit,
    dockPosition: DockPosition,
) {
    val theme = LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()
    val paddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
    val paddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp

    val firstYear = collection.parts.firstOrNull()?.releaseYear
    val lastYear = collection.parts.lastOrNull()?.releaseYear
    val timelineSpan = if (firstYear != null && lastYear != null && firstYear != lastYear) "$firstYear-$lastYear" else firstYear.orEmpty()

    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Ambient full-window blurred glow matching DetailsScreen
        if (heroBackgroundBlurEnabled && !collection.backdropUrl.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val context = coil3.compose.LocalPlatformContext.current
                val ambientRequest = remember(collection.backdropUrl) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(collection.backdropUrl)
                        .size(640, 360)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = ambientRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(heroBackdropBlurRadius.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = heroBackdropDarkening)),
                )
            }
        }

        // Layer 2: Hero Crisp Backdrop with seamless vignette matching DetailsHeader
        if (!collection.backdropUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(580.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val verticalFade = Brush.verticalGradient(
                            0.00f to Color.Black,
                            0.35f to Color.Black,
                            0.60f to Color.Black.copy(alpha = 0.85f),
                            0.80f to Color.Black.copy(alpha = 0.40f),
                            0.94f to Color.Black.copy(alpha = 0.08f),
                            1.00f to Color.Transparent,
                        )
                        val scrimBase = Color.Black
                        val logoVignette = Brush.horizontalGradient(
                            0.00f to scrimBase.copy(alpha = 0.85f),
                            0.08f to scrimBase.copy(alpha = 0.80f),
                            0.18f to scrimBase.copy(alpha = 0.70f),
                            0.30f to scrimBase.copy(alpha = 0.55f),
                            0.42f to scrimBase.copy(alpha = 0.35f),
                            0.54f to scrimBase.copy(alpha = 0.18f),
                            0.64f to scrimBase.copy(alpha = 0.06f),
                            0.72f to Color.Transparent,
                            1.00f to Color.Transparent,
                        )
                        val bottomScrim = Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.35f to Color.Transparent,
                            0.65f to Color.Black.copy(alpha = 0.50f),
                            0.85f to Color.Black.copy(alpha = 0.88f),
                            1.00f to Color.Black,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(bottomScrim)
                            drawRect(logoVignette)
                            drawRect(verticalFade, blendMode = BlendMode.DstIn)
                        }
                    },
            ) {
                val context = coil3.compose.LocalPlatformContext.current
                val crispRequest = remember(collection.backdropUrl) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(collection.backdropUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = crispRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = posterWidthDp.dp.coerceAtLeast(145.dp)),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(homeSpacingDp.dp.coerceAtLeast(14.dp)),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = paddingStart,
                end = paddingEnd,
                top = 118.dp,
                bottom = 48.dp,
            ),
        ) {
            // Top Collection Hero Header spanning full grid width
            item(span = { GridItemSpan(maxLineSpan) }, key = "collection_header_hero") {
                CollectionHeader(
                    collection = collection,
                    timelineSpan = timelineSpan,
                )
            }

            // Installment Movie Posters Grid
            items(
                count = collection.parts.size,
                key = { i -> "coll_poster_${collection.parts[i].id}" },
            ) { index ->
                val item = collection.parts[index]
                val watchHistory = watchHistoryMap[item.id]
                    ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                ExplorePosterCard(
                    item = item,
                    watchHistory = watchHistory,
                    autoCleanTitles = autoCleanTitles,
                    onClick = { onItemClick(item) },
                )
            }
        }

        // Floating Top Back Button (pinned, matching DetailsScreen & PersonScreen)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = paddingStart, top = 52.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F0F12).copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .zIndex(10f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CollectionHeader(
    collection: ExploreCollectionDetail,
    timelineSpan: String,
) {
    val theme = LocalDesktopTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Collection Poster on the Left
        val posterUrl = collection.posterUrl ?: collection.backdropUrl
        if (!posterUrl.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(2f / 3f)
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = theme.SurfaceCard,
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = collection.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Collection Info on the Right
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "COLLECTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.TextMuted,
                letterSpacing = 1.5.sp,
            )

            Text(
                text = collection.name,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 42.sp,
            )

            val metaLine = listOfNotNull(
                "${collection.parts.size} films",
                timelineSpan.takeIf { it.isNotBlank() },
            ).joinToString("  •  ")

            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.TextMuted,
                )
            }

            if (collection.overview.isNotBlank()) {
                Text(
                    text = collection.overview,
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
