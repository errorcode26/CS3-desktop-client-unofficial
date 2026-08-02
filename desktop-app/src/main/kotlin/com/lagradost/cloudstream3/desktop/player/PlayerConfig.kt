package com.lagradost.cloudstream3.desktop.player

import com.lagradost.common.storage.DesktopDataStore
import com.sun.jna.Pointer

object PlayerConfig {
    const val PREF_HWDEC = "player_hwdec"
    const val PREF_SUB_SIZE = "player_sub_size"
    const val PREF_SUB_COLOR = "player_sub_color"
    const val PREF_SUB_BG = "player_sub_bg"
    const val PREF_YTDL_FORMAT = "player_ytdl_format"
    const val PREF_PREFERRED_QUALITY = "player_preferred_quality"
    const val PREF_AUTO_PLAY = "player_auto_play"
    const val PREF_AUTO_PLAY_WAIT_FOR_LINKS = "player_auto_play_wait_for_links"
    const val PREF_AUTO_PLAY_TIMEOUT = "player_auto_play_timeout"
    const val PREF_INTERPOLATION = "player_interpolation_enabled"
    const val PREF_ACTIVE_SHADER = "player_active_shader"
    const val PREF_SUB_FONT = "player_sub_font"
    const val PREF_SUB_BORDER_COLOR = "player_sub_border_color"
    const val PREF_SUB_BORDER_SIZE = "player_sub_border_size"
    const val PREF_SUB_SHADOW_COLOR = "player_sub_shadow_color"
    const val PREF_SUB_SHADOW_OFFSET = "player_sub_shadow_offset"
    const val PREF_SUB_BLUR = "player_sub_blur"
    const val PREF_SUB_BOLD = "player_sub_bold"
    const val PREF_SUB_ITALIC = "player_sub_italic"
    const val PREF_ENABLE_SUB_OVERRIDE = "player_enable_sub_override"

    fun applyMpvSettings(handle: Pointer, lib: MpvLibrary) {
        // Hardware Acceleration — let MPV auto-detect the best decoder.
        // Do NOT set vo, gpu-api, gpu-context, d3d11-flip, etc. here.
        // MPV's auto-detection is battle-tested; our overrides have caused
        // repeated regressions. The VO is set in ComposeMpvPlayer before init.
        val hwdec = DesktopDataStore.getKey<String>(PREF_HWDEC) ?: "auto-copy"
        lib.mpv_set_option_string(handle, "hwdec", hwdec)

        // Subtitles Size (Default: 45)
        val subSize = DesktopDataStore.getKey<String>(PREF_SUB_SIZE) ?: "45"
        lib.mpv_set_option_string(handle, "sub-font-size", subSize)

        // Subtitle Color (Default: #FFFFFF)
        val subColor = DesktopDataStore.getKey<String>(PREF_SUB_COLOR) ?: "#FFFFFF"
        lib.mpv_set_option_string(handle, "sub-color", subColor)

        // Subtitle Background (Default: None/Transparent -> #00000000)
        val subBg = DesktopDataStore.getKey<String>(PREF_SUB_BG) ?: "#00000000"
        lib.mpv_set_option_string(handle, "sub-bg-color", subBg)

        // Advanced Subtitle Styling
        DesktopDataStore.getKey<String>(PREF_SUB_BORDER_COLOR)?.let { lib.mpv_set_option_string(handle, "sub-border-color", it) }
        DesktopDataStore.getKey<String>(PREF_SUB_BORDER_SIZE)?.let { lib.mpv_set_option_string(handle, "sub-border-size", it) }
        DesktopDataStore.getKey<String>(PREF_SUB_SHADOW_COLOR)?.let { lib.mpv_set_option_string(handle, "sub-shadow-color", it) }
        DesktopDataStore.getKey<String>(PREF_SUB_SHADOW_OFFSET)?.let { lib.mpv_set_option_string(handle, "sub-shadow-offset", it) }
        DesktopDataStore.getKey<String>(PREF_SUB_BLUR)?.let { lib.mpv_set_option_string(handle, "sub-blur", it) }
        DesktopDataStore.getKey<String>(PREF_SUB_BOLD)?.let { lib.mpv_set_option_string(handle, "sub-bold", it) }
        DesktopDataStore.getKey<String>(PREF_SUB_ITALIC)?.let { lib.mpv_set_option_string(handle, "sub-italic", it) }

        // Custom Subtitle Font & Override
        val subFont = DesktopDataStore.getKey<String>(PREF_SUB_FONT)
        val enableOverride = DesktopDataStore.getKey<Boolean>(PREF_ENABLE_SUB_OVERRIDE) ?: false
        lib.mpv_set_option_string(handle, "sub-fonts-dir", com.lagradost.common.platform.PlatformPaths.fontsDir.absolutePath)
        
        if (!subFont.isNullOrBlank()) {
            lib.mpv_set_option_string(handle, "sub-font", subFont)
        }
        
        if (enableOverride) {
            lib.mpv_set_option_string(handle, "sub-ass-override", "force")
        } else {
            lib.mpv_set_option_string(handle, "sub-ass-override", "no")
        }

        // YTDL Format / Quality Selection
        val ytdlFormat = DesktopDataStore.getKey<String>(PREF_YTDL_FORMAT) ?: "bestvideo[height<=?1080]+bestaudio/best"
        lib.mpv_set_option_string(handle, "ytdl-format", ytdlFormat)

        // Verbose Logging for Dev Console
        lib.mpv_set_option_string(handle, "msg-level", "all=warn")
        lib.mpv_set_option_string(handle, "terminal", "yes")

        // Fast Startup Optimizations
        // NOTE: demuxer-max-bytes / demuxer-max-back-bytes are NOT set here.
        // Each stream kind (HLS/DASH/PROGRESSIVE) sets its own optimal values
        // in ComposeMpvPlayer after init, via mpv_set_property_string.
        lib.mpv_set_option_string(handle, "cache", "yes")
        lib.mpv_set_option_string(handle, "cache-pause", "yes") // Allow MPV to pause to buffer, preventing video freeze with audio continuing

        // Interpolation / Blending (Smooth motion for 24fps/30fps videos on high refresh rate displays)
        val useInterpolation = DesktopDataStore.getKey<Boolean>(PREF_INTERPOLATION) ?: false
        if (useInterpolation) {
            lib.mpv_set_option_string(handle, "video-sync", "display-resample")
            lib.mpv_set_option_string(handle, "interpolation", "yes")
            lib.mpv_set_option_string(handle, "tscale", "oversample")
        } else {
            lib.mpv_set_option_string(handle, "video-sync", "audio")
            lib.mpv_set_option_string(handle, "interpolation", "no")
        }

        // Custom Shaders (e.g. Anime4K)
        val activeShader = DesktopDataStore.getKey<String>(PREF_ACTIVE_SHADER)
        if (!activeShader.isNullOrBlank() && activeShader != "None") {
            val shaderFile = java.io.File(com.lagradost.common.platform.PlatformPaths.shadersDir, activeShader)
            if (shaderFile.exists()) {
                // Must be an absolute path for MPV to read it
                lib.mpv_set_option_string(handle, "glsl-shaders", shaderFile.absolutePath)
                com.lagradost.common.logging.AppLogger.i("PlayerConfig: Applied shader ${shaderFile.absolutePath}")
            }
        }
    }
}
