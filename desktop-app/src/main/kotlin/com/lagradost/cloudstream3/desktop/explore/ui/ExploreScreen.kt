package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
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
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.components.getTextShadow
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.text.style.TextAlign
import java.util.Locale

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onNavigate: (Config) -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val uiState by viewModel.uiState.collectAsState()
    val gridScale by AppearanceConfig.gridScale.collectAsState()
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

    val minPosterSize = when (gridScale) {
        "Compact" -> if (isLandscapeGrid) 220.dp else 145.dp
        "Large" -> if (isLandscapeGrid) 320.dp else 210.dp
        else -> if (isLandscapeGrid) 270.dp else 175.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
        } else {
            // Content Area: Platform Shelves vs Main Shelves vs Grid View
            val currentPlatform = uiState.drilledPlatform
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
                        onItemClick = { item -> viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item)) },
                    )
                }
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
                        onHeroBackgroundChanged = { url -> currentHeroImageUrl = url },
                        onViewAll = { catalog -> viewModel.onEvent(ExploreUiEvent.DrillIntoCatalog(catalog)) },
                        onItemClick = { item -> viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item)) },
                        onSelectPlatform = { platform -> viewModel.onEvent(ExploreUiEvent.DrillIntoPlatform(platform)) },
                    )
                }
            } else {
                // Classic or Drilled Grid View (padded down so it does not collide with floating controls)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 98.dp),
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
                        onItemClick = { item -> viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item)) },
                    )
                }
            }

            // Floating Header Controls Overlay
            val dockPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
            val dockPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp
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
                                0.0f to Color.Black.copy(alpha = 0.70f),
                                0.6f to Color.Black.copy(alpha = 0.25f),
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
                    modifier = Modifier.padding(top = 58.dp),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dockPaddingStart, end = dockPaddingEnd, bottom = 6.dp)
            .height(34.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val drilledCat = uiState.drilledCatalog
        val drilledPlat = uiState.drilledPlatform
        if (drilledCat != null || drilledPlat != null) {
            // Left: Back button + Drilled Catalog Title + Addon Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Settings-consistent back button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.onEvent(ExploreUiEvent.ReturnToShelves) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
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
            }

            // Right: Search + Genre Filter + Year Filter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExploreSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onEvent(ExploreUiEvent.UpdateSearchQuery(it)) },
                    onClear = { viewModel.onEvent(ExploreUiEvent.ClearSearchQuery) },
                    searchResults = uiState.searchResults,
                    isLiveSearching = uiState.isLiveSearching,
                    onItemClick = { item -> viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item)) },
                )

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
            }
        } else {
            // Left: Title + Segmented Media Type Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Explore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                    )
                }

                // Media type selector pills
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    color = Color.White.copy(alpha = 0.05f),
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .clickable { viewModel.onEvent(ExploreUiEvent.SelectType(type)) }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else theme.TextMuted,
                                    letterSpacing = 0.2.sp,
                                )
                            }
                        }
                    }
                }

                // Addon Source filter dropdown
                if (uiState.availableAddons.size > 2) {
                    AddonSourceDropdown(
                        selectedAddon = uiState.selectedAddon,
                        addons = uiState.availableAddons,
                        onSelectAddon = { viewModel.onEvent(ExploreUiEvent.SelectAddon(it)) },
                    )
                }
            }

            // Right: Search Input + Mode Toggle + Optional Year Filter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExploreSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onEvent(ExploreUiEvent.UpdateSearchQuery(it)) },
                    onClear = { viewModel.onEvent(ExploreUiEvent.ClearSearchQuery) },
                    searchResults = uiState.searchResults,
                    isLiveSearching = uiState.isLiveSearching,
                    onItemClick = { item -> viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item)) },
                )

                if (!uiState.isShelvesMode) {
                    YearDropdown(
                        selectedYear = uiState.selectedYear,
                        onSelectYear = { viewModel.onEvent(ExploreUiEvent.SelectYear(it)) },
                    )
                }

                // View Mode Toggle (Shelves vs Grid)
                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { viewModel.onEvent(ExploreUiEvent.ToggleViewMode(!uiState.isShelvesMode)) },
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (uiState.isShelvesMode) Icons.Default.GridView else Icons.Default.ViewAgenda,
                            contentDescription = if (uiState.isShelvesMode) "Switch to Grid" else "Switch to Shelves",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = if (uiState.isShelvesMode) "Grid" else "Shelves",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.TextPrimary,
                        )
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

    val heroMetaMap = remember(heroItems) {
        heroItems.associate { item ->
            item.id to HeroMeta(
                title = item.name,
                backdropUrl = item.backgroundUrl ?: item.posterUrl,
                logoUrl = item.logoUrl,
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
                text = if (searchQuery.isNotBlank()) "No items match \"$searchQuery\" in active catalogs." else "No catalogs loaded.",
                color = theme.TextMuted,
                fontSize = 13.5.sp,
            )
        }
        return
    }

    val shouldPadTop = searchQuery.isNotBlank() || heroItems.isEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
        contentPadding = PaddingValues(
            top = if (shouldPadTop) 104.dp else 0.dp,
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
        if (searchQuery.isBlank()) {
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
            key = { index -> "${filteredShelves[index].catalog.addonBaseUrl}_${filteredShelves[index].catalog.type}_${filteredShelves[index].catalog.id}" },
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
            top = if (shouldPadTop) 104.dp else 0.dp,
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
            key = { index -> "${filteredShelves[index].catalog.addonBaseUrl}_${filteredShelves[index].catalog.id}" },
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
        val gridPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
        val gridPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minPosterSize),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                        .aspectRatio(effectiveAspectRatio),
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
    var isFocused by remember { mutableStateOf(false) }
    val targetWidth = if (isFocused || query.isNotBlank()) 260.dp else 180.dp
    val animatedWidth by animateDpAsState(targetWidth, animationSpec = tween(180))

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .height(32.dp)
                .width(animatedWidth)
                .clip(RoundedCornerShape(6.dp)),
            color = Color.White.copy(alpha = if (isFocused) 0.08f else 0.05f),
            border = BorderStroke(
                0.5.dp,
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
            ),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (query.isNotBlank() || isFocused) MaterialTheme.colorScheme.primary else theme.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search TMDB & Addons...",
                            fontSize = 11.5.sp,
                            color = theme.TextMuted.copy(alpha = 0.6f),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 11.5.sp,
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
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                    )
                } else if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = theme.TextMuted,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onClear() },
                    )
                }
            }
        }

        // Live suggestions dropdown popup
        if (isFocused && query.trim().length >= 2) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 40),
                onDismissRequest = { isFocused = false },
            ) {
                ExploreSearchDropdown(
                    results = searchResults,
                    isSearching = isLiveSearching,
                    query = query,
                    onItemClick = { item ->
                        isFocused = false
                        onItemClick(item)
                    },
                )
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

@Composable
private fun AddonSourceDropdown(
    selectedAddon: String,
    addons: List<String>,
    onSelectAddon: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalDesktopTheme.current

    Box {
        Surface(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true },
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Source: $selectedAddon",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selectedAddon == "All Sources") theme.TextMuted else MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = theme.TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF14161E))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
        ) {
            addons.forEach { addon ->
                val isSelected = addon == selectedAddon
                DropdownMenuItem(
                    text = {
                        Text(
                            text = addon,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else theme.TextPrimary,
                        )
                    },
                    onClick = {
                        onSelectAddon(addon)
                        expanded = false
                    },
                )
            }
        }
    }
}

