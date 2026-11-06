package com.lagradost.cloudstream3.desktop.ui.screens.profile

import androidx.compose.ui.geometry.Offset
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

data class AvatarCropperUiState(
    val isLoading: Boolean = true,
    val isCropping: Boolean = false,
    val loadError: String? = null,
    val rawBytes: ByteArray? = null,
    val rawImage: BufferedImage? = null,
    val isGifMedia: Boolean = false,
) : UiState

sealed interface AvatarCropperUiEvent : UiEvent {
    data class LoadImage(val pathOrUrl: String) : AvatarCropperUiEvent
    data class ApplyCrop(
        val zoomScale: Float,
        val panOffset: Offset,
    ) : AvatarCropperUiEvent
}

sealed interface AvatarCropperUiEffect : UiEffect {
    data class CropSuccess(val savedPath: String) : AvatarCropperUiEffect
}

class AvatarCropperViewModel(
    initialPathOrUrl: String,
) : BaseMviViewModel<AvatarCropperUiState, AvatarCropperUiEvent, AvatarCropperUiEffect>(
    initialState = AvatarCropperUiState(),
) {
    companion object {
        private val cropperHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    init {
        loadImage(initialPathOrUrl)
    }

    override fun handleEvent(event: AvatarCropperUiEvent) {
        when (event) {
            is AvatarCropperUiEvent.LoadImage -> loadImage(event.pathOrUrl)
            is AvatarCropperUiEvent.ApplyCrop -> applyCrop(event.zoomScale, event.panOffset)
        }
    }

    private fun loadImage(pathOrUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isLoading = true, loadError = null) }
            try {
                val bytes = if (pathOrUrl.startsWith("http://", ignoreCase = true) ||
                    pathOrUrl.startsWith("https://", ignoreCase = true)
                ) {
                    val req = Request.Builder()
                        .url(pathOrUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val resp = cropperHttpClient.newCall(req).execute()
                    val b = resp.body.bytes()
                    resp.close()
                    b
                } else {
                    val file = File(pathOrUrl)
                    if (file.exists()) file.readBytes() else null
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    val isGif = GifCropper.isGif(bytes) || GifCropper.isGif(pathOrUrl)
                    val img = ImageIO.read(ByteArrayInputStream(bytes))
                    val err = if (img == null && !isGif) "Unable to decode image format." else null
                    updateState {
                        copy(
                            rawBytes = bytes,
                            rawImage = img,
                            isGifMedia = isGif,
                            loadError = err,
                            isLoading = false,
                        )
                    }
                } else {
                    updateState {
                        copy(
                            loadError = "Unable to load the selected image.",
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        loadError = "Failed to load: ${e.localizedMessage ?: "Unknown error"}",
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun applyCrop(zoomScale: Float, panOffset: Offset) {
        val bytes = uiState.value.rawBytes ?: return
        val isGifMedia = uiState.value.isGifMedia

        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isCropping = true, loadError = null) }
            try {
                val avatarDir = File(PlatformPaths.appDataDir, "profiles/avatars").also { it.mkdirs() }
                val isGif = GifCropper.isGif(bytes) || isGifMedia

                if (isGif) {
                    val destFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.gif")
                    destFile.writeBytes(bytes)

                    if (destFile.exists() && destFile.length() > 0) {
                        updateState { copy(isCropping = false) }
                        sendEffect(AvatarCropperUiEffect.CropSuccess(destFile.absolutePath))
                    } else {
                        updateState {
                            copy(
                                loadError = "Failed to save raw animated GIF.",
                                isCropping = false,
                            )
                        }
                    }
                } else {
                    val img = uiState.value.rawImage ?: ImageIO.read(ByteArrayInputStream(bytes))
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

                        updateState { copy(isCropping = false) }
                        sendEffect(AvatarCropperUiEffect.CropSuccess(destFile.absolutePath))
                    } else {
                        updateState {
                            copy(
                                loadError = "Failed to process image.",
                                isCropping = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        loadError = "Crop failed: ${e.localizedMessage}",
                        isCropping = false,
                    )
                }
            }
        }
    }
}
