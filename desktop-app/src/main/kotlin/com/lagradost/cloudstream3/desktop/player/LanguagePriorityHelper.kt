package com.lagradost.cloudstream3.desktop.player

import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Desktop Audio and Subtitle Language Priority Manager.
 * Enables ranked priority queues for audio tracks and subtitle selection.
 */
object LanguagePriorityHelper {
    private const val PREF_AUDIO_LANGUAGE_STACK = "cs_desktop_audio_lang_stack"
    private const val PREF_SUBTITLE_LANGUAGE_STACK = "cs_desktop_sub_lang_stack"

    // Legacy keys for automatic one-time migration
    private const val PREF_LEGACY_AUDIO_PRIORITIES = "cs_desktop_audio_lang_priorities"
    private const val PREF_LEGACY_SUBTITLE_PRIORITIES = "cs_desktop_sub_lang_priorities"

    val DEFAULT_AUDIO_STACK = listOf(
        "eng,en",
        "original",
    )

    val DEFAULT_SUBTITLE_STACK = listOf(
        "eng,en",
    )

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _audioLanguageStack = MutableStateFlow<List<String>>(DEFAULT_AUDIO_STACK)
    val audioLanguageStack: StateFlow<List<String>> = _audioLanguageStack.asStateFlow()

    private val _subtitleLanguageStack = MutableStateFlow<List<String>>(DEFAULT_SUBTITLE_STACK)
    val subtitleLanguageStack: StateFlow<List<String>> = _subtitleLanguageStack.asStateFlow()

    // Backward-compatibility maps for external observers
    private val _audioPriorities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val audioPriorities: StateFlow<Map<String, Int>> = _audioPriorities.asStateFlow()

    private val _subtitlePriorities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val subtitlePriorities: StateFlow<Map<String, Int>> = _subtitlePriorities.asStateFlow()

    init {
        loadPriorities()
    }

    fun loadPriorities() {
        try {
            // Audio Stack
            val savedAudioStack = DesktopDataStore.getKey<List<String>>(PREF_AUDIO_LANGUAGE_STACK)
            if (!savedAudioStack.isNullOrEmpty()) {
                _audioLanguageStack.value = savedAudioStack
            } else {
                val legacyAudio = DesktopDataStore.getKey<Map<String, Int>>(PREF_LEGACY_AUDIO_PRIORITIES)
                if (!legacyAudio.isNullOrEmpty()) {
                    val migrated = legacyAudio.filter { it.value > 0 }.toList().sortedByDescending { it.second }.map { it.first }
                    if (migrated.isNotEmpty()) {
                        _audioLanguageStack.value = migrated
                        persistAudioStack(migrated)
                    } else {
                        _audioLanguageStack.value = DEFAULT_AUDIO_STACK
                    }
                } else {
                    val legacySingle = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_AUDIO_LANG)
                    if (!legacySingle.isNullOrBlank() && legacySingle != "auto") {
                        _audioLanguageStack.value = listOf(legacySingle)
                    } else {
                        _audioLanguageStack.value = DEFAULT_AUDIO_STACK
                    }
                }
            }

            // Subtitle Stack
            val savedSubStack = DesktopDataStore.getKey<List<String>>(PREF_SUBTITLE_LANGUAGE_STACK)
            if (!savedSubStack.isNullOrEmpty()) {
                _subtitleLanguageStack.value = savedSubStack
            } else {
                val legacySub = DesktopDataStore.getKey<Map<String, Int>>(PREF_LEGACY_SUBTITLE_PRIORITIES)
                if (!legacySub.isNullOrEmpty()) {
                    val migrated = legacySub.filter { it.value > 0 }.toList().sortedByDescending { it.second }.map { it.first }
                    if (migrated.isNotEmpty()) {
                        _subtitleLanguageStack.value = migrated
                        persistSubtitleStack(migrated)
                    } else {
                        _subtitleLanguageStack.value = DEFAULT_SUBTITLE_STACK
                    }
                } else {
                    val legacySingle = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_SUB_LANG)
                    if (!legacySingle.isNullOrBlank() && legacySingle != "auto" && legacySingle != "off") {
                        _subtitleLanguageStack.value = listOf(legacySingle)
                    } else {
                        _subtitleLanguageStack.value = DEFAULT_SUBTITLE_STACK
                    }
                }
            }

            updateLegacyMaps()
        } catch (e: Exception) {
            AppLogger.e("LanguagePriorityHelper", "Failed to load language priorities", e)
        }
    }

    private fun updateLegacyMaps() {
        val audioList = _audioLanguageStack.value
        _audioPriorities.value = audioList.mapIndexed { index, code ->
            code to (audioList.size - index).coerceIn(1, 15)
        }.toMap()

        val subList = _subtitleLanguageStack.value
        _subtitlePriorities.value = subList.mapIndexed { index, code ->
            code to (subList.size - index).coerceIn(1, 15)
        }.toMap()
    }

    private fun persistAudioStack(stack: List<String>) {
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PREF_AUDIO_LANGUAGE_STACK, stack)
        }
    }

    private fun persistSubtitleStack(stack: List<String>) {
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PREF_SUBTITLE_LANGUAGE_STACK, stack)
        }
    }

    fun moveAudio(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _audioLanguageStack.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _audioLanguageStack.value = current
            updateLegacyMaps()
            persistAudioStack(current)
        }
    }

    fun moveAudioUp(code: String) {
        val current = _audioLanguageStack.value.toMutableList()
        val index = current.indexOf(code)
        if (index > 0) {
            moveAudio(index, index - 1)
        }
    }

    fun moveAudioDown(code: String) {
        val current = _audioLanguageStack.value.toMutableList()
        val index = current.indexOf(code)
        if (index >= 0 && index < current.size - 1) {
            moveAudio(index, index + 1)
        }
    }

    fun addAudioToStack(code: String) {
        val current = _audioLanguageStack.value.toMutableList()
        if (!current.contains(code)) {
            current.add(code)
            _audioLanguageStack.value = current
            updateLegacyMaps()
            persistAudioStack(current)
        }
    }

    fun removeAudioFromStack(code: String) {
        val current = _audioLanguageStack.value.toMutableList()
        if (current.remove(code)) {
            _audioLanguageStack.value = current
            updateLegacyMaps()
            persistAudioStack(current)
        }
    }

    fun setAudioStack(stack: List<String>) {
        val cleaned = stack.distinct()
        _audioLanguageStack.value = cleaned
        updateLegacyMaps()
        persistAudioStack(cleaned)
    }

    fun moveSubtitle(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _subtitleLanguageStack.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _subtitleLanguageStack.value = current
            updateLegacyMaps()
            persistSubtitleStack(current)
        }
    }

    fun moveSubtitleUp(code: String) {
        val current = _subtitleLanguageStack.value.toMutableList()
        val index = current.indexOf(code)
        if (index > 0) {
            moveSubtitle(index, index - 1)
        }
    }

    fun moveSubtitleDown(code: String) {
        val current = _subtitleLanguageStack.value.toMutableList()
        val index = current.indexOf(code)
        if (index >= 0 && index < current.size - 1) {
            moveSubtitle(index, index + 1)
        }
    }

    fun addSubtitleToStack(code: String) {
        val current = _subtitleLanguageStack.value.toMutableList()
        if (!current.contains(code)) {
            current.add(code)
            _subtitleLanguageStack.value = current
            updateLegacyMaps()
            persistSubtitleStack(current)
        }
    }

    fun removeSubtitleFromStack(code: String) {
        val current = _subtitleLanguageStack.value.toMutableList()
        if (current.remove(code)) {
            _subtitleLanguageStack.value = current
            updateLegacyMaps()
            persistSubtitleStack(current)
        }
    }

    fun setSubtitleStack(stack: List<String>) {
        val cleaned = stack.distinct()
        _subtitleLanguageStack.value = cleaned
        updateLegacyMaps()
        persistSubtitleStack(cleaned)
    }

    fun getAudioPriority(code: String): Int {
        val list = _audioLanguageStack.value
        val idx = list.indexOf(code)
        return if (idx >= 0) (list.size - idx).coerceIn(1, 15) else 0
    }

    fun setAudioPriority(code: String, priority: Int) {
        if (priority > 0) {
            addAudioToStack(code)
        } else {
            removeAudioFromStack(code)
        }
    }

    fun getSubtitlePriority(code: String): Int {
        val list = _subtitleLanguageStack.value
        val idx = list.indexOf(code)
        return if (idx >= 0) (list.size - idx).coerceIn(1, 15) else 0
    }

    fun setSubtitlePriority(code: String, priority: Int) {
        if (priority > 0) {
            addSubtitleToStack(code)
        } else {
            removeSubtitleFromStack(code)
        }
    }

    fun getOrderedAudioLanguages(): List<String> = _audioLanguageStack.value

    fun getOrderedSubtitleLanguages(): List<String> = _subtitleLanguageStack.value

    fun getMpvAlangString(): String {
        val ordered = getOrderedAudioLanguages()
        return ordered.flatMap { code ->
            if (code == "original") listOf("original", "orig") else code.split(",").map { it.trim() }
        }.distinct().joinToString(",")
    }

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
        setAudioStack(DEFAULT_AUDIO_STACK)
    }

    fun resetSubtitleDefaults() {
        setSubtitleStack(DEFAULT_SUBTITLE_STACK)
    }

    fun setAudioPreset(stack: List<String>) {
        setAudioStack(stack)
    }

    fun setSubtitlePreset(stack: List<String>) {
        setSubtitleStack(stack)
    }

    fun setAudioPreset(priorities: Map<String, Int>) {
        val ordered = priorities.filter { it.value > 0 }.toList().sortedByDescending { it.second }.map { it.first }
        setAudioStack(ordered)
    }

    fun setSubtitlePreset(priorities: Map<String, Int>) {
        val ordered = priorities.filter { it.value > 0 }.toList().sortedByDescending { it.second }.map { it.first }
        setSubtitleStack(ordered)
    }
}
