package com.lagradost.cloudstream3.desktop.ui.theme

import com.lagradost.common.platform.PlatformPaths
import java.io.File

object CustomFontManager {
    private var cachedFontFamilies: List<String>? = null
    private val fontFileCache = java.util.concurrent.ConcurrentHashMap<String, File>()

    /**
     * Retrieves a list of available custom fonts.
     * Uses memory cache for instant O(1) reads without disk or parsing overhead.
     */
    fun getAvailableFonts(): List<String> {
        cachedFontFamilies?.let { return it }
        return refreshCache()
    }

    /**
     * Refreshes the in-memory font cache from disk.
     */
    fun refreshCache(): List<String> {
        val dir = PlatformPaths.fontsDir
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        fontFileCache.clear()
        val files = dir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("ttf", ignoreCase = true) || it.extension.equals("otf", ignoreCase = true) || it.extension.equals("woff", ignoreCase = true)) }
            ?: return emptyList()

        val results = mutableListOf<String>()
        for (file in files) {
            try {
                val family = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file).family
                fontFileCache[family.lowercase()] = file
                fontFileCache[file.name.lowercase()] = file
                fontFileCache[file.nameWithoutExtension.lowercase()] = file
                results.add(family)
            } catch (e: Exception) {
                fontFileCache[file.nameWithoutExtension.lowercase()] = file
                results.add(file.nameWithoutExtension)
            }
        }
        val distinct = results.distinct().sorted()
        cachedFontFamilies = distinct
        return distinct
    }

    /**
     * Gets a java.io.File for a specific font name from cache.
     */
    fun getFontFile(fontName: String?): File? {
        if (fontName.isNullOrBlank() || fontName == "Default" || fontName == "None") return null
        if (cachedFontFamilies == null) {
            refreshCache()
        }
        val lower = fontName.lowercase()
        fontFileCache[lower]?.let { return it }

        // Fallback exact match
        val dir = PlatformPaths.fontsDir
        val file = File(dir, fontName)
        if (file.exists() && file.isFile) {
            fontFileCache[lower] = file
            return file
        }
        return null
    }

    /**
     * Extracts all bundled fonts from the JAR to the user's AppData directory
     * so they can be loaded by MPV natively.
     */
    fun extractBundledFonts() {
        val fontsDir = PlatformPaths.fontsDir
        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }

        // List of all bundled fonts in src/main/resources/fonts
        val bundledFonts = listOf(
            "CourierPrime-Bold.ttf", "CourierPrime-Regular.ttf",
            "DMSans-Bold.ttf", "DMSans-Medium.ttf", "DMSans-Regular.ttf", "DMSans-SemiBold.ttf",
            "FiraSans-Bold.ttf", "FiraSans-Medium.ttf", "FiraSans-Regular.ttf", "FiraSans-SemiBold.ttf",
            "Inter-Bold.ttf", "Inter-Medium.ttf", "Inter-Regular.ttf", "Inter-SemiBold.ttf",
            "Lato-Bold.ttf", "Lato-Medium.ttf", "Lato-Regular.ttf", "Lato-SemiBold.ttf",
            "Lobster-Regular.ttf",
            "Nunito-Bold.ttf", "Nunito-Medium.ttf", "Nunito-Regular.ttf", "Nunito-SemiBold.ttf",
            "Outfit-Bold.ttf", "Outfit-Medium.ttf", "Outfit-Regular.ttf", "Outfit-SemiBold.ttf",
            "Pacifico-Regular.ttf",
            "Poppins-Bold.ttf", "Poppins-Medium.ttf", "Poppins-Regular.ttf", "Poppins-SemiBold.ttf",
            "Roboto-Bold.ttf", "Roboto-Medium.ttf", "Roboto-Regular.ttf",
            "Ubuntu-Bold.ttf", "Ubuntu-Medium.ttf", "Ubuntu-Regular.ttf",
        )

        bundledFonts.forEach { fontName ->
            val targetFile = File(fontsDir, fontName)
            if (!targetFile.exists()) {
                try {
                    val inputStream = this::class.java.classLoader.getResourceAsStream("fonts/$fontName")
                    if (inputStream != null) {
                        java.io.FileOutputStream(targetFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        com.lagradost.common.logging.AppLogger.i("CustomFontManager: Extracted bundled font -> $fontName")
                    }
                } catch (e: Exception) {
                    com.lagradost.common.logging.AppLogger.e("CustomFontManager: Failed to extract $fontName", e)
                }
            }
        }
        refreshCache()
    }
}
