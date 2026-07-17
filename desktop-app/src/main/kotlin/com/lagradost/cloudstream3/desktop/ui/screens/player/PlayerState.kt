package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.desktop.player.MpvLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PlayerState {
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    val bufferMs = MutableStateFlow(0L)
    val isPaused = MutableStateFlow(false)
    val isBuffering = MutableStateFlow(true)
    val isProbing = MutableStateFlow(false)
    val volume = MutableStateFlow(100f) // 0 to 130 in MPV usually, let's say 0 to 100
    val isMuted = MutableStateFlow(false)
    val playbackSpeed = MutableStateFlow(1.0f)
    val showControls = MutableStateFlow(true)
    val subtitleDelayMs = MutableStateFlow(0L)
    val isInterpolationEnabled = MutableStateFlow(
        com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_INTERPOLATION) ?: false,
    )

    data class VideoTrack(
        val id: Int,
        val name: String,
        val isSelected: Boolean,
    )

    val subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList()) // New State for Qualities
    val activeLazyVideoTrackUrl = MutableStateFlow<String?>(null)

    // Video Stats
    val videoCodec = MutableStateFlow("")
    val audioCodec = MutableStateFlow("")
    val hwdecCurrent = MutableStateFlow("")
    val droppedFrames = MutableStateFlow(0L)
    val fps = MutableStateFlow(0.0)
    val resolution = MutableStateFlow("")
    val videoBitrate = MutableStateFlow(0L)
    val audioBitrate = MutableStateFlow(0L)
    val showStats = MutableStateFlow(false)

    private var mpvHandle: com.sun.jna.Pointer? = null
    private var lastSeekTime = 0L
    private var targetSeekMs = -1L

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
        positionMs.value = 0L
        durationMs.value = 0L
        bufferMs.value = 0L
        isPaused.value = false
        isBuffering.value = true
        isProbing.value = false
        isMuted.value = false
        lastSeekTime = 0L
        targetSeekMs = -1L
        activeLazyVideoTrackUrl.value = null
    }

    fun togglePlayPause() {
        mpvHandle?.let {
            val currentlyPaused = isPaused.value
            val nextState = if (currentlyPaused) "no" else "yes"
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "pause", nextState)
            // State will be updated by the observer loop in ComposeMpvPlayer
            isPaused.value = !currentlyPaused
        }
    }

    fun pause() {
        mpvHandle?.let {
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "pause", "yes")
            isPaused.value = true
        }
    }

    fun play() {
        mpvHandle?.let {
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "pause", "no")
            isPaused.value = false
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
            this.positionMs.value = positionMs
        }
    }

    fun seekBy(offsetMs: Long) {
        mpvHandle?.let {
            lastSeekTime = System.currentTimeMillis()
            targetSeekMs = this.positionMs.value + offsetMs
            val offsetSec = offsetMs / 1000.0
            MpvLibrary.INSTANCE.mpv_command_string(it, "seek $offsetSec relative")
            this.positionMs.value = targetSeekMs
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
            } else if (now - lastSeekTime < 5000L) {
                // If it's not close to the target, AND we are within a 5-second grace period,
                // it means the player is still reporting the OLD time or is still buffering.
                // IGNORE this update to prevent rubber-banding.
                return
            } else {
                // 5 seconds have passed and it's STILL not close to the target.
                // The seek probably failed, was queued behind another, or we hit EOF. Reset and accept.
                targetSeekMs = -1L
            }
        }

        this.positionMs.value = posMs
    }

    fun updateDurationFromPlayer(durMs: Long) {
        if (durMs > 0 && this.durationMs.value != durMs) {
            this.durationMs.value = durMs
        }
    }

    fun setSpeed(speed: Float) {
        mpvHandle?.let {
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "speed", speed.toString())
            playbackSpeed.value = speed
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
            volume.value = safeVol
        }
    }

    fun toggleMute() {
        mpvHandle?.let {
            val nextMuted = !isMuted.value
            MpvLibrary.INSTANCE.mpv_set_property_string(it, "mute", if (nextMuted) "yes" else "no")
            isMuted.value = nextMuted
        }
    }

    fun setInterpolation(enabled: Boolean) {
        com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_INTERPOLATION, enabled)
        isInterpolationEnabled.value = enabled
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
            subtitleDelayMs.value = delayMs
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
                activeLazyVideoTrackUrl.value = track.url
            } else {
                val safeUrl = track.url.replace("\\", "\\\\").replace("\"", "\\\"")
                val safeName = track.name.replace("\\", "\\\\").replace("\"", "\\\"")
                val safeLang = track.language.replace("\\", "\\\\").replace("\"", "\\\"")
                // MPV command: video-add <url> select <title> <lang>
                val cmd = "video-add \"$safeUrl\" select \"$safeName\" \"$safeLang\""
                MpvLibrary.INSTANCE.mpv_command_string(it, cmd)

                val proxyState = com.lagradost.player.impl.proxy.LocalStreamProxyState
                proxyState.lazyVideoTracks.value = proxyState.lazyVideoTracks.value.filter { t -> t.url != track.url }
                activeLazyVideoTrackUrl.value = track.url
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

    val aspectRatioMode = MutableStateFlow(0) // 0=Fit, 1=Fill, 2=Crop

    fun cycleAspectRatio() {
        mpvHandle?.let {
            val nextMode = (aspectRatioMode.value + 1) % 3
            aspectRatioMode.value = nextMode
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
