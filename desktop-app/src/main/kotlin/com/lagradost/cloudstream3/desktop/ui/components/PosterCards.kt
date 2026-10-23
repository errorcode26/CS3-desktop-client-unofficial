package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.badges.FastRatingEnricher
import com.lagradost.cloudstream3.desktop.ui.badges.RatingSourcePolicy
import com.lagradost.cloudstream3.desktop.ui.badges.SanitizedCardMeta
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.PosterMetadataPosition
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTextAlignment
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition
import com.lagradost.cloudstream3.fixUrlNull

@androidx.compose.runtime.Immutable
data class PosterCardStyle(
    val roundingDp: Int = 12,
    val titlePosition: PosterTitlePosition = PosterTitlePosition.BELOW,
    val metadataPosition: PosterMetadataPosition = PosterMetadataPosition.BELOW_POSTER,
    val textAlignment: PosterTextAlignment = PosterTextAlignment.LEFT,
    val titleMaxLines: Int = 2,
    val showRating: Boolean = true,
    val showQuality: Boolean = true,
    val showLanguage: Boolean = true,
    val hoverGlowEnabled: Boolean = true,
    val cardOpacity: Float = 1.0f,
    val shadowMultiplier: Float = 1.0f,
    val depthEffectEnabled: Boolean = true,
)

val LocalPosterCardStyle = staticCompositionLocalOf { PosterCardStyle() }

@Composable
fun rememberPosterCardStyle(): PosterCardStyle {
    val roundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val titlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val metadataPosition by AppearanceConfig.posterMetadataPosition.collectAsState()
    val textAlignment by AppearanceConfig.posterTextAlignment.collectAsState()
    val titleMaxLines by AppearanceConfig.posterTitleMaxLines.collectAsState()
    val showRating by AppearanceConfig.showPosterRating.collectAsState()
    val showQuality by AppearanceConfig.showPosterQuality.collectAsState()
    val showLanguage by AppearanceConfig.showPosterLanguage.collectAsState()
    val hoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()
    val cardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()
    val shadowMultiplier by AppearanceConfig.elementShadowMultiplier.collectAsState()
    val depthEffectEnabled by AppearanceConfig.posterDepthEffectEnabled.collectAsState()
    return remember(roundingDp, titlePosition, metadataPosition, textAlignment, titleMaxLines, showRating, showQuality, showLanguage, hoverGlowEnabled, cardOpacity, shadowMultiplier, depthEffectEnabled) {
        PosterCardStyle(
            roundingDp = roundingDp,
            titlePosition = titlePosition,
            metadataPosition = metadataPosition,
            textAlignment = textAlignment,
            titleMaxLines = titleMaxLines,
            showRating = showRating,
            showQuality = showQuality,
            showLanguage = showLanguage,
            hoverGlowEnabled = hoverGlowEnabled,
            cardOpacity = cardOpacity,
            shadowMultiplier = shadowMultiplier,
            depthEffectEnabled = depthEffectEnabled,
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
    val posterMetadataPosition = style.metadataPosition
    val posterTextAlignment = style.textAlignment
    val posterTitleMaxLines = style.titleMaxLines
    val showPosterRating = style.showRating
    val showPosterQuality = style.showQuality
    val showPosterLanguage = style.showLanguage
    val posterHoverGlowEnabled = style.hoverGlowEnabled

    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()
    val ratingPolicy by CardMetadataConfig.ratingPolicy.collectAsState()
    val isCleanMode by AppearanceConfig.cleanModeEnabled.collectAsState()

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
            qName.contains("BlueRay", ignoreCase = true) || qName.contains("BluRay", ignoreCase = true) -> "1080p"
            qName.contains("HD", ignoreCase = true) -> "HD"
            else -> qName
        }
    }

    val meta = remember(item.name, pluginHasSub, pluginHasDub, pluginQualityText, autoCleanTitles, autoDetectSubDub, autoDetectQuality, isCleanMode) {
        CardTitleSanitizer.sanitize(
            rawTitle = item.name,
            pluginHasSub = pluginHasSub,
            pluginHasDub = pluginHasDub,
            pluginQuality = pluginQualityText,
            autoClean = autoCleanTitles || isCleanMode,
            autoDetectSubDub = autoDetectSubDub,
            autoDetectQuality = autoDetectQuality,
        )
    }

    val displayTitle = if (autoCleanTitles || isCleanMode) meta.displayTitle else item.name

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
                            var downPos: androidx.compose.ui.geometry.Offset? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        downPos = event.changes.firstOrNull()?.position
                                    }
                                    PointerEventType.Release -> {
                                        val isConsumed = event.changes.any { it.isConsumed }
                                        val upPos = event.changes.firstOrNull()?.position
                                        val hasDragged = if (downPos != null && upPos != null) {
                                            kotlin.math.hypot(upPos.x - downPos.x, upPos.y - downPos.y) > viewConfiguration.touchSlop
                                        } else {
                                            false
                                        }

                                        if (!isConsumed && !hasDragged) {
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
                                        downPos = null
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
                        .aspectRatio(effectiveAspectRatio)
                        .posterDepthEffect(shape, enabled = style.depthEffectEnabled),
                ) {
                    if (imgUrl != null) {
                        // Crop to fill the entire box with explicit downsampled memory footprint
                        val context = coil3.compose.LocalPlatformContext.current
                        val posterHeaders = item.posterHeaders
                        val effectiveProvider = provider ?: com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(item.apiName)
                        val mainUrl = effectiveProvider?.mainUrl
                        val imageRequest = remember(imgUrl, posterHeaders, mainUrl) {
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
                                    if (!hasReferer && !mainUrl.isNullOrBlank()) {
                                        netHeaders["Referer"] = if (mainUrl.endsWith("/")) mainUrl else "$mainUrl/"
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

                    if (posterMetadataPosition != PosterMetadataPosition.BELOW_POSTER) {
                        val badgesContent: @Composable () -> Unit = {
                            PosterBadges(
                                item = item,
                                showRating = showPosterRating,
                                showQuality = if (posterMetadataPosition == PosterMetadataPosition.HYBRID) false else showPosterQuality,
                                showLanguage = if (posterMetadataPosition == PosterMetadataPosition.HYBRID) false else showPosterLanguage,
                                effectiveRating = effectiveRating,
                                meta = meta,
                            )
                        }

                        if (posterMetadataPosition == PosterMetadataPosition.STEALTH_HOVER) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isHovered,
                                enter = androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.fadeOut(),
                            ) {
                                badgesContent()
                            }
                        } else {
                            badgesContent()
                        }
                    }
                } // end box
            } // end surface
        } // end outer box

        if (posterTitlePosition == PosterTitlePosition.BELOW) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = if (posterTextAlignment == PosterTextAlignment.LEFT) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Text(
                    text = displayTitle,
                    maxLines = posterTitleMaxLines,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (posterTextAlignment == PosterTextAlignment.LEFT) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 15.sp,
                )

                if (posterMetadataPosition == PosterMetadataPosition.BELOW_POSTER ||
                    posterMetadataPosition == PosterMetadataPosition.HYBRID
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    DesktopBadgeComponents.BelowPosterMetadataRow(
                        year = meta.year,
                        rating = if (posterMetadataPosition == PosterMetadataPosition.BELOW_POSTER && showPosterRating) effectiveRating else null,
                        quality = if (showPosterQuality) meta.qualityText else null,
                        hasSub = if (showPosterLanguage) meta.hasSub else false,
                        hasDub = if (showPosterLanguage) meta.hasDub else false,
                        alignment = posterTextAlignment,
                    )
                }
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
    effectiveRating: Double? = null,
    meta: SanitizedCardMeta? = null,
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
            qName.contains("BlueRay", ignoreCase = true) || qName.contains("BluRay", ignoreCase = true) -> "1080p"
            qName.contains("HD", ignoreCase = true) -> "HD"
            else -> qName
        }
    }

    val resolvedMeta = meta ?: remember(item.name, pluginHasSub, pluginHasDub, pluginQualityText, autoCleanTitles, autoDetectSubDub, autoDetectQuality) {
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

    val resolvedRating = effectiveRating ?: run {
        val nativeScore = item.score?.let { score ->
            val v = score.toFloat(10).toDouble()
            if (v > 0.0) v else null
        }
        val verifiedRating = remember(resolvedMeta.displayTitle) {
            FastRatingEnricher.getCachedRating(resolvedMeta.displayTitle)
        }
        when (ratingPolicy) {
            RatingSourcePolicy.VERIFIED_ADDON -> verifiedRating ?: nativeScore
            RatingSourcePolicy.SCRAPER_NATIVE -> nativeScore
            RatingSourcePolicy.SMART_HYBRID -> nativeScore ?: verifiedRating
        }
    }

    val shouldShowRating = showRating && showRatingBadges && resolvedRating != null && resolvedRating > 0.0
    val shouldShowSubDub = showLanguage && autoDetectSubDub && (resolvedMeta.hasSub || resolvedMeta.hasDub)
    val shouldShowQuality = showQuality && autoDetectQuality && !resolvedMeta.qualityText.isNullOrBlank()

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
                DesktopBadgeComponents.RatingGoldBadge(rating = resolvedRating)
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Top Right: Language and Quality in a single Unified Capsule
            if (shouldShowSubDub || shouldShowQuality) {
                DesktopBadgeComponents.UnifiedMetadataCapsule(
                    hasSub = if (shouldShowSubDub) resolvedMeta.hasSub else false,
                    hasDub = if (shouldShowSubDub) resolvedMeta.hasDub else false,
                    quality = if (shouldShowQuality) resolvedMeta.qualityText else null,
                )
            }
        }
    }
}
