package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.repo.HeroMeta
import com.lagradost.cloudstream3.desktop.ui.screens.home.HomeHeroCarousel
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchResults
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ExploreShelf
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.models.StreamingPlatform
import com.lagradost.cloudstream3.desktop.explore.viewmodel.EXPLORE_YEAR_OPTIONS
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEffect
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEvent
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiState
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreViewModel
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCollectionDetail
import com.lagradost.cloudstream3.desktop.explore.client.ExploreHubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.components.posterDepthEffect
import com.lagradost.cloudstream3.desktop.ui.components.getTextShadow
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.text.style.TextAlign
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSession
import com.lagradost.cloudstream3.desktop.ui.screens.settings.LeafTab
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSubScreen
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

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is ExploreUiEffect.OpenDetails -> {
                    onNavigate(
                        Config.Details(
                            providerName = effect.providerName,
                            url = effect.url,
                            preloadedName = effect.title,
                        )
                    )
                }
            }
        }
    }

    // Scroll to top when catalog, genre, year, or query changes
    LaunchedEffect(uiState.selectedCatalog, uiState.selectedGenre, uiState.selectedYear) {
        gridState.scrollToItem(0)
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

        // Main Dynamic & Scrollable Content (Sampled as Haze Source)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(hazeSourceModifier)
                .onPointerEvent(PointerEventType.Scroll) {
                    focusManager.clearFocus()
                    if (uiState.searchQuery.isNotBlank()) {
                        viewModel.onEvent(ExploreUiEvent.ClearSearchQuery)
                    }
                }
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
                EmptyCatalogState(onNavigateToSettings = { onNavigate(Config.Settings) })
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
                            )
                        )
                    },
                    dockPosition = dockPosition,
                )
            } else if (uiState.isShelvesMode && uiState.drilledCatalog == null) {
                if (uiState.isShelvesLoading && uiState.shelves.all { it.items.isEmpty() }) {
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
                    ExploreShelvesView(
                        shelves = uiState.shelves,
                        heroItems = uiState.heroItems,
                        searchQuery = uiState.searchQuery,
                        watchHistoryMap = uiState.watchHistoryMap,
                        autoCleanTitles = autoCleanTitles,
                        selectedType = uiState.selectedType,
                        onHeroBackgroundChanged = { url -> currentHeroImageUrl = url },
                        onViewAll = { catalog -> viewModel.onEvent(ExploreUiEvent.DrillIntoCatalog(catalog)) },
                        onItemClick = handleItemClick,
                        onSelectPlatform = { platform -> viewModel.onEvent(ExploreUiEvent.DrillIntoPlatform(platform)) },
                    )
                }
            } else {
                // Classic or Drilled Grid View (padded down so it does not collide with floating controls)
                val gridTopPadding = if (uiState.drilledCatalog != null) 124.dp else 98.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = gridTopPadding),
                ) {
                    if (uiState.drilledCatalog == null && uiState.filteredCatalogs.isNotEmpty()) {
                        val gridBarStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
                        val gridBarEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = gridBarStart, end = gridBarEnd)
                                .height(30.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                LazyRow(
                                    state = catalogListState,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    items(uiState.filteredCatalogs, key = { "${it.addonBaseUrl}_${it.type}_${it.id}" }) { catalog ->
                                        val isSelected = uiState.selectedCatalog?.id == catalog.id &&
                                            uiState.selectedCatalog?.addonBaseUrl == catalog.addonBaseUrl
                                        CompactCatalogChip(
                                            catalog = catalog,
                                            isSelected = isSelected,
                                            onClick = { viewModel.onEvent(ExploreUiEvent.SelectCatalog(catalog)) },
                                        )
                                    }
                                }

                                val canScrollLeft by remember { derivedStateOf { catalogListState.canScrollBackward } }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = canScrollLeft,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier.align(Alignment.CenterStart),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        shadowElevation = 6.dp,
                                        modifier = Modifier.size(26.dp),
                                        onClick = {
                                            coroutineScope.launch { catalogListState.animateScrollBy(-300f) }
                                        },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronLeft,
                                                contentDescription = "Scroll Left",
                                                tint = theme.TextPrimary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }

                                val canScrollRight by remember { derivedStateOf { catalogListState.canScrollForward } }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = canScrollRight,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        shadowElevation = 6.dp,
                                        modifier = Modifier.size(26.dp),
                                        onClick = {
                                            coroutineScope.launch { catalogListState.animateScrollBy(300f) }
                                        },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Scroll Right",
                                                tint = theme.TextPrimary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            val selectedCat = uiState.selectedCatalog
                            if (selectedCat != null && selectedCat.genres.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(10.dp))
                                GenreDropdown(
                                    selectedGenre = uiState.selectedGenre,
                                    genres = selectedCat.genres,
                                    onSelectGenre = { viewModel.onEvent(ExploreUiEvent.SelectGenre(it)) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    ExploreGridView(
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
                                0.0f to Color.Black.copy(alpha = 0.55f),
                                0.5f to Color.Black.copy(alpha = 0.15f),
                                1.0f to Color.Transparent,
                            )
                        )
                )

                // Header Controls Row
                ExploreHeaderControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    dockPaddingStart = dockPaddingStart,
                    dockPaddingEnd = dockPaddingEnd,
                    modifier = Modifier.padding(top = 52.dp),
                )
            }
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
                    )
                )
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
                    )
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
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val hazeState = com.lagradost.cloudstream3.desktop.ui.LocalHazeState.current
    val isAmoled = theme.isAmoled
    val isLightMode = theme.isLightMode
    val barShape = RoundedCornerShape(16.dp)

    val hazeModifier = if (hazeState != null && !isAmoled) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = if (isLightMode) Color(0xFFEAEAF0).copy(alpha = 0.72f) else Color(0xFF0F0F14).copy(alpha = 0.72f),
                tint = HazeTint(if (isLightMode) Color(0xFFF0F0F4).copy(alpha = 0.50f) else Color(0xFF14141A).copy(alpha = 0.55f)),
                blurRadius = 24.dp,
                noiseFactor = 0f,
            ),
        )
    } else {
        Modifier.background(
            if (isAmoled) Color.Black.copy(alpha = 0.94f)
            else if (isLightMode) theme.SurfaceElevated.copy(alpha = 0.92f)
            else Color(0xFF121218).copy(alpha = 0.88f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dockPaddingStart, end = dockPaddingEnd)
            .height(48.dp),
    ) {
        val drilledCat = uiState.drilledCatalog
        val drilledPlat = uiState.drilledPlatform
        val drilledSearchCat = uiState.drilledSearchCategory
        val selectedCollection = uiState.selectedCollection

        // ── Left: Floating Navigation Island ──
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .height(46.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = barShape,
                    spotColor = Color.Black.copy(alpha = 0.40f),
                    ambientColor = Color.Black.copy(alpha = 0.20f),
                )
                .clip(barShape)
                .then(hazeModifier)
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                if (isLightMode) theme.Divider.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f),
                                if (isLightMode) theme.Divider.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
                            )
                        ),
                    ),
                    shape = barShape,
                ),
            color = Color.Transparent,
            shape = barShape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                } else if (uiState.searchQuery.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.onEvent(ExploreUiEvent.ClearSearchQuery) },
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
                            text = "Explore  ›  Search",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Search Results",
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

                    if (drilledPlat == null && drilledCat != null && drilledCat.genres.isNotEmpty()) {
                        GenreDropdown(
                            selectedGenre = uiState.selectedGenre,
                            genres = drilledCat.genres,
                            onSelectGenre = { viewModel.onEvent(ExploreUiEvent.SelectGenre(it)) },
                        )
                    }

                    if (drilledPlat == null) {
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
                            .background(if (isLightMode) theme.Divider else Color.White.copy(alpha = 0.15f))
                    )

                    // Media type selector pills (Movies, TV Shows, Anime, Collections)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                0.5.dp,
                                if (isLightMode) theme.Divider.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp)
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
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
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
                            }
                        }
                    }
                }
            }
        }

        // ── Mid: Centered Floating Search Island ──
        ExploreSearchField(
            query = uiState.searchQuery,
            onQueryChange = { viewModel.onEvent(ExploreUiEvent.UpdateSearchQuery(it)) },
            onClear = { viewModel.onEvent(ExploreUiEvent.ClearSearchQuery) },
            searchResults = uiState.searchResults,
            isLiveSearching = uiState.isLiveSearching,
            onItemClick = { item -> viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item)) },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ExploreNativeHero(
    heroItems: List<ExploreItem>,
    onHeroBackgroundChanged: (String?) -> Unit,
    onItemClick: (ExploreItem) -> Unit,
) {
    if (heroItems.isEmpty()) return

    val resolvedLogos = remember { mutableStateMapOf<String, String>() }

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
    searchQuery: String,
    watchHistoryMap: Map<String, WatchHistory>,
    autoCleanTitles: Boolean,
    selectedType: String = "movie",
    onHeroBackgroundChanged: (String?) -> Unit,
    onViewAll: (ManifestCatalogDescriptor) -> Unit,
    onItemClick: (ExploreItem) -> Unit,
    onSelectPlatform: (StreamingPlatform) -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()

    val filteredShelves = remember(shelves) {
        val seenIds = mutableSetOf<String>()
        shelves.map { shelf ->
            if (shelf.items.isEmpty()) shelf
            else {
                val uniqueItems = shelf.items.filter { item -> seenIds.add(item.id) }
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
                text = "No catalogs loaded.",
                color = theme.TextMuted,
                fontSize = 13.5.sp,
            )
        }
        return
    }

    val shouldPadTop = heroItems.isEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
        contentPadding = PaddingValues(
            top = if (shouldPadTop) 124.dp else 0.dp,
            bottom = 32.dp,
        ),
    ) {
        // Hero Spotlight Banner
        if (searchQuery.isBlank() && heroItems.isNotEmpty()) {
            item(key = "explore_hero_banner") {
                ExploreNativeHero(
                    heroItems = heroItems.take(6),
                    onHeroBackgroundChanged = onHeroBackgroundChanged,
                    onItemClick = onItemClick,
                )
            }
        }

        // Streaming Platform Hub (Netflix, Disney+, Prime Video, Apple TV+, Hulu, Max, Paramount+, Crunchyroll)
        if (searchQuery.isBlank() && !selectedType.equals("collections", ignoreCase = true)) {
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
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val availableWidth = this.maxWidth
                    val isCompact = availableWidth < 600.dp
                    val spacingDp = if (isCompact) 8.dp else homeSpacingDp.dp
                    val rowPaddingStart = if (isCompact) 8.dp else if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
                    val rowPaddingEnd = if (isCompact) 8.dp else if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp
                    val totalHorizontalPadding = rowPaddingStart + rowPaddingEnd

                    val optimalItemWidth = if (isCompact) {
                        if (isLandscape) 160.dp else 115.dp
                    } else {
                        val baseWidth = if (isLandscape) (posterWidthDp.dp * 1.45f) else posterWidthDp.dp
                        val netWidth = (availableWidth - totalHorizontalPadding).coerceAtLeast(100.dp)
                        val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
                        val columns = exactColumns.toInt().coerceAtLeast(1)
                        ((netWidth + spacingDp) / columns) - spacingDp
                    }

                    val rowVerticalPadding = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp)

                    val rowTitle = if (shelf.catalog.addonName.isNotBlank() && !shelf.catalog.name.contains(shelf.catalog.addonName, ignoreCase = true)) {
                        "${shelf.catalog.name}  •  ${shelf.catalog.addonName}"
                    } else {
                        shelf.catalog.name
                    }

                    CategoryRowWithHeader(
                        modifier = Modifier.fillMaxWidth(),
                        title = rowTitle,
                        itemCount = shelf.items.size,
                        onViewAll = { onViewAll(shelf.catalog) },
                        rowContentPadding = PaddingValues(
                            start = rowPaddingStart,
                            end = rowPaddingEnd,
                            top = rowVerticalPadding,
                            bottom = rowVerticalPadding,
                        ),
                        headerPadding = PaddingValues(
                            start = rowPaddingStart,
                            end = rowPaddingEnd,
                            top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                            bottom = 4.dp,
                        ),
                        itemSpacing = spacingDp,
                    ) {
                        items(
                            count = shelf.items.size,
                            key = { i -> "${shelf.catalog.id}_${shelf.items[i].id}" },
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

    val shouldPadTop = searchQuery.isNotBlank() || platformHeroes.isEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
        contentPadding = PaddingValues(
            top = if (shouldPadTop) 124.dp else 0.dp,
            bottom = 32.dp,
        ),
    ) {
        // Spotlight Hero Carousel for this platform
        if (searchQuery.isBlank() && platformHeroes.isNotEmpty()) {
            item(key = "platform_hero_banner_${platform.id}") {
                ExploreNativeHero(
                    heroItems = platformHeroes,
                    onHeroBackgroundChanged = onHeroBackgroundChanged,
                    onItemClick = onItemClick,
                )
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
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val availableWidth = this.maxWidth
                    val isCompact = availableWidth < 600.dp
                    val spacingDp = if (isCompact) 8.dp else homeSpacingDp.dp
                    val rowPaddingStart = if (isCompact) 8.dp else if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
                    val rowPaddingEnd = if (isCompact) 8.dp else if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp
                    val totalHorizontalPadding = rowPaddingStart + rowPaddingEnd

                    val optimalItemWidth = if (isCompact) {
                        if (isLandscape) 160.dp else 115.dp
                    } else {
                        val baseWidth = if (isLandscape) (posterWidthDp.dp * 1.45f) else posterWidthDp.dp
                        val netWidth = (availableWidth - totalHorizontalPadding).coerceAtLeast(100.dp)
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
                            start = rowPaddingStart,
                            end = rowPaddingEnd,
                            top = rowVerticalPadding,
                            bottom = rowVerticalPadding,
                        ),
                        headerPadding = PaddingValues(
                            start = rowPaddingStart,
                            end = rowPaddingEnd,
                            top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                            bottom = 4.dp,
                        ),
                        itemSpacing = spacingDp,
                    ) {
                        items(
                            count = shelf.items.size,
                            key = { i -> "${shelf.catalog.id}_${shelf.items[i].id}" },
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
                        .background(Color.Black.copy(alpha = heroBackdropDarkening))
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


@Composable
private fun ExploreSearchResultsView(
    searchQuery: String,
    searchResults: ExploreSearchResults?,
    isSearching: Boolean,
    isShelvesMode: Boolean,
    drilledCategory: String?,
    recommendations: List<ExploreItem> = emptyList(),
    watchHistoryMap: Map<String, WatchHistory>,
    autoCleanTitles: Boolean,
    onDrillCategory: (String) -> Unit,
    onReturnFromDrill: () -> Unit,
    onClearSearch: () -> Unit,
    onItemClick: (ExploreItem) -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()

    val dockPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
    val dockPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp
    val spacingDp = homeSpacingDp.dp
    val minPosterSize = posterWidthDp.dp
    val optimalItemWidth = posterWidthDp.dp
    val rowVerticalPadding = (4.dp + (homeVerticalSpacingDp * 0.25f).dp)

    val results = searchResults
    val hasAnyResults = results != null && (
        results.movies.isNotEmpty() ||
        results.series.isNotEmpty() ||
        results.anime.isNotEmpty() ||
        results.addonGroups.any { it.items.isNotEmpty() }
    )

    if ((results == null || !hasAnyResults) && isSearching) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp),
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
                    text = "Searching Cinemeta & Addon Catalogs for \"$searchQuery\"...",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.TextMuted,
                )
            }
        }
        return
    }

    if (!hasAnyResults && !isSearching) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
            contentPadding = PaddingValues(
                top = 104.dp,
                bottom = 40.dp,
            ),
        ) {
            item(key = "search_no_results_header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = dockPaddingStart, end = dockPaddingEnd, top = 20.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = theme.TextMuted,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.TextPrimary,
                        )
                        Text(
                            text = "Check spelling or explore recommended titles below.",
                            fontSize = 12.5.sp,
                            color = theme.TextMuted,
                        )
                        Button(
                            onClick = onClearSearch,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("Clear Search", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (recommendations.isNotEmpty()) {
                item(key = "search_recommendations_row") {
                    CategoryRowWithHeader(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Trending & Recommended",
                        itemCount = recommendations.size,
                        onViewAll = null,
                        rowContentPadding = PaddingValues(
                            start = dockPaddingStart,
                            end = dockPaddingEnd,
                            top = rowVerticalPadding,
                            bottom = rowVerticalPadding,
                        ),
                        headerPadding = PaddingValues(
                            start = dockPaddingStart,
                            end = dockPaddingEnd,
                            top = 12.dp,
                            bottom = 4.dp,
                        ),
                        itemSpacing = spacingDp,
                    ) {
                        items(
                            count = recommendations.size,
                            key = { i -> "search_rec_${recommendations[i].id}" },
                        ) { i ->
                            val item = recommendations[i]
                            val watchHistory = watchHistoryMap[item.id]
                                ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                            Box(modifier = Modifier.width(optimalItemWidth)) {
                                ExplorePosterCard(
                                    item = item,
                                    watchHistory = watchHistory,
                                    autoCleanTitles = autoCleanTitles,
                                    isCatalogLandscape = false,
                                    onClick = { onItemClick(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (drilledCategory != null) {
        val drilledItems = when (drilledCategory) {
            "Movies" -> results?.movies.orEmpty()
            "TV Series" -> results?.series.orEmpty()
            "Anime" -> results?.anime.orEmpty()
            else -> results?.addonGroups?.firstOrNull { it.addonName == drilledCategory }?.items.orEmpty()
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minPosterSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dockPaddingStart,
                end = dockPaddingEnd,
                top = 104.dp,
                bottom = 40.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacingDp),
            verticalArrangement = Arrangement.spacedBy(spacingDp),
        ) {
            items(
                count = drilledItems.size,
                key = { i -> "search_drilled_${drilledCategory}_${drilledItems[i].id}" },
            ) { i ->
                val item = drilledItems[i]
                val watchHistory = watchHistoryMap[item.id]
                    ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                ExplorePosterCard(
                    item = item,
                    watchHistory = watchHistory,
                    autoCleanTitles = autoCleanTitles,
                    isCatalogLandscape = false,
                    onClick = { onItemClick(item) },
                )
            }
        }
        return
    }

    if (!isShelvesMode) {
        val allSearchItems = remember(results) {
            if (results == null) emptyList()
            else {
                val seen = mutableSetOf<String>()
                val list = mutableListOf<ExploreItem>()
                results.topMatch?.let {
                    if (seen.add(it.id)) list.add(it)
                }
                (results.movies + results.series + results.anime + results.addonGroups.flatMap { it.items }).forEach { item ->
                    if (seen.add(item.id)) list.add(item)
                }
                list
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minPosterSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dockPaddingStart,
                end = dockPaddingEnd,
                top = 104.dp,
                bottom = 40.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacingDp),
            verticalArrangement = Arrangement.spacedBy(spacingDp),
        ) {
            items(
                count = allSearchItems.size,
                key = { i -> "search_all_grid_${allSearchItems[i].id}" },
            ) { i ->
                val item = allSearchItems[i]
                val watchHistory = watchHistoryMap[item.id]
                    ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                ExplorePosterCard(
                    item = item,
                    watchHistory = watchHistory,
                    autoCleanTitles = autoCleanTitles,
                    isCatalogLandscape = false,
                    onClick = { onItemClick(item) },
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
        contentPadding = PaddingValues(
            top = 104.dp,
            bottom = 40.dp,
        ),
    ) {

        // 1. Movies Row
        if (results != null && results.movies.isNotEmpty()) {
            val movies = results.movies
            item(key = "search_row_movies") {
                CategoryRowWithHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Movies",
                    itemCount = movies.size,
                    onViewAll = { onDrillCategory("Movies") },
                    rowContentPadding = PaddingValues(
                        start = dockPaddingStart,
                        end = dockPaddingEnd,
                        top = rowVerticalPadding,
                        bottom = rowVerticalPadding,
                    ),
                    headerPadding = PaddingValues(
                        start = dockPaddingStart,
                        end = dockPaddingEnd,
                        top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                        bottom = 4.dp,
                    ),
                    itemSpacing = spacingDp,
                ) {
                    items(
                        count = movies.size,
                        key = { i -> "search_movie_${movies[i].id}" },
                    ) { i ->
                        val item = movies[i]
                        val watchHistory = watchHistoryMap[item.id]
                            ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                        Box(modifier = Modifier.width(optimalItemWidth)) {
                            ExplorePosterCard(
                                item = item,
                                watchHistory = watchHistory,
                                autoCleanTitles = autoCleanTitles,
                                isCatalogLandscape = false,
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }

        // 2. TV Series Row
        if (results != null && results.series.isNotEmpty()) {
            val series = results.series
            item(key = "search_row_series") {
                CategoryRowWithHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = "TV Series",
                    itemCount = series.size,
                    onViewAll = { onDrillCategory("TV Series") },
                    rowContentPadding = PaddingValues(
                        start = dockPaddingStart,
                        end = dockPaddingEnd,
                        top = rowVerticalPadding,
                        bottom = rowVerticalPadding,
                    ),
                    headerPadding = PaddingValues(
                        start = dockPaddingStart,
                        end = dockPaddingEnd,
                        top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                        bottom = 4.dp,
                    ),
                    itemSpacing = spacingDp,
                ) {
                    items(
                        count = series.size,
                        key = { i -> "search_series_${series[i].id}" },
                    ) { i ->
                        val item = series[i]
                        val watchHistory = watchHistoryMap[item.id]
                            ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                        Box(modifier = Modifier.width(optimalItemWidth)) {
                            ExplorePosterCard(
                                item = item,
                                watchHistory = watchHistory,
                                autoCleanTitles = autoCleanTitles,
                                isCatalogLandscape = false,
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }

        // 3. Anime Row
        if (results != null && results.anime.isNotEmpty()) {
            val anime = results.anime
            item(key = "search_row_anime") {
                CategoryRowWithHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Anime",
                    itemCount = anime.size,
                    onViewAll = { onDrillCategory("Anime") },
                    rowContentPadding = PaddingValues(
                        start = dockPaddingStart,
                        end = dockPaddingEnd,
                        top = rowVerticalPadding,
                        bottom = rowVerticalPadding,
                    ),
                    headerPadding = PaddingValues(
                        start = dockPaddingStart,
                        end = dockPaddingEnd,
                        top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                        bottom = 4.dp,
                    ),
                    itemSpacing = spacingDp,
                ) {
                    items(
                        count = anime.size,
                        key = { i -> "search_anime_${anime[i].id}" },
                    ) { i ->
                        val item = anime[i]
                        val watchHistory = watchHistoryMap[item.id]
                            ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                        Box(modifier = Modifier.width(optimalItemWidth)) {
                            ExplorePosterCard(
                                item = item,
                                watchHistory = watchHistory,
                                autoCleanTitles = autoCleanTitles,
                                isCatalogLandscape = false,
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }

        // 4. Addon-specific Groups
        results?.addonGroups?.forEach { group ->
            if (group.items.isNotEmpty()) {
                item(key = "search_row_addon_${group.addonName}") {
                    CategoryRowWithHeader(
                        modifier = Modifier.fillMaxWidth(),
                        title = group.addonName,
                        itemCount = group.items.size,
                        onViewAll = { onDrillCategory(group.addonName) },
                        rowContentPadding = PaddingValues(
                            start = dockPaddingStart,
                            end = dockPaddingEnd,
                            top = rowVerticalPadding,
                            bottom = rowVerticalPadding,
                        ),
                        headerPadding = PaddingValues(
                            start = dockPaddingStart,
                            end = dockPaddingEnd,
                            top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                            bottom = 4.dp,
                        ),
                        itemSpacing = spacingDp,
                    ) {
                        items(
                            count = group.items.size,
                            key = { i -> "search_${group.addonName}_${group.items[i].id}" },
                        ) { i ->
                            val item = group.items[i]
                            val watchHistory = watchHistoryMap[item.id]
                                ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                            Box(modifier = Modifier.width(optimalItemWidth)) {
                                ExplorePosterCard(
                                    item = item,
                                    watchHistory = watchHistory,
                                    autoCleanTitles = autoCleanTitles,
                                    isCatalogLandscape = false,
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
private fun ExploreGridView(
    uiState: ExploreUiState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    minPosterSize: androidx.compose.ui.unit.Dp,
    isLandscapeGrid: Boolean,
    autoCleanTitles: Boolean,
    onItemClick: (ExploreItem) -> Unit,
) {
    val theme = LocalDesktopTheme.current

    if (uiState.isLoading && uiState.rawItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
                strokeWidth = 3.dp,
            )
        }
    } else if (!uiState.isLoading && uiState.displayItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val emptyText = if (uiState.allCatalogs.isEmpty()) {
                "No catalogs active. Add an addon from Extensions or use Search to browse providers."
            } else {
                "No titles match the selected filters or search query."
            }
            Text(
                text = emptyText,
                color = theme.TextMuted,
                fontSize = 13.5.sp,
            )
        }
    } else {
        val dockPosition by AppearanceConfig.dockPosition.collectAsState()
        val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
        val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
        val gridPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
        val gridPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp
        val spacingDp = homeSpacingDp.dp
        val verticalSpacingDp = if (homeVerticalSpacingDp > 0) homeVerticalSpacingDp.dp else 16.dp

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minPosterSize),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(verticalSpacingDp),
            horizontalArrangement = Arrangement.spacedBy(spacingDp),
            contentPadding = PaddingValues(
                start = gridPaddingStart,
                end = gridPaddingEnd,
                bottom = 28.dp,
            ),
        ) {
            items(
                count = uiState.displayItems.size,
                key = { index -> uiState.displayItems[index].id },
            ) { index ->
                val item = uiState.displayItems[index]
                val watchHistory = uiState.watchHistoryMap[item.id]
                    ?: uiState.watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]
                ExplorePosterCard(
                    item = item,
                    watchHistory = watchHistory,
                    autoCleanTitles = autoCleanTitles,
                    isCatalogLandscape = isLandscapeGrid,
                    onClick = { onItemClick(item) },
                )
            }

            if (uiState.isLoadingMore) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorePosterCard(
    item: ExploreItem,
    watchHistory: WatchHistory? = null,
    autoCleanTitles: Boolean = true,
    isCatalogLandscape: Boolean = false,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val sanitized = remember(item.name, autoCleanTitles) { CardTitleSanitizer.sanitize(item.name, autoClean = autoCleanTitles) }
    val roundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val posterTitlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()
    val posterDepthEffectEnabled by AppearanceConfig.posterDepthEffectEnabled.collectAsState()
    val shape = remember(roundingDp) { RoundedCornerShape(roundingDp.dp) }
    val primary = MaterialTheme.colorScheme.primary

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val hasProgress = watchHistory != null && watchHistory.position > 0L && watchHistory.duration > 0L
    val isCompleted = watchHistory != null && watchHistory.duration > 0L && PlayerLinkHandler.isCompleted(watchHistory.position, watchHistory.duration)
    val progress = if (watchHistory != null && watchHistory.position > 0L && watchHistory.duration > 0L) {
        if (isCompleted) 1f else (watchHistory.position.toFloat() / watchHistory.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val seText = if (watchHistory != null && (watchHistory.season != null || watchHistory.episode != null)) {
        listOfNotNull(
            watchHistory.season?.let { "S$it" },
            watchHistory.episode?.let { "E$it" },
        ).joinToString("")
    } else ""

    // Deterministic aspect ratio derived from catalog & media type (no async image jumping)
    val isLandscape = remember(item.posterShape, item.type, isCatalogLandscape) {
        when {
            item.posterShape.equals("landscape", ignoreCase = true) -> true
            item.type.equals("tv", ignoreCase = true) || item.type.equals("channel", ignoreCase = true) -> true
            isCatalogLandscape -> true
            else -> false
        }
    }
    val effectiveAspectRatio = if (isLandscape) 16f / 9f else 2f / 3f

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            // Ambient primary glow bloom on hover (matching Home's PosterCard)
            if (isHovered && posterHoverGlowEnabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .background(primary.copy(alpha = 0.65f), shape),
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .posterHoverEffect(shape)
                    .clip(shape)
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
                shape = shape,
                color = theme.SurfaceCard,
                tonalElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(effectiveAspectRatio)
                        .posterDepthEffect(shape, enabled = posterDepthEffectEnabled),
                ) {
                    AsyncImage(
                        model = item.posterUrl ?: item.backgroundUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Dark scrim on hover
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                        )
                    }

                    // Frosted glass play button (matching Home's 56dp aesthetic)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
                        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp).padding(start = 2.dp),
                            )
                        }
                    }

                    // Inside Title Overlay (on hover if posterTitlePosition == INSIDE)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered && posterTitlePosition == PosterTitlePosition.INSIDE,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Transparent,
                                            0.35f to Color.Black.copy(alpha = 0.7f),
                                            1f to Color.Black.copy(alpha = 0.92f),
                                        ),
                                    ),
                                )
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = sanitized.displayTitle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }

                    // Top Badges Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Top Left: Rating
                        item.rating?.let { rating ->
                            if (rating > 0.0) {
                                DesktopBadgeComponents.RatingGoldBadge(rating = rating)
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                        } ?: Spacer(modifier = Modifier.width(1.dp))

                        // Top Right: Format tags if detected
                        if (sanitized.hasSub || sanitized.hasDub) {
                            DesktopBadgeComponents.SubDubBadge(hasSub = sanitized.hasSub, hasDub = sanitized.hasDub)
                        }
                    }

                    // Bottom Watch Indicators
                    if (isCompleted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Watched",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = "Watched",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    } else if (hasProgress && progress > 0f) {
                        if (seText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 6.dp, bottom = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = seText,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .background(Color.Black.copy(alpha = 0.6f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }

        // Title BELOW (matches Home's 38dp height, 2-line layout and text shadow)
        if (posterTitlePosition == PosterTitlePosition.BELOW) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = sanitized.displayTitle,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = getTextShadow(),
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun CompactCatalogChip(
    catalog: ManifestCatalogDescriptor,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else if (isHovered) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.White.copy(alpha = 0.05f)
        },
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.dp else 0.5.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else if (isHovered) {
                Color.White.copy(alpha = 0.30f)
            } else {
                Color.White.copy(alpha = 0.12f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = catalog.name,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.Black else theme.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GenreDropdown(
    selectedGenre: String,
    genres: List<String>,
    onSelectGenre: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalDesktopTheme.current
    val allOptions = remember(genres) { listOf("All") + genres }
    val displayText = if (selectedGenre.equals("All", ignoreCase = true)) "All Genres" else selectedGenre

    Box {
        Surface(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true },
            color = Color.White.copy(alpha = 0.05f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = displayText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.TextPrimary,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = theme.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            allOptions.forEach { genre ->
                val isSelected = selectedGenre.equals(genre, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (genre == "All") "All Genres" else genre,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else theme.TextPrimary,
                        )
                    },
                    onClick = {
                        onSelectGenre(genre)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun YearDropdown(
    selectedYear: String,
    onSelectYear: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalDesktopTheme.current

    Box {
        Surface(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true },
            color = Color.White.copy(alpha = 0.05f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = selectedYear,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.TextPrimary,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = theme.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            EXPLORE_YEAR_OPTIONS.forEach { yr ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = yr,
                            fontSize = 12.sp,
                            fontWeight = if (yr == selectedYear) FontWeight.Bold else FontWeight.Normal,
                            color = if (yr == selectedYear) MaterialTheme.colorScheme.primary else theme.TextPrimary,
                        )
                    },
                    onClick = {
                        onSelectYear(yr)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExploreSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    searchResults: ExploreSearchResults?,
    isLiveSearching: Boolean,
    onItemClick: (ExploreItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val hazeState = com.lagradost.cloudstream3.desktop.ui.LocalHazeState.current
    val focusManager = LocalFocusManager.current
    val isAmoled = theme.isAmoled
    val isLightMode = theme.isLightMode
    val searchShape = RoundedCornerShape(16.dp)

    var isFocused by remember { mutableStateOf(false) }
    val targetWidth = if (isFocused || query.isNotBlank()) 380.dp else 290.dp
    val animatedWidth by animateDpAsState(targetWidth, animationSpec = tween(180))

    val hazeModifier = if (hazeState != null && !isAmoled) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = if (isLightMode) Color(0xFFEAEAF0).copy(alpha = 0.72f) else Color(0xFF0F0F14).copy(alpha = 0.72f),
                tint = HazeTint(if (isLightMode) Color(0xFFF0F0F4).copy(alpha = 0.50f) else Color(0xFF14141A).copy(alpha = 0.55f)),
                blurRadius = 24.dp,
                noiseFactor = 0f,
            ),
        )
    } else {
        Modifier.background(
            if (isAmoled) Color.Black.copy(alpha = 0.94f)
            else if (isLightMode) theme.SurfaceElevated.copy(alpha = 0.92f)
            else Color(0xFF121218).copy(alpha = 0.88f)
        )
    }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .height(46.dp)
                .width(animatedWidth)
                .shadow(
                    elevation = 10.dp,
                    shape = searchShape,
                    spotColor = Color.Black.copy(alpha = 0.40f),
                    ambientColor = Color.Black.copy(alpha = 0.20f),
                )
                .clip(searchShape)
                .then(hazeModifier)
                .border(
                    BorderStroke(
                        1.dp,
                        if (isFocused) {
                            SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    if (isLightMode) theme.Divider.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f),
                                    if (isLightMode) theme.Divider.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
                                )
                            )
                        },
                    ),
                    shape = searchShape,
                ),
            color = Color.Transparent,
            shape = searchShape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (query.isNotBlank() || isFocused) MaterialTheme.colorScheme.primary else theme.TextMuted,
                    modifier = Modifier.size(17.dp),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search movies, series, anime...",
                            fontSize = 12.5.sp,
                            color = theme.TextMuted.copy(alpha = 0.7f),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.TextPrimary,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused },
                    )
                }
                if (isLiveSearching) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = theme.TextMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { onClear() },
                    )
                }

                // Sleek Cancel Pill Button
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFocused || query.isNotBlank(),
                    enter = androidx.compose.animation.fadeIn(tween(150)) + androidx.compose.animation.expandHorizontally(),
                    exit = androidx.compose.animation.fadeOut(tween(100)) + androidx.compose.animation.shrinkHorizontally(),
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                focusManager.clearFocus()
                                if (query.isNotBlank()) onClear()
                            },
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCatalogState(
    onNavigateToSettings: () -> Unit,
) {
    val theme = LocalDesktopTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )

            Text(
                text = "No Catalog Addons Configured",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = theme.TextPrimary,
            )

            Text(
                text = "Configure catalog metadata addons in Settings to explore real-time movie, series, and anime catalogs.",
                fontSize = 12.5.sp,
                color = theme.TextMuted,
            )

            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Open Addons Settings", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}



