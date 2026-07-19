package com.lagradost.cloudstream3.desktop.utils

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

object ImageColorExtractor {
    fun decodeSubsampled(inputStream: InputStream, subsampleX: Int = 8, subsampleY: Int = 8): BufferedImage? {
        val imageInputStream = ImageIO.createImageInputStream(inputStream) ?: return null
        try {
            val readers = ImageIO.getImageReaders(imageInputStream)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = imageInputStream
                val param = reader.defaultReadParam
                param.setSourceSubsampling(subsampleX, subsampleY, 0, 0)
                return reader.read(0, param)
            } finally {
                reader.dispose()
            }
        } finally {
            imageInputStream.close()
        }
    }

    suspend fun extractDominantColorFromUrl(imageUrl: String): androidx.compose.ui.graphics.Color? = withContext(Dispatchers.IO) {
        try {
            val response = app.get(imageUrl)
            val bytes = response.body.bytes()
            val img = decodeSubsampled(bytes.inputStream(), subsampleX = 8, subsampleY = 8) ?: return@withContext null
            sampleDominantColor(img)
        } catch (e: Exception) {
            AppLogger.w("ImageColorExtractor: Failed to extract color from $imageUrl — ${e.message}")
            null
        }
    }

    fun sampleDominantColor(img: BufferedImage): androidx.compose.ui.graphics.Color? {
        val area = img.width * img.height
        // Sample only 300 pixels because reading the whole image takes 2 seconds and makes the fan spin
        val step = maxOf(1, Math.sqrt(area / 300.0).toInt())
        val colorBuckets = mutableMapOf<Int, Int>()

        var x = 0
        var pixelCount = 0
        while (x < img.width) {
            var y = 0
            while (y < img.height) {
                val argb = img.getRGB(x, y)
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val saturation = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
                val brightness = max / 255f
                if (saturation < 0.25f || brightness < 0.15f || brightness > 0.95f) {
                    y += step
                    pixelCount++
                    continue
                }

                val qr = (r / 32) * 32
                val qg = (g / 32) * 32
                val qb = (b / 32) * 32
                val key = (qr shl 16) or (qg shl 8) or qb
                colorBuckets[key] = (colorBuckets[key] ?: 0) + 1
                y += step
                pixelCount++
            }
            x += step
        }

        if (colorBuckets.isEmpty()) return null

        val dominant = colorBuckets.maxByOrNull { entry ->
            val key = entry.key
            val count = entry.value
            val r = (key shr 16) and 0xFF
            val g = (key shr 8) and 0xFF
            val b = key and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val sat = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
            count * (sat * sat)
        }?.key ?: return null

        val r = (dominant shr 16) and 0xFF
        val g = (dominant shr 8) and 0xFF
        val b = dominant and 0xFF
        return androidx.compose.ui.graphics.Color(r, g, b)
    }
}
