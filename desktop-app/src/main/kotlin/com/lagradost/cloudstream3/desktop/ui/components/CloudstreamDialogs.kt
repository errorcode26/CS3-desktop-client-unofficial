package com.lagradost.cloudstream3.desktop.ui.components

/**
 * The ONLY dialog system for this app. Raw AlertDialog/Dialog are FORBIDDEN.
 *
 * [CloudstreamAlertDialog] → simple confirmations, inputs (yes/no, single field)
 * [CloudstreamCustomDialog] → lists, grids, LazyColumn/LazyVerticalGrid content
 *                             (MUST use Modifier.fillMaxWidth(0.85f+).fillMaxHeight(0.85f+))
 *
 * Both handle Amoled Mode styling and scale/fade animations automatically.
 * DB writes inside onClick → MUST use scope.launch(Dispatchers.IO).
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object GlobalDialogState {
    var activeDialogCount by mutableStateOf(0)
    val isAnyDialogOpen: Boolean get() = activeDialogCount > 0
}

/**
 * Use for simple dialogs: confirmations, warnings, single text-field inputs.
 * Do NOT use for lists/grids — use [CloudstreamCustomDialog] instead.
 *
 * @param show Drives the enter/exit animation.
 * @param onDismissRequest Called on outside click or Escape.
 * @param modifier Leave empty for simple dialogs. Material3 sizes naturally at ~460dp.
 */
@Composable
fun CloudstreamAlertDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = show

    val isVisible = transitionState.currentState || transitionState.targetState

    DisposableEffect(isVisible) {
        if (isVisible) GlobalDialogState.activeDialogCount++
        onDispose {
            if (isVisible) GlobalDialogState.activeDialogCount--
        }
    }

    if (isVisible) {
        val isAmoled = LocalDesktopTheme.current.isAmoled

        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.96f),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.96f),
            ) {
                AlertDialog(
                    onDismissRequest = onDismissRequest,
                    containerColor = if (isAmoled) Color(0xFF101010) else MaterialTheme.colorScheme.surface,
                    modifier = modifier.then(if (isAmoled) Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)) else Modifier),
                    title = title,
                    text = text,
                    confirmButton = confirmButton,
                    dismissButton = dismissButton,
                )
            }
        }
    }
}

/**
 * Use for content-heavy dialogs: lists, grids, settings panels, plugin browsers.
 * MUST provide an explicit size modifier, e.g. Modifier.fillMaxWidth(0.90f).fillMaxHeight(0.88f).
 * You own the full layout inside [content] (header, body, footer buttons).
 *
 * @param show Drives the enter/exit animation.
 * @param onDismissRequest Called on outside click or Escape.
 * @param modifier Required — set width/height explicitly for desktop screen sizes.
 */
@Composable
fun CloudstreamCustomDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = show

    val isVisible = transitionState.currentState || transitionState.targetState

    DisposableEffect(isVisible) {
        if (isVisible) GlobalDialogState.activeDialogCount++
        onDispose {
            if (isVisible) GlobalDialogState.activeDialogCount--
        }
    }

    if (isVisible) {
        val isAmoled = LocalDesktopTheme.current.isAmoled
        val effectiveColor = containerColor ?: (if (isAmoled) Color(0xFF101010) else MaterialTheme.colorScheme.surface)
        val showBorder = isAmoled && containerColor == null

        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.96f),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.96f),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = effectiveColor,
                    contentColor = if (isAmoled) Color.White else androidx.compose.material3.contentColorFor(MaterialTheme.colorScheme.surface),
                    modifier = modifier.then(if (showBorder) Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)) else Modifier),
                ) {
                    content()
                }
            }
        }
    }
}
