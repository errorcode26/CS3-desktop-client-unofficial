package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun AmoledConfirmDialog(
    show: Boolean,
    title: String,
    text: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        isVisible = show
    }

    if (show || isVisible) {
        val amoledMode by AppearanceConfig.amoledMode.collectAsState()
        
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.8f),
                exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.8f)
            ) {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    containerColor = if (amoledMode) Color.Black else MaterialTheme.colorScheme.surface,
                    modifier = if (amoledMode) Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)) else Modifier,
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (amoledMode) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (amoledMode) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onConfirm,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(confirmText)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismiss) {
                            Text(dismissText, color = if (amoledMode) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
                        }
                    },
                )
            }
        }
    }
}
