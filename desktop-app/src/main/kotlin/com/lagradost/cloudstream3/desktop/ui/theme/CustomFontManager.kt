package com.lagradost.cloudstream3.desktop.ui.theme

import com.lagradost.common.platform.PlatformPaths
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CustomFontManager {
    val BUILT_IN_FONTS = listOf(
        "Plus Jakarta Sans",
        "Manrope",
        "Outfit",
        "Inter",
        "DM Sans",
        "Poppins",
        "Roboto",
        "Nunito",
    )

    private val _availableFonts = MutableStateFlow<List<String>>(BUILT_IN_FONTS)
    val availableFonts: StateFlow<List<String>> = _availableFonts.asStateFlow()

    private val _userInstalledFonts = MutableStateFlow<List<String>>(emptyList())
    val userInstalledFonts: StateFlow<List<String>> = _userInstalledFonts.asStateFlow()

    private var cachedFontFamilies: List<String>? = null
    private var cachedUserFonts: List<String>? = null
    private val fontFileCache = java.util.concurrent.ConcurrentHashMap<String, File>()

    /**
     * Retrieves a list of only user-installed custom fonts (excluding bundled fonts).
     */
    fun getUserInstalledFonts(): List<String> {
        cachedUserFonts?.let { return it }
        refreshCache()
        return cachedUserFonts ?: emptyList()
    }

    /**
     * Retrieves a list of all available fonts (curated built-in + user-installed).
     */
    fun getAvailableFonts(): List<String> {
        return _availableFonts.value
    }

    private val BUNDLED_FONT_MAP = mapOf(
        "plusjakartasans-bold.ttf" to "Plus Jakarta Sans",
        "plusjakartasans-medium.ttf" to "Plus Jakarta Sans",
        "plusjakartasans-regular.ttf" to "Plus Jakarta Sans",
        "plusjakartasans-semibold.ttf" to "Plus Jakarta Sans",
        "manrope-bold.ttf" to "Manrope",
        "manrope-medium.ttf" to "Manrope",
        "manrope-regular.ttf" to "Manrope",
        "manrope-semibold.ttf" to "Manrope",
        "dmsans-bold.ttf" to "DM Sans",
        "dmsans-medium.ttf" to "DM Sans",
        "dmsans-regular.ttf" to "DM Sans",
        "dmsans-semibold.ttf" to "DM Sans",
        "inter-bold.ttf" to "Inter",
        "inter-medium.ttf" to "Inter",
        "inter-regular.ttf" to "Inter",
        "inter-semibold.ttf" to "Inter",
        "nunito-bold.ttf" to "Nunito",
        "nunito-medium.ttf" to "Nunito",
        "nunito-regular.ttf" to "Nunito",
        "nunito-semibold.ttf" to "Nunito",
        "outfit-bold.ttf" to "Outfit",
        "outfit-medium.ttf" to "Outfit",
        "outfit-regular.ttf" to "Outfit",
        "outfit-semibold.ttf" to "Outfit",
        "poppins-bold.ttf" to "Poppins",
        "poppins-medium.ttf" to "Poppins",
        "poppins-regular.ttf" to "Poppins",
        "poppins-semibold.ttf" to "Poppins",
        "roboto-bold.ttf" to "Roboto",
        "roboto-medium.ttf" to "Roboto",
        "roboto-regular.ttf" to "Roboto",
    )

    init {
        refreshCache()
    }

    /**
     * Refreshes the in-memory font cache from disk.
     */
    fun refreshCache(): List<String> {
        val dir = PlatformPaths.fontsDir
        if (!dir.exists() || !dir.isDirectory) {
            cachedUserFonts = emptyList()
            cachedFontFamilies = BUILT_IN_FONTS
            _userInstalledFonts.value = emptyList()
            _availableFonts.value = BUILT_IN_FONTS
            return BUILT_IN_FONTS
        }

        fontFileCache.clear()
        val files = dir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("ttf", ignoreCase = true) || it.extension.equals("otf", ignoreCase = true) || it.extension.equals("woff", ignoreCase = true)) }
            ?: run {
                _userInstalledFonts.value = emptyList()
                _availableFonts.value = BUILT_IN_FONTS
                return BUILT_IN_FONTS
            }

        val userResults = mutableListOf<String>()
        for (file in files) {
            val knownFamily = BUNDLED_FONT_MAP[file.name.lowercase()]
            if (knownFamily != null) {
                fontFileCache[knownFamily.lowercase()] = file
                fontFileCache[file.name.lowercase()] = file
                fontFileCache[file.nameWithoutExtension.lowercase()] = file
            } else {
                try {
                    val family = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file).family
                    fontFileCache[family.lowercase()] = file
                    fontFileCache[file.name.lowercase()] = file
                    fontFileCache[file.nameWithoutExtension.lowercase()] = file
                    userResults.add(family)
                } catch (e: Exception) {
                    fontFileCache[file.nameWithoutExtension.lowercase()] = file
                    userResults.add(file.nameWithoutExtension)
                }
            }
        }
        val distinctUser = userResults.distinct().sorted()
        cachedUserFonts = distinctUser
        val allFonts = (BUILT_IN_FONTS + distinctUser).distinct()
        cachedFontFamilies = allFonts
        _userInstalledFonts.value = distinctUser
        _availableFonts.value = allFonts
        return allFonts
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
     * Ensures baseline subtitle font availability without polluting AppData.
     */
    fun extractBundledFonts() {
        val fontsDir = PlatformPaths.fontsDir
        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }

        val targetFile = File(fontsDir, "PlusJakartaSans-Regular.ttf")
        if (!targetFile.exists()) {
            try {
                val inputStream = this::class.java.classLoader.getResourceAsStream("fonts/PlusJakartaSans-Regular.ttf")
                if (inputStream != null) {
                    java.io.FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("CustomFontManager: Failed to extract baseline font", e)
            }
        }
        refreshCache()
    }

    /**
     * Installs a custom font file (.ttf, .otf, .woff) to the fonts directory.
     * Returns the detected or extracted font family name on success.
     */
    fun installFont(sourceFile: File): Result<String> {
        return runCatching {
            if (!sourceFile.exists() || !sourceFile.isFile) {
                error("Selected file does not exist")
            }
            val ext = sourceFile.extension.lowercase()
            if (ext !in listOf("ttf", "otf", "woff")) {
                error("Unsupported font format: .$ext (only .ttf, .otf, and .woff are supported)")
            }

            val fontsDir = PlatformPaths.fontsDir
            if (!fontsDir.exists()) fontsDir.mkdirs()

            val targetFile = File(fontsDir, sourceFile.name)
            sourceFile.copyTo(targetFile, overwrite = true)

            // Extract family name
            val familyName = try {
                java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, targetFile).family
            } catch (e: Exception) {
                targetFile.nameWithoutExtension
            }

            refreshCache()
            familyName
        }
    }

    /**
     * Deletes a user-installed font by name.
     */
    fun deleteFont(fontName: String): Boolean {
        if (fontName in BUILT_IN_FONTS) return false
        val file = getFontFile(fontName) ?: return false
        val deleted = file.delete()
        refreshCache()
        return deleted
    }

    /**
     * Opens the fonts directory in the system file manager.
     */
    fun openFontsDirectory() {
        try {
            val dir = PlatformPaths.fontsDir
            if (!dir.exists()) dir.mkdirs()
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir)
            }
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("CustomFontManager: Failed to open fonts directory", e)
        }
    }
}
