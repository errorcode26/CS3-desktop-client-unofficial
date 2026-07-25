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
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Gets a java.io.File for a specific font name if it exists in the fonts directory.
     */
    fun getFontFile(fontName: String?): File? {
        if (fontName.isNullOrBlank() || fontName == "Default" || fontName == "None") return null

        val file = File(PlatformPaths.fontsDir, fontName)
        return if (file.exists() && file.isFile) file else null
    }
}
