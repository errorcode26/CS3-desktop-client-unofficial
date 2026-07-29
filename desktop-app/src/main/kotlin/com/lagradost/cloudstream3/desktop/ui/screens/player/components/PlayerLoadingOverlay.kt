package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi

@Composable
fun StreamLoadingOverlay(
    title: String,
    linkName: String,
    loadingStatus: String? = null,
    backdropUrl: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    // Animated pulsing dots
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, initialStartOffset = StartOffset(0)),
        label = "d1",
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, initialStartOffset = StartOffset(200)),
        label = "d2",
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, initialStartOffset = StartOffset(400)),
        label = "d3",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (backdropUrl != null) {
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                    .data(backdropUrl)
                    .size(2560, 1440)
                    .build(),
                contentDescription = "Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
        )

        // Top-left Back button
        if (onCancel != null) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Surface(
            modifier = Modifier.widthIn(min = 320.dp, max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xB3121212),
        ) {
            Box {
                if (onCancel != null) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DesktopUi.TextMuted)
                    }
                }

                Column(
                    modifier = Modifier.padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Animated pulsing dots row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(12.dp).scale(dot1Scale).clip(CircleShape).background(DesktopUi.Accent))
                        Box(modifier = Modifier.size(12.dp).scale(dot2Scale).clip(CircleShape).background(DesktopUi.Accent))
                        Box(modifier = Modifier.size(12.dp).scale(dot3Scale).clip(CircleShape).background(DesktopUi.Accent))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Loading Stream",
                        color = DesktopUi.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    if (linkName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = linkName,
                            color = DesktopUi.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (loadingStatus != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = loadingStatus,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Please wait while we buffer the stream…",
                        color = DesktopUi.TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerLoadingOverlay(
    title: String,
    episodeText: String,
    links: List<com.lagradost.cloudstream3.utils.ExtractorLink>,
    currentLinkIndex: Int,
    failedLinks: Set<Int>,
    backdropUrl: String? = null,
    logoUrl: String? = null,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val logoScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val spinnerAlpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spinnerAlpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop
        if (backdropUrl != null) {
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                    .data(backdropUrl)
                    .size(2560, 1440)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        // Gradient overlay — heavier at bottom for elegance
        Box(
            modifier = Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.25f),
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.80f),
                    ),
                ),
            ),
        )

        // Back button — top-left
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        // Center content: logo/title + minimal status
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!logoUrl.isNullOrBlank()) {
                coil3.compose.AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(logoUrl)
                        .size(1600, 800)
                        .build(),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .heightIn(max = 160.dp)
                        .widthIn(max = 320.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                        },
                    contentScale = ContentScale.Fit,
                )
            } else if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                        },
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Slim spinner
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp).graphicsLayer { alpha = spinnerAlpha },
                color = Color.White,
                strokeWidth = 2.5.dp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Episode / status label — no box, just plain text
            if (episodeText.isNotBlank()) {
                Text(
                    text = episodeText,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (links.isNotEmpty()) {
                Text(
                    text = "${links.size} stream${if (links.size == 1) "" else "s"} found",
                    color = DesktopUi.Accent.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
