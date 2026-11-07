package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.explore.client.ExploreHubClient
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ExploreShelf
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.models.StreamingPlatform
import com.lagradost.cloudstream3.desktop.repo.HeroMeta
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.home.HomeHeroCarousel
import com.lagradost.cloudstream3.desktop.ui.screens.home.HomeHeroCarouselPlaceholder
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun ExploreNativeHero(
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
internal fun ExploreShelvesView(
    shelves: List<ExploreShelf>,
    heroItems: List<ExploreItem>,
    isShelvesLoading: Boolean = false,
    searchQuery: String,
    watchHistoryMap: Map<String, WatchHistory>,
    autoCleanTitles: Boolean,
    selectedType: String = "movie",
    state: LazyListState = rememberLazyListState(),
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

                    val typeLabel = when (shelf.catalog.type.lowercase(Locale.US)) {
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
internal fun PlatformShelvesView(
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
