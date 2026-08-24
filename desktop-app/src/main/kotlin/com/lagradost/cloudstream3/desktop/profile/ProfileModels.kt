package com.lagradost.cloudstream3.desktop.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class Profile(
    @JsonProperty("id") val id: Int = 0,
    @JsonProperty("name") val name: String = "Main",
    @JsonProperty("avatarColorIndex") val avatarColorIndex: Int = 0,
    @JsonProperty("customAvatarPath") val customAvatarPath: String? = null,
    @JsonProperty("pinCode") val pinCode: String? = null,
    @JsonProperty("isKids") val isKids: Boolean = false,
    @JsonProperty("createdTimestamp") val createdTimestamp: Long = System.currentTimeMillis(),
) {
    val hasPin: Boolean
        get() = !pinCode.isNullOrBlank()

    val initial: String
        get() = name.trim().take(1).uppercase().ifEmpty { "P" }
}

private val avatarHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

@Composable
fun SkiaAnimatedGif(
    source: String,
    modifier: Modifier = Modifier,
) {
    val fileTimestamp = remember(source) {
        val f = File(source)
        if (f.exists()) f.lastModified() else 0L
    }
    var rawBytes by remember(source, fileTimestamp) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(source, fileTimestamp) {
        withContext(Dispatchers.IO) {
            try {
                val bytes = if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
                    val req = Request.Builder()
                        .url(source)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val resp = avatarHttpClient.newCall(req).execute()
                    val b = resp.body.bytes()
                    resp.close()
                    b
                } else {
                    val f = File(source)
                    if (f.exists()) f.readBytes() else null
                }
                rawBytes = bytes
            } catch (_: Exception) {
                rawBytes = null
            }
        }
    }

    val bytes = rawBytes
    if (bytes == null) {
        AsyncImage(
            model = source,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = modifier,
        )
        return
    }

    val codec = remember(bytes) {
        try {
            Codec.makeFromData(Data.makeFromBytes(bytes))
        } catch (_: Exception) {
            null
        }
    }

    if (codec == null || codec.frameCount <= 1) {
        val staticBitmap = remember(bytes) {
            try {
                if (codec != null && codec.frameCount == 1) {
                    val bitmap = Bitmap()
                    bitmap.allocPixels(codec.imageInfo)
                    codec.readPixels(bitmap, 0)
                    bitmap.asComposeImageBitmap()
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        if (staticBitmap != null) {
            Image(
                bitmap = staticBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = modifier,
            )
        } else {
            AsyncImage(
                model = source,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = modifier,
            )
        }
        return
    }

    var frameIndex by remember(codec) { mutableStateOf(0) }
    val frameCount = codec.frameCount

    LaunchedEffect(codec) {
        while (isActive) {
            val frameInfo = try { codec.framesInfo[frameIndex] } catch (_: Exception) { null }
            val duration = (frameInfo?.duration?.coerceAtLeast(20) ?: 100).toLong()
            delay(duration)
            frameIndex = (frameIndex + 1) % frameCount
        }
    }

    val currentBitmap = remember(codec, frameIndex) {
        try {
            val bitmap = Bitmap()
            bitmap.allocPixels(codec.imageInfo)
            codec.readPixels(bitmap, frameIndex)
            bitmap.asComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = source,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = modifier,
        )
    }
}

@Composable
fun ProfileAvatar(
    profile: Profile,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    fontSize: TextUnit = 16.sp,
) {
    val avatarSource = profile.customAvatarPath?.trim()
    if (!avatarSource.isNullOrBlank()) {
        SkiaAnimatedGif(
            source = avatarSource,
            modifier = modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(ProfilePalette.getBrush(profile.avatarColorIndex)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = profile.initial,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

object ProfilePalette {
    val colors = listOf(
        // Dark Blue / Sapphire
        listOf(Color(0xFF1E3C72), Color(0xFF2A5298)),
        // Emerald / Forest Green
        listOf(Color(0xFF134E5E), Color(0xFF71B280)),
        // Crimson / Ruby Red
        listOf(Color(0xFF870000), Color(0xFF190A05)),
        // Royal Purple / Violet
        listOf(Color(0xFF654EA3), Color(0xFFEAAFC8)),
        // Sunset Orange / Amber
        listOf(Color(0xFFFF512F), Color(0xFFF09819)),
        // Midnight Cyber / Neon Teal
        listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
        // Hot Pink / Magenta
        listOf(Color(0xFFFF0844), Color(0xFFFFB199)),
        // Dark Charcoal / Slate
        listOf(Color(0xFF232526), Color(0xFF414345)),
    )

    fun getBrush(colorIndex: Int): Brush {
        val safeIndex = colorIndex.coerceIn(0, colors.size - 1)
        val palette = colors[safeIndex]
        return Brush.linearGradient(
            colors = palette,
        )
    }
}
