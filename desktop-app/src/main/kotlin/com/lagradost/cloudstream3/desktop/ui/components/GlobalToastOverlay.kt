package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GlobalToastOverlay(
    modifier: Modifier = Modifier,
    isVideoPlaying: Boolean = false,
) {
    val toasts by AppToastManager.toasts.collectAsState()
    val theme = LocalDesktopTheme.current
    val isPipMode by com.lagradost.cloudstream3.desktop.ui.PipState.isPipMode.collectAsState()

    val visibleToasts = remember(toasts, isVideoPlaying, isPipMode) {
        if (isVideoPlaying || isPipMode) {
            toasts.filter { it.type == ToastType.ERROR }
        } else {
            toasts
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 24.dp, bottom = 28.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            visibleToasts.forEach { toast ->
                key(toast.id) {
                    ToastCardItem(
                        toast = toast,
                        isAmoledMode = theme.isAmoled,
                        uiCardOpacity = theme.cardOpacity,
                        onDismiss = { AppToastManager.dismiss(toast.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastCardItem(
    toast: ToastMessage,
    isAmoledMode: Boolean,
    uiCardOpacity: Float,
    onDismiss: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val dismissWithAnimation: () -> Unit = {
        if (isVisible) {
            isVisible = false
            coroutineScope.launch {
                delay(200L)
                onDismiss()
            }
        }
    }

    LaunchedEffect(toast.id) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(180)) + slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) { it },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(180)) { it },
    ) {
        ToastCard(
            toast = toast,
            isAmoledMode = isAmoledMode,
            uiCardOpacity = uiCardOpacity,
            onDismiss = dismissWithAnimation,
        )
    }
}

private data class ToastVisualConfig(
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val badgeBg: Color,
    val borderTint: Color,
)

@Composable
private fun ToastCard(
    toast: ToastMessage,
    isAmoledMode: Boolean,
    uiCardOpacity: Float,
    onDismiss: () -> Unit,
) {
    val visuals = remember(toast.type) {
        when (toast.type) {
            ToastType.SUCCESS -> ToastVisualConfig(
                title = "COMPLETED",
                icon = Icons.Default.CheckCircle,
                accentColor = Color(0xFF10B981),
                badgeBg = Color(0xFF10B981).copy(alpha = 0.16f),
                borderTint = Color(0xFF10B981).copy(alpha = 0.35f),
            )
            ToastType.WARNING -> ToastVisualConfig(
                title = "ATTENTION",
                icon = Icons.Default.Warning,
                accentColor = Color(0xFFF59E0B),
                badgeBg = Color(0xFFF59E0B).copy(alpha = 0.16f),
                borderTint = Color(0xFFF59E0B).copy(alpha = 0.38f),
            )
            ToastType.ERROR -> ToastVisualConfig(
                title = "FAILURE",
                icon = Icons.Default.Error,
                accentColor = Color(0xFFEF4444),
                badgeBg = Color(0xFFEF4444).copy(alpha = 0.16f),
                borderTint = Color(0xFFEF4444).copy(alpha = 0.40f),
            )
            ToastType.INFO -> ToastVisualConfig(
                title = "NOTICE",
                icon = Icons.Default.Info,
                accentColor = Color(0xFF0EA5E9),
                badgeBg = Color(0xFF0EA5E9).copy(alpha = 0.16f),
                borderTint = Color(0xFF0EA5E9).copy(alpha = 0.32f),
            )
        }
    }

    val containerBg = if (isAmoledMode) {
        Color(0xFF0C0C0E)
    } else {
        Color(0xFF1A1A22).copy(alpha = (uiCardOpacity + 0.30f).coerceIn(0.92f, 0.98f))
    }

    val progress = remember { Animatable(1f) }
    LaunchedEffect(toast.id) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = toast.durationMs.toInt().coerceAtLeast(100),
                easing = LinearEasing,
            ),
        )
        onDismiss()
    }

    Surface(
        modifier = Modifier
            .width(360.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() },
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(1.dp, visuals.borderTint),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(visuals.badgeBg, RoundedCornerShape(8.dp))
                        .border(1.dp, visuals.accentColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 1.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = visuals.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                        ),
                        color = visuals.accentColor,
                    )

                    Text(
                        text = toast.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (isAmoledMode) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = if (isAmoledMode) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.value.coerceIn(0f, 1f))
                        .height(2.5.dp)
                        .background(visuals.accentColor),
                )
            }
        }
    }
}
