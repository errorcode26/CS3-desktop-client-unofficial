package com.lagradost.cloudstream3.desktop.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

@Composable
fun AvatarCropperDialog(
    imagePathOrUrl: String,
    onDismiss: () -> Unit,
    onCropCompleted: (savedPath: String) -> Unit,
    viewModel: AvatarCropperViewModel = remember(imagePathOrUrl) { AvatarCropperViewModel(imagePathOrUrl) },
) {
    val uiState by viewModel.uiState.collectAsState()

    var zoomScale by remember { mutableStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val cropBoxSizeDp = 260.dp

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is AvatarCropperUiEffect.CropSuccess -> {
                    onCropCompleted(effect.savedPath)
                    onDismiss()
                }
            }
        }
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = { if (!uiState.isCropping) onDismiss() },
        modifier = Modifier.widthIn(max = 500.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Crop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (uiState.isGifMedia) "Crop & Frame Animated GIF" else "Crop & Adjust Avatar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = "Drag to reposition and use the zoom slider to frame your avatar in real time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            // Crop Viewport Canvas
            Box(
                modifier = Modifier
                    .size(cropBoxSizeDp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            panOffset += dragAmount
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val curBytes = uiState.rawBytes
                val curImage = uiState.rawImage
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else if (uiState.loadError != null) {
                    Text(
                        text = uiState.loadError ?: "Error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                } else if (curBytes != null) {
                    val bytes = curBytes
                    val skiaCodec = remember(bytes) {
                        try {
                            Codec.makeFromData(Data.makeFromBytes(bytes))
                        } catch (_: Exception) {
                            null
                        }
                    }

                    if (skiaCodec != null && skiaCodec.frameCount > 1) {
                        // Live animated GIF preview in cropper
                        var frameIdx by remember(skiaCodec) { mutableStateOf(0) }
                        val frameCnt = skiaCodec.frameCount

                        LaunchedEffect(skiaCodec) {
                            while (isActive) {
                                val frameInfo = try {
                                    skiaCodec.framesInfo[frameIdx]
                                } catch (_: Exception) {
                                    null
                                }
                                val duration = (frameInfo?.duration?.coerceAtLeast(20) ?: 100).toLong()
                                delay(duration)
                                frameIdx = (frameIdx + 1) % frameCnt
                            }
                        }

                        val liveBitmap = remember(skiaCodec, frameIdx) {
                            try {
                                val bmp = Bitmap()
                                bmp.allocPixels(skiaCodec.imageInfo)
                                skiaCodec.readPixels(bmp, frameIdx)
                                bmp.asComposeImageBitmap()
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (liveBitmap != null) {
                            Image(
                                bitmap = liveBitmap,
                                contentDescription = "Live GIF Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = panOffset.x
                                        translationY = panOffset.y
                                    },
                            )
                        }
                    } else if (curImage != null) {
                        val composeBitmap = remember(curImage) { curImage.toComposeImageBitmap() }
                        Image(
                            bitmap = composeBitmap,
                            contentDescription = "Avatar Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                },
                        )
                    }

                    // Inner Framing Guide Ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    )
                }

                if (uiState.isCropping) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (uiState.isGifMedia) "Rendering GIF frames..." else "Applying crop...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // Zoom Controls & Slider
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = { zoomScale = (zoomScale - 0.2f).coerceAtLeast(1.0f) },
                    enabled = zoomScale > 1.0f && !uiState.isCropping,
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                }

                Slider(
                    value = zoomScale,
                    onValueChange = { zoomScale = it },
                    valueRange = 1.0f..3.0f,
                    enabled = !uiState.isCropping,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.2f).coerceAtMost(3.0f) },
                    enabled = zoomScale < 3.0f && !uiState.isCropping,
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                }

                IconButton(
                    onClick = {
                        zoomScale = 1.0f
                        panOffset = Offset.Zero
                    },
                    enabled = !uiState.isCropping,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Framing")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !uiState.isCropping,
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.onEvent(AvatarCropperUiEvent.ApplyCrop(zoomScale, panOffset))
                    },
                    enabled = uiState.rawBytes != null && !uiState.isCropping,
                ) {
                    if (uiState.isCropping) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Text("Saving...")
                        }
                    } else {
                        Text("Apply & Crop")
                    }
                }
            }
        }
    }
}
