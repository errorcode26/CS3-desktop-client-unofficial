package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi

@Composable
fun PlaybackControls(
    playerState: PlayerState,
    modifier: Modifier = Modifier,
    onSkipPrevious: (() -> Unit)? = null,
    onSkipNext: (() -> Unit)? = null,
) {
    val isPaused by playerState.isPaused.collectAsState()
    val isBuffering by playerState.isBuffering.collectAsState()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSkipPrevious != null) {
            GlassIconButton(
                onClick = onSkipPrevious,
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous Episode"
            )
            Spacer(modifier = Modifier.width(24.dp))
        }

        // Skip backward 10s
        GlassIconButton(
            onClick = { playerState.seekBy(-10000L) },
            icon = Icons.Default.Replay10,
            contentDescription = "Skip back 10s"
        )

        Spacer(modifier = Modifier.width(36.dp))

        // Play / Pause (Premium Gradient)
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        val isPressed by interactionSource.collectIsPressedAsState()
        
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.9f else if (isHovered) 1.05f else 1f,
            animationSpec = tween(150),
            label = "playScale"
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = if (isHovered) 0.6f else 0.4f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(color = Color.White),
                    onClick = { playerState.togglePlayPause() }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp))
            } else {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Play" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(36.dp))

        // Skip forward 10s
        GlassIconButton(
            onClick = { playerState.seekBy(10000L) },
            icon = Icons.Default.Forward10,
            contentDescription = "Skip forward 10s"
        )

        if (onSkipNext != null) {
            Spacer(modifier = Modifier.width(24.dp))
            GlassIconButton(
                onClick = onSkipNext,
                icon = Icons.Default.SkipNext,
                contentDescription = "Next Episode"
            )
        }
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isHovered) 1.1f else 1f,
        animationSpec = tween(150),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (isHovered) 0.6f else 0.4f))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

