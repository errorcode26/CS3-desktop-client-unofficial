package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Person
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
        val isFallback = data.backgroundPosterUrl.isNullOrBlank() || data.backgroundPosterUrl == data.posterUrl

        val screensaverEnabled by AppearanceConfig.screensaverEnabled.collectAsState()
        val screenshots = uiState?.screenshots ?: emptyList()
        var currentScreenshotIndex by remember { mutableStateOf(-1) }

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
            uiState?.enrichedBackdropUrl?.takeIf { it.isNotBlank() }
                ?: data.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                ?: data.posterUrl?.takeIf { it.isNotBlank() }
                ?: provider.fixUrlNull(data.backgroundPosterUrl) ?: provider.fixUrlNull(data.posterUrl)
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
) {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    var isRightColumnHovered by remember { mutableStateOf(false) }
    var isRightColumnPinned by remember { mutableStateOf(false) }
    val rightColumnAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isRightColumnPinned || isRightColumnHovered) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
    )
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart,
    ) {
        val isNarrow = maxWidth < 1100.dp
        val isButtonsNarrow = maxWidth < 600.dp

        AdaptiveMetadataLayout(
            isNarrow = isNarrow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isNarrow) 24.dp else 64.dp, end = if (isNarrow) 24.dp else 64.dp, bottom = 108.dp, top = if (isNarrow) 80.dp else 160.dp),
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
                                        max = DesktopDimens.HeroLogoMaxWidth,
                                    )
                                    .heightIn(max = DesktopDimens.HeroLogoMaxHeight),
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
                                else -> data.type?.name?.replace(Regex("(?i)tv"), "TV")
                            }
                            if (!typeStr.isNullOrBlank()) {
                                Text(text = typeStr, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            val finalDuration = uiState?.enrichedDuration ?: data.duration
                            finalDuration?.takeIf { it > 0 }?.let { dur ->
                                val mins = if (dur > 1000) dur / 60 else dur // Handle seconds if provider gives seconds
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
                            text = finalTags?.take(6)?.joinToString(" • ") ?: "",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (!isLoading && !data.plot.isNullOrBlank()) {
                        Text(
                            text = data.plot ?: "No plot available",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    val bookmarkId = "${provider.name}_${data.url.hashCode()}"
                    val allBookmarks = uiState?.bookmarks ?: emptyMap()
                    val currentBookmark = allBookmarks[bookmarkId]
                    var showBookmarkMenu by remember { mutableStateOf(false) }

                    val libraryButton: @Composable (Modifier) -> Unit = { mod ->
                        Box(modifier = mod) {
                            Box(
                                modifier = Modifier
                                    .height(56.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (currentBookmark != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.White.copy(alpha = 0.18f)
                                        },
                                    )
                                    .border(
                                        1.2.dp,
                                        if (currentBookmark != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.White.copy(alpha = 0.35f)
                                        },
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { showBookmarkMenu = true }
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Library,
                                        contentDescription = "Library",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val text = currentBookmark?.let { b ->
                                        com.lagradost.common.storage.DesktopWatchType.entries.find { type -> type.id == b.watchType }?.stringRes
                                    } ?: "Add to Library"
                                    Text(
                                        text = text,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
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
                                                name = data.name,
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

                    if (isButtonsNarrow) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            heroAction(Modifier.fillMaxWidth())
                            downloadAction?.invoke(Modifier.fillMaxWidth())
                            libraryButton(Modifier.fillMaxWidth())
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
                                libraryButton(Modifier.weight(1f))
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
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = if (isNarrow) Alignment.CenterHorizontally else Alignment.Start,
                    ) {
                        val textShadow = androidx.compose.ui.text.TextStyle(
                            shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                        )

                        // Pin Toggle Row
                        if (!isNarrow) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "SIDEBAR",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = Color.White.copy(alpha = 0.4f),
                                        shadow = textShadow.shadow,
                                    ),
                                    fontWeight = FontWeight.Bold,
                                )
                                IconButton(
                                    onClick = { isRightColumnPinned = !isRightColumnPinned },
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

                        // Photos Preview
                        val finalScreenshots = screenshots ?: uiState?.screenshots
                        if (!finalScreenshots.isNullOrEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Photos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = textShadow, modifier = Modifier.clickable { onPhotosClick() })
                                var currentPhotoIndex by remember { mutableStateOf(0) }
                                val displayPhotos = finalScreenshots.take(10)

                                LaunchedEffect(displayPhotos) {
                                    if (displayPhotos.size > 1) {
                                        while (true) {
                                            kotlinx.coroutines.delay(4000)
                                            currentPhotoIndex = (currentPhotoIndex + 1) % displayPhotos.size
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val imageWidth = (130f * 16f / 9f).dp
                                    Box(
                                        modifier = Modifier
                                            .height(160.dp) // Taller to avoid clipping the bottom cards' shadows and offsets
                                            .width(imageWidth + 80.dp) // Wider to prevent horizontal clipping
                                            .clickable { onPhotosClick() },
                                        contentAlignment = Alignment.CenterStart, // Align everything to the left side of the container
                                    ) {
                                        androidx.compose.animation.AnimatedContent(
                                            targetState = currentPhotoIndex,
                                            transitionSpec = {
                                                // Slide the old stack out to the left and up (like peeling/tossing it away)
                                                (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(600)))
                                                    .togetherWith(
                                                        androidx.compose.animation.slideOutHorizontally(
                                                            animationSpec = androidx.compose.animation.core.tween(600),
                                                            targetOffsetX = { -it },
                                                        ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(600)),
                                                    )
                                            },
                                            label = "photos_carousel",
                                        ) { page ->
                                            val url = displayPhotos.getOrNull(page)
                                            val next1Url = if (displayPhotos.size > 1) displayPhotos.getOrNull((page + 1) % displayPhotos.size) else null
                                            val next2Url = if (displayPhotos.size > 2) displayPhotos.getOrNull((page + 2) % displayPhotos.size) else null

                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                                // Bottom Card (peeking bottom-right)
                                                if (next2Url != null) {
                                                    coil3.compose.AsyncImage(
                                                        model = next2Url,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .height(130.dp)
                                                            .width(imageWidth)
                                                            .offset(x = 32.dp, y = 16.dp)
                                                            .shadow(8.dp, RoundedCornerShape(12.dp))
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .alpha(0.4f),
                                                        contentScale = ContentScale.Crop,
                                                    )
                                                }
                                                // Middle Card (peeking middle-right)
                                                if (next1Url != null) {
                                                    coil3.compose.AsyncImage(
                                                        model = next1Url,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .height(130.dp)
                                                            .width(imageWidth)
                                                            .offset(x = 16.dp, y = 8.dp)
                                                            .shadow(12.dp, RoundedCornerShape(12.dp))
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .alpha(0.7f),
                                                        contentScale = ContentScale.Crop,
                                                    )
                                                }
                                                // Top Card
                                                if (url != null) {
                                                    coil3.compose.AsyncImage(
                                                        model = url,
                                                        contentDescription = "Screenshot",
                                                        modifier = Modifier
                                                            .height(130.dp)
                                                            .width(imageWidth)
                                                            .shadow(16.dp, RoundedCornerShape(12.dp))
                                                            .clip(RoundedCornerShape(12.dp)),
                                                        contentScale = ContentScale.Crop,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (displayPhotos.size > 1) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.width((130f * 16f / 9f).dp), // Approximately match width of image
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            displayPhotos.forEachIndexed { index, _ ->
                                                val isSelected = index == currentPhotoIndex
                                                val width by androidx.compose.animation.core.animateDpAsState(if (isSelected) 16.dp else 6.dp, label = "indicator_width")
                                                val color by androidx.compose.animation.animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.3f), label = "indicator_color")
                                                Box(
                                                    modifier = Modifier
                                                        .height(3.dp)
                                                        .width(width)
                                                        .clip(RoundedCornerShape(1.5.dp))
                                                        .background(color),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Stars Preview
                        val cast = uiState?.enrichedActors ?: data.actors
                        if (!cast.isNullOrEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Stars", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = textShadow, modifier = Modifier.clickable { onCastClick() })
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    cast.take(4).forEach { actor ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            coil3.compose.AsyncImage(
                                                model = actor.actor.image,
                                                contentDescription = actor.actor?.name,
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .shadow(12.dp, CircleShape)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.1f))
                                                    .clickable { onActorClick(actor) },
                                                contentScale = ContentScale.Crop,
                                            )
                                            Text(
                                                text = actor.actor?.name?.split(" ")?.firstOrNull() ?: "",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 80.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                style = textShadow,
                                            )
                                        }
                                    }
                                    if (cast.size > 4) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .shadow(12.dp, CircleShape)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .clickable { onCastClick() },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("+${cast.size - 4}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = textShadow)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun InfoRowItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
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
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            val invertedMap = remember { androidx.compose.runtime.mutableStateMapOf<ActorData, Boolean>() }

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

            if (directors.isNotEmpty() && cast.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
            }

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
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
    ) {
        Box(modifier = Modifier.size(130.dp)) {
            val actorImg = provider.fixUrlNull(mainImgRaw)
            if (actorImg != null) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(actorImg)
                        .size(256, 256)
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
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            val voiceActorImg = cornerImgRaw?.let { provider.fixUrlNull(it) }
            if (voiceActorImg != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(3.dp)
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
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            mainName ?: "",
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (!subName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subName,
                style = MaterialTheme.typography.bodyMedium,
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsStatsSection(
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Text(
                text = "Information & Production",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))

            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                if (seasons != null && seasons > 0) {
                    val epStr = if (episodes != null && episodes > 0) " ($episodes Episodes)" else ""
                    InfoStatCard(label = "Seasons", value = "$seasons ${if (seasons == 1) "Season" else "Seasons"}$epStr")
                } else if (episodes != null && episodes > 0) {
                    InfoStatCard(label = "Episodes", value = "$episodes ${if (episodes == 1) "Episode" else "Episodes"}")
                }
                if (!status.isNullOrBlank()) {
                    InfoStatCard(label = "Status", value = status)
                }
                if (networks.isNotEmpty()) {
                    val label = if (networks.size > 1) "Networks" else "Network"
                    InfoStatCard(label = label, value = networks.joinToString(", "))
                }
                if (studios.isNotEmpty()) {
                    val label = if (studios.size > 1) "Production Companies" else "Production Company"
                    InfoStatCard(label = label, value = studios.joinToString(", "))
                }
                if (budget != null) {
                    InfoStatCard(label = "Budget", value = formatCurrency(budget))
                }
                if (revenue != null) {
                    InfoStatCard(label = "Box Office Revenue", value = formatCurrency(revenue))
                }
                if (!relDate.isNullOrBlank()) {
                    InfoStatCard(label = "Release Date", value = relDate)
                }
                if (!country.isNullOrBlank() || !lang.isNullOrBlank()) {
                    val combined = listOfNotNull(country, lang).joinToString(" • ")
                    InfoStatCard(label = "Origin", value = combined)
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

@Composable
private fun InfoStatCard(label: String, value: String) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .heightIn(min = 80.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
