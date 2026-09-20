package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.ui.DesktopDimens
import com.lagradost.cloudstream3.desktop.ui.components.CinematicTitle
import com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground

/**
 * Premium, desktop-optimized loading presentation for media details.
 * Matches DetailsContent geometry 1:1 to eliminate layout jumps and screen flashing.
 */
@Composable
fun DetailsLoadingView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preloadedName: String? = null,
    preloadedPoster: String? = null,
    preloadedBg: String? = null,
    providerName: String? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWindowCompact = maxWidth < 600.dp
        val isNarrow = maxWidth < 950.dp
        val isButtonsNarrow = maxWidth < 850.dp
        val isCompactHeight = maxHeight < 700.dp
        val isMediumHeight = maxHeight in 700.dp..850.dp

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
        val titleContainerMinHeight = when {
            isCompactHeight -> 80.dp
            isNarrow -> 80.dp
            else -> 110.dp
        }

        // 1. Persistent Top Backdrop with Vignette Scrim (matching DetailsBackdrop 1:1)
        val bgUrl = preloadedBg?.takeIf { it.isNotBlank() } ?: preloadedPoster?.takeIf { it.isNotBlank() }
        if (bgUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
            ) {
                val enhancedBg = remember(bgUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhanceBackdropUrl(bgUrl) }
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(enhancedBg)
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

        // 2. Scrollable 1:1 Content Skeleton
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AdaptiveMetadataLayout(
                isNarrow = isNarrow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isNarrow) 24.dp else 64.dp,
                        end = if (isNarrow) 24.dp else 64.dp,
                        top = responsiveTopPadding,
                        bottom = 28.dp,
                    ),
                mainContent = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isNarrow) Alignment.CenterHorizontally else Alignment.Start,
                    ) {
                        // Title Container bounded to prevent vertical jumps
                        Box(
                            modifier = Modifier
                                .widthIn(
                                    min = DesktopDimens.HeroLogoMinWidth,
                                    max = responsiveLogoMaxWidth,
                                )
                                .heightIn(min = titleContainerMinHeight, max = responsiveLogoMaxHeight),
                            contentAlignment = if (isNarrow) Alignment.Center else Alignment.BottomStart,
                        ) {
                            if (!preloadedName.isNullOrBlank()) {
                                CinematicTitle(
                                    text = preloadedName,
                                    fontSize = if (preloadedName.length > 28) 34.sp else 42.sp,
                                    textAlign = if (isNarrow) TextAlign.Center else TextAlign.Start,
                                    modifier = Modifier
                                        .widthIn(max = responsivePlotMaxWidth)
                                        .padding(bottom = 2.dp),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(minOf(360.dp, responsiveLogoMaxWidth * 0.8f))
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .shimmerBackground(),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tagline Skeleton
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerBackground(),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Primary Metadata Badges Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isNarrow) Arrangement.Center else Arrangement.spacedBy(10.dp),
                            modifier = if (isNarrow) Modifier.fillMaxWidth() else Modifier,
                        ) {
                            Box(modifier = Modifier.width(52.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                            Box(modifier = Modifier.width(64.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                            Box(modifier = Modifier.width(80.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                            Box(modifier = Modifier.width(48.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Overview / Synopsis Skeleton Lines
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.widthIn(max = responsivePlotMaxWidth),
                            horizontalAlignment = if (isNarrow) Alignment.CenterHorizontally else Alignment.Start,
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(0.75f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                            Box(modifier = Modifier.fillMaxWidth(0.60f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                            Box(modifier = Modifier.fillMaxWidth(0.40f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Action Buttons Skeleton Row
                        if (isButtonsNarrow) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .widthIn(max = 480.dp)
                                    .fillMaxWidth()
                                    .align(if (isNarrow) Alignment.CenterHorizontally else Alignment.Start),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(if (isButtonsNarrow) 44.dp else 52.dp)
                                        .clip(RoundedCornerShape(if (isButtonsNarrow) 10.dp else 12.dp))
                                        .shimmerBackground(),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(if (isButtonsNarrow) 44.dp else 52.dp)
                                        .clip(RoundedCornerShape(if (isButtonsNarrow) 10.dp else 12.dp))
                                        .shimmerBackground(),
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(if (isNarrow) Alignment.CenterHorizontally else Alignment.Start),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .shimmerBackground(),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .shimmerBackground(),
                                )
                            }
                        }
                    }
                },
                sideContent = {
                    if (!isNarrow) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 420.dp)
                                .padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                // Source info
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "SOURCE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = providerName ?: "Loading...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.95f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                    )
                                }
                                // Release date placeholder
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.width(60.dp).height(11.dp).clip(RoundedCornerShape(3.dp)).shimmerBackground())
                                    Box(modifier = Modifier.width(44.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                                }
                                // Status placeholder
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.width(50.dp).height(11.dp).clip(RoundedCornerShape(3.dp)).shimmerBackground())
                                    Box(modifier = Modifier.width(60.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                                }
                            }
                        }
                    }
                },
            )

            // 3. Episodes Section Skeleton
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isNarrow) 24.dp else 64.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.width(110.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                    // Ambient loading status badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val pulseTransition = rememberInfiniteTransition(label = "PulseLoading")
                        val pulseAlpha by pulseTransition.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "PulseAlpha",
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
                        )
                        Text(
                            text = if (!providerName.isNullOrBlank()) "Loading from $providerName..." else "Loading details...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Row of 3 episode cards (16:9)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(if (isNarrow) 2 else 3) {
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .shimmerBackground(),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground(),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground(),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // 4. Floating Top Action Layer (Back button & Window Controls)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F0F12).copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            WindowControlsPill(
                isHome = false,
                isCompact = isWindowCompact,
            )
        }
    }
}
