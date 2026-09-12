package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
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
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
@Composable
fun ExploreProviderDialog(
    item: ExploreItem?,
    matches: List<ProviderMatch>,
    isSearching: Boolean,
    onDismissRequest: () -> Unit,
    onSelectMatch: ((ProviderMatch) -> Unit)? = null,
    onOpenDetails: ((providerName: String, url: String, title: String) -> Unit)? = null,
) {
    if (item == null) return

    val theme = LocalDesktopTheme.current
    val dialogBg = if (theme.isAmoled) Color.Black else theme.Background
    val panelBg = if (theme.isAmoled) Color(0xFF0C0C0C) else theme.SurfaceCard
    val panelBorder = if (theme.isAmoled) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.10f)

    val handleSelect: (ProviderMatch) -> Unit = { match ->
        onSelectMatch?.invoke(match) ?: onOpenDetails?.invoke(match.providerName, match.searchResponse.url, match.displayTitle)
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.86f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(dialogBg)
                .padding(18.dp),
        ) {
            // ── TOP HEADER BAR: Dialog Title + Live Status Pill + Close Button ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
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
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Available Extensions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                    )

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
                                    text = if (matches.isNotEmpty()) "${matches.size} Found (Searching...)" else "Searching extensions...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = if (matches.size == 1) "1 Provider Found" else "${matches.size} Providers Found",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // ── MAIN BODY: LEFT (Theatrical Media Overview 50%) & RIGHT (Sources Rail 50%) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── LEFT PANE: Theatrical Media Overview (Modifier.weight(1f)) ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(panelBg)
                        .border(0.5.dp, panelBorder, RoundedCornerShape(14.dp)),
                ) {
                    // Ambient backdrop banner across the top
                    val backdropModel = item.backgroundUrl ?: item.posterUrl
                    if (!backdropModel.isNullOrBlank()) {
                        AsyncImage(
                            model = backdropModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to panelBg.copy(alpha = 0.30f),
                                        0.65f to panelBg.copy(alpha = 0.85f),
                                        1.0f to panelBg,
                                    )
                                )
                        )
                    }

                    // Foreground content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // Upper Section: Poster (Left) + Identity / Badges / CTA Button (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Well-Proportioned Poster (150dp x 225dp)
                            Box(
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(225.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
                            ) {
                                AsyncImage(
                                    model = item.posterUrl ?: item.backgroundUrl,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                item.rating?.let { rating ->
                                    if (rating > 0.0) {
                                        Box(modifier = Modifier.padding(6.dp)) {
                                            DesktopBadgeComponents.RatingGoldBadge(rating = rating)
                                        }
                                    }
                                }
                            }

                            // Identity Column: Logo / Title + Badges + Genres + Primary CTA
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Transparent Clear Logo (up to 72dp) or Prominent Bold Title
                                val hasLogo = !item.logoUrl.isNullOrBlank()
                                if (hasLogo) {
                                    AsyncImage(
                                        model = item.logoUrl,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart,
                                        modifier = Modifier
                                            .heightIn(min = 40.dp, max = 72.dp)
                                            .fillMaxWidth(0.95f),
                                    )
                                } else {
                                    Text(
                                        text = item.name,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                // Year & Type Badges Row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    item.releaseYear?.let { year ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.White.copy(alpha = 0.10f),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
                                        ) {
                                            Text(
                                                text = year,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.TextPrimary,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            )
                                        }
                                    }

                                    val typeLabel = when (item.type.lowercase(java.util.Locale.US)) {
                                        "movie" -> "Movie"
                                        "series" -> "TV Series"
                                        "anime" -> "Anime"
                                        else -> item.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                                    ) {
                                        Text(
                                            text = typeLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        )
                                    }
                                }

                                // Genre Chips
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    item.genres.take(5).forEach { genre ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.White.copy(alpha = 0.06f),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                                        ) {
                                            Text(
                                                text = genre,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = theme.TextMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                }

                                // Primary One-Click CTA Action Button
                                if (matches.isNotEmpty()) {
                                    val firstMatch = matches.first()
                                    Button(
                                        onClick = { handleSelect(firstMatch) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.Black,
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = "Open on ${firstMatch.providerName}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Overview / Synopsis Section (Left-Aligned, Highly Legible)
                        item.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "OVERVIEW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = theme.TextMuted,
                                )
                                Text(
                                    text = desc,
                                    fontSize = 13.5.sp,
                                    color = theme.TextPrimary.copy(alpha = 0.90f),
                                    lineHeight = 20.sp,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                    }
                }

                // ── RIGHT PANE: Sources Rail (50% Equal Split) ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(panelBg)
                        .border(0.5.dp, panelBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Rail Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "AVAILABLE SOURCES",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = theme.TextPrimary,
                            )

                            if (isSearching) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "Searching...",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        // Empty states or Provider List
                        if (isSearching && matches.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.03f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(30.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.5.dp,
                                    )
                                    Text(
                                        text = "Searching extension providers...",
                                        fontSize = 12.sp,
                                        color = theme.TextMuted,
                                    )
                                }
                            }
                        } else if (!isSearching && matches.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = theme.TextMuted,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Text(
                                        text = "No extension providers found",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.TextPrimary,
                                    )
                                    Text(
                                        text = "Install or enable more providers in Settings to stream this title.",
                                        fontSize = 11.5.sp,
                                        color = theme.TextMuted,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                contentPadding = PaddingValues(bottom = 8.dp),
                            ) {
                                items(matches) { match ->
                                    ProviderMatchRow(
                                        match = match,
                                        onClick = { handleSelect(match) },
                                    )
                                }

                                if (!isSearching && matches.size in 1..5) {
                                    item {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White.copy(alpha = 0.03f),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = null,
                                                    tint = theme.TextMuted,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                                Text(
                                                    text = "All matching extensions listed. Add more providers in Settings.",
                                                    fontSize = 10.5.sp,
                                                    color = theme.TextMuted,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isSearching) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Searching remaining providers...",
                                                fontSize = 11.sp,
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
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Provider Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = match.providerName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Title
                Text(
                    text = match.displayTitle,
                    fontSize = 12.5.sp,
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

            Spacer(modifier = Modifier.width(8.dp))

            // Open Action Button
            Button(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(7.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Open Details",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

