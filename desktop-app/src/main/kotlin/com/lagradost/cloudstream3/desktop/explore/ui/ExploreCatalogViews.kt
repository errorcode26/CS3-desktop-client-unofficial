package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.viewmodel.EXPLORE_YEAR_OPTIONS
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEvent
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiState
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreViewModel
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopDataStore

@Composable
internal fun ExploreDrilledCatalogView(
    dockPosition: DockPosition,
    viewModel: ExploreViewModel,
    uiState: ExploreUiState,
    gridState: LazyGridState,
    minPosterSize: Dp,
    isLandscapeGrid: Boolean,
    autoCleanTitles: Boolean,
    onItemClick: (ExploreItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = this.maxWidth >= 840.dp
        val catalog = uiState.drilledCatalog

        if (isWideScreen && catalog != null) {
            Row(modifier = Modifier.fillMaxSize()) {
                val sidebarDockPadding = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
                ExploreFilterSidebar(
                    catalog = catalog,
                    selectedGenre = uiState.selectedGenre,
                    selectedYear = uiState.selectedYear,
                    onSelectGenre = { g -> viewModel.onEvent(ExploreUiEvent.SelectGenre(g)) },
                    onSelectYear = { y -> viewModel.onEvent(ExploreUiEvent.SelectYear(y)) },
                    modifier = Modifier
                        .padding(start = sidebarDockPadding)
                        .width(220.dp)
                        .fillMaxHeight(),
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ExploreGridView(
                        hasExternalLeftSidebar = true,
                        uiState = uiState,
                        gridState = gridState,
                        minPosterSize = minPosterSize,
                        isLandscapeGrid = isLandscapeGrid,
                        autoCleanTitles = autoCleanTitles,
                        onItemClick = onItemClick,
                    )
                }
            }
        } else {
            ExploreGridView(
                hasExternalLeftSidebar = false,
                uiState = uiState,
                gridState = gridState,
                minPosterSize = minPosterSize,
                isLandscapeGrid = isLandscapeGrid,
                autoCleanTitles = autoCleanTitles,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
internal fun ExploreFilterSidebar(
    catalog: ManifestCatalogDescriptor,
    selectedGenre: String,
    selectedYear: String,
    onSelectGenre: (String) -> Unit,
    onSelectYear: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val scrollState = rememberScrollState()
    val isLightMode = theme.isLightMode

    val activeCount = (if (!selectedGenre.equals("All", ignoreCase = true)) 1 else 0) +
        (if (!selectedYear.equals("All Years", ignoreCase = true)) 1 else 0)
    val hasActiveFilter = activeCount > 0

    Surface(
        modifier = modifier
            .padding(top = 10.dp, bottom = 24.dp, end = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    0.5.dp,
                    if (isLightMode) theme.Divider.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                ),
                shape = RoundedCornerShape(16.dp),
            ),
        color = if (isLightMode) theme.SurfaceElevated.copy(alpha = 0.85f) else Color(0xFF14141A).copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Filter Title + Active Count Badge + Reset Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = theme.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Filters",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                    )
                    if (hasActiveFilter) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
                        ) {
                            Text(
                                text = "$activeCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (hasActiveFilter) {
                    Text(
                        text = "Reset",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onSelectGenre("All")
                                onSelectYear("All Years")
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            HorizontalDivider(
                color = if (isLightMode) theme.Divider.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                thickness = 0.5.dp,
            )

            // Section 1: Genres
            if (catalog.genres.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "GENRES",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextMuted,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )

                    val allGenres = remember(catalog.genres) { listOf("All") + catalog.genres }
                    allGenres.forEach { genre ->
                        val isSelected = selectedGenre.equals(genre, ignoreCase = true)
                        val genreLabel = if (genre == "All") "All Genres" else genre
                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered by interactionSource.collectIsHoveredAsState()

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .hoverable(interactionSource)
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    onSelectGenre(genre)
                                },
                            color = if (isSelected) {
                                Color.White.copy(alpha = 0.12f)
                            } else if (isHovered) {
                                Color.White.copy(alpha = 0.05f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = if (isSelected) {
                                BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f))
                            } else if (isHovered) {
                                BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                            } else {
                                null
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = genreLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else (if (isHovered) theme.TextPrimary else theme.TextMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = if (isLightMode) theme.Divider.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp,
                )
            }

            // Section 2: Release Year
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "RELEASE YEAR",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.TextMuted,
                    letterSpacing = 1.2.sp,
                )

                // 2-column compact chips
                val yearChunks = remember { EXPLORE_YEAR_OPTIONS.chunked(2) }
                yearChunks.forEach { rowYears ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowYears.forEach { yr ->
                            val isSelected = selectedYear.equals(yr, ignoreCase = true)
                            val interactionSource = remember { MutableInteractionSource() }
                            val isHovered by interactionSource.collectIsHoveredAsState()

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .hoverable(interactionSource)
                                    .clickable(interactionSource = interactionSource, indication = null) {
                                        onSelectYear(yr)
                                    },
                                color = if (isSelected) {
                                    Color.White.copy(alpha = 0.14f)
                                } else if (isHovered) {
                                    Color.White.copy(alpha = 0.07f)
                                } else {
                                    Color.White.copy(alpha = 0.03f)
                                },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    0.5.dp,
                                    if (isSelected) {
                                        Color.White.copy(alpha = 0.30f)
                                    } else if (isHovered) {
                                        Color.White.copy(alpha = 0.18f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                ),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = yr,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else (if (isHovered) theme.TextPrimary else theme.TextMuted),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (rowYears.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExploreGridView(
    hasExternalLeftSidebar: Boolean = false,
    uiState: ExploreUiState,
    gridState: LazyGridState,
    minPosterSize: Dp,
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
        val gridPaddingStart = if (hasExternalLeftSidebar) 12.dp else (if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp)
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
                key = { index -> "${uiState.displayItems[index].id}_$index" },
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
                item(span = { GridItemSpan(maxLineSpan) }) {
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
