package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun ExploreProviderDialog(
    item: ExploreItem?,
    matches: List<ProviderMatch>,
    isSearching: Boolean,
    watchHistory: WatchHistory? = null,
    onDismissRequest: () -> Unit,
    onSelectMatch: ((ProviderMatch) -> Unit)? = null,
    onOpenDetails: ((providerName: String, url: String, title: String) -> Unit)? = null,
    onOpenAddonMode: ((ExploreItem) -> Unit)? = null,
) {
    if (item == null) return

    val theme = LocalDesktopTheme.current
    val dialogBg = if (theme.isAmoled) Color.Black else theme.Background
    val panelBg = if (theme.isAmoled) Color(0xFF0C0C0C) else theme.SurfaceCard
    val panelBorder = if (theme.isAmoled) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.10f)

    val handleSelect: (ProviderMatch) -> Unit = { match ->
        onSelectMatch?.invoke(match) ?: onOpenDetails?.invoke(match.providerName, match.searchResponse.url, match.displayTitle)
    }

    val addonButtonText = remember(watchHistory) {
        if (watchHistory != null && watchHistory.duration > 0L) {
            val isCompleted = PlayerLinkHandler.isCompleted(watchHistory.position, watchHistory.duration)
            if (isCompleted) {
                "Watch Again"
            } else {
                val percent = ((watchHistory.position.toFloat() / watchHistory.duration.toFloat()) * 100).toInt().coerceIn(1, 99)
                val isSeries = watchHistory.season != null || watchHistory.episode != null
                if (isSeries) {
                    val se = listOfNotNull(
                        watchHistory.season?.let { "S$it" },
                        watchHistory.episode?.let { "E$it" },
                    ).joinToString("")
                    if (se.isNotBlank()) "Resume $se ($percent%)" else "Resume ($percent%)"
                } else {
                    "Resume ($percent%)"
                }
            }
        } else {
            "Play with Addon"
        }
    }

    val addonButtonIcon = remember(watchHistory) {
        Icons.Default.PlayArrow
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        containerColor = dialogBg,
        modifier = Modifier
            .fillMaxWidth(0.80f)
            .fillMaxHeight(0.84f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(dialogBg)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── LEFT PANE: MEDIA SHOWCASE CARD (42% width) ──
            Surface(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                color = if (theme.isAmoled) Color(0xFF0C0E12) else theme.SurfaceCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Full-width cinematic banner — anchored at top, outside scroll
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 230.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    ) {
                        AsyncImage(
                            model = item.backgroundUrl ?: item.posterUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Bottom scrim so content below reads cleanly
                        val surfaceColor = if (theme.isAmoled) Color(0xFF0C0E12) else theme.SurfaceCard
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Black.copy(alpha = 0.15f),
                                        0.55f to Color.Transparent,
                                        1.0f to surfaceColor.copy(alpha = 0.92f),
                                    )
                                ),
                        )
                        // Rating badge — top-left
                        item.rating?.let { rating ->
                            if (rating > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp),
                                ) {
                                    DesktopBadgeComponents.RatingGoldBadge(rating = rating)
                                }
                            }
                        }
                        // Portrait poster thumbnail overlaid bottom-left of banner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 14.dp, bottom = 12.dp)
                                .width(66.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                        ) {
                            AsyncImage(
                                model = item.posterUrl ?: item.backgroundUrl,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // Title / logo overlaid on banner, to the right of the portrait thumbnail
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 90.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val hasLogo = !item.logoUrl.isNullOrBlank()
                            if (hasLogo) {
                                AsyncImage(
                                    model = item.logoUrl,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .heightIn(min = 28.dp, max = 48.dp)
                                        .fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = item.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    letterSpacing = (-0.3).sp,
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }

                    // Scrollable metadata + synopsis + addons below banner
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Watch progress (if resuming)
                        if (watchHistory != null && watchHistory.duration > 0L) {
                            val percent = ((watchHistory.position.toFloat() / watchHistory.duration.toFloat()) * 100).toInt().coerceIn(0, 100)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Watched $percent%",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    watchHistory.season?.let { s ->
                                        watchHistory.episode?.let { e ->
                                            Text(
                                                text = "S${s}:E${e}",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = theme.TextMuted,
                                            )
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { watchHistory.position.toFloat() / watchHistory.duration.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.15f),
                                )
                            }
                        }

                        // Metadata chips strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val typeLabel = when (item.type.lowercase(java.util.Locale.US)) {
                                "movie" -> "Movie"
                                "series" -> "TV Series"
                                "anime" -> "Anime"
                                else -> item.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            ) {
                                Text(
                                    text = typeLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            item.releaseYear?.let { year ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.10f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
                                ) {
                                    Text(
                                        text = year,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.TextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            item.genres.take(3).forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = theme.TextMuted,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                        // Synopsis
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SYNOPSIS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = theme.TextMuted,
                            )
                            Text(
                                text = item.description?.takeIf { it.isNotBlank() }
                                    ?: "No synopsis provided for this title. Explore available extension sources to stream or view additional details.",
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = theme.TextPrimary.copy(alpha = 0.88f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                        // Active Streaming Addons Section
                        val stremioAddons by StremioAddonManager.addons.collectAsState()
                        val streamAddons = remember(stremioAddons) {
                            val streamSpecific = stremioAddons.filter { it.enabled && it.providesStreams }
                            if (streamSpecific.isNotEmpty()) streamSpecific else stremioAddons.filter { it.enabled }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = PremiumIcons.Extensions,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "STREAMING ADDONS",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = theme.TextMuted,
                                    )
                                }
                                if (streamAddons.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = "${streamAddons.size} Ready",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            if (streamAddons.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.03f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = theme.TextMuted,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "No streaming addons active. Install addons in Settings to stream torrent & debrid links.",
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            color = theme.TextMuted,
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    streamAddons.take(3).forEach { addon ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White.copy(alpha = 0.04f),
                                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    if (!addon.logoUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = addon.logoUrl,
                                                            contentDescription = addon.name,
                                                            contentScale = ContentScale.Fit,
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clip(RoundedCornerShape(4.dp)),
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Text(
                                                                text = addon.name.take(1).uppercase(),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = addon.name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = theme.TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (addon.version.isNotBlank()) {
                                                        Text(
                                                            text = "v${addon.version}",
                                                            fontSize = 10.sp,
                                                            color = theme.TextMuted.copy(alpha = 0.6f),
                                                        )
                                                    }
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF22C55E)),
                                                    )
                                                    Text(
                                                        text = "Active",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF22C55E),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (streamAddons.size > 3) {
                                        Text(
                                            text = "+ ${streamAddons.size - 3} more active streaming addons",
                                            fontSize = 10.5.sp,
                                            color = theme.TextMuted.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(start = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CTA button — fixed at bottom, padded
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (onOpenAddonMode != null) {
                            val buttonInteractionSource = remember { MutableInteractionSource() }
                            val isButtonHovered by buttonInteractionSource.collectIsHoveredAsState()

                            val cardBg = if (isButtonHovered) {
                                if (theme.isAmoled) Color(0xFF1C1F28) else Color(0xFF242733)
                            } else {
                                if (theme.isAmoled) Color(0xFF12141A) else Color(0xFF181A22)
                            }

                            val cardBorder = if (isButtonHovered) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            } else {
                                Color.White.copy(alpha = 0.14f)
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, cardBorder),
                                shadowElevation = if (isButtonHovered) 6.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .hoverable(buttonInteractionSource)
                                    .clickable(
                                        interactionSource = buttonInteractionSource,
                                        indication = null,
                                        onClick = { onOpenAddonMode(item) },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isButtonHovered) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = addonButtonIcon,
                                                contentDescription = null,
                                                tint = if (isButtonHovered) Color.Black else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                        Text(
                                            text = addonButtonText,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            letterSpacing = (-0.2).sp,
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isButtonHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                                        border = BorderStroke(0.5.dp, if (isButtonHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.12f)),
                                        modifier = Modifier.size(26.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = if (isButtonHovered) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.70f),
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── RIGHT PANE: DEDICATED EXTENSION STREAM HUB (58% width) ──
            Surface(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                color = if (theme.isAmoled) Color(0xFF0A0C10) else theme.SurfaceElevated,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Top Header Row with Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "AVAILABLE EXTENSION SOURCES",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = theme.TextPrimary,
                                )
                                Text(
                                    text = "Select an extension below to browse episodes and stream options",
                                    fontSize = 11.5.sp,
                                    color = theme.TextMuted,
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    // Quality Filter Pills Bar
                    var selectedQualityFilter by remember { mutableStateOf("All") }
                    val count4K = remember(matches) { matches.count { it.qualityText?.contains("4k", ignoreCase = true) == true } }
                    val count1080p = remember(matches) { matches.count { it.qualityText?.contains("1080", ignoreCase = true) == true } }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterPill(
                            label = "All (${matches.size})",
                            selected = selectedQualityFilter == "All",
                            onClick = { selectedQualityFilter = "All" },
                        )
                        if (count4K > 0) {
                            FilterPill(
                                label = "4K UHD ($count4K)",
                                selected = selectedQualityFilter == "4K",
                                onClick = { selectedQualityFilter = "4K" },
                            )
                        }
                        if (count1080p > 0) {
                            FilterPill(
                                label = "1080p ($count1080p)",
                                selected = selectedQualityFilter == "1080p",
                                onClick = { selectedQualityFilter = "1080p" },
                            )
                        }

                        if (isSearching) {
                            Spacer(modifier = Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Searching...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    val filteredMatches = remember(matches, selectedQualityFilter) {
                        when (selectedQualityFilter) {
                            "4K" -> matches.filter { it.qualityText?.contains("4k", ignoreCase = true) == true }
                            "1080p" -> matches.filter { it.qualityText?.contains("1080", ignoreCase = true) == true }
                            else -> matches
                        }
                    }

                    // Sources List Area
                    if (isSearching && matches.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.02f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp,
                                )
                                Text(
                                    text = "Searching extension providers...",
                                    fontSize = 13.sp,
                                    color = theme.TextMuted,
                                )
                            }
                        }
                    } else if (!isSearching && matches.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.02f))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = "No Extension Matches Found",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.TextPrimary,
                                )
                                Text(
                                    text = "None of your installed extensions returned results for this title. You can stream directly via Addon Mode, or install additional extension repositories.",
                                    fontSize = 12.sp,
                                    color = theme.TextMuted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.fillMaxWidth(0.85f),
                                )
                                if (onOpenAddonMode != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { onOpenAddonMode(item) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.Black,
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Play with Addon Instead", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(filteredMatches) { match ->
                                    ProviderStreamCard(
                                        match = match,
                                        onClick = { handleSelect(match) },
                                    )
                                }
                            }

                            // Sleek Dual Streaming Engines Guide (fills space when few providers match)
                            if (filteredMatches.size <= 3) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.03f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp).padding(top = 1.dp),
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "PLAYBACK ENGINE OVERVIEW",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp,
                                                color = theme.TextPrimary,
                                            )
                                            Text(
                                                text = "• Addon Mode (left): Fast torrent & debrid playback via configured Stremio addons.\n• Extension Sources (above): Direct scrapers from installed CloudStream plugins.",
                                                fontSize = 11.5.sp,
                                                lineHeight = 17.sp,
                                                color = theme.TextMuted,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom info footnote
                    Text(
                        text = "Discovered ${matches.size} total sources across your installed extension repositories.",
                        fontSize = 11.sp,
                        color = theme.TextMuted.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(0.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) Color.Black else theme.TextPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ProviderStreamCard(
    match: ProviderMatch,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardBg = if (isHovered) {
        if (theme.isAmoled) Color(0xFF161820) else theme.SurfaceElevated
    } else {
        if (theme.isAmoled) Color(0xFF0F1116) else theme.SurfaceCard
    }

    val cardBorder = if (isHovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Provider Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                ) {
                    Text(
                        text = match.providerName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }

                // Title and Badges
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = match.displayTitle,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (match.hasSub || match.hasDub) {
                        DesktopBadgeComponents.SubDubBadge(hasSub = match.hasSub, hasDub = match.hasDub)
                    }
                    if (!match.qualityText.isNullOrBlank()) {
                        DesktopBadgeComponents.QualityBadge(quality = match.qualityText)
                    }
                }
            }

            // Icon-only arrow circle — compact, keeps title readable
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (isHovered) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.07f)
                    )
                    .border(0.5.dp, if (isHovered) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Details",
                    tint = if (isHovered) Color.Black else theme.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

