package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.utils.ExtractorLink

@Composable
fun ExploreProviderDialog(
    item: ExploreItem?,
    matches: List<ProviderMatch>,
    stremioStreams: List<ExtractorLink> = emptyList(),
    isSearching: Boolean,
    onDismissRequest: () -> Unit,
    onOpenDetails: (providerName: String, url: String, title: String) -> Unit,
    onPlayDirectStream: ((ExtractorLink, ExploreItem) -> Unit)? = null,
) {
    if (item == null) return

    val theme = LocalDesktopTheme.current

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.74f).fillMaxHeight(0.82f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFA0E1524))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            // Header Row: Title, Stream Counter Badge & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Extensions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Available Extensions & Streams",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                    )

                    val totalCount = matches.size + stremioStreams.size

                    // Results status pill
                    if (isSearching) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    )
                                Text(
                                    text = if (totalCount > 0) "$totalCount Found (Searching...)" else "Searching extensions & addons...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = "$totalCount Streams Found",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = theme.TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Body: Split 2-Column Desktop Layout
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Left Column: Media Info (32% width)
                Column(
                    modifier = Modifier
                        .weight(0.32f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    ) {
                        AsyncImage(
                            model = item.posterUrl ?: item.backgroundUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Top left rating badge
                        item.rating?.let { rating ->
                            if (rating > 0.0) {
                                Box(modifier = Modifier.padding(8.dp)) {
                                    DesktopBadgeComponents.RatingGoldBadge(rating = rating)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = item.name,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                        lineHeight = 20.sp,
                    )

                    item.releaseYear?.let { year ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = year,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.TextMuted,
                        )
                    }

                    if (item.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item.genres.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    item.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = theme.TextMuted,
                            lineHeight = 17.sp,
                        )
                    }
                }

                // Right Column: Matching Providers List (68% width)
                Column(
                    modifier = Modifier
                        .weight(0.68f)
                        .fillMaxHeight(),
                ) {
                    if (isSearching && matches.isEmpty() && stremioStreams.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(38.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                )
                                Text(
                                    text = "Searching extensions & addons for matching streams...",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.TextMuted,
                                )
                            }
                        }
                    } else if (!isSearching && matches.isEmpty() && stremioStreams.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = theme.TextMuted,
                                    modifier = Modifier.size(42.dp),
                                )
                                Text(
                                    text = "No matching streams found for this title",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.TextPrimary,
                                )
                                Text(
                                    text = "Try installing additional provider extensions or Stremio stream addons in Settings.",
                                    fontSize = 12.5.sp,
                                    color = theme.TextMuted,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Section 1: Direct Stremio Streams (if any)
                            if (stremioStreams.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "DIRECT STREMIO STREAMS (${stremioStreams.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(bottom = 2.dp, top = 2.dp),
                                    )
                                }

                                items(stremioStreams) { stream ->
                                    StremioStreamMatchRow(
                                        stream = stream,
                                        onPlay = { onPlayDirectStream?.invoke(stream, item) },
                                    )
                                }

                                if (matches.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "MATCHING EXTENSIONS (${matches.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.TextMuted,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(bottom = 2.dp),
                                        )
                                    }
                                }
                            }

                            // Section 2: Matching Extensions
                            items(matches) { match ->
                                ProviderMatchRow(
                                    match = match,
                                    onClick = {
                                        onOpenDetails(
                                            match.providerName,
                                            match.searchResponse.url,
                                            match.displayTitle,
                                        )
                                    },
                                )
                            }

                            if (isSearching) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Searching remaining extensions...",
                                            fontSize = 11.5.sp,
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
    }
}

@Composable
private fun ProviderMatchRow(
    match: ProviderMatch,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                0.5.dp,
                if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        color = if (isHovered) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Provider Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = match.providerName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Title
                Text(
                    text = match.displayTitle,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                // Format Badges
                if (match.hasSub || match.hasDub) {
                    DesktopBadgeComponents.SubDubBadge(hasSub = match.hasSub, hasDub = match.hasDub)
                }
                if (!match.qualityText.isNullOrBlank()) {
                    DesktopBadgeComponents.QualityBadge(quality = match.qualityText)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Open Details Action Button
            Button(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "Open Details",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StremioStreamMatchRow(
    stream: ExtractorLink,
    onPlay: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                0.5.dp,
                if (isHovered) Color(0xFF10B981).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .hoverable(interactionSource)
            .clickable(onClick = onPlay),
        color = if (isHovered) Color(0xFF10B981).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Stremio Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.20f))
                        .border(0.5.dp, Color(0xFF10B981).copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stream.source,
                        color = Color(0xFF10B981),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Stream Label
                Text(
                    text = stream.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                // Quality Badge
                val qualText = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(stream.quality)
                if (qualText.isNotBlank()) {
                    DesktopBadgeComponents.QualityBadge(quality = qualText)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Play Now Button
            Button(
                onClick = onPlay,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "Play Stream",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
