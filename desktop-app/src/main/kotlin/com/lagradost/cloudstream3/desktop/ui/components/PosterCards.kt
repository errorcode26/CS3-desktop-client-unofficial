package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.badges.FastRatingEnricher
import com.lagradost.cloudstream3.desktop.ui.badges.RatingSourcePolicy
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode
import com.lagradost.cloudstream3.fixUrlNull

@androidx.compose.runtime.Immutable
data class PosterCardStyle(
    val roundingDp: Int = 12,
    val titlePosition: com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition = com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW,
    val showRating: Boolean = true,
    val showQuality: Boolean = true,
    val showLanguage: Boolean = true,
    val hoverGlowEnabled: Boolean = true,
    val cardOpacity: Float = 1.0f,
    val shadowMultiplier: Float = 1.0f,
)

val LocalPosterCardStyle = staticCompositionLocalOf { PosterCardStyle() }

@Composable
fun rememberPosterCardStyle(): PosterCardStyle {
    val roundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val titlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val showRating by AppearanceConfig.showPosterRating.collectAsState()
    val showQuality by AppearanceConfig.showPosterQuality.collectAsState()
    val showLanguage by AppearanceConfig.showPosterLanguage.collectAsState()
    val hoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()
    val cardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()
    val shadowMultiplier by AppearanceConfig.elementShadowMultiplier.collectAsState()
    return remember(roundingDp, titlePosition, showRating, showQuality, showLanguage, hoverGlowEnabled, cardOpacity, shadowMultiplier) {
        PosterCardStyle(
            roundingDp = roundingDp,
            titlePosition = titlePosition,
            showRating = showRating,
            showQuality = showQuality,
            showLanguage = showLanguage,
            hoverGlowEnabled = hoverGlowEnabled,
            cardOpacity = cardOpacity,
            shadowMultiplier = shadowMultiplier,
        )
    }
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PosterCard(
    item: SearchResponse,
    provider: MainAPI?,
    modifier: Modifier = Modifier,
    itemWidth: androidx.compose.ui.unit.Dp? = null,
    aspectRatio: Float? = null,
    gridScale: String? = null,
    isHoverEnabled: Boolean = true,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    val style = LocalPosterCardStyle.current
    val currentGridScale by AppearanceConfig.gridScale.collectAsState()
    val effectiveGridScale = gridScale ?: currentGridScale
    val shape = remember(style.roundingDp) { RoundedCornerShape(style.roundingDp.dp) }
    val rawImgUrl = provider?.fixUrlNull(item.posterUrl) ?: item.posterUrl
    val imgUrl = remember(rawImgUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(rawImgUrl) }

    val effectiveAspectRatio = aspectRatio
        ?: if (item.type == com.lagradost.cloudstream3.TvType.Live || item.posterHeaders?.containsKey("landscape") == true) {
            16f / 9f
        } else {
            2f / 3f
        }

    val width = itemWidth ?: when (effectiveGridScale) {
        "Compact" -> if (effectiveAspectRatio > 1f) 220.dp else 150.dp
        "Large" -> if (effectiveAspectRatio > 1f) 320.dp else 220.dp
        else -> if (effectiveAspectRatio > 1f) 270.dp else 190.dp
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHoveredRaw by interactionSource.collectIsHoveredAsState()
    val isHovered = isHoveredRaw && isHoverEnabled

    val posterTitlePosition = style.titlePosition
    val showPosterRating = style.showRating
    val showPosterQuality = style.showQuality
    val showPosterLanguage = style.showLanguage
    val posterHoverGlowEnabled = style.hoverGlowEnabled

    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val isCleanMode by AppearanceConfig.cleanModeEnabled.collectAsState()
    val displayTitle = remember(item.name, autoCleanTitles, isCleanMode) {
        if (autoCleanTitles || isCleanMode) {
            CardTitleSanitizer.sanitize(item.name, autoClean = true).displayTitle
        } else {
            item.name
        }
    }

    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val primary = MaterialTheme.colorScheme.primary

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPlayClick by rememberUpdatedState(onPlayClick)
    val currentItem by rememberUpdatedState(item)
    val currentProvider by rememberUpdatedState(provider)

    Column(modifier = modifier.width(width)) {
        Box {
            if (isHovered && posterHoverGlowEnabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .background(primary.copy(alpha = 0.65f), shape),
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isHoverEnabled) Modifier.posterHoverEffect(shape) else Modifier)
                    .clip(shape)
                    .hoverable(interactionSource)
                    .onGloballyPositioned { coordinates ->
                        val newBounds = Rect(
                            offset = coordinates.positionInWindow(),
                            size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                        )
                        if (bounds != newBounds) {
                            bounds = newBounds
                        }
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Release) {
                                    if (event.button == PointerButton.Secondary) {
                                        GlobalContextMenuState.showForPoster(
                                            bounds = bounds,
                                            item = currentItem,
                                            provider = currentProvider,
                                            onClick = currentOnClick,
                                            onPlayClick = currentOnPlayClick,
                                        )
                                    } else if (event.button == PointerButton.Primary) {
                                        currentOnClick()
                                    }
                                }
                            }
                        }
                    },
                shape = shape,
                color = DesktopUi.SurfaceCard,
                tonalElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(effectiveAspectRatio),
                ) {
                    if (imgUrl != null) {
                        // Crop to fill the entire box with explicit downsampled memory footprint
                        val context = coil3.compose.LocalPlatformContext.current
                        val posterHeaders = item.posterHeaders
                        val imageRequest = remember(imgUrl, posterHeaders, provider?.mainUrl) {
                            coil3.request.ImageRequest.Builder(context)
                                .data(imgUrl)
                                .apply {
                                    val netHeaders = NetworkHeaders.Builder()
                                    if (!posterHeaders.isNullOrEmpty()) {
                                        posterHeaders.forEach { (k, v) ->
                                            if (!k.equals("landscape", ignoreCase = true)) {
                                                netHeaders[k] = v
                                            }
                                        }
                                    }
                                    val hasReferer = posterHeaders?.keys?.any { it.equals("Referer", ignoreCase = true) } == true
                                    if (!hasReferer && provider != null && provider.mainUrl.isNotBlank()) {
                                        val main = provider.mainUrl
                                        netHeaders["Referer"] = if (main.endsWith("/")) main else "$main/"
                                    }
                                    httpHeaders(netHeaders.build())
                                }
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // No image placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DesktopUi.SurfaceElevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                displayTitle.take(2).uppercase(),
                                color = DesktopUi.Accent,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) +
                            androidx.compose.animation.scaleIn(initialScale = 0.8f, animationSpec = androidx.compose.animation.core.tween(200)),
                        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) +
                            androidx.compose.animation.scaleOut(targetScale = 0.8f, animationSpec = androidx.compose.animation.core.tween(200)),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp).padding(start = 2.dp),
                            )
                        }
                    }

                    // Gradient at the bottom with the title
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered && posterTitlePosition == com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.INSIDE,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Transparent,
                                            0.35f to Color.Black.copy(alpha = 0.7f),
                                            1f to Color.Black.copy(alpha = 0.92f),
                                        ),
                                    ),
                                )
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = displayTitle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }

                    PosterBadges(
                        item = item,
                        showRating = showPosterRating,
                        showQuality = showPosterQuality,
                        showLanguage = showPosterLanguage,
                    )
                } // end box
            } // end surface
        } // end outer box

        if (posterTitlePosition == com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = displayTitle,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
fun BoxScope.PosterBadges(
    item: SearchResponse,
    showRating: Boolean,
    showQuality: Boolean,
    showLanguage: Boolean,
) {
    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()
    val ratingPolicy by CardMetadataConfig.ratingPolicy.collectAsState()

    val isAnime = item is com.lagradost.cloudstream3.AnimeSearchResponse
    val pluginHasSub = isAnime && (
        item.episodes[com.lagradost.cloudstream3.DubStatus.Subbed] != null ||
        item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Subbed) == true
    )
    val pluginHasDub = isAnime && (
        item.episodes[com.lagradost.cloudstream3.DubStatus.Dubbed] != null ||
        item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Dubbed) == true
    )

    val pluginQualityText = item.quality?.name?.let { qName ->
        when {
            qName == "FourK" || qName.contains("UHD", ignoreCase = true) -> "4K"
            qName.contains("BlueRay", ignoreCase = true) || qName.contains("BluRay", ignoreCase = true) -> "BD"
            qName.contains("HD", ignoreCase = true) -> "HD"
            else -> qName
        }
    }

    val meta = remember(item.name, pluginHasSub, pluginHasDub, pluginQualityText, autoCleanTitles, autoDetectSubDub, autoDetectQuality) {
        CardTitleSanitizer.sanitize(
            rawTitle = item.name,
            pluginHasSub = pluginHasSub,
            pluginHasDub = pluginHasDub,
            pluginQuality = pluginQualityText,
            autoClean = autoCleanTitles,
            autoDetectSubDub = autoDetectSubDub,
            autoDetectQuality = autoDetectQuality,
        )
    }

    // Rating Resolution - Use provider native score or cached rating without firing background queries
    val nativeScore = item.score?.let { score ->
        val v = score.toFloat(10).toDouble()
        if (v > 0.0) v else null
    }

    val verifiedRating = remember(meta.displayTitle) {
        FastRatingEnricher.getCachedRating(meta.displayTitle)
    }

    val effectiveRating = when (ratingPolicy) {
        RatingSourcePolicy.VERIFIED_ADDON -> verifiedRating ?: nativeScore
        RatingSourcePolicy.SCRAPER_NATIVE -> nativeScore
        RatingSourcePolicy.SMART_HYBRID -> nativeScore ?: verifiedRating
    }

    val shouldShowRating = showRatingBadges && effectiveRating != null && effectiveRating > 0.0
    val shouldShowSubDub = autoDetectSubDub && (meta.hasSub || meta.hasDub)
    val shouldShowQuality = autoDetectQuality && !meta.qualityText.isNullOrBlank()

    if (shouldShowRating || shouldShowSubDub || shouldShowQuality) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Top Left: Rating
            if (shouldShowRating) {
                DesktopBadgeComponents.RatingGoldBadge(rating = effectiveRating)
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Top Right: Language and Quality in a single Unified Capsule
            if (shouldShowSubDub || shouldShowQuality) {
                DesktopBadgeComponents.UnifiedMetadataCapsule(
                    hasSub = if (shouldShowSubDub) meta.hasSub else false,
                    hasDub = if (shouldShowSubDub) meta.hasDub else false,
                    quality = if (shouldShowQuality) meta.qualityText else null,
                )
            }
        }
    }
}
