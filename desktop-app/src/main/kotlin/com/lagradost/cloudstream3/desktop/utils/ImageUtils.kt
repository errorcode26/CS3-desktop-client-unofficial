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
     * Upgrades actor and person profile thumbnail URLs (TMDB, IMDb, Amazon, AniList)
     * to high-resolution desktop portraits (h632/original).
     */
    fun enhanceProfileUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        // TMDB profile resolution upgrade: w45, w185, w300, w500 -> h632
        if (url.contains("image.tmdb.org/t/p/")) {
            return url.replace("/w45/", "/h632/")
                .replace("/w185/", "/h632/")
                .replace("/w300/", "/h632/")
                .replace("/w500/", "/h632/")
        }

        // IMDb / Amazon portrait quality enhancement
        if (url.contains("m.media-amazon.com") || url.contains("images-na.ssl-images-amazon.com")) {
            return url.replace(Regex("""_SX\d+_"""), "_SX700_")
                .replace(Regex("""_SY\d+_"""), "_SY850_")
                .replace(Regex("""_UX\d+_"""), "_UX700_")
                .replace(Regex("""_UY\d+_"""), "_UY850_")
        }

        // AniList / MyAnimeList avatars
        if (url.contains("anilist.co") || url.contains("myanimelist.net")) {
            return url.replace("/medium/", "/large/")
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

    private val logoDarknessCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun isDarkLogoCached(url: String?): Boolean? {
        if (url.isNullOrBlank()) return null
        return logoDarknessCache[url]
    }

    fun cacheDarkLogo(url: String?, isDark: Boolean) {
        if (!url.isNullOrBlank()) {
            logoDarknessCache[url] = isDark
        }
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
     * Inspects a logo bitmap to identify dark monochrome typography that lacks contrast on dark surfaces.
     * Uses absolute chroma spread (deltaC) instead of normalized saturation to avoid numerical instability near zero.
     * Validates canvas transparency to avoid inverting solid opaque card images.
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

        // Denser 50x50 sampling grid (up to 2500 samples) to avoid skipping thin typography stems
        val stepX = maxOf(2, width / 50)
        val stepY = maxOf(2, height / 50)

        var darkMonochromePixels = 0
        var visiblePixels = 0
        var transparentPixels = 0
        var totalSamples = 0
        var chromaticPixels = 0

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                totalSamples++
                val color = bitmap.getColor(x, y)
                val a = (color ushr 24 and 0xFF) / 255.0

                if (a <= 0.10) {
                    transparentPixels++
                } else if (a >= 0.35) {
                    val r = (color ushr 16 and 0xFF) / 255.0
                    val g = (color ushr 8 and 0xFF) / 255.0
                    val b = (color and 0xFF) / 255.0

                    val maxC = maxOf(r, g, b)
                    val minC = minOf(r, g, b)
                    val deltaC = maxC - minC
                    val lum = 0.299 * r + 0.587 * g + 0.114 * b

                    // A pixel is dark monochrome if luminance < 0.32 and channel spread deltaC <= 0.10
                    // This avoids the zero-division saturation singularity while catching charcoal/slate colors
                    if (lum < 0.32 && deltaC <= 0.10) {
                        darkMonochromePixels++
                    } else if (deltaC > 0.18) {
                        chromaticPixels++
                    }
                    visiblePixels++
                }
            }
        }

        // Require at least a minimal visible sample count
        if (visiblePixels < 15 || totalSamples == 0) return false

        // Cutout check: require at least 10% transparency across the canvas to avoid inverting opaque solid-fill cards
        val transparencyRatio = transparentPixels.toDouble() / totalSamples
        if (transparencyRatio < 0.10) return false

        // Logo is dark monochrome if >= 75% of visible pixels are dark monochrome and <= 10% are strongly chromatic
        val darkRatio = darkMonochromePixels.toDouble() / visiblePixels
        val chromaticRatio = chromaticPixels.toDouble() / visiblePixels
        return darkRatio >= 0.75 && chromaticRatio <= 0.10
    }
}

