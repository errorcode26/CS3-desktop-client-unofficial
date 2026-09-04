package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlobalToastOverlay(
    modifier: Modifier = Modifier,
) {
    val toasts by AppToastManager.toasts.collectAsState()
    val theme = LocalDesktopTheme.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            toasts.forEach { toast ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
                ) {
                    ToastCard(
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
private fun ToastCard(
    toast: ToastMessage,
    isAmoledMode: Boolean,
    uiCardOpacity: Float,
    onDismiss: () -> Unit,
) {
    val (icon: ImageVector, iconTint: Color, borderTint: Color) = when (toast.type) {
        ToastType.SUCCESS -> Triple(
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50),
            Color(0xFF4CAF50).copy(alpha = 0.4f),
        )
        ToastType.WARNING -> Triple(
            Icons.Default.Warning,
            Color(0xFFFFB300),
            Color(0xFFFFB300).copy(alpha = 0.45f),
        )
        ToastType.ERROR -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
        )
        ToastType.INFO -> Triple(
            Icons.Default.Info,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        )
    }

    val containerBg = if (isAmoledMode) {
        Color(0xFF0D0D0D).copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = (uiCardOpacity + 0.25f).coerceAtMost(0.98f))
    }

    Surface(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 460.dp)
            .clickable { onDismiss() },
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(1.dp, borderTint),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = toast.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (isAmoledMode) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = if (isAmoledMode) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
