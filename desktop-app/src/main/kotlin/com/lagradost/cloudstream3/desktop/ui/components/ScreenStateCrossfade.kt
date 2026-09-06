package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class ScreenStage {
    LOADING,
    CONTENT,
    ERROR,
}

@Composable
fun ScreenStateCrossfade(
    stage: ScreenStage,
    modifier: Modifier = Modifier,
    animationDurationMs: Int = 350,
    loadingContent: @Composable () -> Unit,
    errorContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Crossfade(
        targetState = stage,
        animationSpec = tween(
            durationMillis = animationDurationMs,
            easing = FastOutSlowInEasing,
        ),
        modifier = modifier,
        label = "screen_state_crossfade",
    ) { currentStage ->
        when (currentStage) {
            ScreenStage.LOADING -> loadingContent()
            ScreenStage.CONTENT -> content()
            ScreenStage.ERROR -> errorContent()
        }
    }
}
