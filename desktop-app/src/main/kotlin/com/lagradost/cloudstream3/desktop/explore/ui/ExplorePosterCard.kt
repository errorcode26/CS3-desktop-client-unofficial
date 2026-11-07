package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
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
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.getTextShadow
import com.lagradost.cloudstream3.desktop.ui.components.posterDepthEffect
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.PosterMetadataPosition
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTextAlignment
import com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
internal fun ExplorePosterCard(
    item: ExploreItem,
    watchHistory: WatchHistory? = null,
    autoCleanTitles: Boolean = true,
    isCatalogLandscape: Boolean = false,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val sanitized = remember(item.name, autoCleanTitles, autoDetectSubDub, autoDetectQuality) {
        CardTitleSanitizer.sanitize(
            rawTitle = item.name,
            autoClean = autoCleanTitles,
            autoDetectSubDub = autoDetectSubDub,
            autoDetectQuality = autoDetectQuality,
        )
    }
    val roundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val posterTitlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val metadataPosition by AppearanceConfig.posterMetadataPosition.collectAsState()
    val posterTextAlignment by AppearanceConfig.posterTextAlignment.collectAsState()
    val posterTitleMaxLines by AppearanceConfig.posterTitleMaxLines.collectAsState()
    val showPosterRating by AppearanceConfig.showPosterRating.collectAsState()
    val showPosterQuality by AppearanceConfig.showPosterQuality.collectAsState()
    val showPosterLanguage by AppearanceConfig.showPosterLanguage.collectAsState()
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()
    val posterDepthEffectEnabled by AppearanceConfig.posterDepthEffectEnabled.collectAsState()
    val shape = remember(roundingDp) { RoundedCornerShape(roundingDp.dp) }
    val primary = MaterialTheme.colorScheme.primary

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val hasProgress = watchHistory != null && watchHistory.position > 0L && watchHistory.duration > 0L
    val isCompleted = watchHistory != null && watchHistory.duration > 0L && PlayerLinkHandler.isCompleted(watchHistory.position, watchHistory.duration)
    val progress = if (watchHistory != null && watchHistory.position > 0L && watchHistory.duration > 0L) {
        if (isCompleted) 1f else (watchHistory.position.toFloat() / watchHistory.duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val seText = if (watchHistory != null && (watchHistory.season != null || watchHistory.episode != null)) {
        listOfNotNull(
            watchHistory.season?.let { "S$it" },
            watchHistory.episode?.let { "E$it" },
        ).joinToString("")
    } else {
        ""
    }

    // Deterministic aspect ratio derived from catalog & media type (no async image jumping)
    val isLandscape = remember(item.posterShape, item.type, isCatalogLandscape) {
        when {
            item.posterShape.equals("landscape", ignoreCase = true) -> true
            item.type.equals("tv", ignoreCase = true) || item.type.equals("channel", ignoreCase = true) -> true
            isCatalogLandscape -> true
            else -> false
        }
    }
    val effectiveAspectRatio = if (isLandscape) 16f / 9f else 2f / 3f

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            // Ambient primary glow bloom on hover (matching Home's PosterCard)
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
                    .posterHoverEffect(shape)
                    .clip(shape)
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
                shape = shape,
                color = theme.SurfaceCard,
                tonalElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(effectiveAspectRatio)
                        .posterDepthEffect(shape, enabled = posterDepthEffectEnabled),
                ) {
                    AsyncImage(
                        model = item.posterUrl ?: item.backgroundUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Dark scrim on hover
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                        )
                    }

                    // Frosted glass play button (matching Home's 56dp aesthetic)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
                        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
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

                    // Inside Title Overlay (on hover if posterTitlePosition == INSIDE)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered && posterTitlePosition == PosterTitlePosition.INSIDE,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = fadeIn(),
                        exit = fadeOut(),
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
                                    text = sanitized.displayTitle,
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

                    // Top Badges Row
                    if (metadataPosition != PosterMetadataPosition.BELOW_POSTER) {
                        val badgesContent: @Composable () -> Unit = {
                            val shouldShowRating = showPosterRating && (item.rating ?: 0.0) > 0.0
                            val shouldShowSubDub = (metadataPosition != PosterMetadataPosition.HYBRID) && showPosterLanguage && (sanitized.hasSub || sanitized.hasDub)
                            val shouldShowQuality = (metadataPosition != PosterMetadataPosition.HYBRID) && showPosterQuality && !sanitized.qualityText.isNullOrBlank()

                            if (shouldShowRating || shouldShowSubDub || shouldShowQuality) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 7.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Top Left: Rating
                                    if (shouldShowRating) {
                                        DesktopBadgeComponents.RatingGoldBadge(rating = item.rating ?: 0.0)
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    // Top Right: Language and Quality in a single Unified Capsule
                                    if (shouldShowSubDub || shouldShowQuality) {
                                        DesktopBadgeComponents.UnifiedMetadataCapsule(
                                            hasSub = if (shouldShowSubDub) sanitized.hasSub else false,
                                            hasDub = if (shouldShowSubDub) sanitized.hasDub else false,
                                            quality = if (shouldShowQuality) sanitized.qualityText else null,
                                        )
                                    }
                                }
                            }
                        }

                        if (metadataPosition == PosterMetadataPosition.STEALTH_HOVER) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isHovered,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                badgesContent()
                            }
                        } else {
                            badgesContent()
                        }
                    }

                    // Bottom Watch Indicators
                    if (isCompleted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Watched",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = "Watched",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    } else if (hasProgress && progress > 0f) {
                        if (seText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 6.dp, bottom = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = seText,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .background(Color.Black.copy(alpha = 0.6f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }

        // Title BELOW (matches Home's compact layout and text shadow)
        if (posterTitlePosition == PosterTitlePosition.BELOW) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = if (posterTextAlignment == PosterTextAlignment.LEFT) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Text(
                    text = sanitized.displayTitle,
                    maxLines = posterTitleMaxLines,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (posterTextAlignment == PosterTextAlignment.LEFT) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = getTextShadow(),
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 15.sp,
                )

                if (metadataPosition == PosterMetadataPosition.BELOW_POSTER ||
                    metadataPosition == PosterMetadataPosition.HYBRID
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    DesktopBadgeComponents.BelowPosterMetadataRow(
                        year = sanitized.year ?: item.releaseYear?.toIntOrNull(),
                        rating = if (metadataPosition == PosterMetadataPosition.BELOW_POSTER && showPosterRating) item.rating else null,
                        quality = if (showPosterQuality) sanitized.qualityText else null,
                        hasSub = if (showPosterLanguage) sanitized.hasSub else false,
                        hasDub = if (showPosterLanguage) sanitized.hasDub else false,
                        alignment = posterTextAlignment,
                    )
                }
            }
        }
    }
}
