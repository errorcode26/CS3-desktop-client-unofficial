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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
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

@Composable
fun DetailsBackdrop(
    provider: MainAPI,
    data: LoadResponse,
    scrollState: LazyListState,
    enrichmentPhase: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.EnrichmentPhase,
    modifier: Modifier = Modifier,
    dynamicColorEnabled: Boolean = false,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState? = null,
    activeBgUrl: String? = null,
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
            },
    ) {
        val currentPhase = enrichmentPhase

        val baseBgUrl = remember(data, currentPhase, uiState) {
            // Always prefer enriched TMDB backdrop
            uiState?.enrichedBackdropUrl?.takeIf { it.isNotBlank() }
                ?: if (uiState?.isEnriching == false) {
                    provider.fixUrlNull(data.backgroundPosterUrl)?.takeIf { it.isNotBlank() }
                        ?: provider.fixUrlNull(data.posterUrl)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
        }

        val bgUrl = activeBgUrl ?: baseBgUrl
        val isFallback = remember(data, bgUrl, uiState) {
            if (!uiState?.enrichedBackdropUrl.isNullOrBlank()) return@remember false
            data.backgroundPosterUrl.isNullOrBlank() || data.backgroundPosterUrl == data.posterUrl
        }

        if (bgUrl != null) {
            androidx.compose.animation.Crossfade(
                targetState = bgUrl,
                animationSpec = androidx.compose.animation.core.tween(2000),
                label = "backdrop_crossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .run { if (isFallback) this.blur(18.dp) else this }
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
                        // Horizontal vignette gradient
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
                            0.65f to Color(0xFF0F0F0F).copy(alpha = 0.50f),
                            0.85f to Color(0xFF0F0F0F).copy(alpha = 0.88f),
                            1.00f to Color(0xFF0F0F0F),
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
    onEvent: (com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent) -> Unit = {},
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
                onEvent = onEvent,
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
            val responsiveLogoMaxWidth = minOf(600.dp, maxWidth * if (isNarrow) 0.7f else 0.42f)
            val responsivePlotMaxWidth = minOf(580.dp, maxWidth * if (isNarrow) 0.85f else 0.46f)

            val responsiveLogoMaxHeight = when {
                isCompactHeight -> 96.dp
                isMediumHeight -> 130.dp
                else -> 165.dp
            }
            val responsiveTopPadding = when {
                isCompactHeight -> 48.dp
                isMediumHeight -> if (isNarrow) 56.dp else 64.dp
                else -> if (isNarrow) 64.dp else 72.dp
            }

            val isMovie = data.type == TvType.Movie || data.type == TvType.AnimeMovie || data.type == TvType.Live
            val responsiveBottomPadding = when {
                isCompactHeight -> 14.dp
                isMovie -> 36.dp
                else -> 28.dp
            }

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
                                var isDarkLogo by remember(activeLogoUrl) { mutableStateOf(false) }
                                val platformContext = coil3.compose.LocalPlatformContext.current

                                val logoRequest = remember(activeLogoUrl, platformContext) {
                                    coil3.request.ImageRequest.Builder(platformContext)
                                        .data(activeLogoUrl)
                                        .size(1600, 800)
                                        .crossfade(true)
                                        .listener(
                                            onSuccess = { _, result ->
                                                isDarkLogo = com.lagradost.cloudstream3.desktop.utils.ImageUtils.isDarkImage(result.image)
                                            },
                                        )
                                        .build()
                                }
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
                                    colorFilter = if (isDarkLogo) androidx.compose.ui.graphics.ColorFilter.colorMatrix(com.lagradost.cloudstream3.desktop.utils.ImageUtils.InvertColorMatrix) else null,
                                    error = {
                                        com.lagradost.cloudstream3.desktop.ui.components.CinematicTitle(
                                            text = displayName,
                                            fontSize = if (displayName.length > 28) 34.sp else 42.sp,
                                            textAlign = if (isNarrow) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                                            modifier = Modifier
                                                .widthIn(max = responsivePlotMaxWidth)
                                                .padding(bottom = 2.dp),
                                        )
                                    },
                                )
                            }
                        } else {
                            com.lagradost.cloudstream3.desktop.ui.components.CinematicTitle(
                                text = displayName,
                                fontSize = if (displayName.length > 28) 34.sp else 42.sp,
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

                    Spacer(modifier = Modifier.height(12.dp))

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

                            // 2. Runtime Duration
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

                            // 3. Content Type
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
                                    text = metaItems.joinToString("   •   "),
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            // 4. Normalized Content Rating Badge (e.g. TV-14, Rated R, 18+)
                            data.contentRating?.takeIf { it.isNotBlank() }?.let { rating ->
                                com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents.ContentRatingBadge(
                                    rating = rating,
                                    isLarge = true,
                                    isSeries = data.type == TvType.TvSeries || data.type == TvType.Anime || data.type == TvType.AsianDrama,
                                )
                            }
                        }

                        // Row 2: Ratings and genres
                        val imdbScore = uiState?.enrichedImdbRating
                        val tmdbScore = uiState?.enrichedTmdbRating ?: if (imdbScore == null) data.score?.toFloat(10)?.toDouble() else null
                        val anilistScore = uiState?.enrichedAniListRating
                        val finalTags = uiState?.enrichedTags ?: data.tags

                        if (imdbScore != null || tmdbScore != null || anilistScore != null || !finalTags.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (isNarrow) Arrangement.Center else Arrangement.spacedBy(16.dp),
                                modifier = if (isNarrow) Modifier.fillMaxWidth() else Modifier,
                            ) {
                                // IMDb score
                                if (imdbScore != null && imdbScore > 0.0) {
                                    com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents.BrandedRatingBadge(
                                        logoRes = "badges/rating_imdb.png",
                                        scoreText = String.format(java.util.Locale.US, "%.1f", imdbScore),
                                        textColor = Color(0xFFF5C518),
                                        logoWidth = 40.dp,
                                        logoHeight = 20.dp,
                                    )
                                }

                                // TMDB score
                                if (tmdbScore != null && tmdbScore > 0.0) {
                                    com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents.BrandedRatingBadge(
                                        logoRes = "badges/rating_tmdb.png",
                                        scoreText = String.format(java.util.Locale.US, "%.1f", tmdbScore),
                                        textColor = Color(0xFF01B4E4),
                                        logoWidth = 34.dp,
                                        logoHeight = 21.dp,
                                    )
                                }

                                // MAL / AniList score
                                if (anilistScore != null && anilistScore > 0.0) {
                                    com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents.BrandedRatingBadge(
                                        logoRes = "badges/rating_mal.png",
                                        scoreText = "${(anilistScore * 10).toInt()}%",
                                        textColor = Color(0xFF02A9FF),
                                        logoWidth = 32.dp,
                                        logoHeight = 20.dp,
                                    )
                                }

                                // Genres
                                if (!finalTags.isNullOrEmpty()) {
                                    val hasRatings = (imdbScore != null && imdbScore > 0.0) || (tmdbScore != null && tmdbScore > 0.0) || (anilistScore != null && anilistScore > 0.0)
                                    Text(
                                        text = (if (hasRatings) "•   " else "") + finalTags.take(4).joinToString(", "),
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    val cleanedPlot = remember(data.plot) { com.lagradost.cloudstream3.desktop.utils.TitleUtils.cleanHtml(data.plot) }
                    if (!isLoading && !cleanedPlot.isNullOrBlank()) {
                        Text(
                            text = cleanedPlot,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = responsivePlotMaxWidth),
                            textAlign = if (isNarrow) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                        onEvent(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent.OnAddBookmark(newBookmark))
                        isEditingStatus = false
                    }

                    val removeBookmarkAction: () -> Unit = {
                        onEvent(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent.OnRemoveBookmark(bookmarkId))
                        isEditingStatus = false
                    }

                    val libraryButton: @Composable (Modifier) -> Unit = { mod ->
                        val isInLibrary = currentBookmark != null
                        val libraryIcon = if (isInLibrary) {
                            when (activeWatchType) {
                                com.lagradost.common.storage.DesktopWatchType.WATCHING -> Icons.Default.PlayArrow
                                com.lagradost.common.storage.DesktopWatchType.COMPLETED -> Icons.Default.Check
                                com.lagradost.common.storage.DesktopWatchType.ONHOLD -> Icons.Default.Pause
                                com.lagradost.common.storage.DesktopWatchType.DROPPED -> Icons.Default.Close
                                com.lagradost.common.storage.DesktopWatchType.PLANTOWATCH -> Icons.Default.Bookmark
                                com.lagradost.common.storage.DesktopWatchType.REWATCHING -> Icons.AutoMirrored.Filled.RotateRight
                            }
                        } else {
                            Icons.Default.Add
                        }
                        Surface(
                            onClick = { isEditingStatus = true },
                            modifier = mod.size(if (isButtonsNarrow) 44.dp else 52.dp),
                            shape = RoundedCornerShape(if (isButtonsNarrow) 10.dp else 12.dp),
                            color = if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.28f),
                            ),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = libraryIcon,
                                    contentDescription = if (isInLibrary) activeWatchType.stringRes else "Add to Library",
                                    tint = if (isInLibrary) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(if (isButtonsNarrow) 20.dp else 24.dp),
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
                            val statusItems = listOf(
                                com.lagradost.common.storage.DesktopWatchType.WATCHING to Icons.Default.PlayArrow,
                                com.lagradost.common.storage.DesktopWatchType.COMPLETED to Icons.Default.CheckCircle,
                                com.lagradost.common.storage.DesktopWatchType.PLANTOWATCH to Icons.Default.Bookmark,
                                com.lagradost.common.storage.DesktopWatchType.ONHOLD to Icons.Default.PauseCircle,
                                com.lagradost.common.storage.DesktopWatchType.REWATCHING to Icons.AutoMirrored.Filled.RotateRight,
                                com.lagradost.common.storage.DesktopWatchType.DROPPED to Icons.Default.Cancel,
                            )
                            val primaryColor = MaterialTheme.colorScheme.primary

                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) {
                                statusItems.chunked(3).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        rowItems.forEach { (type, icon) ->
                                            val isSelected = currentBookmark?.watchType == type.id
                                            val itemInteraction = remember { MutableInteractionSource() }
                                            val isHovered by itemInteraction.collectIsHoveredAsState()

                                            Surface(
                                                onClick = { setWatchType(type) },
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isSelected) primaryColor.copy(alpha = 0.18f) else if (isHovered) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    if (isSelected) 1.5.dp else 1.dp,
                                                    if (isSelected) primaryColor else if (isHovered) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
                                                ),
                                                interactionSource = itemInteraction,
                                                modifier = Modifier.weight(1f).height(84.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxSize(),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                    ) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = if (isSelected) primaryColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                                                            modifier = Modifier.size(32.dp),
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                                Icon(
                                                                    imageVector = icon,
                                                                    contentDescription = null,
                                                                    tint = if (isSelected) primaryColor else Color.White.copy(alpha = 0.85f),
                                                                    modifier = Modifier.size(17.dp),
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(
                                                            text = type.stringRes,
                                                            color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.85f),
                                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }

                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = primaryColor,
                                                            modifier = Modifier.size(14.dp).align(Alignment.TopEnd),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            if (currentBookmark != null) {
                                TextButton(
                                    onClick = removeBookmarkAction,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Remove from Library", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { isEditingStatus = false }) {
                                Text("Cancel", fontSize = 13.sp)
                            }
                        },
                    )

                    if (isButtonsNarrow) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().align(if (isNarrow) Alignment.CenterHorizontally else Alignment.Start),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                heroAction(Modifier.fillMaxWidth())
                            }
                            libraryButton(Modifier)
                            downloadAction?.invoke(Modifier)
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.align(if (isNarrow) Alignment.CenterHorizontally else Alignment.Start),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                heroAction(Modifier.wrapContentWidth())
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

                        // Action Bar Row (Screensaver Toggle + Pin Toggle)
                        if (!isNarrow) {
                            val actionAlpha by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isRightColumnHovered) 1f else 0f,
                                label = "actionAlpha",
                            )
                            val screensaverEnabled by AppearanceConfig.screensaverEnabled.collectAsState()
                            val screenshots = uiState?.screenshots ?: emptyList()

                            Row(
                                modifier = Modifier.graphicsLayer { alpha = actionAlpha },
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (screenshots.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                AppearanceConfig.setScreensaverEnabled(!screensaverEnabled)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (screensaverEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (screensaverEnabled) "Pause backdrop screensaver" else "Resume backdrop screensaver",
                                            tint = if (screensaverEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

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
                        val hideDetailsSource by AppearanceConfig.hideDetailsSource.collectAsState()

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = if (isNarrow) Alignment.CenterHorizontally else Alignment.End,
                        ) {
                            val stats = buildList {
                                if (!hideDetailsSource) {
                                    add("Source" to provider.name)
                                }

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
    onEvent: (com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent) -> Unit,
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
        onEvent(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent.OnAddBookmark(newBookmark))
        isEditingStatus = false
    }

    val removeBookmarkAction: () -> Unit = {
        onEvent(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent.OnRemoveBookmark(bookmarkId))
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
                var isDarkLogo by remember(activeLogoUrl) { mutableStateOf(false) }
                val platformContext = coil3.compose.LocalPlatformContext.current
                val logoRequest = remember(activeLogoUrl, platformContext) {
                    coil3.request.ImageRequest.Builder(platformContext)
                        .data(activeLogoUrl)
                        .size(1200, 600)
                        .crossfade(true)
                        .listener(
                            onSuccess = { _, result ->
                                isDarkLogo = com.lagradost.cloudstream3.desktop.utils.ImageUtils.isDarkImage(result.image)
                            },
                        )
                        .build()
                }
                coil3.compose.SubcomposeAsyncImage(
                    model = logoRequest,
                    contentDescription = displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.Center,
                    colorFilter = if (isDarkLogo) androidx.compose.ui.graphics.ColorFilter.colorMatrix(com.lagradost.cloudstream3.desktop.utils.ImageUtils.InvertColorMatrix) else null,
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

        val compactLibraryIcon = if (isInLibrary) {
            when (activeWatchType) {
                com.lagradost.common.storage.DesktopWatchType.WATCHING -> Icons.Default.PlayArrow
                com.lagradost.common.storage.DesktopWatchType.COMPLETED -> Icons.Default.Check
                com.lagradost.common.storage.DesktopWatchType.ONHOLD -> Icons.Default.Pause
                com.lagradost.common.storage.DesktopWatchType.DROPPED -> Icons.Default.Close
                com.lagradost.common.storage.DesktopWatchType.PLANTOWATCH -> Icons.Default.Bookmark
                com.lagradost.common.storage.DesktopWatchType.REWATCHING -> Icons.AutoMirrored.Filled.RotateRight
            }
        } else {
            Icons.Default.Add
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                heroAction(Modifier.fillMaxWidth())
            }
            Surface(
                onClick = { isEditingStatus = true },
                shape = RoundedCornerShape(10.dp),
                color = if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.28f)),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = compactLibraryIcon,
                        contentDescription = if (isInLibrary) activeWatchType.stringRes else "Add to Library",
                        tint = if (isInLibrary) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val trailerUrl = uiState?.enrichedTrailerUrl ?: uiState?.enrichedTrailers?.firstOrNull()?.url
            if (!trailerUrl.isNullOrBlank() && onTrailerClick != null) {
                Surface(
                    onClick = { onTrailerClick(trailerUrl) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.28f)),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Trailer",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // 5. Compact 2-Line Synopsis
        val rawPlot = remember(data.plot) { com.lagradost.cloudstream3.desktop.utils.TitleUtils.cleanHtml(data.plot) }
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
    selectedSeason: Int? = null,
    seasonCredits: Map<Int, List<ActorData>>? = null,
) {
    val activeSeasonActors = if (selectedSeason != null && seasonCredits?.containsKey(selectedSeason) == true) {
        seasonCredits[selectedSeason]
    } else null
    val actors = activeSeasonActors ?: uiState?.enrichedActors ?: data.actors ?: emptyList()

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
        val netWidth = (maxWidth - (hPad * 2)).coerceAtLeast(100.dp)
        val spacingDp = if (isCompact) 12.dp else 16.dp
        val minCardWidth = if (isCompact) 110.dp else 140.dp

        // Column calculation
        val columns = maxOf(2, ((netWidth + spacingDp) / (minCardWidth + spacingDp)).toInt())
        val totalSpacingDp = spacingDp * (columns - 1)
        val dynamicCardWidth = (netWidth - totalSpacingDp) / columns

        if (cast.isNotEmpty() || directors.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = 8.dp),
            ) {
                val invertedMap = remember { androidx.compose.runtime.mutableStateMapOf<ActorData, Boolean>() }
                var showAllCast by remember { androidx.compose.runtime.mutableStateOf(false) }
                val displayedCast = if (showAllCast) cast else cast.take(columns * 2)

                if (cast.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (activeSeasonActors != null && selectedSeason != null) "Season $selectedSeason Cast & Characters" else "Cast & Crew",
                            style = if (isCompact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!isCompact && cast.size > columns * 2) {
                            androidx.compose.material3.TextButton(
                                onClick = { showAllCast = !showAllCast },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (showAllCast) "Show Less" else "View All (${cast.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
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
                            horizontalArrangement = Arrangement.spacedBy(spacingDp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            maxItemsInEachRow = columns,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            displayedCast.forEach { actor ->
                                ActorCard(
                                    actor = actor,
                                    provider = provider,
                                    isInverted = invertedMap[actor] == true,
                                    onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                                    onClick = { onActorClick(actor) },
                                    modifier = Modifier.width(dynamicCardWidth),
                                )
                            }
                        }
                    }
                }

                if (directors.isNotEmpty() && cast.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 32.dp))
                }

                if (directors.isNotEmpty()) {
                    var showAllDirectors by remember { androidx.compose.runtime.mutableStateOf(false) }
                    val displayedDirectors = if (showAllDirectors) directors else directors.take(columns)
                    val headerTitle = if (directors.any { it.roleString?.equals("Creator", ignoreCase = true) == true }) "Directors & Creators" else "Directors"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = headerTitle,
                            style = if (isCompact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!isCompact && directors.size > columns) {
                            androidx.compose.material3.TextButton(
                                onClick = { showAllDirectors = !showAllDirectors },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (showAllDirectors) "Show Less" else "View All (${directors.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
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
                            horizontalArrangement = Arrangement.spacedBy(spacingDp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            maxItemsInEachRow = columns,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            displayedDirectors.forEach { actor ->
                                ActorCard(
                                    actor = actor,
                                    provider = provider,
                                    isInverted = invertedMap[actor] == true,
                                    onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                                    onClick = { onActorClick(actor) },
                                    modifier = Modifier.width(dynamicCardWidth),
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
    val roleStr = when {
        actor.voiceActor?.name?.isNotBlank() == true -> "🎙 ${actor.voiceActor?.name}"
        !actor.roleString.isNullOrBlank() && !actor.roleString.equals("Director", ignoreCase = true) && !actor.roleString.equals("Creator", ignoreCase = true) -> {
            val raw = actor.roleString!!.trim()
            if (raw.startsWith("as ", ignoreCase = true)) raw else "as $raw"
        }
        else -> actor.roleString ?: actor.role?.name
    }

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
    modifier: Modifier = Modifier,
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
        !subName.isNullOrBlank() -> {
            if (!isInverted) "🎙 Voice: $subName" else {
                val raw = subName.trim()
                if (raw.startsWith("as ", ignoreCase = true)) raw else "as $raw"
            }
        }
        !actor.roleString.isNullOrBlank() && actor.roleString?.equals("Director", ignoreCase = true) != true && actor.roleString?.equals("Creator", ignoreCase = true) != true -> {
            val raw = actor.roleString!!.trim()
            if (raw.startsWith("as ", ignoreCase = true)) raw else "as $raw"
        }
        else -> null
    }

    Column(
        modifier = modifier
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
                text = secondaryText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
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

    return budget != null || revenue != null ||
        !country.isNullOrBlank() || !lang.isNullOrBlank() || !status.isNullOrBlank() ||
        !relDate.isNullOrBlank() || !cert.isNullOrBlank() || (dur ?: 0) > 0 ||
        (seasons ?: 0) > 0 || (episodes ?: 0) > 0
}

fun hasStudiosOrNetworks(
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
): Boolean {
    return uiState?.enrichedProductionCompanies?.isNotEmpty() == true ||
        uiState?.enrichedNetworksList?.isNotEmpty() == true ||
        uiState?.enrichedStudios?.isNotEmpty() == true ||
        uiState?.enrichedNetworks?.isNotEmpty() == true
}

@Composable
fun DetailsStatsSection(
    data: LoadResponse,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    modifier: Modifier = Modifier,
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

    if (detailRows.isNotEmpty()) {
        Column(
            modifier = modifier.widthIn(max = 920.dp).fillMaxWidth(),
        ) {
            val half = (detailRows.size + 1) / 2
            val leftCol = detailRows.take(half)
            val rightCol = detailRows.drop(half)

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isTwoColumns = maxWidth >= 600.dp
                if (isTwoColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            leftCol.forEachIndexed { index, (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                    )
                                }
                                if (index < leftCol.lastIndex) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.08f),
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            rightCol.forEachIndexed { index, (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                    )
                                }
                                if (index < rightCol.lastIndex) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.08f),
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        detailRows.forEachIndexed { index, (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
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
            }
        }
    }
}

@Composable
fun DetailsStudiosSection(
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    modifier: Modifier = Modifier,
    onCompanyClick: ((com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany) -> Unit)? = null,
) {
    val separateNetworksPref by com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.separateNetworks.collectAsState()

    val netCompanies = remember(uiState) {
        val list = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany>()
        if (uiState != null) {
            list.addAll(uiState.enrichedNetworksList)
            if (list.isEmpty()) {
                list.addAll(uiState.enrichedNetworks.map { com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany(name = it) })
            }
        }
        list.distinctBy { it.name.trim().lowercase() }
    }

    val prodCompanies = remember(uiState) {
        val list = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany>()
        if (uiState != null) {
            list.addAll(uiState.enrichedProductionCompanies)
            if (list.isEmpty()) {
                list.addAll(uiState.enrichedStudios.map { com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany(name = it) })
            }
        }
        list.distinctBy { it.name.trim().lowercase() }
    }

    val allCompanies = remember(netCompanies, prodCompanies) {
        (prodCompanies + netCompanies).distinctBy { it.name.trim().lowercase() }
    }

    if (allCompanies.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            if (separateNetworksPref && netCompanies.isNotEmpty() && prodCompanies.isNotEmpty()) {
                // Subsection 1: Broadcast Networks
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "BROADCAST NETWORKS",
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
                        netCompanies.forEach { company ->
                            ProductionCompanyCard(
                                company = company,
                                onClick = if (onCompanyClick != null) { { onCompanyClick(company) } } else null,
                            )
                        }
                    }
                }

                // Subsection 2: Production Studios
                Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Text(
                        text = "PRODUCTION STUDIOS",
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
                        prodCompanies.forEach { company ->
                            ProductionCompanyCard(
                                company = company,
                                onClick = if (onCompanyClick != null) { { onCompanyClick(company) } } else null,
                            )
                        }
                    }
                }
            } else {
                val sectionTitle = if (netCompanies.isNotEmpty() && prodCompanies.isEmpty()) "BROADCAST NETWORKS" else if (prodCompanies.isNotEmpty() && netCompanies.isEmpty()) "PRODUCTION STUDIOS" else "STUDIOS & NETWORKS"
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = sectionTitle,
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
