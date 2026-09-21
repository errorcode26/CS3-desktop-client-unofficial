package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.PushPin
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
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.utils.TitleCleaner
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark

data class HeaderMetaKey(
    val cleanTitle: String,
    val year: Int?,
    val season: Int?,
) {
    val isValid: Boolean get() = cleanTitle.isNotBlank()

    companion object {
        fun from(data: LoadResponse): HeaderMetaKey {
            val rawTitle = data.name.orEmpty()
            val clean = TitleCleaner.clean(rawTitle)
            val year = data.year ?: TitleCleaner.extractYear(rawTitle)
            val season = TitleCleaner.extractSeason(rawTitle)
                ?: TitleCleaner.extractSeason(data.url)
            return HeaderMetaKey(clean, year, season)
        }
    }
}

private fun isSeriesLike(type: TvType?): Boolean = when (type) {
    TvType.TvSeries, TvType.Anime, TvType.AsianDrama, TvType.OVA, TvType.Cartoon -> true
    else -> false
}

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

        val baseBgUrl = remember(data.backgroundPosterUrl, data.posterUrl, uiState?.enrichedBackdropUrl) {
            // Always prefer enriched TMDB backdrop
            uiState?.enrichedBackdropUrl?.takeIf { it.isNotBlank() }
                ?: provider.fixUrlNull(data.backgroundPosterUrl)?.takeIf { it.isNotBlank() }
                ?: provider.fixUrlNull(data.posterUrl)?.takeIf { it.isNotBlank() }
        }

        val bgUrl = activeBgUrl ?: baseBgUrl
        val isFallback = remember(data.backgroundPosterUrl, data.posterUrl, uiState?.enrichedBackdropUrl) {
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
    val isLightMode = LocalDesktopTheme.current.isLightMode

   // potato title and year season trigger

    val headerKey = remember(data.name, data.year, data.url, data.type) {
        HeaderMetaKey.from(data)
    }

    LaunchedEffect(headerKey) {
        if (headerKey.isValid) {
            onEvent(
                com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent.OnFetchHeaderMeta(
                    cleanTitle = headerKey.cleanTitle,
                    year = headerKey.year,
                    season = headerKey.season,
                    isSeries = isSeriesLike(data.type),
                )
            )
        }
    }

    var isRightColumnHovered by remember { mutableStateOf(false) }
    var isRightColumnPinned by remember { mutableStateOf(com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(PreferenceKeys.DETAILS_RIGHT_COLUMN_PINNED) ?: false) }
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
                        androidx.compose.animation.Crossfade(
                            targetState = isLoading,
                            animationSpec = androidx.compose.animation.core.tween(350),
                            label = "header_loading_crossfade",
                        ) { loading ->
                            if (loading) {
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

                                val displayName = remember(data.name, uiState?.preloadedName, headerKey.cleanTitle) {
                                    headerKey.cleanTitle.takeIf { it.isNotBlank() }
                                        ?: data.name
                                            .takeIf { it.isNotBlank() }
                                            ?.let { TitleCleaner.clean(it) }
                                            ?.takeIf { it.isNotBlank() }
                                        ?: uiState?.preloadedName
                                            ?.let { TitleCleaner.clean(it) }
                                            ?.takeIf { it.isNotBlank() }
                                        ?: ""
                                }

                                val hasLogo = !activeLogoUrl.isNullOrBlank()
                                val titleContainerMinHeight = when {
                                    isCompactHeight -> 80.dp
                                    isNarrow -> 80.dp
                                    else -> 110.dp
                                }
                                Box(
                                    modifier = Modifier
                                        .widthIn(
                                            min = DesktopDimens.HeroLogoMinWidth,
                                            max = responsiveLogoMaxWidth,
                                        )
                                        .heightIn(min = titleContainerMinHeight, max = responsiveLogoMaxHeight),
                                    contentAlignment = if (isNarrow) Alignment.Center else Alignment.BottomStart,
                                ) {
                                    androidx.compose.animation.Crossfade(
                                        targetState = hasLogo,
                                        animationSpec = androidx.compose.animation.core.tween(350),
                                        label = "title_logo_crossfade",
                                        modifier = Modifier.fillMaxSize(),
                                    ) { showLogo ->
                                        if (showLogo) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = if (isNarrow) Alignment.Center else Alignment.BottomStart,
                                            ) {
                                                var isDarkLogo by remember(activeLogoUrl) {
                                                    mutableStateOf(com.lagradost.cloudstream3.desktop.utils.ImageUtils.isDarkLogoCached(activeLogoUrl) ?: false)
                                                }
                                                val platformContext = coil3.compose.LocalPlatformContext.current

                                                val logoRequest = remember(activeLogoUrl, platformContext) {
                                                    coil3.request.ImageRequest.Builder(platformContext)
                                                        .data(activeLogoUrl)
                                                        .size(1600, 800)
                                                        .crossfade(true)
                                                        .listener(
                                                            onSuccess = { _, result ->
                                                                val dark = com.lagradost.cloudstream3.desktop.utils.ImageUtils.isDarkImage(result.image)
                                                                com.lagradost.cloudstream3.desktop.utils.ImageUtils.cacheDarkLogo(activeLogoUrl, dark)
                                                                isDarkLogo = dark
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
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = if (isNarrow) Alignment.Center else Alignment.BottomStart,
                                            ) {
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
                                    }
                                }
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

                                // 1. Year / Multi-Year Span (prefer enriched -> headerKey -> raw)
                                val enrichedDate = uiState?.enrichedReleaseDate
                                val effectiveYear = uiState?.enrichedYear ?: headerKey.year ?: data.year
                                val yearSpan = if (!isMovie && !enrichedDate.isNullOrBlank() && enrichedDate.contains("–")) {
                                    enrichedDate
                                } else {
                                    effectiveYear?.toString()
                                }
                                yearSpan?.let { metaItems.add(it) }

                                // 2. Season (series only) — sourced from the cleaned title
                                if (isSeriesLike(data.type) && headerKey.season != null) {
                                    metaItems.add("S${headerKey.season}")
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
                                        text = metaItems.joinToString("   •   "),
                                        color = Color.White.copy(alpha = 0.88f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }

                                // 5. Normalized Content Rating Badge (e.g. TV-14, Rated R, 18+)
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
                        val cleanedPlot = remember(data.plot) { com.lagradost.cloudstream3.desktop.utils.TitleUtils.cleanPlot(data.plot) }
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
                                name = headerKey.cleanTitle.takeIf { it.isNotBlank() }
                                    ?: data.name.takeIf { it.isNotBlank() }
                                    ?: uiState?.preloadedName
                                    ?: "",
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
                                    com.lagradost.common.storage.DesktopWatchType.WATCHING -> Icons.Outlined.PlayArrow
                                    com.lagradost.common.storage.DesktopWatchType.COMPLETED -> Icons.Outlined.Check
                                    com.lagradost.common.storage.DesktopWatchType.ONHOLD -> Icons.Outlined.Pause
                                    com.lagradost.common.storage.DesktopWatchType.DROPPED -> Icons.Outlined.Close
                                    com.lagradost.common.storage.DesktopWatchType.PLANTOWATCH -> Icons.Outlined.BookmarkAdded
                                    com.lagradost.common.storage.DesktopWatchType.REWATCHING -> Icons.AutoMirrored.Filled.RotateRight
                                }
                            } else {
                                Icons.Outlined.BookmarkAdd
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
                                        modifier = Modifier.size(if (isButtonsNarrow) 22.dp else 26.dp),
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
                                                com.lagradost.common.storage.DesktopDataStore.setKey(PreferenceKeys.DETAILS_RIGHT_COLUMN_PINNED, nextPinned)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isRightColumnPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin sidebar",
                                            tint = if (isRightColumnPinned) Color.White else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp),
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

                                    // Prefer enriched release date, fall back to headerKey year, then raw year
                                    val relDate = uiState?.enrichedReleaseDate
                                        ?: headerKey.year?.toString()
                                        ?: data.year?.toString()
                                    if (!relDate.isNullOrBlank()) add("Release Date" to relDate)

                                    val status = uiState?.enrichedStatus
                                    if (!status.isNullOrBlank()) add("Status" to status)

                                    if (isSeriesLike(data.type) && headerKey.season != null) {
                                        add("Season" to "S${headerKey.season}")
                                    }
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