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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Minimalist, desktop-optimized loading presentation for media details.
 * Rendered within [ScreenStateCrossfade] while the provider scrapes metadata.
 */
@Composable
fun DetailsLoadingView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preloadedName: String? = null,
    providerName: String? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DetailsLoadingPulse")
    val titleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "TitleAlpha",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWindowCompact = maxWidth < 600.dp

        // Floating Top Action Layer (1:1 matching DetailsContent geometry)
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

            com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill(
                isHome = false,
                isCompact = isWindowCompact,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .widthIn(max = 520.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )

            if (!preloadedName.isNullOrBlank()) {
                Text(
                    text = preloadedName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = titleAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = if (!providerName.isNullOrBlank()) {
                    "Loading media from $providerName..."
                } else {
                    "Loading media details..."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
