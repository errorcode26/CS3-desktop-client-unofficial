package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.viewmodel.EXPLORE_YEAR_OPTIONS
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEffect
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEvent
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreViewModel
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.WatchHistory

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onNavigate: (Config) -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val uiState by viewModel.uiState.collectAsState()
    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()

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

    val minPosterSize = when (gridScale) {
        "Compact" -> 145.dp
        "Large" -> 210.dp
        else -> 175.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            if (uiState.isInitializing) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                EmptyCatalogState(onNavigateToSettings = { onNavigate(Config.Settings) })
            } else {
                // Row 1: Primary Scope & Search Controls (~34dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    }

                    // Right: Search Input + Year Filter Dropdown
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExploreSearchField(
                            query = uiState.searchQuery,
                            onQueryChange = { viewModel.onEvent(ExploreUiEvent.UpdateSearchQuery(it)) },
                            onClear = { viewModel.onEvent(ExploreUiEvent.ClearSearchQuery) },
                        )

                        YearDropdown(
                            selectedYear = uiState.selectedYear,
                            onSelectYear = { viewModel.onEvent(ExploreUiEvent.SelectYear(it)) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Catalogs Rail (Left) + Inline Genre Dropdown (Right) (~30dp)
                if (uiState.filteredCatalogs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Catalogs Pills (with scroll and chevrons)
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

                            // Left Chevron Button
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
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
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

                            // Right Chevron Button
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
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
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

                        // Inline Genre Dropdown
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
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Media Poster Grid
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minPosterSize),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        items(
                            count = uiState.displayItems.size,
                            key = { index -> uiState.displayItems[index].id },
                        ) { index ->
                            val item = uiState.displayItems[index]
                            ExplorePosterCard(
                                item = item,
                                autoCleanTitles = autoCleanTitles,
                                onClick = { viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item)) },
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
        }

        // Multi-Provider Match Resolver Dialog
        ExploreProviderDialog(
            item = uiState.selectedItemForMatch,
            matches = uiState.providerMatches,
            isSearching = uiState.isSearchingProviders,
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
        )
    }
}

@Composable
private fun ExplorePosterCard(
    item: ExploreItem,
    autoCleanTitles: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val sanitized = remember(item.name, autoCleanTitles) { CardTitleSanitizer.sanitize(item.name, autoClean = autoCleanTitles) }
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .posterHoverEffect(shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape),
        ) {
            AsyncImage(
                model = item.posterUrl ?: item.backgroundUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Top Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
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
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = sanitized.displayTitle,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = theme.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        item.releaseYear?.let { year ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = year,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = theme.TextMuted,
            )
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
) {
    val theme = LocalDesktopTheme.current
    Surface(
        modifier = Modifier
            .height(32.dp)
            .width(180.dp)
            .clip(RoundedCornerShape(6.dp)),
        color = Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
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
                tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary else theme.TextMuted,
                modifier = Modifier.size(14.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Filter titles...",
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
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
