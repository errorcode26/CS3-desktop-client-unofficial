package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCollectionDetail
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory

@Composable
internal fun CollectionDetailView(
    collection: ExploreCollectionDetail,
    watchHistoryMap: Map<String, WatchHistory>,
    autoCleanTitles: Boolean,
    onBack: () -> Unit,
    onItemClick: (ExploreItem) -> Unit,
    onOpenDetails: (ExploreItem) -> Unit,
    dockPosition: DockPosition,
) {
    val theme = LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()
    val paddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
    val paddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp

    val firstYear = collection.parts.firstOrNull()?.releaseYear
    val lastYear = collection.parts.lastOrNull()?.releaseYear
    val timelineSpan = if (firstYear != null && lastYear != null && firstYear != lastYear) "$firstYear-$lastYear" else firstYear.orEmpty()

    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Ambient full-window blurred glow matching DetailsScreen
        if (heroBackgroundBlurEnabled && !collection.backdropUrl.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val context = LocalPlatformContext.current
                val ambientRequest = remember(collection.backdropUrl) {
                    ImageRequest.Builder(context)
                        .data(collection.backdropUrl)
                        .size(640, 360)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = ambientRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(heroBackdropBlurRadius.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = heroBackdropDarkening)),
                )
            }
        }

        // Layer 2: Hero Crisp Backdrop with seamless vignette matching DetailsHeader
        if (!collection.backdropUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(580.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val verticalFade = Brush.verticalGradient(
                            0.00f to Color.Black,
                            0.35f to Color.Black,
                            0.60f to Color.Black.copy(alpha = 0.85f),
                            0.80f to Color.Black.copy(alpha = 0.40f),
                            0.94f to Color.Black.copy(alpha = 0.08f),
                            1.00f to Color.Transparent,
                        )
                        val scrimBase = Color.Black
                        val logoVignette = Brush.horizontalGradient(
                            0.00f to scrimBase.copy(alpha = 0.85f),
                            0.08f to scrimBase.copy(alpha = 0.80f),
                            0.18f to scrimBase.copy(alpha = 0.70f),
                            0.30f to scrimBase.copy(alpha = 0.55f),
                            0.42f to scrimBase.copy(alpha = 0.35f),
                            0.54f to scrimBase.copy(alpha = 0.18f),
                            0.64f to scrimBase.copy(alpha = 0.06f),
                            0.72f to Color.Transparent,
                            1.00f to Color.Transparent,
                        )
                        val bottomScrim = Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.35f to Color.Transparent,
                            0.65f to Color.Black.copy(alpha = 0.50f),
                            0.85f to Color.Black.copy(alpha = 0.88f),
                            1.00f to Color.Black,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(bottomScrim)
                            drawRect(logoVignette)
                            drawRect(verticalFade, blendMode = BlendMode.DstIn)
                        }
                    },
            ) {
                val context = LocalPlatformContext.current
                val crispRequest = remember(collection.backdropUrl) {
                    ImageRequest.Builder(context)
                        .data(collection.backdropUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = crispRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = posterWidthDp.dp.coerceAtLeast(145.dp)),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(homeSpacingDp.dp.coerceAtLeast(14.dp)),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = paddingStart,
                end = paddingEnd,
                top = 118.dp,
                bottom = 48.dp,
            ),
        ) {
            // Top Collection Hero Header spanning full grid width
            item(span = { GridItemSpan(maxLineSpan) }, key = "collection_header_hero") {
                CollectionHeader(
                    collection = collection,
                    timelineSpan = timelineSpan,
                )
            }

            // Installment Movie Posters Grid
            items(
                count = collection.parts.size,
                key = { i -> "coll_poster_${collection.parts[i].id}" },
            ) { index ->
                val item = collection.parts[index]
                val watchHistory = watchHistoryMap[item.id]
                    ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                ExplorePosterCard(
                    item = item,
                    watchHistory = watchHistory,
                    autoCleanTitles = autoCleanTitles,
                    onClick = { onItemClick(item) },
                )
            }
        }

        // Floating Top Back Button (pinned, matching DetailsScreen & PersonScreen)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = paddingStart, top = 52.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F0F12).copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .zIndex(10f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun CollectionHeader(
    collection: ExploreCollectionDetail,
    timelineSpan: String,
) {
    val theme = LocalDesktopTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Collection Poster on the Left
        val posterUrl = collection.posterUrl ?: collection.backdropUrl
        if (!posterUrl.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(2f / 3f)
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = theme.SurfaceCard,
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = collection.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Collection Info on the Right
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "COLLECTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.TextMuted,
                letterSpacing = 1.5.sp,
            )

            Text(
                text = collection.name,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 42.sp,
            )

            val metaLine = listOfNotNull(
                "${collection.parts.size} films",
                timelineSpan.takeIf { it.isNotBlank() },
            ).joinToString("  •  ")

            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.TextMuted,
                )
            }

            if (collection.overview.isNotBlank()) {
                Text(
                    text = collection.overview,
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
