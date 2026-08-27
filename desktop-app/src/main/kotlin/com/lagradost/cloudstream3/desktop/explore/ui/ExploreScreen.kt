package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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

                // Right: Addon Settings Link
                OutlinedButton(
                    onClick = { onNavigate(Config.Settings) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.04f),
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = theme.TextMuted,
                        )
                        Text(
                            text = "Addon Settings",
                            fontSize = 11.5.sp,
                            color = theme.TextPrimary,
                            fontWeight = FontWeight.Medium,
                        )
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
                // ── Controls & Filter Bar ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
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

                    // Catalog Feed Dropdown
                    if (uiState.filteredCatalogs.size > 1) {
                        CatalogDropdown(
                            catalogs = uiState.filteredCatalogs,
                            selected = uiState.selectedCatalog,
                            onSelect = { viewModel.onEvent(ExploreUiEvent.SelectCatalog(it)) },
                        )
                    }

                    // Year Filter Dropdown
                    YearDropdown(
                        selectedYear = uiState.selectedYear,
                        onSelectYear = { viewModel.onEvent(ExploreUiEvent.SelectYear(it)) },
                    )

                    // Refined Genre Filter Chips (Horizontal Scrollable)
                    uiState.selectedCatalog?.let { cat ->
                        if (cat.genres.isNotEmpty()) {
                            val allGenres = listOf("All") + cat.genres
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                allGenres.forEach { genre ->
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
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
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
                        }
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
private fun CatalogDropdown(
    catalogs: List<ManifestCatalogDescriptor>,
    selected: ManifestCatalogDescriptor?,
    onSelect: (ManifestCatalogDescriptor) -> Unit,
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
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = selected?.name ?: "Feed",
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
            catalogs.forEach { cat ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(cat.addonName, fontSize = 10.sp, color = theme.TextMuted)
                        }
                    },
                    onClick = {
                        onSelect(cat)
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
