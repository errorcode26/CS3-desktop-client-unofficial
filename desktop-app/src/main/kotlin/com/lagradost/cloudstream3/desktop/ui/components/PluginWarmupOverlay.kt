package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.desktop.init.PluginWarmupState
import kotlinx.coroutines.delay

@Composable
fun PluginWarmupOverlay(
    modifier: Modifier = Modifier,
) {
    val warmupProgress by PluginWarmupState.progress.collectAsState()
    val theme = LocalDesktopTheme.current
    val isAmoled = theme.isAmoled

    var showCompletePill by remember { mutableStateOf(false) }

    LaunchedEffect(warmupProgress.isWarmingUp, warmupProgress.isComplete) {
        if (!warmupProgress.isWarmingUp && warmupProgress.isComplete && warmupProgress.total > 0) {
            showCompletePill = true
            delay(1800L)
            showCompletePill = false
        }
    }

    val isVisible = warmupProgress.isWarmingUp || showCompletePill

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it },
        modifier = modifier.zIndex(98f),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isAmoled) Color(0xFF101014).copy(alpha = 0.94f) else theme.SurfaceCard.copy(alpha = 0.92f),
            border = BorderStroke(
                0.7.dp,
                if (isAmoled) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.12f),
            ),
            shadowElevation = 8.dp,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (warmupProgress.isWarmingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val label = if (warmupProgress.total > 0) {
                        "Loading extensions (${warmupProgress.current}/${warmupProgress.total})..."
                    } else {
                        "Warming up extensions..."
                    }
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.TextPrimary,
                    )
                } else if (showCompletePill) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "All extensions ready",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.TextPrimary,
                    )
                }
            }
        }
    }
}
