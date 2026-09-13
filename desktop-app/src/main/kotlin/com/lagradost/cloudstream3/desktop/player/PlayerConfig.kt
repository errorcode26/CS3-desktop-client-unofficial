package com.lagradost.cloudstream3.desktop.player

import com.lagradost.common.storage.DesktopDataStore
import com.sun.jna.Pointer

object PlayerConfig {
    const val PREF_HWDEC = "player_hwdec"
    const val PREF_AUDIO_NORMALIZATION = "player_audio_normalization"
    const val PREF_AUDIO_NORM_STRENGTH = "player_audio_norm_strength"
    const val PREF_AUDIO_VOLUME_MAX = "player_audio_volume_max"
    const val PREF_AUDIO_SPATIAL = "player_audio_spatial"
    const val PREF_AUDIO_EQ_PRESET = "player_audio_eq_preset"
    const val PREF_AUDIO_DELAY = "player_audio_delay"
    const val PREF_SUB_SIZE = "player_sub_size"
    const val PREF_SUB_COLOR = "player_sub_color"
    const val PREF_SUB_BG = "player_sub_bg"
    const val PREF_YTDL_FORMAT = "player_ytdl_format"
    const val PREF_PREFERRED_QUALITY = "player_preferred_quality"
    const val PREF_PREFERRED_AUDIO_LANG = "player_preferred_audio_lang"
    const val PREF_PREFERRED_SUB_LANG = "player_preferred_sub_lang"
    const val PREF_SUB_ENABLED = "player_sub_enabled"
    const val PREF_AUTO_PLAY = "player_auto_play"
    const val PREF_AUTO_PLAY_TIMEOUT = "player_auto_play_timeout"
    const val PREF_INTERPOLATION = "player_interpolation_enabled"
    const val PREF_DEBAND = "player_deband"
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
    const val PREF_SHOW_END_TIME = "player_show_end_time"
    const val PREF_SHOW_CLOCK = "player_show_clock"
    const val PREF_SHOW_SERVER_QUALITY = "player_show_server_quality"
    const val PREF_SCREENSHOT_DIR = "player_screenshot_dir"
    const val PREF_SCREENSHOT_FORMAT = "player_screenshot_format"
    const val PREF_SCREENSHOT_TEMPLATE = "player_screenshot_template"
    const val PREF_ENABLE_SKIP_INTERVALS = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.KEY_ENABLE_SKIP_INTERVALS
    const val PREF_AUTO_SKIP_INTRO = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.KEY_AUTO_SKIP_INTRO
    const val PREF_AUTO_SKIP_OUTRO = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.KEY_AUTO_SKIP_OUTRO
    const val PREF_PAUSE_INFO_MODE = "player_pause_info_mode" // "delay_5s" (default), "delay_10s", "delay_20s", "immediate", "off"
    const val PREF_PAUSE_SHOW_CAST = "player_pause_show_cast"

    fun toMpvBackgroundColor(hexOrRgba: String?): Pair<String, String> {
        return when (hexOrRgba?.trim()?.lowercase()) {
            "#80000000", "semi-transparent", "0.0/0.0/0.0/0.5" -> Pair("0.0/0.0/0.0/0.5", "background-box")
            "#ff000000", "#000000", "solid", "0.0/0.0/0.0/1.0" -> Pair("0.0/0.0/0.0/1.0", "background-box")
            else -> Pair("0.0/0.0/0.0/0.0", "outline-and-shadow")
        }
    }

    fun applyMpvSettings(handle: Pointer, lib: MpvLibrary) {
        // Fast rendering profile to eliminate shader overhead in embedded presentation
        lib.mpv_set_option_string(handle, "profile", "fast")
        lib.mpv_set_option_string(handle, "framedrop", "vo")
        lib.mpv_set_option_string(handle, "hr-seek-framedrop", "yes")

        // Hardware Acceleration — let MPV auto-detect the best decoder with safe recovery fallback.
        val hwdec = DesktopDataStore.getKey<String>(PREF_HWDEC) ?: "auto-safe"
        lib.mpv_set_option_string(handle, "hwdec", hwdec)

        // Subtitles Size (Default: 45)
        val subSize = DesktopDataStore.getKey<String>(PREF_SUB_SIZE) ?: "45"
        lib.mpv_set_option_string(handle, "sub-font-size", subSize)

        // Subtitle Color (Default: #FFFFFF)
        val subColor = DesktopDataStore.getKey<String>(PREF_SUB_COLOR) ?: "#FFFFFF"
        lib.mpv_set_option_string(handle, "sub-color", subColor)

        // Subtitle Background (Default: None/Transparent -> 0.0/0.0/0.0/0.0)
        val subBg = DesktopDataStore.getKey<String>(PREF_SUB_BG) ?: "#00000000"
        val (mpvBgColor, borderStyle) = toMpvBackgroundColor(subBg)
        lib.mpv_set_option_string(handle, "sub-back-color", mpvBgColor)
        lib.mpv_set_option_string(handle, "sub-border-style", borderStyle)

        // Advanced Subtitle Styling
        val subBorderColor = DesktopDataStore.getKey<String>(PREF_SUB_BORDER_COLOR) ?: "#000000"
        val subBorderSize = DesktopDataStore.getKey<String>(PREF_SUB_BORDER_SIZE) ?: "3"
        val subShadowColor = DesktopDataStore.getKey<String>(PREF_SUB_SHADOW_COLOR) ?: "#000000"
        val subShadowOffset = DesktopDataStore.getKey<String>(PREF_SUB_SHADOW_OFFSET) ?: "0"
        val subBlur = DesktopDataStore.getKey<String>(PREF_SUB_BLUR) ?: "0"
        val subBold = DesktopDataStore.getKey<String>(PREF_SUB_BOLD) ?: "no"
        val subItalic = DesktopDataStore.getKey<String>(PREF_SUB_ITALIC) ?: "no"

        lib.mpv_set_option_string(handle, "sub-border-color", subBorderColor)
        lib.mpv_set_option_string(handle, "sub-border-size", subBorderSize)
        lib.mpv_set_option_string(handle, "sub-shadow-color", subShadowColor)
        lib.mpv_set_option_string(handle, "sub-shadow-offset", subShadowOffset)
        lib.mpv_set_option_string(handle, "sub-blur", subBlur)
        lib.mpv_set_option_string(handle, "sub-bold", subBold)
        lib.mpv_set_option_string(handle, "sub-italic", subItalic)

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
        val ytdlFormat = DesktopDataStore.getKey<String>(PREF_YTDL_FORMAT) ?: "bestvideo[height<=?1080][vcodec^=avc1]+bestaudio/bestvideo[height<=?1080][vcodec!*=?av01]+bestaudio/best"
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

        // Deband (Reduces color banding artifacts)
        val deband = DesktopDataStore.getKey<Boolean>(PREF_DEBAND) ?: false
        lib.mpv_set_option_string(handle, "deband", if (deband) "yes" else "no")

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

        // Screenshots Pipeline
        val screenshotDir = com.lagradost.common.platform.PlatformPaths.screenshotsDir.absolutePath
        lib.mpv_set_option_string(handle, "screenshot-directory", screenshotDir)

        val screenshotFormat = DesktopDataStore.getKey<String>(PREF_SCREENSHOT_FORMAT) ?: "png"
        lib.mpv_set_option_string(handle, "screenshot-format", screenshotFormat)

        val screenshotTemplate = DesktopDataStore.getKey<String>(PREF_SCREENSHOT_TEMPLATE) ?: "%F_%P_%n"
        lib.mpv_set_option_string(handle, "screenshot-template", screenshotTemplate)
        lib.mpv_set_option_string(handle, "screenshot-png-compression", "7")
        lib.mpv_set_option_string(handle, "screenshot-jpeg-quality", "95")
    }

    val GLOBAL_LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
        "auto" to "Auto (Stream Default)",
        "original" to "Original Audio (Native / Org)",
        "eng,en" to "English",
        "hin,hi" to "Hindi",
        "spa,es" to "Spanish",
        "fre,fra,fr" to "French",
        "ger,deu,de" to "German",
        "tha,th" to "Thai",
        "jpn,ja" to "Japanese",
        "ita,it" to "Italian",
        "por,pt" to "Portuguese",
        "rus,ru" to "Russian",
        "kor,ko" to "Korean",
        "chi,zho,zh" to "Chinese / Mandarin",
        "vie,vi" to "Vietnamese",
        "ind,id" to "Indonesian",
        "ara,ar" to "Arabic",
        "tur,tr" to "Turkish",
        "tel,te" to "Telugu",
        "tam,ta" to "Tamil",
        "mal,ml" to "Malayalam",
        "kan,kn" to "Kannada",
        "ben,bn" to "Bengali",
        "mar,mr" to "Marathi",
        "pan,pa" to "Punjabi",
        "guj,gu" to "Gujarati",
        "urd,ur" to "Urdu",
        "tgl,fil,tl" to "Tagalog / Filipino",
        "pol,pl" to "Polish",
        "nld,dut,nl" to "Dutch",
        "swe,sv" to "Swedish",
        "ell,gre,el" to "Greek",
        "heb,he" to "Hebrew",
        "fas,per,fa" to "Persian / Farsi",
        "ron,rum,ro" to "Romanian",
        "ces,cze,cs" to "Czech",
        "hun,hu" to "Hungarian",
        "ukr,uk" to "Ukrainian",
        "msa,may,ms" to "Malay",
        "dan,da" to "Danish",
        "fin,fi" to "Finnish",
        "nor,no" to "Norwegian",
    )
}

object LanguageMatcher {
    private val LANGUAGE_KEYWORDS: Map<String, List<String>> = mapOf(
        "original" to listOf("original", "orig", "org", "native"),
        "eng,en" to listOf("eng", "en", "english", "dub", "dubbed", "eng dub", "english dub"),
        "hin,hi" to listOf("hin", "hi", "hindi"),
        "spa,es" to listOf("spa", "es", "spanish", "espanol"),
        "fre,fra,fr" to listOf("fre", "fra", "fr", "french", "francais"),
        "ger,deu,de" to listOf("ger", "deu", "de", "german", "deutsch"),
        "tha,th" to listOf("tha", "th", "thai"),
        "jpn,ja" to listOf("jpn", "ja", "japanese", "jap", "sub", "subbed", "raw"),
        "ita,it" to listOf("ita", "it", "italian", "italiano"),
        "por,pt" to listOf("por", "pt", "portuguese", "portugues"),
        "rus,ru" to listOf("rus", "ru", "russian"),
        "kor,ko" to listOf("kor", "ko", "korean"),
        "chi,zho,zh" to listOf("chi", "zho", "zh", "chinese", "mandarin", "cantonese"),
        "vie,vi" to listOf("vie", "vi", "vietnamese"),
        "ind,id" to listOf("ind", "id", "indonesian", "indo"),
        "ara,ar" to listOf("ara", "ar", "arabic"),
        "tur,tr" to listOf("tur", "tr", "turkish"),
        "tel,te" to listOf("tel", "te", "telugu"),
        "tam,ta" to listOf("tam", "ta", "tamil"),
        "mal,ml" to listOf("mal", "ml", "malayalam"),
        "kan,kn" to listOf("kan", "kn", "kannada"),
        "ben,bn" to listOf("ben", "bn", "bengali", "bangla"),
        "mar,mr" to listOf("mar", "mr", "marathi"),
        "pan,pa" to listOf("pan", "pa", "punjabi"),
        "guj,gu" to listOf("guj", "gu", "gujarati"),
        "urd,ur" to listOf("urd", "ur", "urdu"),
        "tgl,fil,tl" to listOf("tgl", "fil", "tl", "tagalog", "filipino"),
        "pol,pl" to listOf("pol", "pl", "polish"),
        "nld,dut,nl" to listOf("nld", "dut", "nl", "dutch"),
        "swe,sv" to listOf("swe", "sv", "swedish"),
        "ell,gre,el" to listOf("ell", "gre", "el", "greek"),
        "heb,he" to listOf("heb", "he", "hebrew"),
        "fas,per,fa" to listOf("fas", "per", "fa", "persian", "farsi"),
        "ron,rum,ro" to listOf("ron", "rum", "ro", "romanian"),
        "ces,cze,cs" to listOf("ces", "cze", "cs", "czech"),
        "hun,hu" to listOf("hun", "hu", "hungarian"),
        "ukr,uk" to listOf("ukr", "uk", "ukrainian"),
        "msa,may,ms" to listOf("msa", "may", "ms", "malay"),
        "dan,da" to listOf("dan", "da", "danish"),
        "fin,fi" to listOf("fin", "fi", "finnish"),
        "nor,no" to listOf("nor", "no", "norwegian"),
    )

    private val GENERIC_MULTI_AUDIO_KEYWORDS = listOf(
        "dual audio", "dual-audio", "dualaudio",
        "multi audio", "multi-audio", "multiaudio",
        "multi language", "multi-language",
        "multi-sub", "multisub",
    )

    fun sanitizeTextForMatching(text: String): String {
        return text.replace(Regex("[._\\[\\](){}\\-+/,]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    fun getKeywordsForCode(code: String?): List<String> {
        if (code.isNullOrBlank() || code == "auto" || code == "off") return emptyList()
        val mapped = LANGUAGE_KEYWORDS[code]
        if (mapped != null) return mapped
        return code.split(",", "-", " ").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }

    fun matchLinkPriority(
        linkName: String?,
        source: String? = null,
        audioTracks: List<com.lagradost.cloudstream3.AudioFile> = emptyList(),
        prefLangCode: String?,
    ): Int {
        if (prefLangCode.isNullOrBlank() || prefLangCode == "auto") return 0
        val keywords = getKeywordsForCode(prefLangCode)

        // 1. Check explicit audioTracks attached to ExtractorLink (e.g. from DASH / multi-audio providers)
        if (audioTracks.isNotEmpty()) {
            val hasExplicitAudio = audioTracks.any { audio ->
                val lowerUrl = audio.url.lowercase()
                keywords.any { kw ->
                    val regex = Regex("(^|[^a-z0-9])${Regex.escape(kw)}([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(lowerUrl)
                }
            }
            if (hasExplicitAudio) return 2
        }

        // 2. Check linkName and source name with dirty-string normalization
        val rawCombined = "${linkName.orEmpty()} ${source.orEmpty()}".lowercase()
        if (rawCombined.isBlank()) return 0
        val sanitizedCombined = sanitizeTextForMatching(rawCombined)

        for (kw in keywords) {
            val regex = Regex("(^|[^a-z0-9])${Regex.escape(kw)}([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(rawCombined) || regex.containsMatchIn(sanitizedCombined)) {
                return 2
            }
        }
        for (kw in GENERIC_MULTI_AUDIO_KEYWORDS) {
            if (rawCombined.contains(kw) || sanitizedCombined.contains(kw)) return 1
        }
        return 0
    }

    fun matchesAudioTrack(lang: String?, title: String?, name: String?, prefLangCode: String?): Boolean {
        if (prefLangCode.isNullOrBlank() || prefLangCode == "auto") return false
        val keywords = getKeywordsForCode(prefLangCode)
        val combined = "${lang.orEmpty()} ${title.orEmpty()} ${name.orEmpty()}".lowercase()
        return keywords.any { kw ->
            val regex = Regex("(^|[^a-z0-9])${Regex.escape(kw)}([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
            regex.containsMatchIn(combined)
        }
    }
}
