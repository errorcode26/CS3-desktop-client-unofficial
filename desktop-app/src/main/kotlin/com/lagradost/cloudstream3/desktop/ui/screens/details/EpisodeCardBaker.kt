package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

private const val MAX_BAKED_EPISODE_CACHE_SIZE = 150

/**
 * Caches and renders extended card backgrounds for episode thumbnails.
 */
object EpisodeCardBaker {
    private val memoryCache = object : java.util.LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_BAKED_EPISODE_CACHE_SIZE
        }
    }
    private val lock = Any()

    fun getFromCache(
        key: String,
        shouldHideSpoilers: Boolean,
    ): ImageBitmap? {
        val cacheKey = "$key-$shouldHideSpoilers"
        synchronized(lock) {
            return memoryCache[cacheKey]
        }
    }

    fun getOrBake(
        key: String,
        srcBitmap: Bitmap,
        shouldHideSpoilers: Boolean,
        width: Int = 480,
        height: Int = 405, // Aspect ratio 16:13.5
    ): ImageBitmap? {
        if (srcBitmap.width <= 0 || srcBitmap.height <= 0) return null
        val cacheKey = "$key-$shouldHideSpoilers"
        synchronized(lock) {
            val cached = memoryCache[cacheKey]
            if (cached != null) return cached
        }

        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas

        // 1. Base dark container background
        val basePaint = Paint().apply { color = 0xFF141518.toInt() }
        canvas.drawRect(Rect.makeWH(width.toFloat(), height.toFloat()), basePaint)

        val srcImage = Image.makeFromBitmap(srcBitmap)
        val srcW = srcBitmap.width.toFloat()
        val srcH = srcBitmap.height.toFloat()

        // Smart Center-Crop to 16:9 to preserve aspect ratio on posters and backdrops
        val targetAspect = 16f / 9f
        val srcAspect = srcW / srcH
        val cropSrc = if (srcAspect > targetAspect) {
            // Source is wider than 16:9 -> crop horizontal sides (center crop)
            val cropW = srcH * targetAspect
            val cropX = (srcW - cropW) / 2f
            Rect.makeXYWH(cropX, 0f, cropW, srcH)
        } else {
            // Source is taller than 16:9 (e.g. 2:3 vertical poster) -> top-center crop to preserve faces
            val cropH = srcW / targetAspect
            val cropY = ((srcH - cropH) * 0.15f).coerceAtLeast(0f)
            Rect.makeXYWH(0f, cropY, srcW, cropH)
        }

        // 2. Bottom 15% Ribbon Ambient Blur (Lush, creamy ambient bed)
        val ribbonSrc = Rect.makeLTRB(
            cropSrc.left,
            cropSrc.top + cropSrc.height * 0.85f,
            cropSrc.right,
            cropSrc.bottom
        )
        val blurDst = Rect.makeLTRB(
            0f,
            height * 0.40f,
            width.toFloat(),
            height.toFloat()
        )
        val blurPaint = Paint().apply {
            imageFilter = ImageFilter.makeBlur(if (shouldHideSpoilers) 45f else 36f, if (shouldHideSpoilers) 45f else 36f, FilterTileMode.CLAMP)
        }
        canvas.drawImageRect(srcImage, ribbonSrc, blurDst, blurPaint)

        // 3. Sharp 16:9 Thumbnail Frame with Soft Alpha Dissolve into the Ambient Blur
        val thumbHeight = width * (9f / 16f) // 270px for 480px width
        val thumbDst = Rect.makeLTRB(
            0f,
            0f,
            width.toFloat(),
            thumbHeight
        )
        canvas.saveLayer(thumbDst, null)
        val sharpPaint = Paint().apply {
            if (shouldHideSpoilers) {
                imageFilter = ImageFilter.makeBlur(24f, 24f, FilterTileMode.CLAMP)
            }
        }
        canvas.drawImageRect(srcImage, cropSrc, thumbDst, sharpPaint)

        // Dissolve bottom 40% of the thumbnail into transparent alpha (No black bar!)
        val alphaShader = Shader.makeLinearGradient(
            0f, thumbHeight * 0.60f,
            0f, thumbHeight,
            intArrayOf(0xFF000000.toInt(), 0x00000000),
            floatArrayOf(0.0f, 1.0f)
        )
        val maskPaint = Paint().apply {
            shader = alphaShader
            blendMode = BlendMode.DST_IN
        }
        canvas.drawRect(thumbDst, maskPaint)
        canvas.restore()

        // 4. Subtle Text Readability Scrim (Soft translucent shadow strictly behind the bottom text deck)
        val textScrimShader = Shader.makeLinearGradient(
            0f, height * 0.50f,
            0f, height.toFloat(),
            intArrayOf(0x00000000, 0x330A0B0E, 0x8C0A0B0E.toInt(), 0xD90A0B0E.toInt()),
            floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f)
        )
        val textScrimPaint = Paint().apply { shader = textScrimShader }
        canvas.drawRect(Rect.makeLTRB(0f, height * 0.50f, width.toFloat(), height.toFloat()), textScrimPaint)

        val result = surface.makeImageSnapshot().toComposeImageBitmap()
        synchronized(lock) {
            memoryCache[cacheKey] = result
        }
        return result
    }
}
