package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
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
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEvent
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreViewModel
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
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
    val playVideo = LocalVideoPlayer.current
    val uiState by viewModel.uiState.collectAsState()
    val gridScale by AppearanceConfig.gridScale.collectAsState()

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val catalogListState = rememberLazyListState()
    val genreListState = rememberLazyListState()

    // Scroll to top when catalog, genre, year, or query changes
    LaunchedEffect(uiState.selectedCatalog, uiState.selectedGenre, uiState.selectedYear) {
        gridState.scrollToItem(0)
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
            // ── Top Header Bar: Perfect Horizontal Alignment ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                // Left: Screen Title
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Explore & Catalogs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                    )
                }

                // Center: Frosted Search Capsule (True Exact Window Center)
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(min = 280.dp, max = 440.dp)
                        .height(36.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = theme.TextMuted,
                            modifier = Modifier.size(15.dp),
                        )

                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onEvent(ExploreUiEvent.UpdateSearchQuery(it)) },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                color = theme.TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (uiState.searchQuery.isEmpty()) {
                                    Text(
                                        text = "Filter or search in catalog...",
                                        color = theme.TextMuted.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                    )
                                }
                                innerTextField()
                            },
                        )

                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onEvent(ExploreUiEvent.ClearSearchQuery) },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = theme.TextMuted,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.isInitializing) {
                // Initial Boot Discovery State (Smooth Spinner instead of flashing empty state)
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
                // ── Controls & Filter Hierarchy ──

                // Tier 1: Media Type Selector + Year Dropdown (Aligned in a clean bar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Frosted Segmented Media Type Selector
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.Black else theme.TextMuted,
                                        letterSpacing = 0.2.sp,
                                    )
                                }
                            }
                        }
                    }

                    // Year Filter Dropdown
                    YearDropdown(
                        selectedYear = uiState.selectedYear,
                        onSelectYear = { viewModel.onEvent(ExploreUiEvent.SelectYear(it)) },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tier 2: Dynamic Horizontal Catalog Cards Rail
                if (uiState.filteredCatalogs.isNotEmpty()) {
                    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            state = catalogListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPointerEvent(PointerEventType.Scroll) { event ->
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (delta != 0f) {
                                        coroutineScope.launch {
                                            catalogListState.scrollBy(delta * 120f)
                                        }
                                    }
                                },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(uiState.filteredCatalogs, key = { "${it.addonBaseUrl}_${it.type}_${it.id}" }) { catalog ->
                                val isSelected = uiState.selectedCatalog?.id == catalog.id &&
                                    uiState.selectedCatalog?.addonBaseUrl == catalog.addonBaseUrl
                                DynamicCatalogCard(
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
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                shadowElevation = 10.dp,
                                modifier = Modifier.size(34.dp),
                                onClick = {
                                    coroutineScope.launch { catalogListState.animateScrollBy(-420f) }
                                },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Scroll Left",
                                        tint = theme.TextPrimary,
                                        modifier = Modifier.size(22.dp),
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
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                shadowElevation = 10.dp,
                                modifier = Modifier.size(34.dp),
                                onClick = {
                                    coroutineScope.launch { catalogListState.animateScrollBy(420f) }
                                },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Scroll Right",
                                        tint = theme.TextPrimary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Tier 3: Dedicated Full-Width Genre Filter Chips
                uiState.selectedCatalog?.let { cat ->
                    if (cat.genres.isNotEmpty()) {
                        val allGenres = listOf("All") + cat.genres
                        @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                        LazyRow(
                            state = genreListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPointerEvent(PointerEventType.Scroll) { event ->
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (delta != 0f) {
                                        coroutineScope.launch {
                                            genreListState.scrollBy(delta * 80f)
                                        }
                                    }
                                },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(allGenres) { genre ->
                                val isGenreSelected = uiState.selectedGenre.equals(genre, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isGenreSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f)
                                        )
                                        .border(
                                            0.5.dp,
                                            if (isGenreSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f),
                                            RoundedCornerShape(6.dp),
                                        )
                                        .clickable { viewModel.onEvent(ExploreUiEvent.SelectGenre(genre)) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isGenreSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isGenreSelected) MaterialTheme.colorScheme.primary else theme.TextMuted,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                        Text(
                            text = "No titles match the selected filters or search query.",
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
                            key = { index -> "${uiState.displayItems[index].id}_$index" },
                        ) { index ->
                            val item = uiState.displayItems[index]
                            ExplorePosterCard(
                                item = item,
                                onClick = { viewModel.onEvent(ExploreUiEvent.OpenProviderPicker(item)) },
                            )
                        }
                    }
                }
            }
        }

        // Multi-Provider Match Resolver Dialog
        ExploreProviderDialog(
            item = uiState.selectedItemForMatch,
            matches = uiState.providerMatches,
            stremioStreams = uiState.stremioStreamMatches,
            isSearching = uiState.isSearchingProviders,
            onDismissRequest = { viewModel.onEvent(ExploreUiEvent.CloseProviderPicker) },
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
            onPlayDirectStream = { stream, item ->
                viewModel.onEvent(ExploreUiEvent.CloseProviderPicker)
                playVideo(
                    VideoLaunchData(
                        links = listOf(stream),
                        initialIndex = 0,
                        title = item.name,
                        subtitles = emptyList(),
                        startPositionMs = 0L,
                        history = WatchHistory(
                            parentId = "stremio_${item.id}",
                            showName = item.name,
                            showUrl = item.id,
                            apiName = stream.source,
                            posterUrl = item.posterUrl ?: item.backgroundUrl,
                            episodeThumbnailUrl = null,
                            screenshotUrl = null,
                            episode = if (item.type.equals("series", ignoreCase = true)) 1 else null,
                            season = if (item.type.equals("series", ignoreCase = true)) 1 else null,
                            episodeId = item.id,
                            position = 0L,
                            duration = 0L,
                            episodeDescription = item.description,
                        ),
                    )
                )
            },
        )
    }
}

@Composable
private fun ExplorePosterCard(
    item: ExploreItem,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val sanitized = remember(item.name) { CardTitleSanitizer.sanitize(item.name) }
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
private fun DynamicCatalogCard(
    catalog: ManifestCatalogDescriptor,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    var isHovered by remember { mutableStateOf(false) }
    val animatedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isHovered) 1.03f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(150),
    )

    Surface(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 220.dp)
            .height(74.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)
        } else if (isHovered) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.04f)
        },
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else if (isHovered) {
                Color.White.copy(alpha = 0.35f)
            } else {
                Color.White.copy(alpha = 0.12f)
            },
        ),
        tonalElevation = if (isSelected) 8.dp else 0.dp,
        shadowElevation = if (isHovered || isSelected) 6.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Eyebrow Source Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = catalog.addonName.uppercase(),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else theme.TextMuted,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (catalog.genres.isNotEmpty()) {
                    Text(
                        text = "${catalog.genres.size} GENRES",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.TextMuted.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            // Main Catalog Name
            Text(
                text = catalog.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) theme.TextPrimary else theme.TextPrimary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true },
            color = Color.White.copy(alpha = 0.05f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                text = "Install Cinemeta, Anime Kitsu, or CyberFlix in External Addons to explore real-time movie, series, and anime catalogs.",
                fontSize = 12.5.sp,
                color = theme.TextMuted,
            )

            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Open External Addons Settings", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
