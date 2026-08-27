package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.DesktopDimens
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun DetailsBackdrop(
    provider: MainAPI,
    data: LoadResponse,
    scrollState: LazyListState,
    hazeState: HazeState,
    enrichmentPhase: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase,
    modifier: Modifier = Modifier,
    dynamicColorEnabled: Boolean = false,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (scrollState.firstVisibleItemIndex == 0) {
                    val scrollOffset = scrollState.firstVisibleItemScrollOffset.toFloat()
                    translationY = -scrollOffset * 0.5f
                    alpha = 1f - (scrollOffset / (size.height * 0.8f)).coerceIn(0f, 1f)
                } else {
                    alpha = 0f
                }
            }
            .haze(state = hazeState),
    ) {
        val currentPhase = enrichmentPhase

        val screensaverEnabled by AppearanceConfig.screensaverEnabled.collectAsState()
        val screenshots = uiState?.screenshots ?: emptyList()
        var currentScreenshotIndex by remember { mutableStateOf(-1) }

        val isFallback = remember(data, currentScreenshotIndex, screenshots, uiState) {
            if (currentScreenshotIndex >= 0 && screenshots.isNotEmpty()) return@remember false
            if (!uiState?.enrichedBackdropUrl.isNullOrBlank()) return@remember false
            data.backgroundPosterUrl.isNullOrBlank() || data.backgroundPosterUrl == data.posterUrl
        }

        LaunchedEffect(screensaverEnabled, screenshots) {
            if (screensaverEnabled && screenshots.isNotEmpty()) {
                currentScreenshotIndex = 0
                while (true) {
                    kotlinx.coroutines.delay(10_000)
                    currentScreenshotIndex = (currentScreenshotIndex + 1) % screenshots.size
                }
            } else {
                currentScreenshotIndex = -1
            }
        }

        val baseBgUrl = remember(data, currentPhase, uiState) {
            // Always prefer enriched TMDB backdrop
            uiState?.enrichedBackdropUrl?.takeIf { it.isNotBlank() }
                // Only fall back to the provider's (potentially CF-protected) URL AFTER enrichment
                // finishes. If we fall back mid-enrichment we trigger a CF storm for an image
                // we'll crossfade away in 2 seconds anyway.
                ?: if (uiState?.isEnriching == false) {
                    provider.fixUrlNull(data.backgroundPosterUrl)?.takeIf { it.isNotBlank() }
                        ?: provider.fixUrlNull(data.posterUrl)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
        }

        val bgUrl = if (currentScreenshotIndex >= 0 && screenshots.isNotEmpty()) {
            screenshots[currentScreenshotIndex]
        } else {
            baseBgUrl
        }

        if (bgUrl != null) {
            androidx.compose.animation.Crossfade(
                targetState = bgUrl,
                animationSpec = androidx.compose.animation.core.tween(2000),
                label = "backdrop_crossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .run { if (isFallback) this.blur(80.dp) else this }
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val verticalFade = Brush.verticalGradient(
                            0.0f to Color.Black,
                            0.65f to Color.Black,
                            1.0f to Color.Transparent,
                        )
                        val scrimBase = Color.Black
                        // Smooth horizontal sweep from left — many stops so the edge is completely invisible
                        val logoVignette = Brush.horizontalGradient(
                            0.00f to scrimBase.copy(alpha = 0.82f),
                            0.08f to scrimBase.copy(alpha = 0.78f),
                            0.18f to scrimBase.copy(alpha = 0.68f),
                            0.30f to scrimBase.copy(alpha = 0.52f),
                            0.42f to scrimBase.copy(alpha = 0.32f),
                            0.54f to scrimBase.copy(alpha = 0.16f),
                            0.64f to scrimBase.copy(alpha = 0.06f),
                            0.72f to Color.Transparent,
                            1.00f to Color.Transparent,
                        )
                        val bottomScrim = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.40f to Color.Transparent,
                            0.75f to scrimBase.copy(alpha = 0.25f),
                            1.0f to scrimBase.copy(alpha = 0.35f),
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(bottomScrim)
                            drawRect(logoVignette)
                            drawRect(verticalFade, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                        }
                    },
            ) { targetBgUrl ->
                val enhancedBgUrl = remember(targetBgUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhanceBackdropUrl(targetBgUrl) }
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(enhancedBgUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                )
            }
        }
    }
}

@Composable
fun InfoPanelRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AdaptiveMetadataLayout(
    isNarrow: Boolean,
    modifier: Modifier = Modifier,
    mainContent: @Composable () -> Unit,
    sideContent: @Composable () -> Unit,
) {
    if (isNarrow) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            mainContent()
            sideContent()
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                mainContent()
            }
            Spacer(modifier = Modifier.width(64.dp))
            Box {
                sideContent()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsMetadata(
    provider: MainAPI,
    data: LoadResponse,
    hazeState: HazeState,
    heroAction: @Composable (Modifier) -> Unit = {},
    downloadAction: (@Composable (Modifier) -> Unit)? = null,
    enrichmentPhase: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase,
    isLoading: Boolean = false,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState? = null,
    screenshots: List<String>? = null,
    onPhotosClick: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onActorClick: (com.lagradost.cloudstream3.ActorData) -> Unit = {},
    onTrailerClick: ((String) -> Unit)? = null,
) {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    var isRightColumnHovered by remember { mutableStateOf(false) }
    var isRightColumnPinned by remember { mutableStateOf(com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>("DETAILS_RIGHT_COLUMN_PINNED") ?: false) }
    val rightColumnAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isRightColumnPinned || isRightColumnHovered) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
    )
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (maxWidth < 600.dp) {
            DetailsMetadataCompact(
                provider = provider,
                data = data,
                uiState = uiState,
                isLoading = isLoading,
                enrichmentPhase = enrichmentPhase,
                heroAction = heroAction,
                downloadAction = downloadAction,
                onPhotosClick = onPhotosClick,
                onCastClick = onCastClick,
                onActorClick = onActorClick,
                onTrailerClick = onTrailerClick,
            )
        } else {
            // Prevent crash on unbounded height (Dp.Infinity) when inside LazyColumn
            val actualMaxHeight = if (maxHeight == androidx.compose.ui.unit.Dp.Infinity) 800.dp else maxHeight

            val isNarrow = maxWidth < 1100.dp
            val isButtonsNarrow = false
            val isCompactHeight = actualMaxHeight < 550.dp
            val isMediumHeight = actualMaxHeight < 750.dp

            // The logo and text should scale together and have similar sensible maximums
            // so the logo never dwarfs the text on massive monitors.
            val responsiveLogoMaxWidth = minOf(600.dp, maxWidth * if (isNarrow) 0.7f else 0.4f)
            val responsivePlotMaxWidth = minOf(600.dp, maxWidth * if (isNarrow) 0.85f else 0.45f)

            val responsiveLogoMaxHeight = when {
                isCompactHeight -> 100.dp
                isMediumHeight -> 140.dp
                else -> minOf(180.dp, actualMaxHeight * 0.25f)
            }
            val responsiveTopPadding = when {
                isCompactHeight -> 16.dp
                isMediumHeight -> if (isNarrow) 24.dp else 40.dp
                else -> if (isNarrow) 32.dp else 56.dp
            }

            val responsiveBottomPadding = 148.dp

            AdaptiveMetadataLayout(
                isNarrow = isNarrow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (isNarrow) 24.dp else 64.dp, end = if (isNarrow) 24.dp else 64.dp, bottom = responsiveBottomPadding, top = responsiveTopPadding),
            mainContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isNarrow) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    if (isLoading) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = if (isNarrow) Alignment.CenterHorizontally else Alignment.Start,
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(0.45f).height(48.dp).clip(RoundedCornerShape(8.dp)).shimmerBackground())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.width(56.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                                Box(modifier = Modifier.width(48.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                                Box(modifier = Modifier.width(64.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                            Box(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                        }
                    } else {
                        val currentPhase = enrichmentPhase
                        val activeLogoUrl = remember(data, currentPhase, uiState) {
                            uiState?.enrichedLogoUrl?.takeIf { it.isNotBlank() }
                                ?: data.logoUrl?.takeIf { it.isNotBlank() }
                                ?: provider.fixUrlNull(data.logoUrl)
                        }
                        val displayName = data.name.takeIf { it.isNotBlank() } ?: uiState?.preloadedName ?: ""

                        if (!activeLogoUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .widthIn(
                                        min = DesktopDimens.HeroLogoMinWidth,
                                        max = responsiveLogoMaxWidth,
                                    )
                                    .heightIn(max = responsiveLogoMaxHeight),
                                contentAlignment = if (isNarrow) Alignment.Center else Alignment.BottomStart,
                            ) {
                                val logoRequest = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                    .data(activeLogoUrl)
                                    .size(1600, 800)
                                    .crossfade(true)
                                    .build()
                                AsyncImage(
                                    model = logoRequest,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(
                                            x = DesktopDimens.LogoShadowOffsetX,
                                            y = DesktopDimens.LogoShadowOffsetY,
                                        )
                                        .blur(
                                            DesktopDimens.LogoShadowBlur,
                                            edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded,
                                        ),
                                    contentScale = ContentScale.Fit,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                    alignment = if (isNarrow) Alignment.Center else Alignment.BottomStart,
                                    colorFilter = DesktopDimens.LogoShadowFilter,
                                )
                                coil3.compose.SubcomposeAsyncImage(
                                    model = logoRequest,
                                    contentDescription = displayName,
                                    contentScale = ContentScale.Fit,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                    modifier = Modifier.fillMaxSize(),
                                    alignment = if (isNarrow) Alignment.Center else Alignment.BottomStart,
                                    error = {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontSize = when {
                                                    displayName.length > 40 -> 28.sp
                                                    displayName.length > 24 -> 34.sp
                                                    displayName.length > 14 -> 40.sp
                                                    else -> 46.sp
                                                },
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = (-0.5).sp,
                                                lineHeight = when {
                                                    displayName.length > 40 -> 34.sp
                                                    displayName.length > 24 -> 40.sp
                                                    displayName.length > 14 -> 46.sp
                                                    else -> 52.sp
                                                },
                                                shadow = androidx.compose.ui.graphics.Shadow(
                                                    color = Color.Black.copy(alpha = 0.85f),
                                                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                                    blurRadius = 16f,
                                                ),
                                            ),
                                            color = Color.White,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = if (isNarrow) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                                            modifier = Modifier
                                                .widthIn(max = responsivePlotMaxWidth)
                                                .padding(bottom = 2.dp),
                                        )
                                    },
                                )
                            }
                        } else {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = when {
                                        displayName.length > 40 -> 28.sp
                                        displayName.length > 24 -> 34.sp
                                        displayName.length > 14 -> 40.sp
                                        else -> 46.sp
                                    },
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp,
                                    lineHeight = when {
                                        displayName.length > 40 -> 34.sp
                                        displayName.length > 24 -> 40.sp
                                        displayName.length > 14 -> 46.sp
                                        else -> 52.sp
                                    },
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                        blurRadius = 16f,
                                    ),
                                ),
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = if (isNarrow) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                                modifier = Modifier
                                    .widthIn(max = responsivePlotMaxWidth)
                                    .padding(bottom = 2.dp),
                            )
                        }
                    }
                    val activeTagline = uiState?.enrichedTagline?.takeIf { it.isNotBlank() }
                    if (activeTagline != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\"$activeTagline\"",
                            style = MaterialTheme.typography.titleMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = responsivePlotMaxWidth)
                                .padding(start = if (isNarrow) 0.dp else 4.dp),
                            textAlign = if (isNarrow) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isLoading) {
                        // Row 1: Primary Meta Information & Status Badges
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isNarrow) Arrangement.Center else Arrangement.spacedBy(10.dp),
                            modifier = if (isNarrow) Modifier.fillMaxWidth() else Modifier,
                        ) {
                            val metaItems = mutableListOf<String>()

                            // 1. Year / Multi-Year Span
                            val enrichedDate = uiState?.enrichedReleaseDate
                            val yearSpan = if (data.type != TvType.Movie && data.type != TvType.AnimeMovie && !enrichedDate.isNullOrBlank() && enrichedDate.contains("–")) {
                                enrichedDate
                            } else {
                                (uiState?.enrichedYear ?: data.year)?.toString()
                            }
                            yearSpan?.let { metaItems.add(it) }

                            // 2. Total Seasons & Episodes for TV / Anime
                            val seasonsCount = uiState?.enrichedSeasonsCount
                            val episodesCount = uiState?.enrichedEpisodesCount
                            if (seasonsCount != null && seasonsCount > 0) {
                                val epStr = if (episodesCount != null && episodesCount > 0) " (${episodesCount} Eps)" else ""
                                metaItems.add("$seasonsCount ${if (seasonsCount == 1) "Season" else "Seasons"}$epStr")
                            } else if (episodesCount != null && episodesCount > 0) {
                                metaItems.add("$episodesCount Episodes")
                            }

                            // 3. Runtime Duration
                            val finalDuration = uiState?.enrichedDuration ?: data.duration
                            finalDuration?.takeIf { it > 0 }?.let { dur ->
                                val mins = if (dur > 360) dur / 60 else dur
                                val durationStr = if (mins >= 60) {
                                    val h = mins / 60
                                    val m = mins % 60
                                    if (m > 0) "${h}h ${m}m" else "${h}h"
                                } else {
                                    "${mins}m"
                                }
                                metaItems.add(durationStr)
                            }

                            // 4. Content Type
                            val typeStr = when (data.type) {
                                TvType.TvSeries -> "TV Series"
                                TvType.Anime -> "Anime"
                                TvType.Movie -> "Movie"
                                TvType.AnimeMovie -> "Anime Movie"
                                TvType.OVA -> "OVA"
                                TvType.Live -> "Live"
                                TvType.Documentary -> "Documentary"
                                TvType.Cartoon -> "Cartoon"
                                TvType.AsianDrama -> "Asian Drama"
                                else -> data.type.name
                            }
                            if (typeStr.isNotBlank()) {
                                metaItems.add(typeStr)
                            }

                            if (metaItems.isNotEmpty()) {
                                Text(
                                    text = metaItems.joinToString("  •  "),
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            data.contentRating?.takeIf { it.isNotBlank() }?.let { rating ->
                                com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents.ContentRatingBadge(
                                    rating = rating,
                                    isLarge = true,
                                )
                            }

                            val rawStatus = uiState?.enrichedStatus
                                ?: (data as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.showStatus?.name
                                ?: (data as? com.lagradost.cloudstream3.AnimeLoadResponse)?.showStatus?.name

                            if (!rawStatus.isNullOrBlank()) {
                                val cleanStatus = when (rawStatus.trim().lowercase()) {
                                    "returning series", "ongoing" -> "Ongoing"
                                    "ended", "completed" -> "Ended"
                                    "canceled", "cancelled" -> "Canceled"
                                    "in production" -> "In Production"
                                    else -> rawStatus
                                }
                                Surface(
                                    shape = RoundedCornerShape(5.5.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                ) {
                                    Text(
                                        text = cleanStatus,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.3.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                    )
                                }
                            }
                        }

                        // Row 2: Branded Vector Rating Badges (IMDb, TMDB, AniList)
                        val imdbScore = uiState?.enrichedImdbRating
                        val tmdbScore = uiState?.enrichedTmdbRating ?: if (imdbScore == null) data.score?.toFloat(10)?.toDouble() else null
                        val anilistScore = uiState?.enrichedAniListRating
                        val isAnime = data.type == TvType.Anime || data.type == TvType.AnimeMovie || data.type == TvType.OVA

                        if (imdbScore != null || tmdbScore != null || anilistScore != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (isNarrow) Arrangement.Center else Arrangement.spacedBy(10.dp),
                                modifier = if (isNarrow) Modifier.fillMaxWidth() else Modifier,
                            ) {
                                // 1. IMDb Vector Rating Pill
                                if (imdbScore != null && imdbScore > 0.0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF5C518).copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF5C518).copy(alpha = 0.45f)),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = Color(0xFFF5C518),
                                            ) {
                                                Text(
                                                    text = "IMDb",
                                                    color = Color.Black,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = String.format(java.util.Locale.US, "%.1f", imdbScore),
                                                color = Color(0xFFF5C518),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }

                                // 2. TMDB Vector Rating Pill
                                if (tmdbScore != null && tmdbScore > 0.0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF01B4E4).copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF01B4E4).copy(alpha = 0.4f)),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = Color(0xFF01B4E4),
                                            ) {
                                                Text(
                                                    text = "TMDB",
                                                    color = Color.Black,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = String.format(java.util.Locale.US, "%.1f", tmdbScore),
                                                color = Color(0xFF01B4E4),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }

                                // 3. AniList Vector Rating Pill (for Anime)
                                if (anilistScore != null && anilistScore > 0.0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF02A9FF).copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF02A9FF).copy(alpha = 0.4f)),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = Color(0xFF02A9FF),
                                            ) {
                                                Text(
                                                    text = "AniList",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${(anilistScore * 10).toInt()}%",
                                                color = Color(0xFF02A9FF),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Row 3: Frosted Genre Chips
                        val finalTags = uiState?.enrichedTags ?: data.tags
                        if (!finalTags.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = if (isNarrow) Arrangement.Center else Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = if (isNarrow) Modifier.fillMaxWidth() else Modifier,
                            ) {
                                finalTags.take(6).forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    ) {
                                        Text(
                                            text = tag,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (!isLoading && !data.plot.isNullOrBlank()) {
                        Text(
                            text = data.plot ?: "",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = responsivePlotMaxWidth),
                            textAlign = if (isNarrow) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val bookmarkId = "${provider.name}_${data.url.hashCode()}"
                    val allBookmarks = uiState?.bookmarks ?: emptyMap()
                    val currentBookmark = allBookmarks[bookmarkId]
                    var isEditingStatus by remember { mutableStateOf(false) }

                    val activeWatchType = currentBookmark?.let { b ->
                        com.lagradost.common.storage.DesktopWatchType.entries.find { it.id == b.watchType }
                    } ?: com.lagradost.common.storage.DesktopWatchType.WATCHING

                    val setWatchType: (com.lagradost.common.storage.DesktopWatchType) -> Unit = { type ->
                        val newBookmark = DesktopBookmark(
                            id = bookmarkId,
                            name = data.name.takeIf { it.isNotBlank() } ?: uiState?.preloadedName ?: "",
                            url = data.url,
                            apiName = provider.name,
                            posterUrl = data.posterUrl,
                            watchType = type.id,
                        )
                        com.lagradost.cloudstream3.desktop.repo.BookmarksRepository.addBookmark(newBookmark)
                        isEditingStatus = false
                    }

                    val removeBookmarkAction: () -> Unit = {
                        com.lagradost.cloudstream3.desktop.repo.BookmarksRepository.removeBookmark(bookmarkId)
                        isEditingStatus = false
                    }

                    val libraryButton: @Composable (Modifier) -> Unit = { mod ->
                        val isInLibrary = currentBookmark != null
                        Surface(
                            onClick = { isEditingStatus = true },
                            modifier = mod.height(if (isButtonsNarrow) 42.dp else 56.dp),
                            shape = RoundedCornerShape(if (isButtonsNarrow) 10.dp else 12.dp),
                            color = if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.28f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = if (isButtonsNarrow) 12.dp else 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isButtonsNarrow) 6.dp else 8.dp),
                            ) {
                                Icon(
                                    imageVector = if (isInLibrary) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (isInLibrary) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(if (isButtonsNarrow) 18.dp else 20.dp),
                                )
                                Text(
                                    text = if (isInLibrary) activeWatchType.stringRes else "Add to Library",
                                    color = if (isInLibrary) MaterialTheme.colorScheme.primary else Color.White,
                                    fontSize = if (isButtonsNarrow) 13.sp else 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    CloudstreamAlertDialog(
                        show = isEditingStatus,
                        onDismissRequest = { isEditingStatus = false },
                        title = {
                            Text(
                                text = if (currentBookmark != null) "Library Status" else "Add to Library",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) {
                                com.lagradost.common.storage.DesktopWatchType.entries.forEach { type ->
                                    val isSelected = currentBookmark?.watchType == type.id
                                    val icon = when (type) {
                                        com.lagradost.common.storage.DesktopWatchType.WATCHING -> Icons.Default.PlayArrow
                                        com.lagradost.common.storage.DesktopWatchType.COMPLETED -> Icons.Default.Check
                                        com.lagradost.common.storage.DesktopWatchType.ONHOLD -> Icons.Default.Pause
                                        com.lagradost.common.storage.DesktopWatchType.DROPPED -> Icons.Default.Close
                                        com.lagradost.common.storage.DesktopWatchType.PLANTOWATCH -> Icons.Default.Bookmark
                                        com.lagradost.common.storage.DesktopWatchType.REWATCHING -> Icons.AutoMirrored.Filled.RotateRight
                                    }
                                    Surface(
                                        onClick = { setWatchType(type) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = type.stringRes,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                if (currentBookmark != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        onClick = removeBookmarkAction,
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = "Remove from Library",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { isEditingStatus = false }) {
                                Text("Cancel")
                            }
                        },
                    )

                    if (isButtonsNarrow) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().align(if (isNarrow) Alignment.CenterHorizontally else Alignment.Start),
                        ) {
                            Box(modifier = Modifier.weight(1.15f)) {
                                heroAction(Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                libraryButton(Modifier.fillMaxWidth())
                            }
                            downloadAction?.invoke(Modifier)
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth().align(if (isNarrow) Alignment.CenterHorizontally else Alignment.Start),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    heroAction(Modifier.fillMaxWidth())
                                }
                                libraryButton(Modifier)
                                downloadAction?.invoke(Modifier)
                            }
                        }
                    }
                }
            },
            sideContent = {
                if (!isLoading) {
                    val pinIcon = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                        androidx.compose.ui.graphics.vector.ImageVector.Builder(
                            name = "Pin",
                            defaultWidth = 24.dp,
                            defaultHeight = 24.dp,
                            viewportWidth = 24f,
                            viewportHeight = 24f,
                        ).path(
                            fill = androidx.compose.ui.graphics.SolidColor(Color.White),
                            stroke = null,
                            strokeAlpha = 1f,
                            fillAlpha = 1f,
                            strokeLineWidth = 1f,
                            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Butt,
                            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Bevel,
                            strokeLineMiter = 1f,
                        ) {
                            moveTo(16f, 9f)
                            verticalLineTo(4f)
                            horizontalLineTo(17f)
                            verticalLineTo(2f)
                            horizontalLineTo(7f)
                            verticalLineTo(4f)
                            horizontalLineTo(8f)
                            verticalLineTo(9f)
                            curveTo(8f, 10.66f, 6.66f, 12f, 5f, 12f)
                            verticalLineTo(14f)
                            horizontalLineTo(10.97f)
                            verticalLineTo(21f)
                            lineTo(11.97f, 22f)
                            lineTo(12.97f, 21f)
                            verticalLineTo(14f)
                            horizontalLineTo(19f)
                            verticalLineTo(12f)
                            curveTo(17.34f, 12f, 16f, 10.66f, 16f, 9f)
                            close()
                        }.build(),
                    )

                    Column(
                        modifier = Modifier
                            .then(if (isNarrow) Modifier.fillMaxWidth() else Modifier.widthIn(max = 420.dp))
                            .padding(bottom = 12.dp)
                            .graphicsLayer { alpha = rightColumnAlpha }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Enter) {
                                            isRightColumnHovered = true
                                        } else if (event.type == PointerEventType.Exit) {
                                            isRightColumnHovered = false
                                        }
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = if (isNarrow) Alignment.CenterHorizontally else Alignment.End,
                    ) {
                        val textShadow = androidx.compose.ui.text.TextStyle(
                            shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                        )

                        // Pin Toggle Row
                        if (!isNarrow) {
                            val pinAlpha by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isRightColumnHovered) 1f else 0f,
                                label = "pinAlpha",
                            )
                            Row(
                                modifier = Modifier.graphicsLayer { alpha = pinAlpha },
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {
                                        val nextPinned = !isRightColumnPinned
                                        isRightColumnPinned = nextPinned
                                        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            com.lagradost.common.storage.DesktopDataStore.setKey("DETAILS_RIGHT_COLUMN_PINNED", nextPinned)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painter = pinIcon,
                                        contentDescription = "Pin sidebar",
                                        tint = if (isRightColumnPinned) Color.White else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }

                        // Stats & Info Sidebar
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = if (isNarrow) Alignment.CenterHorizontally else Alignment.End,
                        ) {
                            val stats = buildList {
                                add("Source" to provider.name)

                                val relDate = uiState?.enrichedReleaseDate ?: data.year?.toString()
                                if (!relDate.isNullOrBlank()) add("Release Date" to relDate)

                                val status = uiState?.enrichedStatus
                                if (!status.isNullOrBlank()) add("Status" to status)
                            }

                            stats.forEachIndexed { index, stat ->
                                InfoRowItem(
                                    label = stat.first,
                                    value = stat.second,
                                    textShadow = textShadow,
                                )
                            }
                        }
                    }
                }
            },
        )
        }
    }
}

@Composable
private fun DetailsMetadataCompact(
    provider: MainAPI,
    data: LoadResponse,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    isLoading: Boolean,
    enrichmentPhase: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase,
    heroAction: @Composable (Modifier) -> Unit,
    downloadAction: (@Composable (Modifier) -> Unit)?,
    onPhotosClick: () -> Unit,
    onCastClick: () -> Unit,
    onActorClick: (com.lagradost.cloudstream3.ActorData) -> Unit,
    onTrailerClick: ((String) -> Unit)?,
) {
    val activeLogoUrl = remember(data, enrichmentPhase, uiState) {
        uiState?.enrichedLogoUrl?.takeIf { it.isNotBlank() }
            ?: data.logoUrl?.takeIf { it.isNotBlank() }
            ?: provider.fixUrlNull(data.logoUrl)
    }
    val displayName = data.name.takeIf { it.isNotBlank() } ?: uiState?.preloadedName ?: ""

    val bookmarkId = "${provider.name}_${data.url.hashCode()}"
    val allBookmarks = uiState?.bookmarks ?: emptyMap()
    val currentBookmark = allBookmarks[bookmarkId]
    var isEditingStatus by remember { mutableStateOf(false) }

    val setWatchType: (com.lagradost.common.storage.DesktopWatchType) -> Unit = { type ->
        val newBookmark = DesktopBookmark(
            id = bookmarkId,
            name = data.name.takeIf { it.isNotBlank() } ?: uiState?.preloadedName ?: "",
            url = data.url,
            apiName = provider.name,
            posterUrl = data.posterUrl,
            watchType = type.id,
        )
        com.lagradost.cloudstream3.desktop.repo.BookmarksRepository.addBookmark(newBookmark)
        isEditingStatus = false
    }

    val removeBookmarkAction: () -> Unit = {
        com.lagradost.cloudstream3.desktop.repo.BookmarksRepository.removeBookmark(bookmarkId)
        isEditingStatus = false
    }

    var isSynopsisExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 140.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Logo or Title Text
        if (!activeLogoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .heightIn(min = 60.dp, max = 95.dp),
                contentAlignment = Alignment.Center,
            ) {
                val logoRequest = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                    .data(activeLogoUrl)
                    .size(1200, 600)
                    .crossfade(true)
                    .build()
                coil3.compose.SubcomposeAsyncImage(
                    model = logoRequest,
                    contentDescription = displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.Center,
                    error = {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                    blurRadius = 12f,
                                ),
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        } else {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                        blurRadius = 12f,
                    ),
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 2. Metadata tags: Year • Duration • Type • Rating
        val metaItems = mutableListOf<String>()
        val enrichedDate = uiState?.enrichedReleaseDate
        val yearSpan = if (data.type != TvType.Movie && data.type != TvType.AnimeMovie && !enrichedDate.isNullOrBlank() && enrichedDate.contains("–")) {
            enrichedDate
        } else {
            (uiState?.enrichedYear ?: data.year)?.toString()
        }
        yearSpan?.let { metaItems.add(it) }

        val finalDuration = uiState?.enrichedDuration ?: data.duration
        finalDuration?.takeIf { it > 0 }?.let { dur ->
            val mins = if (dur > 360) dur / 60 else dur
            val durationStr = if (mins >= 60) {
                val h = mins / 60
                val m = mins % 60
                if (m > 0) "${h}h ${m}m" else "${h}h"
            } else {
                "${mins}m"
            }
            metaItems.add(durationStr)
        }

        val typeStr = when (data.type) {
            TvType.TvSeries -> "TV Series"
            TvType.Anime -> "Anime"
            TvType.Movie -> "Movie"
            TvType.AnimeMovie -> "Anime Movie"
            TvType.OVA -> "OVA"
            TvType.Live -> "Live"
            TvType.Documentary -> "Documentary"
            TvType.Cartoon -> "Cartoon"
            TvType.AsianDrama -> "Asian Drama"
            else -> data.type.name
        }
        if (typeStr.isNotBlank()) metaItems.add(typeStr)

        data.contentRating?.takeIf { it.isNotBlank() }?.let { metaItems.add(it) }

        if (metaItems.isNotEmpty()) {
            Text(
                text = metaItems.joinToString("  •  "),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 3. Compact Scores Row (IMDb / TMDB / AniList) + Genres
        val imdbScore = uiState?.enrichedImdbRating
        val tmdbScore = uiState?.enrichedTmdbRating ?: if (imdbScore == null) data.score?.toFloat(10)?.toDouble() else null
        val anilistScore = uiState?.enrichedAniListRating

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (imdbScore != null && imdbScore > 0.0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF5C518).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF5C518).copy(alpha = 0.45f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Surface(shape = RoundedCornerShape(2.dp), color = Color(0xFFF5C518)) {
                            Text("IMDb", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 3.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(String.format(java.util.Locale.US, "%.1f", imdbScore), color = Color(0xFFF5C518), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (tmdbScore != null && tmdbScore > 0.0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF01B4E4).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF01B4E4).copy(alpha = 0.4f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Surface(shape = RoundedCornerShape(2.dp), color = Color(0xFF01B4E4)) {
                            Text("TMDB", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 3.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(String.format(java.util.Locale.US, "%.1f", tmdbScore), color = Color(0xFF01B4E4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (anilistScore != null && anilistScore > 0.0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF02A9FF).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF02A9FF).copy(alpha = 0.4f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Surface(shape = RoundedCornerShape(2.dp), color = Color(0xFF02A9FF)) {
                            Text("AniList", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${(anilistScore * 10).toInt()}%", color = Color(0xFF02A9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Clean Genre chips (first 2 genres)
            val genres = data.tags ?: emptyList()
            genres.take(2).forEach { genre ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.padding(horizontal = 1.dp),
                ) {
                    Text(
                        text = genre,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // 4. Action Buttons (Side by Side Play and Library)
        val isInLibrary = currentBookmark != null
        val activeWatchType = currentBookmark?.let { b ->
            com.lagradost.common.storage.DesktopWatchType.entries.find { it.id == b.watchType }
        } ?: com.lagradost.common.storage.DesktopWatchType.WATCHING

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1.15f)) {
                heroAction(Modifier.fillMaxWidth())
            }
            Surface(
                onClick = { isEditingStatus = true },
                shape = RoundedCornerShape(10.dp),
                color = if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f).height(42.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (isInLibrary) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (isInLibrary) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isInLibrary) activeWatchType.stringRes else "Add to Library",
                        color = if (isInLibrary) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            val trailerUrl = uiState?.enrichedTrailerUrl ?: uiState?.enrichedTrailers?.firstOrNull()?.url
            if (!trailerUrl.isNullOrBlank() && onTrailerClick != null) {
                Surface(
                    onClick = { onTrailerClick(trailerUrl) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Trailer",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // 5. Compact 2-Line Synopsis
        val rawPlot = data.plot
        if (!rawPlot.isNullOrBlank()) {
            Text(
                text = rawPlot,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }

        // Library status popup dialog
        CloudstreamAlertDialog(
            show = isEditingStatus,
            onDismissRequest = { isEditingStatus = false },
            title = {
                Text(
                    text = if (currentBookmark != null) "Library Status" else "Add to Library",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    com.lagradost.common.storage.DesktopWatchType.entries.forEach { type ->
                        val isSelected = currentBookmark?.watchType == type.id
                        val icon = when (type) {
                            com.lagradost.common.storage.DesktopWatchType.WATCHING -> Icons.Default.PlayArrow
                            com.lagradost.common.storage.DesktopWatchType.COMPLETED -> Icons.Default.Check
                            com.lagradost.common.storage.DesktopWatchType.ONHOLD -> Icons.Default.Pause
                            com.lagradost.common.storage.DesktopWatchType.DROPPED -> Icons.Default.Close
                            com.lagradost.common.storage.DesktopWatchType.PLANTOWATCH -> Icons.Default.Bookmark
                            com.lagradost.common.storage.DesktopWatchType.REWATCHING -> Icons.AutoMirrored.Filled.RotateRight
                        }
                        Surface(
                            onClick = { setWatchType(type) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = type.stringRes,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (currentBookmark != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            onClick = removeBookmarkAction,
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Remove from Library",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isEditingStatus = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun InfoRowItem(label: String, value: String, textShadow: androidx.compose.ui.text.TextStyle) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.merge(textShadow),
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.merge(textShadow),
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsCastSection(
    data: LoadResponse,
    provider: MainAPI,
    onActorClick: (ActorData) -> Unit = {},
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val actors = uiState?.enrichedActors ?: data.actors ?: emptyList()

    val directors = actors.filter {
        it.roleString?.equals("Director", ignoreCase = true) == true ||
            it.roleString?.equals("Creator", ignoreCase = true) == true
    }
    val cast = actors.filter {
        it.roleString?.equals("Director", ignoreCase = true) != true &&
            it.roleString?.equals("Creator", ignoreCase = true) != true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 600.dp
        val hPad = if (isCompact) 12.dp else horizontalPadding

        if (cast.isNotEmpty() || directors.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = 8.dp),
            ) {
                val invertedMap = remember { androidx.compose.runtime.mutableStateMapOf<ActorData, Boolean>() }

                if (cast.isNotEmpty()) {
                    Text(
                        text = "Cast",
                        style = if (isCompact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))
                    if (isCompact) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(cast.take(20)) { actor ->
                                CompactActorCard(
                                    actor = actor,
                                    provider = provider,
                                    onClick = { onActorClick(actor) },
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            cast.take(18).forEach { actor ->
                                ActorCard(
                                    actor = actor,
                                    provider = provider,
                                    isInverted = invertedMap[actor] == true,
                                    onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                                    onClick = { onActorClick(actor) },
                                )
                            }
                        }
                    }
                }

                if (directors.isNotEmpty() && cast.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 32.dp))
                }

                if (directors.isNotEmpty()) {
                    val headerTitle = if (directors.any { it.roleString?.equals("Creator", ignoreCase = true) == true }) "Directors & Creators" else "Directors"
                    Text(
                        text = headerTitle,
                        style = if (isCompact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))
                    if (isCompact) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(directors) { actor ->
                                CompactActorCard(
                                    actor = actor,
                                    provider = provider,
                                    onClick = { onActorClick(actor) },
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            directors.forEach { actor ->
                                ActorCard(
                                    actor = actor,
                                    provider = provider,
                                    isInverted = invertedMap[actor] == true,
                                    onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                                    onClick = { onActorClick(actor) },
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
private fun CompactActorCard(
    actor: ActorData,
    provider: MainAPI,
    onClick: () -> Unit,
) {
    val actorImg = provider.fixUrlNull(actor.actor.image ?: actor.voiceActor?.image)
    val actorName = actor.actor.name
    val roleStr = actor.roleString ?: actor.role?.name

    Column(
        modifier = Modifier
            .width(76.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (actorImg != null) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(actorImg)
                        .size(160, 160)
                        .crossfade(true)
                        .build(),
                    contentDescription = actorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = actorName,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actorName,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!roleStr.isNullOrBlank()) {
            Text(
                text = roleStr,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActorCard(
    actor: ActorData,
    provider: MainAPI,
    isInverted: Boolean,
    onInvertToggle: () -> Unit,
    onClick: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
    )

    val (mainImgRaw, cornerImgRaw) = if (!isInverted || actor.voiceActor?.image.isNullOrBlank()) {
        Pair(actor.actor.image, actor.voiceActor?.image)
    } else {
        Pair(actor.voiceActor?.image, actor.actor.image)
    }

    val (mainName, subName) = if (!isInverted || actor.voiceActor?.name.isNullOrBlank()) {
        Pair(actor.actor.name, actor.voiceActor?.name)
    } else {
        Pair(actor.voiceActor?.name ?: "", actor.actor.name)
    }

    val roleStr = when {
        actor.roleString?.equals("Director", ignoreCase = true) == true -> "DIRECTOR"
        actor.roleString?.equals("Creator", ignoreCase = true) == true -> "CREATOR"
        actor.role != null -> actor.role?.name?.uppercase()
        !actor.roleString.isNullOrBlank() && subName.isNullOrBlank() -> null
        else -> actor.roleString?.uppercase()
    }

    val secondaryText = when {
        !subName.isNullOrBlank() -> subName
        !actor.roleString.isNullOrBlank() && actor.roleString?.equals("Director", ignoreCase = true) != true && actor.roleString?.equals("Creator", ignoreCase = true) != true -> actor.roleString
        else -> null
    }

    Column(
        modifier = Modifier
            .width(165.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
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
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .shadow(
                    elevation = if (isHovered) 16.dp else 6.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f),
                    spotColor = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f),
                )
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isHovered) 1.5.dp else 1.dp,
                    color = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            val actorImg = provider.fixUrlNull(mainImgRaw)
            if (actorImg != null) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(actorImg)
                        .size(400, 560)
                        .crossfade(true)
                        .build(),
                    contentDescription = mainName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = mainName,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // Bottom gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )

            // Top-left Role Badge
            if (!roleStr.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = roleStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = when (roleStr) {
                            "MAIN" -> MaterialTheme.colorScheme.primary
                            "SUPPORTING" -> Color(0xFF4DD0E1)
                            "DIRECTOR", "CREATOR" -> Color(0xFFFFB74D)
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            // Bottom-right Dual Cast (Voice Actor) mini avatar badge
            val voiceActorImg = cornerImgRaw?.let { provider.fixUrlNull(it) }
            if (voiceActorImg != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .shadow(8.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onInvertToggle() },
                ) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                            .data(voiceActorImg)
                            .size(128, 128)
                            .crossfade(true)
                            .build(),
                        contentDescription = subName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Title (Character Name or Live Action Actor Name)
        Text(
            text = mainName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )

        // Secondary Text (Voice Actor name or Live Action Character Role)
        if (!secondaryText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (!subName.isNullOrBlank()) "🎙 $secondaryText" else secondaryText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
fun hasDetailsStats(
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    data: LoadResponse? = null,
): Boolean {
    if (uiState == null && data == null) return false
    val budget = uiState?.enrichedBudget
    val revenue = uiState?.enrichedRevenue
    val country = uiState?.enrichedCountry
    val lang = uiState?.enrichedOriginalLanguage
    val relDate = uiState?.enrichedReleaseDate ?: data?.year?.toString()
    val status = uiState?.enrichedStatus ?: (data as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.showStatus?.name ?: (data as? com.lagradost.cloudstream3.AnimeLoadResponse)?.showStatus?.name
    val cert = data?.contentRating
    val dur = data?.duration
    val seasons = uiState?.enrichedSeasonsCount
    val episodes = uiState?.enrichedEpisodesCount
    val hasCompanies = uiState?.enrichedProductionCompanies?.isNotEmpty() == true ||
        uiState?.enrichedNetworksList?.isNotEmpty() == true ||
        uiState?.enrichedStudios?.isNotEmpty() == true ||
        uiState?.enrichedNetworks?.isNotEmpty() == true

    return budget != null || revenue != null || hasCompanies ||
        !country.isNullOrBlank() || !lang.isNullOrBlank() || !status.isNullOrBlank() ||
        !relDate.isNullOrBlank() || !cert.isNullOrBlank() || (dur ?: 0) > 0 ||
        (seasons ?: 0) > 0 || (episodes ?: 0) > 0
}

@Composable
fun DetailsStatsSection(
    data: LoadResponse,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    modifier: Modifier = Modifier,
    onCompanyClick: ((com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany) -> Unit)? = null,
) {
    val budget = uiState?.enrichedBudget
    val revenue = uiState?.enrichedRevenue
    val country = uiState?.enrichedCountry
    val lang = uiState?.enrichedOriginalLanguage
    val relDate = uiState?.enrichedReleaseDate ?: data.year?.toString()
    val status = uiState?.enrichedStatus ?: (data as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.showStatus?.name ?: (data as? com.lagradost.cloudstream3.AnimeLoadResponse)?.showStatus?.name
    val cert = data.contentRating
    val seasons = uiState?.enrichedSeasonsCount
    val episodes = uiState?.enrichedEpisodesCount

    val dur = data.duration
    val runtimeStr = if (dur != null && dur > 0) {
        val mins = if (dur > 360) dur / 60 else dur
        if (mins >= 60) {
            val h = mins / 60
            val m = mins % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        } else {
            "${mins}m"
        }
    } else null

    val allCompanies = remember(uiState) {
        val list = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany>()
        if (uiState != null) {
            list.addAll(uiState.enrichedProductionCompanies)
            list.addAll(uiState.enrichedNetworksList)
            if (list.isEmpty()) {
                list.addAll(uiState.enrichedStudios.map { com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany(name = it) })
                list.addAll(uiState.enrichedNetworks.map { com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany(name = it) })
            }
        }
        list.distinctBy { it.name.trim().lowercase() }
    }

    val detailRows = remember(data, uiState, budget, revenue, country, lang, relDate, status, cert, runtimeStr, seasons, episodes) {
        val list = mutableListOf<Pair<String, String>>()
        if (!status.isNullOrBlank()) {
            list.add("Status" to status)
        }
        if (!relDate.isNullOrBlank()) {
            list.add("Release Info" to relDate)
        }
        if (!runtimeStr.isNullOrBlank()) {
            list.add("Runtime" to runtimeStr)
        }
        if (!cert.isNullOrBlank()) {
            list.add("Certification" to cert)
        }
        if (!country.isNullOrBlank()) {
            list.add("Origin Country" to country)
        }
        if (!lang.isNullOrBlank()) {
            list.add("Original Language" to lang)
        }
        if (seasons != null && seasons > 0) {
            val epStr = if (episodes != null && episodes > 0) " ($episodes Episodes)" else ""
            list.add("Seasons" to "$seasons ${if (seasons == 1) "Season" else "Seasons"}$epStr")
        }
        if (budget != null && budget > 0) {
            list.add("Budget" to formatCurrency(budget))
        }
        if (revenue != null && revenue > 0) {
            list.add("Box Office" to formatCurrency(revenue))
        }
        list
    }

    if (detailRows.isNotEmpty() || allCompanies.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            if (detailRows.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    detailRows.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                            )
                        }
                        if (index < detailRows.lastIndex) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }

            // Production Studios & Networks Section (with Logos)
            if (allCompanies.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) {
                    Text(
                        text = "STUDIOS & NETWORKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        allCompanies.forEach { company ->
                            ProductionCompanyCard(
                                company = company,
                                onClick = if (onCompanyClick != null) { { onCompanyClick(company) } } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductionCompanyCard(
    company: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val hasLogo = !company.logoUrl.isNullOrBlank()
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isHovered && onClick != null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isHovered && onClick != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f),
        ),
        modifier = modifier
            .height(84.dp)
            .widthIn(min = 200.dp, max = 320.dp)
            .hoverable(interactionSource)
            .run {
                if (onClick != null) {
                    this.clickable(interactionSource = interactionSource, indication = null) { onClick() }
                } else {
                    this
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (hasLogo) {
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.96f))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = company.logoUrl,
                        contentDescription = company.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!company.originCountry.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = company.originCountry.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
        }
    }
}

private fun formatCurrency(amount: Long): String {
    return when {
        amount >= 1_000_000_000 -> "$${String.format("%.1f", amount.toDouble() / 1_000_000_000)} Billion"
        amount >= 1_000_000 -> "$${String.format("%.1f", amount.toDouble() / 1_000_000)} Million"
        else -> "$${java.text.NumberFormat.getIntegerInstance().format(amount)}"
    }
}
