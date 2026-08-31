package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.profile.Profile
import com.lagradost.cloudstream3.desktop.profile.ProfileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PinCodeDialog(
    show: Boolean,
    profile: Profile?,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    if (!show || profile == null) return

    val expectedPinLength = remember(profile) { profile.pinCode?.length?.coerceIn(4, 6) ?: 4 }
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Shake animation on error
    val shakeAnim = remember { Animatable(0f) }

    LaunchedEffect(profile) {
        delay(150)
        focusRequester.requestFocus()
    }

    fun triggerShakeAndClear() {
        coroutineScope.launch {
            isError = true
            isVerifying = false
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -12f at 50
                    12f at 100
                    -8f at 150
                    8f at 200
                    -4f at 250
                    4f at 300
                    0f at 400
                },
            )
            delay(250)
            enteredPin = ""
            isError = false
            focusRequester.requestFocus()
        }
    }

    fun verify(pin: String) {
        isVerifying = true
        coroutineScope.launch {
            // Smooth micro-delay so the 4th dot spring animation renders completely
            delay(180)
            if (ProfileManager.verifyPin(profile.id, pin)) {
                isSuccess = true
                isVerifying = false
                // Brief success feedback with unlocked badge
                delay(220)
                onVerified()
            } else {
                triggerShakeAndClear()
            }
        }
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = {
            if (!isVerifying && !isSuccess) onDismiss()
        },
        modifier = Modifier.widthIn(max = 420.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
                .graphicsLayer { translationX = shakeAnim.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header Lock Icon + Profile Name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                isSuccess -> Color(0xFF4CAF50).copy(alpha = 0.20f)
                                isVerifying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = Triple(isError, isSuccess, isVerifying),
                        transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessHigh)) togetherWith fadeOut() },
                        label = "LockStateIcon",
                    ) { (err, succ, ver) ->
                        when {
                            succ -> Icon(
                                Icons.Default.LockOpen,
                                contentDescription = "Unlocked",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(28.dp),
                            )
                            ver -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            err -> Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(26.dp),
                            )
                            else -> Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }

                Text(
                    text = if (isSuccess) "PIN Accepted" else "Enter Profile PIN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = when {
                        isError -> "Incorrect PIN. Try again."
                        isSuccess -> "Switching to '${profile.name}'…"
                        isVerifying -> "Verifying security PIN…"
                        else -> "Profile '${profile.name}' is PIN protected."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isError -> MaterialTheme.colorScheme.error
                        isSuccess -> Color(0xFF81C784)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Invisible BasicTextField capturing input
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = enteredPin,
                    onValueChange = { input ->
                        if (!isVerifying && !isSuccess && input.all { it.isDigit() } && input.length <= expectedPinLength) {
                            enteredPin = input
                            if (input.length == expectedPinLength) {
                                verify(input)
                            }
                        }
                    },
                    enabled = !isVerifying && !isSuccess,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .size(1.dp)
                        .focusRequester(focusRequester),
                )

                // Discrete PIN Box Cells
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (!isVerifying && !isSuccess) focusRequester.requestFocus()
                        },
                    ),
                ) {
                    for (i in 0 until expectedPinLength) {
                        val isFilled = i < enteredPin.length
                        val isFocused = i == enteredPin.length && !isVerifying && !isSuccess

                        val cellBorder = when {
                            isError -> MaterialTheme.colorScheme.error
                            isSuccess -> Color(0xFF4CAF50)
                            isFocused -> MaterialTheme.colorScheme.primary
                            isFilled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        }

                        val cellBg = when {
                            isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                            isSuccess -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                            isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }

                        val dotScale by animateFloatAsState(
                            targetValue = if (isFilled) 1f else 0f,
                            animationSpec = spring(
                                dampingRatio = 0.58f,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "DotScale_$i",
                        )

                        Box(
                            modifier = Modifier
                                .size(46.dp, 54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cellBg)
                                .border(
                                    if (isFocused || isError || isSuccess) 2.dp else 1.dp,
                                    cellBorder,
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dotScale > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .graphicsLayer {
                                            scaleX = dotScale
                                            scaleY = dotScale
                                        }
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isError -> MaterialTheme.colorScheme.error
                                                isSuccess -> Color(0xFF4CAF50)
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            // Cancel Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isVerifying && !isSuccess,
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
