package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
                model = backdropUrl,
                contentDescription = "Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp)
            )
        }
        
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f))
        )

        // Top-left Back button
        if (onCancel != null) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
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
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
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
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val contentScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 2000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 700, delayMillis = 400, easing = androidx.compose.animation.core.LinearEasing),
        label = "alpha",
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (backdropUrl != null) {
            coil3.compose.AsyncImage(
                model = backdropUrl,
                contentDescription = "Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Box(
            modifier = Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.3f),
                        Color.Black.copy(alpha = 0.6f),
                        Color.Black.copy(alpha = 0.8f),
                        Color.Black.copy(alpha = 0.9f),
                    )
                )
            )
        )

        // Top-left Back button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
 
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!logoUrl.isNullOrBlank()) {
                coil3.compose.AsyncImage(
                    model = logoUrl,
                    contentDescription = "Logo",
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .widthIn(max = 300.dp)
                        .graphicsLayer {
                            alpha = contentAlpha
                            scaleX = contentScale
                            scaleY = contentScale
                        },
                    contentScale = ContentScale.Fit,
                )
                Spacer(modifier = Modifier.height(32.dp))
            } else if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 24.dp).graphicsLayer {
                            alpha = contentAlpha
                            scaleX = contentScale
                            scaleY = contentScale
                    },
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            Surface(
                modifier = Modifier.widthIn(min = 350.dp, max = 450.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xB3121212),
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = DesktopUi.Accent,
                    strokeWidth = 4.dp,
                )
                Spacer(modifier = Modifier.height(24.dp))
 
                Text(
                    text = if (title.contains("Loading")) "Loading Streams" else "Loading Episode",
                    color = DesktopUi.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (episodeText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = episodeText,
                        color = DesktopUi.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DesktopUi.SurfaceElevated,
                ) {
                    Text(
                        text = "${links.size} Stream${if (links.size == 1) "" else "s"} Found",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = DesktopUi.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time Link Probing Animation List
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(links.size) { index ->
                        val link = links[index]
                        val isFailed = failedLinks.contains(index)
                        val isCurrent = index == currentLinkIndex
                        val isWaiting = index > currentLinkIndex

                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { 50 }) + androidx.compose.animation.fadeIn(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) DesktopUi.Accent.copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isFailed) {
                                    Icon(Icons.Default.Close, contentDescription = "Failed", tint = Color.Red, modifier = Modifier.size(16.dp))
                                } else if (isCurrent) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DesktopUi.Accent, strokeWidth = 2.dp)
                                } else if (isWaiting) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Waiting", tint = DesktopUi.TextMuted, modifier = Modifier.size(16.dp))
                                } else {
                                    // Passed but not active (should not happen usually, but fallback)
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Passed", tint = Color.Green, modifier = Modifier.size(16.dp))
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = link.name,
                                        color = if (isFailed) Color.Red.copy(alpha = 0.8f) else if (isCurrent) DesktopUi.Accent else DesktopUi.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                    if (isCurrent) {
                                        Text("Trying connection...", color = DesktopUi.TextMuted, fontSize = 11.sp)
                                    } else if (isFailed) {
                                        Text("Connection failed", color = Color.Red.copy(alpha = 0.6f), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilledTonalButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2C2C2C),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel")
                    }

                    Button(
                        onClick = onPlayNow,
                        enabled = links.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopUi.Accent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play Now")
                    }
                }
            }
        }
    }
}
}
