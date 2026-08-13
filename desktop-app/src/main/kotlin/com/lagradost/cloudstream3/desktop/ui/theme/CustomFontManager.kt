package com.lagradost.cloudstream3.desktop.ui.theme

import com.lagradost.common.platform.PlatformPaths
import java.io.File

object CustomFontManager {
    /**
     * Retrieves a list of available custom fonts by scanning the PlatformPaths.fontsDir.
     * Returns a list of filenames (e.g. "OpenDyslexic.ttf").
     */
    fun getAvailableFonts(): List<String> {
        val dir = PlatformPaths.fontsDir
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("ttf", ignoreCase = true) || it.extension.equals("otf", ignoreCase = true) || it.extension.equals("woff", ignoreCase = true)) }
            ?.mapNotNull { file ->
                try {
                    java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file).family
                } catch (e: Exception) {
                    file.nameWithoutExtension
                }
            }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Gets a java.io.File for a specific font name if it exists in the fonts directory.
     */
    fun getFontFile(fontName: String?): File? {
        if (fontName.isNullOrBlank() || fontName == "Default" || fontName == "None") return null

        val dir = PlatformPaths.fontsDir
        if (!dir.exists() || !dir.isDirectory) return null

        // 1. Try exact filename match
        val file = File(dir, fontName)
        if (file.exists() && file.isFile) return file

        // 2. Try matching by extracted font family name
        val files = dir.listFiles()?.filter {
            it.isFile && (it.extension.equals("ttf", ignoreCase = true) || it.extension.equals("otf", ignoreCase = true))
        } ?: emptyList()

        for (f in files) {
            try {
                val awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, f)
                if (awtFont.family.equals(fontName, ignoreCase = true) || awtFont.name.equals(fontName, ignoreCase = true)) {
                    return f
                }
            } catch (e: Exception) {
                // Ignore parsing errors for individual files
            }
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
            "Ubuntu-Bold.ttf", "Ubuntu-Medium.ttf", "Ubuntu-Regular.ttf"
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
    }
}
