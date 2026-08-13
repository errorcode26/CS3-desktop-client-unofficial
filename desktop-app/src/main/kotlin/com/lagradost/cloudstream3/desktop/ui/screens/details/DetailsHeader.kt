package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                    data.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                        ?: data.posterUrl?.takeIf { it.isNotBlank() }
                        ?: provider.fixUrlNull(data.backgroundPosterUrl)
                        ?: provider.fixUrlNull(data.posterUrl)
                } else null
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
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(targetBgUrl)
                        .size(2560, 1440)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
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
        // Prevent crash on unbounded height (Dp.Infinity) when inside LazyColumn
        val actualMaxHeight = if (maxHeight == androidx.compose.ui.unit.Dp.Infinity) 800.dp else maxHeight
        
        val isNarrow = maxWidth < 1100.dp
        val isButtonsNarrow = maxWidth < 600.dp
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
            isCompactHeight -> 32.dp
            isMediumHeight -> if (isNarrow) 48.dp else 100.dp
            else -> if (isNarrow) 80.dp else 120.dp
        }
        
        // A consistent, sensible bottom buffer that anchors the content closely to the bottom
        // on both small and large screens without creating a massive empty void.
        // It is set to 120.dp to perfectly clear the "Continue Watching" progress bar which sits at 64.dp.
        val responsiveBottomPadding = 120.dp

        AdaptiveMetadataLayout(
            isNarrow = isNarrow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isNarrow) 24.dp else 64.dp, end = if (isNarrow) 24.dp else 64.dp, bottom = responsiveBottomPadding, top = responsiveTopPadding),
            mainContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isLoading) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            if (!activeLogoUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(
                                            min = DesktopDimens.HeroLogoMinWidth,
                                            max = responsiveLogoMaxWidth,
                                        )
                                        .heightIn(max = responsiveLogoMaxHeight),
                                    contentAlignment = Alignment.BottomStart,
                                ) {
                                    val logoRequest = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                        .data(activeLogoUrl)
                                        .size(1600, 800)
                                        .crossfade(true)
                                        .build()
                                    val displayName = data.name.takeIf { it.isNotBlank() } ?: uiState?.preloadedName ?: ""
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
                                        alignment = Alignment.BottomStart,
                                        colorFilter = DesktopDimens.LogoShadowFilter,
                                    )
                                    coil3.compose.SubcomposeAsyncImage(
                                        model = logoRequest,
                                        contentDescription = displayName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                        alignment = Alignment.BottomStart,
                                        error = {
                                            Text(
                                                text = displayName,
                                                style = MaterialTheme.typography.displayLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                            )
                                        },
                                    )
                                }
                            } else {
                                val displayName = data.name.takeIf { it.isNotBlank() } ?: uiState?.preloadedName ?: ""
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }
                        }
                        val activeTagline = uiState?.enrichedTagline?.takeIf { it.isNotBlank() }
                        if (activeTagline != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "\"$activeTagline\"",
                                style = MaterialTheme.typography.titleMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = if (isNarrow) 0.dp else 4.dp),
                                textAlign = if (isNarrow) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (!isLoading) {
                            FlowRow(
                                horizontalArrangement = if (isNarrow) Arrangement.Center else Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = if (isNarrow) Modifier.fillMaxWidth() else Modifier,
                            ) {
                                val finalYear = uiState?.enrichedYear ?: data.year
                                finalYear?.let {
                                    Text(text = it.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                data.contentRating?.let { rating ->
                                    Box(modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text(text = rating, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
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
                                if (!typeStr.isNullOrBlank()) {
                                    Text(text = typeStr, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
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
                                    Text(text = durationStr, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                data.score?.takeIf { it.toFloat(10) > 0f }?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(text = it.toString(10), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        val finalTags = uiState?.enrichedTags ?: data.tags
                        if (!isLoading && !finalTags.isNullOrEmpty()) {
                            Text(
                                text = finalTags.take(6).joinToString(" • "),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
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
                            )
                        }

                    Spacer(modifier = Modifier.height(24.dp))

                    val bookmarkId = "${provider.name}_${data.url.hashCode()}"
                    val allBookmarks = uiState?.bookmarks ?: emptyMap()
                    val currentBookmark = allBookmarks[bookmarkId]
                    var showBookmarkMenu by remember { mutableStateOf(false) }

                    val libraryButton: @Composable (Modifier) -> Unit = { mod ->
                        Box(modifier = mod) {
                            androidx.compose.material3.IconButton(
                                onClick = { showBookmarkMenu = true },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        if (currentBookmark != null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .border(
                                        1.2.dp,
                                        if (currentBookmark != null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                                        RoundedCornerShape(12.dp),
                                    ),
                            ) {
                                Icon(
                                    imageVector = if (currentBookmark != null) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = "Library",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = showBookmarkMenu,
                                onDismissRequest = { showBookmarkMenu = false },
                                modifier = Modifier
                                    .background(DesktopUi.SurfaceElevated, RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                            ) {
                                Text(
                                    "Add to Library",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                                com.lagradost.common.storage.DesktopWatchType.entries.forEach { type ->
                                    val isSelected = currentBookmark?.watchType == type.id
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                type.stringRes,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = {
                                            val newBookmark = DesktopBookmark(
                                                id = bookmarkId,
                                                name = data.name.takeIf { it.isNotBlank() } ?: uiState?.preloadedName ?: "",
                                                url = data.url,
                                                apiName = provider.name,
                                                posterUrl = data.posterUrl,
                                                watchType = type.id,
                                            )
                                            com.lagradost.cloudstream3.desktop.repo.BookmarksRepository.addBookmark(newBookmark)
                                            showBookmarkMenu = false
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent),
                                    )
                                }
                                if (currentBookmark != null) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                                    DropdownMenuItem(
                                        text = {
                                            Text("Remove from Library", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                        },
                                        onClick = {
                                            com.lagradost.cloudstream3.desktop.repo.BookmarksRepository.removeBookmark(bookmarkId)
                                            showBookmarkMenu = false
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                                    )
                                }
                            }
                        }
                    }

                    val activeTrailerUrl = uiState?.enrichedTrailerUrl
                        ?: uiState?.enrichedTrailers?.firstOrNull()?.url

                    val trailerButton: (@Composable (Modifier) -> Unit)? = if (!activeTrailerUrl.isNullOrBlank() && onTrailerClick != null) {
                        { mod ->
                            OutlinedButton(
                                onClick = { onTrailerClick(activeTrailerUrl) },
                                modifier = mod.height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.18f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Trailer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    } else null

                    if (isButtonsNarrow) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            heroAction(Modifier.fillMaxWidth())
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                libraryButton(Modifier)
                                trailerButton?.invoke(Modifier.weight(1f))
                            }
                            downloadAction?.invoke(Modifier.fillMaxWidth())
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    heroAction(Modifier.fillMaxWidth())
                                }
                                libraryButton(Modifier)
                                trailerButton?.invoke(Modifier)
                            }
                            downloadAction?.invoke(Modifier.fillMaxWidth())
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
                                        isRightColumnPinned = !isRightColumnPinned
                                        com.lagradost.common.storage.DesktopDataStore.setKey("DETAILS_RIGHT_COLUMN_PINNED", isRightColumnPinned)
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

    if (cast.isNotEmpty() || directors.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val invertedMap = remember { androidx.compose.runtime.mutableStateMapOf<ActorData, Boolean>() }

            if (cast.isNotEmpty()) {
                Text(
                    text = "Cast",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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

            if (directors.isNotEmpty() && cast.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (directors.isNotEmpty()) {
                val headerTitle = if (directors.any { it.roleString?.equals("Creator", ignoreCase = true) == true }) "Directors & Creators" else "Directors"
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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

@Composable
private fun ActorCard(
    actor: ActorData,
    provider: MainAPI,
    isInverted: Boolean,
    onInvertToggle: () -> Unit,
    onClick: () -> Unit,
) {
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Box(modifier = Modifier.size(170.dp)) {
            val actorImg = provider.fixUrlNull(mainImgRaw)
            if (actorImg != null) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(actorImg)
                        .size(512, 512)
                        .build(),
                    contentDescription = mainName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = mainName,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            val voiceActorImg = cornerImgRaw?.let { provider.fixUrlNull(it) }
            if (voiceActorImg != null) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onInvertToggle() },
                ) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                            .data(voiceActorImg)
                            .size(128, 128)
                            .build(),
                        contentDescription = subName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            mainName,
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (!subName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        val roleStr = actor.role?.name ?: actor.roleString
        if (!roleStr.isNullOrBlank()) {
            Text(
                roleStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsStatsSection(
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    modifier: Modifier = Modifier,
) {
    if (uiState == null) return

    val budget = uiState.enrichedBudget
    val revenue = uiState.enrichedRevenue
    val networks = uiState.enrichedNetworks
    val studios = uiState.enrichedStudios
    val country = uiState.enrichedCountry
    val lang = uiState.enrichedOriginalLanguage
    val relDate = uiState.enrichedReleaseDate
    val status = uiState.enrichedStatus
    val seasons = uiState.enrichedSeasonsCount
    val episodes = uiState.enrichedEpisodesCount

    val hasStats = budget != null || revenue != null || networks.isNotEmpty() || studios.isNotEmpty() || !country.isNullOrBlank() || !lang.isNullOrBlank() || !status.isNullOrBlank() || (seasons ?: 0) > 0 || (episodes ?: 0) > 0

    if (hasStats) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (seasons != null && seasons > 0) {
                    val epStr = if (episodes != null && episodes > 0) " ($episodes Episodes)" else ""
                    InfoStatItem(label = "Seasons", value = "$seasons ${if (seasons == 1) "Season" else "Seasons"}$epStr")
                } else if (episodes != null && episodes > 0) {
                    InfoStatItem(label = "Episodes", value = "$episodes ${if (episodes == 1) "Episode" else "Episodes"}")
                }
                if (!status.isNullOrBlank()) {
                    InfoStatItem(label = "Status", value = status)
                }
                if (!relDate.isNullOrBlank()) {
                    InfoStatItem(label = "Release Date", value = relDate)
                }
                if (!country.isNullOrBlank() || !lang.isNullOrBlank()) {
                    val combined = listOfNotNull(country, lang).joinToString(" • ")
                    InfoStatItem(label = "Origin", value = combined)
                }
                if (budget != null && budget > 0) {
                    InfoStatItem(label = "Budget", value = formatCurrency(budget))
                }
                if (revenue != null && revenue > 0) {
                    InfoStatItem(label = "Box Office", value = formatCurrency(revenue))
                }
                if (networks.isNotEmpty()) {
                    val label = if (networks.size > 1) "Networks" else "Network"
                    InfoStatItem(label = label, value = networks.joinToString(", "))
                }
                if (studios.isNotEmpty()) {
                    val label = if (studios.size > 1) "Production Companies" else "Production Company"
                    InfoStatItem(label = label, value = studios.joinToString(", "))
                }
            }
        }
    }
}

@Composable
private fun InfoStatItem(label: String, value: String) {
    Column(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 240.dp)
            .padding(end = 48.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCurrency(amount: Long): String {
    return when {
        amount >= 1_000_000_000 -> "$${String.format("%.1f", amount.toDouble() / 1_000_000_000)} Billion"
        amount >= 1_000_000 -> "$${String.format("%.1f", amount.toDouble() / 1_000_000)} Million"
        else -> "$${java.text.NumberFormat.getIntegerInstance().format(amount)}"
    }
}
