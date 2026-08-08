package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.desktop.player.MpvLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    internal val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<VideoTrack>> = _subtitleTracks.asStateFlow()
    internal val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks: StateFlow<List<VideoTrack>> = _audioTracks.asStateFlow()
    internal val _videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList()) // New State for Qualities
    val videoTracks: StateFlow<List<VideoTrack>> = _videoTracks.asStateFlow()
    internal val _activeLazyVideoTrackUrl = MutableStateFlow<String?>(null)
    val activeLazyVideoTrackUrl: StateFlow<String?> = _activeLazyVideoTrackUrl.asStateFlow()

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

    private var mpvHandle: com.sun.jna.Pointer? = null
    internal var lastSeekTime = 0L
    internal var targetSeekMs = -1L

    fun isSeekingInProgress(): Boolean {
        val now = System.currentTimeMillis()
        return targetSeekMs != -1L || (now - lastSeekTime < 2000L)
    }

    fun attachMpv(handle: com.sun.jna.Pointer) {
        mpvHandle = handle
    }

    fun detachMpv() {
        mpvHandle = null
    }

    /**
     * Resets all playback state for a new stream load.
     * Call this before ComposeMpvPlayer loads a new URL so the UI shows correct initial state.
     */
    fun reset() {
        _positionMs.value = 0L
        _durationMs.value = 0L
        _bufferMs.value = 0L
        _isPaused.value = false
        _isBuffering.value = true
        _isProbing.value = false
        _isMuted.value = false
        lastSeekTime = 0L
        targetSeekMs = -1L
        _activeLazyVideoTrackUrl.value = null
    }

    fun togglePlayPause() {
        mpvHandle?.let {
            val currentlyPaused = isPaused.value
            val nextState = if (currentlyPaused) "no" else "yes"
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "pause", nextState)
            // State will be updated by the observer loop in ComposeMpvPlayer
            _isPaused.value = !currentlyPaused
        }
    }

    fun pause() {
        mpvHandle?.let {
            val res = MpvLibrary.INSTANCE.mpv_set_property_string(it, "pause", "yes")
            com.lagradost.common.logging.AppLogger.i("PlayerState pause() set_property_string result: $res")
            if (res < 0) {
                MpvLibrary.INSTANCE.mpv_command_string(it, "set pause yes")
            }
            _isPaused.value = true
        }
    }

    fun play() {
        mpvHandle?.let {
            val res = MpvLibrary.INSTANCE.mpv_set_property_string(it, "pause", "no")
            com.lagradost.common.logging.AppLogger.i("PlayerState play() set_property_string result: $res")
            if (res < 0) {
                MpvLibrary.INSTANCE.mpv_command_string(it, "set pause no")
            }
            _isPaused.value = false
        }
    }

    fun seekTo(positionMs: Long) {
        mpvHandle?.let {
            lastSeekTime = System.currentTimeMillis()
            targetSeekMs = positionMs
            val posSec = positionMs / 1000.0
            // Use the seek command instead of setting time-pos directly.
            // For HLS with force-seekable=yes, mpv_set_property_string(time-pos) silently
            // no-ops when the target segment isn't in the demuxer cache — the slider moves
            // but the video doesn't. mpv_command_string(seek absolute) forces a demuxer
            // flush and a real network segment re-request.
            MpvLibrary.INSTANCE.mpv_command_string(it, "seek $posSec absolute")
            this._positionMs.value = positionMs
        }
    }

    fun seekBy(offsetMs: Long) {
        mpvHandle?.let {
            lastSeekTime = System.currentTimeMillis()
            targetSeekMs = this.positionMs.value + offsetMs
            val offsetSec = offsetMs / 1000.0
            MpvLibrary.INSTANCE.mpv_command_string(it, "seek $offsetSec relative+exact")
            this._positionMs.value = targetSeekMs
        }
    }

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
    }

    fun updateDurationFromPlayer(durMs: Long) {
        if (durMs > 0 && this.durationMs.value != durMs) {
            this._durationMs.value = durMs
        }
    }

    fun setSpeed(speed: Float) {
        mpvHandle?.let {
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "speed", speed.toString())
            _playbackSpeed.value = speed
        }
    }

    fun takeScreenshot(filepath: String) {
        mpvHandle?.let {
            // "screenshot-to-file" takes two arguments: <filename> [subtitles/video/window]
            // We use 'window' to get exactly what the user sees (or 'video' for raw frames).
            MpvLibrary.INSTANCE.mpv_command_string(it, "screenshot-to-file \"$filepath\" window")
        }
    }

    fun setVolume(vol: Float) {
        mpvHandle?.let {
            val safeVol = vol.coerceIn(0f, 130f)
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "volume", safeVol.toString())
            _volume.value = safeVol
        }
    }

    fun toggleMute() {
        mpvHandle?.let {
            val nextMuted = !isMuted.value
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "mute", if (nextMuted) "yes" else "no")
            _isMuted.value = nextMuted
        }
    }

    fun setInterpolation(enabled: Boolean) {
        com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_INTERPOLATION, enabled)
        _isInterpolationEnabled.value = enabled
        mpvHandle?.let {
            if (enabled) {
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "video-sync", "display-resample")
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "interpolation", "yes")
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "tscale", "oversample")
            } else {
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "video-sync", "audio")
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "interpolation", "no")
            }
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        mpvHandle?.let {
            val delaySec = delayMs / 1000.0
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "sub-delay", delaySec.toString())
            _subtitleDelayMs.value = delayMs
        }
    }

    fun setSubtitleFont(fontName: String?) {
        mpvHandle?.let {
            if (!fontName.isNullOrBlank()) {
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "sub-font", fontName)
            } else {
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "sub-font", "sans-serif")
            }

            val overrideEnabled = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ENABLE_SUB_OVERRIDE) ?: false
            if (overrideEnabled) {
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "sub-ass-override", "force")
            } else {
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "sub-ass-override", "no")
            }
        }
    }

    fun setSubtitleOverrideEnabled(enabled: Boolean) {
        com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ENABLE_SUB_OVERRIDE, enabled)
        mpvHandle?.let {
            if (enabled) {
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "sub-ass-override", "force")
            } else {
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "sub-ass-override", "no")
            }
        }
    }

    fun setSubtitleTrack(id: Int?) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mpvHandle?.let {
                if (id == null) {
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "sid", "no")
                } else {
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "sid", id.toString())
                }
            }
        }
    }

    fun setAudioTrack(id: Int?) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mpvHandle?.let {
                if (id == null) {
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "aid", "no")
                } else {
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "aid", id.toString())
                }
            }
        }
    }

    fun setVideoTrack(id: Int?) {
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mpvHandle?.let {
                if (id == null) {
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "vid", "auto")
                    // Reset HLS bitrate for streams
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "hls-bitrate", "max")
                } else {
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "vid", id.toString())
                }
            }
        }
    }

    fun setShader(shaderName: String) {
        com.lagradost.common.storage.DesktopDataStore.setKey(
            com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ACTIVE_SHADER,
            shaderName,
        )
        _activeShader.value = shaderName

        mpvHandle?.let { handle ->
            if (shaderName.isBlank() || shaderName == "None") {
                MpvLibrary.INSTANCE.mpv_set_property_string(handle, "glsl-shaders", "")
                com.lagradost.common.logging.AppLogger.i("PlayerState: Cleared active shaders")
            } else {
                val shaderFile = java.io.File(com.lagradost.common.platform.PlatformPaths.shadersDir, shaderName)
                if (shaderFile.exists()) {
                    MpvLibrary.INSTANCE.mpv_set_property_string(handle, "glsl-shaders", shaderFile.absolutePath)
                    com.lagradost.common.logging.AppLogger.i("PlayerState: Applied shader ${shaderFile.absolutePath}")
                } else {
                    com.lagradost.common.logging.AppLogger.w("PlayerState: Shader file not found: ${shaderFile.absolutePath}")
                }
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
        mpvHandle?.let {
            val safeUrl = track.url.replace("\\", "\\\\").replace("\"", "\\\"")
            val safeName = track.name.replace("\\", "\\\\").replace("\"", "\\\"")
            val safeLang = track.language.replace("\\", "\\\\").replace("\"", "\\\"")
            // MPV command: audio-add <url> select <title> <lang>
            val cmd = "audio-add \"$safeUrl\" select \"$safeName\" \"$safeLang\""
            MpvLibrary.INSTANCE.mpv_command_string(it, cmd)

            // Remove from proxy state to prevent TrackRevealer from adding it again and to hide it from UI
            val proxyState = com.lagradost.player.impl.proxy.LocalStreamProxyState
            proxyState.lazyAudioTracks.value = proxyState.lazyAudioTracks.value.filter { it.url != track.url }
        }
    }

    fun loadLazySubtitleTrack(track: LazyTrack) {
        mpvHandle?.let {
            val safeUrl = track.url.replace("\\", "\\\\").replace("\"", "\\\"")
            val safeName = track.name.replace("\\", "\\\\").replace("\"", "\\\"")
            val safeLang = track.language.replace("\\", "\\\\").replace("\"", "\\\"")
            // MPV command: sub-add <url> select <title> <lang>
            val cmd = "sub-add \"$safeUrl\" select \"$safeName\" \"$safeLang\""
            MpvLibrary.INSTANCE.mpv_command_string(it, cmd)

            val proxyState = com.lagradost.player.impl.proxy.LocalStreamProxyState
            proxyState.lazySubtitleTracks.value = proxyState.lazySubtitleTracks.value.filter { t -> t.url != track.url }
        }
    }

    fun loadLazyVideoTrack(track: LazyTrack) {
        mpvHandle?.let {
            if (track.bitrate != null) {
                // Native HLS bitrate switching (seamless!)
                MpvLibrary.INSTANCE.mpv_set_property_string(it, "hls-bitrate", track.bitrate.toString())
                _activeLazyVideoTrackUrl.value = track.url
            } else {
                val safeUrl = track.url.replace("\\", "\\\\").replace("\"", "\\\"")
                val safeName = track.name.replace("\\", "\\\\").replace("\"", "\\\"")
                val safeLang = track.language.replace("\\", "\\\\").replace("\"", "\\\"")
                // MPV command: video-add <url> select <title> <lang>
                val cmd = "video-add \"$safeUrl\" select \"$safeName\" \"$safeLang\""
                MpvLibrary.INSTANCE.mpv_command_string(it, cmd)

                val proxyState = com.lagradost.player.impl.proxy.LocalStreamProxyState
                proxyState.lazyVideoTracks.value = proxyState.lazyVideoTracks.value.filter { t -> t.url != track.url }
                _activeLazyVideoTrackUrl.value = track.url
            }
        }
    }

    fun loadExternalSubtitle(url: String) {
        mpvHandle?.let {
            // Convert backslashes to forward slashes to avoid MPV string escape bugs,
            // and append 'select' flag so the newly added sub is immediately enabled.
            val safeUrl = url.replace("\\", "/").replace("\"", "\\\"")
            val cmd = "sub-add \"$safeUrl\" select"
            MpvLibrary.INSTANCE.mpv_command_string(it, cmd)
        }
    }

    internal val _aspectRatioMode = MutableStateFlow(0) // 0=Fit, 1=Fill, 2=Crop
    val aspectRatioMode: StateFlow<Int> = _aspectRatioMode.asStateFlow()

    fun cycleAspectRatio() {
        mpvHandle?.let {
            val nextMode = (aspectRatioMode.value + 1) % 3
            _aspectRatioMode.value = nextMode
            when (nextMode) {
                0 -> { // Fit
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "video-aspect-override", "no")
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "panscan", "0.0")
                }
                1 -> { // Fill/Stretch
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "video-aspect-override", "window")
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "panscan", "0.0")
                }
                2 -> { // Crop
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "video-aspect-override", "no")
                    MpvLibrary.INSTANCE.mpv_set_property_string(it, "panscan", "1.0")
                }
            }
        }
    }
}
