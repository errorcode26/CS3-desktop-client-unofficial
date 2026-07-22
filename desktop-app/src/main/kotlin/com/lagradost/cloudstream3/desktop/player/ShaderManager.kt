package com.lagradost.cloudstream3.desktop.player

import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import java.io.File
import java.io.FileOutputStream

object ShaderManager {
    private val shadersDir = PlatformPaths.shadersDir

    // Bundled shaders that will be extracted to the user's hard drive
    // so they can be loaded by MPV (which requires absolute file paths).
    private val bundledShaders = listOf(
        "Anime4K_Restore_CNN_M.glsl",
        "Anime4K_Restore_CNN_VL.glsl",
        "Anime4K_Upscale_CNN_x2_M.glsl",
        "Anime4K_Upscale_CNN_x2_VL.glsl"
    )

    /**
     * Extracts bundled shaders from the JAR to the user's AppData directory.
     * Also acts as the setup routine for the Shader folder so users can drag-and-drop their own.
     */
    fun extractBundledShaders() {
        if (!shadersDir.exists()) {
            shadersDir.mkdirs()
        }

        bundledShaders.forEach { shaderName ->
            val targetFile = File(shadersDir, shaderName)
            if (!targetFile.exists()) {
                try {
                    val inputStream = this::class.java.classLoader.getResourceAsStream("shaders/$shaderName")
                    if (inputStream != null) {
                        FileOutputStream(targetFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        AppLogger.i("ShaderManager: Extracted bundled shader -> $shaderName")
                    } else {
                        AppLogger.w("ShaderManager: Bundled shader $shaderName not found in resources/shaders/")
                    }
                } catch (e: Exception) {
                    AppLogger.e("ShaderManager: Failed to extract $shaderName", e)
                }
            }
        }
    }

    /**
     * Returns a list of all .glsl shaders currently available in the user's shaders directory.
     * This naturally includes any custom shaders they've dragged in.
     */
    fun getAvailableShaders(): List<String> {
        val files = shadersDir.listFiles { _, name -> name.endsWith(".glsl", ignoreCase = true) }
        return files?.map { it.name } ?: emptyList()
    }
}
