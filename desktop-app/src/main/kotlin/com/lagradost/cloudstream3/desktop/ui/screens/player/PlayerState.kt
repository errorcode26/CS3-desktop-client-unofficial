package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.desktop.player.DesktopMpvEngine
import com.lagradost.cloudstream3.desktop.player.MpvLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerState {
    internal val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    internal val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    internal val _bufferMs = MutableStateFlow(0L)
    val bufferMs: StateFlow<Long> = _bufferMs.asStateFlow()
    internal val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    internal val _isBuffering = MutableStateFlow(true)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    internal val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()
    internal val _volume = MutableStateFlow(100f) // 0 to 130 in MPV usually, let's say 0 to 100
    val volume: StateFlow<Float> = _volume.asStateFlow()
    internal val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    internal val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    internal val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls.asStateFlow()
    internal val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs.asStateFlow()
    internal val _isInterpolationEnabled = MutableStateFlow(
        com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_INTERPOLATION) ?: false,
    )
    val isInterpolationEnabled: StateFlow<Boolean> = _isInterpolationEnabled.asStateFlow()

    internal val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun showToast(message: String) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch {
            _toastMessage.value = message
            kotlinx.coroutines.delay(4000)
            _toastMessage.compareAndSet(message, null)
        }
    }

    data class VideoTrack(
        val id: Int,
        val name: String,
        val isSelected: Boolean,
    )

    data class Chapter(
        val index: Int,
        val title: String,
        val timeMs: Long,
    )

    internal val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<VideoTrack>> = _subtitleTracks.asStateFlow()
    internal val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks: StateFlow<List<VideoTrack>> = _audioTracks.asStateFlow()
    internal val _videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList()) // New State for Qualities
    val videoTracks: StateFlow<List<VideoTrack>> = _videoTracks.asStateFlow()
    internal val _isAudioOnlyStream = MutableStateFlow(false)
    val isAudioOnlyStream: StateFlow<Boolean> = _isAudioOnlyStream.asStateFlow()
    internal val _isAudioMode = MutableStateFlow(false)
    val isAudioMode: StateFlow<Boolean> = _isAudioMode.asStateFlow()
    internal val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()
    internal val _currentChapterIndex = MutableStateFlow<Int>(-1)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()
    internal val _activeLazyVideoTrackUrl = MutableStateFlow<String?>(null)
    val activeLazyVideoTrackUrl: StateFlow<String?> = _activeLazyVideoTrackUrl.asStateFlow()

    internal val _skipIntervals = MutableStateFlow<List<com.lagradost.cloudstream3.desktop.player.skip.SkipInterval>>(emptyList())
    val skipIntervals: StateFlow<List<com.lagradost.cloudstream3.desktop.player.skip.SkipInterval>> = _skipIntervals.asStateFlow()

    internal val _activeSkipInterval = MutableStateFlow<com.lagradost.cloudstream3.desktop.player.skip.SkipInterval?>(null)
    val activeSkipInterval: StateFlow<com.lagradost.cloudstream3.desktop.player.skip.SkipInterval?> = _activeSkipInterval.asStateFlow()

    internal val _activeShader = MutableStateFlow<String>(
        com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ACTIVE_SHADER) ?: "None",
    )
    val activeShader: StateFlow<String> = _activeShader.asStateFlow()

    // Video Stats
    internal val _videoCodec = MutableStateFlow("")
    val videoCodec: StateFlow<String> = _videoCodec.asStateFlow()
    internal val _audioCodec = MutableStateFlow("")
    val audioCodec: StateFlow<String> = _audioCodec.asStateFlow()
    internal val _hwdecCurrent = MutableStateFlow("")
    val hwdecCurrent: StateFlow<String> = _hwdecCurrent.asStateFlow()
    internal val _droppedFrames = MutableStateFlow(0L)
    val droppedFrames: StateFlow<Long> = _droppedFrames.asStateFlow()
    internal val _fps = MutableStateFlow(0.0)
    val fps: StateFlow<Double> = _fps.asStateFlow()
    internal val _resolution = MutableStateFlow("")
    val resolution: StateFlow<String> = _resolution.asStateFlow()
    internal val _videoBitrate = MutableStateFlow(0L)
    val videoBitrate: StateFlow<Long> = _videoBitrate.asStateFlow()
    internal val _audioBitrate = MutableStateFlow(0L)
    val audioBitrate: StateFlow<Long> = _audioBitrate.asStateFlow()
    internal val _showStats = MutableStateFlow(false)
    val showStats: StateFlow<Boolean> = _showStats.asStateFlow()

    private var engine: DesktopMpvEngine? = null
    val attachedEngine: DesktopMpvEngine? get() = engine

    internal var lastSeekTime = 0L
    internal var targetSeekMs = -1L

    fun isSeekingInProgress(): Boolean {
        val now = System.currentTimeMillis()
        return targetSeekMs != -1L || (now - lastSeekTime < 2000L)
    }

    fun attachEngine(engine: DesktopMpvEngine) {
        this.engine = engine
        engine.playerState = this
    }

    fun detachEngine() {
        if (this.engine?.playerState == this) {
            this.engine?.playerState = null
        }
        this.engine = null
    }

    fun attachMpv(handle: com.sun.jna.Pointer) {
        // Kept for backward compatibility during migration
    }

    fun detachMpv() {
        // Kept for backward compatibility during migration
    }

    fun getNativeHandleValue(): Long? = engine?.nativeHandleValue

    /**
     * Resets all playback state for a new stream load.
     * Call this before ComposeMpvPlayer loads a new URL so the UI shows correct initial state.
     */
    internal val _activeLazyAudioTrackUrl = MutableStateFlow<String?>(null)
    val activeLazyAudioTrackUrl: StateFlow<String?> = _activeLazyAudioTrackUrl.asStateFlow()

    private val processedAutoSkipIntervals = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile var hasUserManuallyChangedSub = false

    fun reset() {
        hasUserManuallyChangedSub = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _bufferMs.value = 0L
        _isPaused.value = false
        _isBuffering.value = true
        _isProbing.value = false
        _isMuted.value = false
        lastSeekTime = 0L
        targetSeekMs = -1L
        processedAutoSkipIntervals.clear()
        _activeLazyVideoTrackUrl.value = null
        _activeLazyAudioTrackUrl.value = null
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _videoTracks.value = emptyList()
        _skipIntervals.value = emptyList()
        _activeSkipInterval.value = null
        _isAudioOnlyStream.value = false
        _isAudioMode.value = false
        com.lagradost.player.impl.proxy.LocalStreamProxyState.reset()
    }

    fun togglePlayPause() {
        engine?.togglePause()
        _isPaused.value = !isPaused.value
    }

    fun pause() {
        engine?.pause()
        _isPaused.value = true
    }

    fun play() {
        engine?.play()
        _isPaused.value = false
    }

    fun seekTo(positionMs: Long) {
        lastSeekTime = System.currentTimeMillis()
        targetSeekMs = positionMs
        val currentIntervals = _skipIntervals.value
        currentIntervals.forEach { inv ->
            if (positionMs >= inv.startMs && positionMs < inv.endMs) {
                processedAutoSkipIntervals.add("${inv.startMs}_${inv.endMs}_${inv.type}")
            }
        }
        engine?.seekTo(positionMs)
        this._positionMs.value = positionMs
    }

    /**
     * Updates internal tracking state when a seek was already executed by the native bridge fast-path.
     */
    fun notifySeekTo(positionMs: Long) {
        lastSeekTime = System.currentTimeMillis()
        targetSeekMs = positionMs
        val currentIntervals = _skipIntervals.value
        currentIntervals.forEach { inv ->
            if (positionMs >= inv.startMs && positionMs < inv.endMs) {
                processedAutoSkipIntervals.add("${inv.startMs}_${inv.endMs}_${inv.type}")
            }
        }
        this._positionMs.value = positionMs
    }

    fun seekBy(offsetMs: Long) {
        lastSeekTime = System.currentTimeMillis()
        targetSeekMs = this.positionMs.value + offsetMs
        engine?.seekBy(offsetMs)
        this._positionMs.value = targetSeekMs
    }

    /**
     * Updates internal tracking state when a relative seek was already executed by the native bridge fast-path.
     */
    fun notifySeekBy(offsetMs: Long) {
        lastSeekTime = System.currentTimeMillis()
        targetSeekMs = this.positionMs.value + offsetMs
        this._positionMs.value = targetSeekMs
    }

    fun setVolume(volume: Float) {
        val isVolumeMaxOn = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_VOLUME_MAX) ?: false
        val maxVol = if (isVolumeMaxOn) 200f else 100f
        val coerced = volume.coerceIn(0f, maxVol)
        _volume.value = coerced
        engine?.setVolume(coerced.toDouble())
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        engine?.setSpeed(speed.toDouble())
    }

    fun setSpeed(speed: Float) = setPlaybackSpeed(speed)

    // Called by the native event observer loop
    // Smart debounced to prevent stale time-pos events from reverting the slider visually right after a seek.
    fun updatePositionFromPlayer(posMs: Long) {
        val now = System.currentTimeMillis()
        if (targetSeekMs != -1L) {
            // We recently sought to targetSeekMs.
            // If the player's reported posMs is within 2 seconds of targetSeekMs,
            // we consider the seek "completed" and resume normal updates.
            if (kotlin.math.abs(posMs - targetSeekMs) < 2000L) {
                targetSeekMs = -1L
            } else if (now - lastSeekTime < 10000L) {
                // If it's not close to the target, AND we are within a 10-second grace period,
                // it means the player is still reporting the OLD time or is still buffering.
                // IGNORE this update to prevent rubber-banding.
                return
            } else {
                // 10 seconds have passed and it's STILL not close to the target.
                // The seek probably failed, was queued behind another, or we hit EOF. Reset and accept.
                targetSeekMs = -1L
            }
        }

        this._positionMs.value = posMs

        // Evaluate active skip interval & auto-skip
        val intervals = _skipIntervals.value
        if (intervals.isNotEmpty()) {
            val matching = intervals.firstOrNull { posMs >= it.startMs && posMs < it.endMs }
            _activeSkipInterval.value = matching

            if (matching != null && !isSeekingInProgress()) {
                val autoSkipIntro = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.autoSkipIntro.value
                    || (com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_SKIP_INTRO) ?: false)
                val autoSkipOutro = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.autoSkipOutro.value
                    || (com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_SKIP_OUTRO) ?: false)

                val shouldAutoSkip = when (matching.type) {
                    com.lagradost.cloudstream3.desktop.player.skip.SkipType.OPENING,
                    com.lagradost.cloudstream3.desktop.player.skip.SkipType.INTRO,
                    com.lagradost.cloudstream3.desktop.player.skip.SkipType.RECAP,
                    com.lagradost.cloudstream3.desktop.player.skip.SkipType.MIXED_OP -> autoSkipIntro

                    com.lagradost.cloudstream3.desktop.player.skip.SkipType.ENDING,
                    com.lagradost.cloudstream3.desktop.player.skip.SkipType.OUTRO,
                    com.lagradost.cloudstream3.desktop.player.skip.SkipType.PREVIEW,
                    com.lagradost.cloudstream3.desktop.player.skip.SkipType.MIXED_ED -> autoSkipOutro
                }

                val intervalKey = "${matching.startMs}_${matching.endMs}_${matching.type}"
                if (shouldAutoSkip && !processedAutoSkipIntervals.contains(intervalKey)) {
                    processedAutoSkipIntervals.add(intervalKey)
                    com.lagradost.common.logging.AppLogger.i("PlayerState", "Auto-skipping ${matching.label} to ${matching.endMs}ms")
                    skipCurrentInterval()
                }
            }
        } else {
            _activeSkipInterval.value = null
        }
    }

    fun skipCurrentInterval() {
        val currentPos = _positionMs.value
        val interval = _activeSkipInterval.value
            ?: _skipIntervals.value.firstOrNull { currentPos >= it.startMs && currentPos < it.endMs }
            ?: return
        val target = interval.endMs + 100L
        val dur = _durationMs.value
        val safeTarget = if (dur > 0) kotlin.math.min(target, dur - 1000L) else target
        seekTo(safeTarget)
        showToast("Skipped ${interval.label}")
    }

    fun loadSkipIntervals(
        title: String,
        episode: Int = 1,
        season: Int = 1,
        durationSeconds: Double = 0.0,
        malId: Int? = null,
        tmdbId: Int? = null,
        imdbId: String? = null
    ) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch {
            try {
                val query = com.lagradost.cloudstream3.desktop.player.skip.SkipQuery(
                    title = title,
                    episode = episode,
                    season = season,
                    durationSeconds = durationSeconds,
                    malId = malId,
                    tmdbId = tmdbId,
                    imdbId = imdbId
                )
                val intervals = com.lagradost.cloudstream3.desktop.player.skip.SkipManager.resolveSkipIntervals(
                    query = query,
                    chapters = _chapters.value,
                    totalDurationMs = _durationMs.value
                )
                _skipIntervals.value = intervals
                val currentPos = _positionMs.value
                _activeSkipInterval.value = intervals.firstOrNull { currentPos >= it.startMs && currentPos < it.endMs }
                com.lagradost.common.logging.AppLogger.i("PlayerState", "Skip intervals updated: ${intervals.size} intervals active for '$title'")
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.i("PlayerState", "Failed to load skip intervals: ${e.message}")
            }
        }
    }

    fun updateChaptersFromPlayer(chapters: List<Chapter>) {
        this._chapters.value = chapters
        if (_skipIntervals.value.isEmpty() && chapters.isNotEmpty()) {
            val chapterIntervals = com.lagradost.cloudstream3.desktop.player.skip.ChapterSkipProvider.parseChapters(chapters, _durationMs.value)
            if (chapterIntervals.isNotEmpty()) {
                _skipIntervals.value = chapterIntervals
                val currentPos = _positionMs.value
                _activeSkipInterval.value = chapterIntervals.firstOrNull { currentPos >= it.startMs && currentPos < it.endMs }
            }
        }
    }

    fun updateDurationFromPlayer(durMs: Long) {
        if (durMs > 0 && this.durationMs.value != durMs) {
            this._durationMs.value = durMs
            if (_skipIntervals.value.isEmpty() && _chapters.value.isNotEmpty()) {
                val chapterIntervals = com.lagradost.cloudstream3.desktop.player.skip.ChapterSkipProvider.parseChapters(_chapters.value, durMs)
                if (chapterIntervals.isNotEmpty()) {
                    _skipIntervals.value = chapterIntervals
                    val currentPos = _positionMs.value
                    _activeSkipInterval.value = chapterIntervals.firstOrNull { currentPos >= it.startMs && currentPos < it.endMs }
                }
            }
        }
    }

    fun cycleSubtitles() {
        engine?.executeCommand("cycle sub")
    }

    fun toggleSubtitleVisibility() {
        engine?.executeCommand("cycle sub-visibility")
    }

    fun seekLive() {
        engine?.executeCommand("seek 100 absolute-percent")
        play()
    }

    fun executeCommand(command: String) {
        engine?.executeCommand(command)
    }

    fun takeScreenshot(filepath: String) {
        // "screenshot-to-file" takes two arguments: <filename> [subtitles/video/window]
        // We use 'window' to get exactly what the user sees (or 'video' for raw frames).
        engine?.executeCommand("screenshot-to-file \"$filepath\" window")
    }

    fun toggleMute() {
        val nextMuted = !isMuted.value
        _isMuted.value = nextMuted
        engine?.setMute(nextMuted)
    }

    fun setMute(isMuted: Boolean) {
        _isMuted.value = isMuted
        engine?.setMute(isMuted)
    }

    fun setInterpolation(enabled: Boolean) {
        _isInterpolationEnabled.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_INTERPOLATION, enabled)
        }
        if (enabled) {
            engine?.setPropertyString("video-sync", "display-resample")
            engine?.setPropertyString("interpolation", "yes")
            engine?.setPropertyString("tscale", "oversample")
        } else {
            engine?.setPropertyString("video-sync", "audio")
            engine?.setPropertyString("interpolation", "no")
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        val delaySec = delayMs / 1000.0
        engine?.setPropertyString("sub-delay", delaySec.toString())
        _subtitleDelayMs.value = delayMs
    }

    fun setSubtitleFont(fontName: String?) {
        if (!fontName.isNullOrBlank()) {
            engine?.setPropertyString("sub-font", fontName)
        } else {
            engine?.setPropertyString("sub-font", "sans-serif")
        }

        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_FONT, fontName ?: "")
            val overrideEnabled = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ENABLE_SUB_OVERRIDE) ?: true
            if (overrideEnabled) {
                engine?.setPropertyString("sub-ass-override", "force")
            } else {
                engine?.setPropertyString("sub-ass-override", "no")
            }
        }
    }

    fun setSubtitleOverrideEnabled(enabled: Boolean) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ENABLE_SUB_OVERRIDE, enabled)
        }
        if (enabled) {
            engine?.setPropertyString("sub-ass-override", "force")
        } else {
            engine?.setPropertyString("sub-ass-override", "no")
        }
    }

    fun setSubtitleBackground(backgroundKey: String) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BG, backgroundKey)
        }
        val (mpvBgColor, borderStyle) = com.lagradost.cloudstream3.desktop.player.PlayerConfig.toMpvBackgroundColor(backgroundKey)
        engine?.setPropertyString("sub-back-color", mpvBgColor)
        engine?.setPropertyString("sub-border-style", borderStyle)
    }

    fun setSubtitlePosition(pos: Int) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_POS, pos.toString())
        }
        engine?.setPropertyString("sub-pos", pos.toString())
    }

    fun setSubFilterSdh(enabled: Boolean) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_REMOVE_CAPTIONS, enabled)
        }
        if (enabled) {
            engine?.setPropertyString("sub-filter-sdh", "yes")
            engine?.setPropertyString("sub-filter-sdh-harder", "yes")
            engine?.setPropertyString("sub-filter-sdh-enclosures", "(),[],{}")
        } else {
            engine?.setPropertyString("sub-filter-sdh", "no")
        }
    }

    fun setSubtitleTrack(id: Int?) {
        hasUserManuallyChangedSub = true
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (id == null) {
                engine?.setPropertyString("sid", "no")
            } else {
                engine?.setPropertyString("sid", id.toString())
                engine?.setPropertyString("sub-visibility", "yes")
            }
        }
    }

    fun setAudioTrack(id: Int?) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (id == null) {
                engine?.setPropertyString("aid", "no")
            } else {
                engine?.setPropertyString("aid", id.toString())
            }
        }
    }

    fun setVideoTrack(id: Int?) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (id == null) {
                engine?.setPropertyString("vid", "auto")
                // Reset HLS bitrate for streams
                engine?.setPropertyString("hls-bitrate", "max")
            } else {
                engine?.setPropertyString("vid", id.toString())
            }
        }
    }

    fun toggleAudioMode(forcedState: Boolean? = null) {
        val next = forcedState ?: !_isAudioMode.value
        _isAudioMode.value = next
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (next) {
                engine?.setPropertyString("vid", "no")
            } else {
                engine?.setPropertyString("vid", "auto")
            }
        }
    }

    fun setShader(shaderName: String) {
        _activeShader.value = shaderName
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(
                com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ACTIVE_SHADER,
                shaderName,
            )
        }

        if (shaderName.isBlank() || shaderName == "None") {
            engine?.setPropertyString("glsl-shaders", "")
            com.lagradost.common.logging.AppLogger.i("PlayerState: Cleared active shaders")
        } else {
            val shaderFile = java.io.File(com.lagradost.common.platform.PlatformPaths.shadersDir, shaderName)
            if (shaderFile.exists()) {
                engine?.setPropertyString("glsl-shaders", shaderFile.absolutePath)
                com.lagradost.common.logging.AppLogger.i("PlayerState: Applied shader ${shaderFile.absolutePath}")
            } else {
                com.lagradost.common.logging.AppLogger.w("PlayerState: Shader file not found: ${shaderFile.absolutePath}")
            }
        }
    }

    data class LazyTrack(
        val url: String,
        val name: String,
        val language: String,
        val bitrate: Int? = null,
    )

    fun loadLazyAudioTrack(track: LazyTrack) {
        val safeUrl = track.url.replace("\\", "\\\\").replace("\"", "\\\"")
        val safeName = track.name.replace("\\", "\\\\").replace("\"", "\\\"")
        val safeLang = track.language.replace("\\", "\\\\").replace("\"", "\\\"")
        // MPV command: audio-add <url> select <title> <lang>
        val cmd = "audio-add \"$safeUrl\" select \"$safeName\" \"$safeLang\""
        engine?.executeCommand(cmd)
        _activeLazyAudioTrackUrl.value = track.url
    }

    fun loadLazySubtitleTrack(track: LazyTrack) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val cleanPath = if (track.url.startsWith("http", ignoreCase = true)) {
                    com.lagradost.cloudstream3.desktop.player.SubtitleExtractionService.downloadAndExtractSubtitle(
                        idPrefix = "stream_extractor",
                        data = track.url,
                        name = track.name,
                        lang = track.language,
                        source = "Stream",
                    )
                } else {
                    track.url.replace("\\", "/")
                }

                if (cleanPath == null || !java.io.File(cleanPath).exists() || java.io.File(cleanPath).length() == 0L) {
                    com.lagradost.common.logging.AppLogger.w("PlayerState", "Failed to extract lazy subtitle: ${track.name}")
                    showToast("Failed to load subtitle: ${track.name}")
                    return@launch
                }

                val safeName = track.name.replace("\"", "").trim()
                val safeLang = track.language.replace("\"", "").trim()
                val cmd = "sub-add \"$cleanPath\" select \"$safeName\" \"$safeLang\""
                engine?.executeCommand(cmd)
                engine?.setPropertyString("sub-visibility", "yes")

                val proxyState = com.lagradost.player.impl.proxy.LocalStreamProxyState
                proxyState.lazySubtitleTracks.value = proxyState.lazySubtitleTracks.value.filter { t -> t.url != track.url }
                showToast("Loaded subtitle: $safeName")
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("PlayerState", "Failed to load lazy subtitle: ${e.message}", e)
                showToast("Failed to load subtitle: ${track.name}")
            }
        }
    }

    fun loadLazyVideoTrack(track: LazyTrack) {
        val currentPos = _positionMs.value / 1000.0
        val currentAudioUrl = _activeLazyAudioTrackUrl.value
        val currentLazyAudio = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.value.find { it.url == currentAudioUrl }
            ?: com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.value.firstOrNull()

        try {
            engine?.setPropertyString("start", currentPos.toString())
            engine?.executeCommandArray(arrayOf("loadfile", track.url, "replace", null))
            _activeLazyVideoTrackUrl.value = track.url

            if (currentLazyAudio != null) {
                val safeAudioUrl = currentLazyAudio.url.replace("\\", "\\\\").replace("\"", "\\\"")
                val safeAudioName = currentLazyAudio.name.replace("\\", "\\\\").replace("\"", "\\\"")
                val safeAudioLang = currentLazyAudio.language.replace("\\", "\\\\").replace("\"", "\\\"")
                val audioCmd = "audio-add \"$safeAudioUrl\" select \"$safeAudioName\" \"$safeAudioLang\""
                engine?.executeCommand(audioCmd)
                _activeLazyAudioTrackUrl.value = currentLazyAudio.url
            }
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("PlayerState", "Failed to switch video track: ${e.message}", e)
        }
    }

    fun loadExternalSubtitle(url: String) {
        val safeUrl = url.replace("\\", "/").replace("\"", "\\\"")
        val cmd = "sub-add \"$safeUrl\" select"
        engine?.executeCommand(cmd)
    }

    internal val _aspectRatioMode = MutableStateFlow(0) // 0=Fit, 1=Fill, 2=Crop
    val aspectRatioMode: StateFlow<Int> = _aspectRatioMode.asStateFlow()

    fun cycleAspectRatio() {
        val nextMode = (aspectRatioMode.value + 1) % 3
        _aspectRatioMode.value = nextMode
        when (nextMode) {
            0 -> { // Fit
                engine?.setPropertyString("video-aspect-override", "no")
                engine?.setPropertyString("panscan", "0.0")
            }
            1 -> { // Fill/Stretch
                engine?.setPropertyString("video-aspect-override", "window")
                engine?.setPropertyString("panscan", "0.0")
            }
            2 -> { // Crop
                engine?.setPropertyString("video-aspect-override", "no")
                engine?.setPropertyString("panscan", "1.0")
            }
        }
    }

    fun nextChapter() {
        engine?.executeCommand("add chapter 1")
    }

    fun previousChapter() {
        engine?.executeCommand("add chapter -1")
    }

    fun seekToChapter(index: Int) {
        engine?.executeCommand("set chapter $index")
    }

    fun setMpvProperty(property: String, value: String) {
        engine?.setPropertyString(property, value)
    }

    fun updateAudioFilters() {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val filters = mutableListOf<String>()

            val audioNorm = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_NORMALIZATION) ?: false
            if (audioNorm) {
                val strength = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_NORM_STRENGTH) ?: "Medium"
                when (strength) {
                    "Low" -> {
                        // Subtle Studio Compression: Broadcast RMS compressor with lookahead safety limiter
                        filters.add("lavfi=[acompressor=threshold=-20dB:ratio=3:attack=20:release=250:makeup=2,alimiter=limit=0.9:attack=5:release=50:asc=1:level=disabled]")
                    }
                    "Aggressive" -> {
                        // Night Mode: Elevates quiet dialogue while strictly clamping explosions & gunshots
                        filters.add("lavfi=[dynaudnorm=f=150:g=15:p=0.9:m=10:r=0.9:b=1:c=1,alimiter=limit=0.65:attack=5:release=50:asc=1:level=disabled]")
                    }
                    else -> {
                        // Balanced Leveling: Smooth dynamic leveling with coupled stereo channels and lookahead peak limiter
                        filters.add("lavfi=[dynaudnorm=f=200:g=21:p=0.95:m=6:r=0.9:b=1:c=1,alimiter=limit=0.8:attack=5:release=50:asc=1:level=disabled]")
                    }
                }
            }

            val spatialAudio = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_SPATIAL) ?: false
            if (spatialAudio) {
                // Psychoacoustic stereo widener that preserves phantom center dialogue
                filters.add("lavfi=[stereowiden=crossfeed=0.3:feedback=0.4]")
            }

            val eqPreset = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_EQ_PRESET) ?: "Flat"
            when (eqPreset) {
                "Bass Boost" -> filters.add("lavfi=[bass=g=6:f=75:width_type=h:width=45]")
                "Vocal Boost" -> filters.add("lavfi=[highpass=f=85,equalizer=f=3000:width_type=q:width=1.2:g=5]")
                "Cinematic" -> filters.add("lavfi=[bass=g=4:f=70:width_type=h:width=40,equalizer=f=3200:width_type=q:width=1.5:g=2.5,treble=g=3:f=9000]")
            }

            engine?.setPropertyString("af", filters.joinToString(","))
        }
    }
}
