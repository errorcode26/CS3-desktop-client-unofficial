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
}
