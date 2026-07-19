package com.lagradost.cloudstream3.desktop.utils

import java.awt.image.BufferedImage

object ImageColorExtractor {
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
