package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchResults
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import java.util.Locale

@Composable
fun ExploreSearchDropdown(
    results: ExploreSearchResults?,
    isSearching: Boolean,
    query: String,
    onItemClick: (ExploreItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val surfaceBg = Color(0xFF0F1117)
    val cardBg = Color(0xFF161820)
    val borderCol = Color.White.copy(alpha = 0.12f)
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .width(560.dp)
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderCol, RoundedCornerShape(16.dp)),
        color = surfaceBg,
        shadowElevation = 24.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (isSearching) "Searching TMDB, Addons & Anime..." else "Results for \"$query\"",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.TextPrimary,
                    )
                }

                if (isSearching) {
                    CircularProgressIndicator(
                        color = primary,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            HorizontalDivider(color = borderCol, thickness = 0.5.dp)

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Top Match Spotlight Card
                val topMatch = results?.topMatch
                if (topMatch != null) {
                    item(key = "search_top_match") {
                        SearchTopMatchCard(
                            item = topMatch,
                            onClick = { onItemClick(topMatch) },
                        )
                    }
                }

                // Movies Section
                val movies = results?.movies ?: emptyList()
                if (movies.isNotEmpty()) {
                    item(key = "search_section_movies") {
                        SearchCategoryRow(
                            title = "Movies (${movies.size})",
                            items = movies,
                            onItemClick = onItemClick,
                        )
                    }
                }

                // TV Shows Section
                val series = results?.series ?: emptyList()
                if (series.isNotEmpty()) {
                    item(key = "search_section_series") {
                        SearchCategoryRow(
                            title = "TV Shows (${series.size})",
                            items = series,
                            onItemClick = onItemClick,
                        )
                    }
                }

                // Anime Section
                val anime = results?.anime ?: emptyList()
                if (anime.isNotEmpty()) {
                    item(key = "search_section_anime") {
                        SearchCategoryRow(
                            title = "Anime (${anime.size})",
                            items = anime,
                            onItemClick = onItemClick,
                        )
                    }
                }

                // Addon Specific Groups
                val addonGroups = results?.addonGroups ?: emptyList()
                for (group in addonGroups) {
                    if (group.items.isNotEmpty()) {
                        item(key = "search_addon_${group.addonName}") {
                            SearchCategoryRow(
                                title = "${group.addonName} (${group.items.size})",
                                items = group.items,
                                onItemClick = onItemClick,
                            )
                        }
                    }
                }

                // Empty state
                val hasAnyResults = topMatch != null || movies.isNotEmpty() || series.isNotEmpty() || anime.isNotEmpty() || addonGroups.any { it.items.isNotEmpty() }
                if (!isSearching && !hasAnyResults) {
                    item(key = "search_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "No matches found for \"$query\"",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.TextPrimary,
                                )
                                Text(
                                    text = "Try another title or check your enabled Stremio addons in Settings.",
                                    fontSize = 12.sp,
                                    color = theme.TextMuted,
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
private fun SearchTopMatchCard(
    item: ExploreItem,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF13161F),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Backdrop gradient background if available
            val bgImage = item.backgroundUrl ?: item.posterUrl
            if (bgImage != null) {
                AsyncImage(
                    model = bgImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to Color(0xFF0F1117).copy(alpha = 0.96f),
                                0.6f to Color(0xFF0F1117).copy(alpha = 0.88f),
                                1.0f to Color(0xFF0F1117).copy(alpha = 0.82f),
                            )
                        ),
                )
            }

            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Poster
                Surface(
                    modifier = Modifier
                        .width(68.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    color = Color.Black.copy(alpha = 0.3f),
                ) {
                    if (item.posterUrl != null) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Badge row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = primary.copy(alpha = 0.2f),
                        ) {
                            Text(
                                text = "TOP MATCH",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                letterSpacing = 0.5.sp,
                            )
                        }

                        val typeLabel = when (item.type.lowercase(Locale.US)) {
                            "series", "tv" -> "TV SHOW"
                            "anime" -> "ANIME"
                            else -> "MOVIE"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.08f),
                        ) {
                            Text(
                                text = typeLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        if (!item.releaseYear.isNullOrBlank()) {
                            Text(
                                text = item.releaseYear,
                                fontSize = 11.sp,
                                color = theme.TextMuted,
                            )
                        }

                        if (item.rating != null && item.rating > 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB800),
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f", item.rating),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB800),
                                )
                            }
                        }
                    }

                    // Title
                    Text(
                        text = item.name,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Description
                    if (!item.description.isNullOrBlank()) {
                        Text(
                            text = item.description,
                            fontSize = 11.sp,
                            color = theme.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp,
                        )
                    }

                    // Action badge
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primary,
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp).padding(1.dp),
                            )
                        }
                        Text(
                            text = "Stream with Addons",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCategoryRow(
    title: String,
    items: List<ExploreItem>,
    onItemClick: (ExploreItem) -> Unit,
) {
    val theme = LocalDesktopTheme.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = theme.TextPrimary,
            letterSpacing = 0.2.sp,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(
                items = items,
                key = { item -> "search_item_${item.id}_${item.type}" },
            ) { item ->
                SearchItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun SearchItemCard(
    item: ExploreItem,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current

    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            color = Color(0xFF1A1C24),
        ) {
            val img = item.posterUrl ?: item.backgroundUrl
            if (img != null) {
                AsyncImage(
                    model = img,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = item.name.take(2).uppercase(Locale.US),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Text(
            text = item.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = theme.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!item.releaseYear.isNullOrBlank()) {
            Text(
                text = item.releaseYear,
                fontSize = 10.sp,
                color = theme.TextMuted,
            )
        }
    }
}
