package com.lagradost.cloudstream3.desktop.player

import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop Audio and Subtitle Language Priority Manager.
 * Enables ranked levels of preference for track matching and stream selection.
 */
object LanguagePriorityHelper {
    private const val PREF_AUDIO_LANGUAGE_PRIORITIES = "cs_desktop_audio_lang_priorities"
    private const val PREF_SUBTITLE_LANGUAGE_PRIORITIES = "cs_desktop_sub_lang_priorities"

    // Default Audio Priorities (Higher = Higher Rank)
    val DEFAULT_AUDIO_PRIORITIES = mapOf(
        "eng,en" to 10,
        "original" to 8,
    )

    // Default Subtitle Priorities
    val DEFAULT_SUBTITLE_PRIORITIES = mapOf(
        "eng,en" to 10,
    )

    private val _audioPriorities = MutableStateFlow<Map<String, Int>>(DEFAULT_AUDIO_PRIORITIES)
    val audioPriorities: StateFlow<Map<String, Int>> = _audioPriorities.asStateFlow()

    private val _subtitlePriorities = MutableStateFlow<Map<String, Int>>(DEFAULT_SUBTITLE_PRIORITIES)
    val subtitlePriorities: StateFlow<Map<String, Int>> = _subtitlePriorities.asStateFlow()

    init {
        loadPriorities()
    }

    fun loadPriorities() {
        try {
            val savedAudio = DesktopDataStore.getKey<Map<String, Int>>(PREF_AUDIO_LANGUAGE_PRIORITIES)
            if (!savedAudio.isNullOrEmpty()) {
                _audioPriorities.value = DEFAULT_AUDIO_PRIORITIES + savedAudio
            } else {
                val legacy = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_AUDIO_LANG)
                if (!legacy.isNullOrBlank() && legacy != "auto") {
                    _audioPriorities.value = mapOf(legacy to 10)
                }
            }

            val savedSubs = DesktopDataStore.getKey<Map<String, Int>>(PREF_SUBTITLE_LANGUAGE_PRIORITIES)
            if (!savedSubs.isNullOrEmpty()) {
                _subtitlePriorities.value = DEFAULT_SUBTITLE_PRIORITIES + savedSubs
            } else {
                val legacy = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_SUB_LANG)
                if (!legacy.isNullOrBlank() && legacy != "auto" && legacy != "off") {
                    _subtitlePriorities.value = mapOf(legacy to 10)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("LanguagePriorityHelper", "Failed to load language priorities", e)
        }
    }

    fun getAudioPriority(code: String): Int {
        return _audioPriorities.value[code] ?: 0
    }

    fun setAudioPriority(code: String, priority: Int) {
        val updated = _audioPriorities.value.toMutableMap()
        if (priority <= 0) {
            updated.remove(code)
        } else {
            updated[code] = priority.coerceIn(1, 15)
        }
        _audioPriorities.value = updated
        DesktopDataStore.setKey(PREF_AUDIO_LANGUAGE_PRIORITIES, updated)
    }

    fun getSubtitlePriority(code: String): Int {
        return _subtitlePriorities.value[code] ?: 0
    }

    fun setSubtitlePriority(code: String, priority: Int) {
        val updated = _subtitlePriorities.value.toMutableMap()
        if (priority <= 0) {
            updated.remove(code)
        } else {
            updated[code] = priority.coerceIn(1, 15)
        }
        _subtitlePriorities.value = updated
        DesktopDataStore.setKey(PREF_SUBTITLE_LANGUAGE_PRIORITIES, updated)
    }

    fun getOrderedAudioLanguages(): List<String> {
        return _audioPriorities.value
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun getOrderedSubtitleLanguages(): List<String> {
        return _subtitlePriorities.value
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Generates a comma-delimited language chain for MPV's `alang` property.
     * Evaluates in descending priority order.
     */
    fun getMpvAlangString(): String {
        val ordered = getOrderedAudioLanguages()
        if (ordered.isEmpty()) {
            val legacy = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_AUDIO_LANG) ?: "auto"
            return if (legacy != "auto" && legacy.isNotBlank()) legacy else ""
        }
        return ordered.flatMap { code ->
            if (code == "original") listOf("original", "orig") else code.split(",").map { it.trim() }
        }.distinct().joinToString(",")
    }

    /**
     * Generates a comma-delimited language chain for MPV's `slang` property.
     * Evaluates in descending priority order.
     */
    fun getMpvSlangString(): String {
        val ordered = getOrderedSubtitleLanguages()
        if (ordered.isEmpty()) {
            val legacy = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_SUB_LANG) ?: "auto"
            return if (legacy != "auto" && legacy != "off" && legacy.isNotBlank()) legacy else ""
        }
        return ordered.flatMap { code ->
            code.split(",").map { it.trim() }
        }.distinct().joinToString(",")
    }

    fun resetAudioDefaults() {
        _audioPriorities.value = DEFAULT_AUDIO_PRIORITIES
        DesktopDataStore.setKey(PREF_AUDIO_LANGUAGE_PRIORITIES, DEFAULT_AUDIO_PRIORITIES)
    }

    fun resetSubtitleDefaults() {
        _subtitlePriorities.value = DEFAULT_SUBTITLE_PRIORITIES
        DesktopDataStore.setKey(PREF_SUBTITLE_LANGUAGE_PRIORITIES, DEFAULT_SUBTITLE_PRIORITIES)
    }

    fun setAudioPreset(priorities: Map<String, Int>) {
        _audioPriorities.value = priorities
        DesktopDataStore.setKey(PREF_AUDIO_LANGUAGE_PRIORITIES, priorities)
    }

    fun setSubtitlePreset(priorities: Map<String, Int>) {
        _subtitlePriorities.value = priorities
        DesktopDataStore.setKey(PREF_SUBTITLE_LANGUAGE_PRIORITIES, priorities)
    }
}
