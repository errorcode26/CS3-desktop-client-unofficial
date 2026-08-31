package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.profile.ProfileAvatar
import com.lagradost.cloudstream3.desktop.profile.ProfileManager
import kotlinx.coroutines.delay

@Composable
fun ProfileWelcomeToast() {
    val toastProfile by ProfileManager.welcomeToast.collectAsState()

    LaunchedEffect(toastProfile) {
        if (toastProfile != null) {
            delay(3200)
            ProfileManager.dismissWelcomeToast()
        }
    }

    // Pulsing animation for active status dot
    val infiniteTransition = rememberInfiniteTransition(label = "WelcomePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "PulseScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = toastProfile != null,
            enter = slideInVertically(
                initialOffsetY = { -it - 50 },
                animationSpec = spring(
                    dampingRatio = 0.68f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(tween(220)) + scaleIn(
                initialScale = 0.90f,
                animationSpec = spring(
                    dampingRatio = 0.70f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it - 50 },
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.94f),
        ) {
            toastProfile?.let { profile ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F1015).copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            ),
                        ),
                    ),
                    shadowElevation = 16.dp,
                    modifier = Modifier.shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Avatar with glowing ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
                                    RoundedCornerShape(12.dp),
                                ),
                        ) {
                            ProfileAvatar(
                                profile = profile,
                                size = 42.dp,
                                shape = RoundedCornerShape(12.dp),
                                fontSize = 16.sp,
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "Welcome back, ${profile.name}!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50)),
                                )
                                Text(
                                    text = if (profile.isKids) "Kids Profile • Safe Streaming" else "Profile Active • Ready to Watch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
