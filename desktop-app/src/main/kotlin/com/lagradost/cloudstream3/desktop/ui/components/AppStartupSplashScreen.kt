package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.init.AppReadyState

@Composable
fun AppStartupSplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashBreath")
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "SplashTextBreathAlpha",
    )

    val targetProgress by AppReadyState.progress.collectAsState()
    val statusText by AppReadyState.statusText.collectAsState()

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "StartupProgressBar",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "C L O U D S T R E A M",
                color = Color(0xFFF1F5F9).copy(alpha = textAlpha),
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 8.sp,
                fontFamily = FontFamily.SansSerif,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "U N O F F I C I A L   D E S K T O P",
                color = Color.White.copy(alpha = (textAlpha * 0.65f).coerceIn(0f, 1f)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.SansSerif,
            )

            Spacer(Modifier.height(36.dp))

            // Non-blocking smooth progress bar
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.85f)),
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}
