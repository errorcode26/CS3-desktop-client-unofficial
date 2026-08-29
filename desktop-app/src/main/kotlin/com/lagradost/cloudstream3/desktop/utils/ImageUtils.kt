package com.lagradost.cloudstream3.desktop.utils

object ImageUtils {
    /**
     * Upgrades mobile-downsampled thumbnail URLs (TMDB, IMDb, Amazon) to crisp, high-resolution desktop poster URLs.
     */
    fun enhancePosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        // TMDB poster resolution upgrade: w92, w154, w185, w300, w342 -> w500
        if (url.contains("image.tmdb.org/t/p/")) {
            return url.replace("/w92/", "/w500/")
                .replace("/w154/", "/w500/")
                .replace("/w185/", "/w500/")
                .replace("/w300/", "/w500/")
                .replace("/w342/", "/w500/")
        }

        // IMDb / Amazon image quality enhancement
        if (url.contains("m.media-amazon.com") || url.contains("images-na.ssl-images-amazon.com")) {
            return url.replace(Regex("""_SX\d+_"""), "_SX700_")
                .replace(Regex("""_SY\d+_"""), "_SY850_")
                .replace(Regex("""_UX\d+_"""), "_UX700_")
                .replace(Regex("""_UY\d+_"""), "_UY850_")
        }

        // GitHub / Repo manifest icon template
        if (url.contains("%size%")) {
            return url.replace("%size%", "128")
        }

        return url
    }

    /**
     * Upgrades backdrop / hero banner URLs to full uncompressed original 4K/1080p resolution.
     */
    fun enhanceBackdropUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.contains("image.tmdb.org/t/p/")) {
            return url.replace("/w300/", "/original/")
                .replace("/w500/", "/original/")
                .replace("/w780/", "/original/")
                .replace("/w1280/", "/original/")
        }
        return enhancePosterUrl(url)
    }

    /**
     * Enhances plugin and repository icon URLs.
     */
    fun enhanceIconUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.contains("%size%")) {
            return url.replace("%size%", "128")
        }
        return url
    }

    /**
     * Resolves an image URL into a local file:/// URI if it is already present in Coil's disk cache.
     * This allows WebView2 to display cached images in 0ms without performing redundant network requests.
     */
    fun getCachedDiskFileUri(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("file:/") || url.startsWith("data:")) return url
        val resolved = enhanceBackdropUrl(url) ?: url
        try {
            val diskCache = coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).diskCache
            val snapshot = diskCache?.openSnapshot(resolved) ?: diskCache?.openSnapshot(url)
            if (snapshot != null) {
                val file = snapshot.data.toFile()
                snapshot.close()
                if (file.exists() && file.length() > 0) {
                    return file.toURI().toString()
                }
            }
        } catch (_: Throwable) {}
        return resolved
    }

    val InvertColorMatrix = androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f,
        )
    )

    /**
     * Samples the visible pixels of a logo bitmap to determine if it is strictly pure pitch-black text.
     * Returns true ONLY if >= 85% of visible pixels are monochrome near-black (lum < 0.15, sat < 0.10).
     * Saturated red, blue, green, and multi-colored logos will always return false and remain 100% untouched.
     */
    fun isDarkImage(image: coil3.Image?): Boolean {
        if (image == null) return false
        val bitmap = (image as? coil3.BitmapImage)?.bitmap ?: return false
        return isDarkBitmap(bitmap)
    }

    fun isDarkBitmap(bitmap: org.jetbrains.skia.Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false

        val stepX = maxOf(1, width / 25)
        val stepY = maxOf(1, height / 25)
        var pureBlackPixels = 0
        var visiblePixels = 0

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val color = bitmap.getColor(x, y)
                val a = (color ushr 24 and 0xFF) / 255.0
                // Only inspect non-transparent pixels
                if (a > 0.25) {
                    val r = (color ushr 16 and 0xFF) / 255.0
                    val g = (color ushr 8 and 0xFF) / 255.0
                    val b = (color and 0xFF) / 255.0

                    val maxC = maxOf(r, g, b)
                    val minC = minOf(r, g, b)
                    val sat = if (maxC > 0.0) (maxC - minC) / maxC else 0.0
                    val lum = 0.299 * r + 0.587 * g + 0.114 * b

                    // A pixel is "pure black monochrome" if luminance < 0.15 and saturation < 0.10
                    if (lum < 0.15 && sat < 0.10) {
                        pureBlackPixels++
                    }
                    visiblePixels++
                }
            }
        }

        if (visiblePixels == 0) return false
        // Strictly require >= 85% of visible text to be pitch black monochrome
        return (pureBlackPixels.toDouble() / visiblePixels) >= 0.85
    }
}

