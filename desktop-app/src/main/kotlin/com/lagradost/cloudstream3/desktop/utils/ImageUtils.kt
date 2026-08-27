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
}
