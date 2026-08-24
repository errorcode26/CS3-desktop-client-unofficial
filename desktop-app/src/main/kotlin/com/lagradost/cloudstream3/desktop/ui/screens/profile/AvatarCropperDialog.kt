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
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

private val cropperHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

@Composable
fun AvatarCropperDialog(
    imagePathOrUrl: String,
    onDismiss: () -> Unit,
    onCropCompleted: (savedPath: String) -> Unit,
) {
    var rawBytes by remember { mutableStateOf<ByteArray?>(null) }
    var rawImage by remember { mutableStateOf<BufferedImage?>(null) }
    var isGifMedia by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isCropping by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var zoomScale by remember { mutableStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val cropBoxSizeDp = 260.dp
    val scope = rememberCoroutineScope()

    // Load Image/GIF bytes in background IO
    LaunchedEffect(imagePathOrUrl) {
        withContext(Dispatchers.IO) {
            try {
                isLoading = true
                loadError = null
                val bytes = if (imagePathOrUrl.startsWith("http://", ignoreCase = true) ||
                    imagePathOrUrl.startsWith("https://", ignoreCase = true)
                ) {
                    val req = Request.Builder()
                        .url(imagePathOrUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val resp = cropperHttpClient.newCall(req).execute()
                    val b = resp.body.bytes()
                    resp.close()
                    b
                } else {
                    val file = File(imagePathOrUrl)
                    if (file.exists()) file.readBytes() else null
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    rawBytes = bytes
                    isGifMedia = GifCropper.isGif(bytes) || GifCropper.isGif(imagePathOrUrl)
                    val img = ImageIO.read(ByteArrayInputStream(bytes))
                    rawImage = img
                    if (img == null && !isGifMedia) {
                        loadError = "Unable to decode image format."
                    }
                } else {
                    loadError = "Unable to load the selected image."
                }
            } catch (e: Exception) {
                loadError = "Failed to load: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isLoading = false
            }
        }
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = { if (!isCropping) onDismiss() },
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
                    text = if (isGifMedia) "Crop & Frame Animated GIF" else "Crop & Adjust Avatar",
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
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else if (loadError != null) {
                    Text(
                        text = loadError ?: "Error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                } else if (rawBytes != null) {
                    val bytes = rawBytes!!
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
                                val frameInfo = try { skiaCodec.framesInfo[frameIdx] } catch (_: Exception) { null }
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
                    } else if (rawImage != null) {
                        val composeBitmap = remember(rawImage) { rawImage!!.toComposeImageBitmap() }
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

                if (isCropping) {
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
                                text = if (isGifMedia) "Rendering GIF frames..." else "Applying crop...",
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
                    enabled = zoomScale > 1.0f && !isCropping,
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                }

                Slider(
                    value = zoomScale,
                    onValueChange = { zoomScale = it },
                    valueRange = 1.0f..3.0f,
                    enabled = !isCropping,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.2f).coerceAtMost(3.0f) },
                    enabled = zoomScale < 3.0f && !isCropping,
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                }

                IconButton(
                    onClick = {
                        zoomScale = 1.0f
                        panOffset = Offset.Zero
                    },
                    enabled = !isCropping,
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
                    enabled = !isCropping,
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val bytes = rawBytes ?: return@Button
                        isCropping = true
                        loadError = null

                        scope.launch(Dispatchers.IO) {
                            try {
                                val avatarDir = File(PlatformPaths.appDataDir, "profiles/avatars").also { it.mkdirs() }
                                val isGif = GifCropper.isGif(bytes) || isGifMedia

                                if (isGif) {
                                    val destFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.gif")
                                    destFile.writeBytes(bytes)

                                    withContext(Dispatchers.Main) {
                                        if (destFile.exists() && destFile.length() > 0) {
                                            onCropCompleted(destFile.absolutePath)
                                            onDismiss()
                                        } else {
                                            loadError = "Failed to save raw animated GIF."
                                            isCropping = false
                                        }
                                    }
                                } else {
                                    val img = rawImage ?: ImageIO.read(ByteArrayInputStream(bytes))
                                    if (img != null) {
                                        val targetSize = 512
                                        val cropped = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
                                        val g2d = cropped.createGraphics()
                                        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                                        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                                        val imgW = img.width.toDouble()
                                        val imgH = img.height.toDouble()
                                        val baseScale = maxOf(targetSize / imgW, targetSize / imgH)
                                        val totalScale = baseScale * zoomScale

                                        val drawW = imgW * totalScale
                                        val drawH = imgH * totalScale

                                        val panScaleFactor = targetSize / 260.0
                                        val drawX = (targetSize - drawW) / 2.0 + (panOffset.x * panScaleFactor)
                                        val drawY = (targetSize - drawH) / 2.0 + (panOffset.y * panScaleFactor)

                                        g2d.drawImage(img, drawX.toInt(), drawY.toInt(), drawW.toInt(), drawH.toInt(), null)
                                        g2d.dispose()

                                        val destFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.png")
                                        ImageIO.write(cropped, "png", destFile)

                                        withContext(Dispatchers.Main) {
                                            onCropCompleted(destFile.absolutePath)
                                            onDismiss()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            loadError = "Failed to process image."
                                            isCropping = false
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    loadError = "Crop failed: ${e.localizedMessage}"
                                    isCropping = false
                                }
                            }
                        }
                    },
                    enabled = rawBytes != null && !isCropping,
                ) {
                    if (isCropping) {
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
