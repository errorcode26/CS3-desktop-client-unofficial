package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppStartupSplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashAnimations")

    // Subtle, luxurious ambient breathing pulse for the glow and icon
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "GlowPulse",
    )

    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "IconScale",
    )

    // Smooth hairline progress bar shimmer
    val progressShift by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ProgressShift",
    )

    val accentPurple = Color(0xFF7C6BFF)
    val accentCyan = Color(0xFF38BDF8)
    val bgDark = Color(0xFF060709)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark),
        contentAlignment = Alignment.Center,
    ) {
        // 1. Ambient Radial Glow behind the central logo
        Box(
            modifier = Modifier
                .size(340.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentPurple.copy(alpha = glowPulse * 0.35f),
                            accentCyan.copy(alpha = glowPulse * 0.12f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 2. Premium App Icon with frosted glass border
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .size(92.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource("app_icon.png"),
                    contentDescription = "CloudStream",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Clean Brand Typography
            Text(
                text = "CloudStream",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Ultra-minimal Indeterminate Hairline Progress Shimmer
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                val startX = (progressShift - 0.35f).coerceAtLeast(0f)
                val endX = (progressShift + 0.35f).coerceAtMost(1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                startX to accentPurple.copy(alpha = 0.8f),
                                ((startX + endX) / 2f) to accentCyan,
                                endX to accentPurple.copy(alpha = 0.8f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
    }
}
