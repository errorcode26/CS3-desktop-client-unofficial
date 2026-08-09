package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.sun.jna.Pointer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Headless controller for the native MPV core engine.
 * Decouples JNI pointer manipulation, background event loops, and subtitle/link I/O from Compose.
 */
class DesktopMpvEngine(
    val scope: CoroutineScope,
    val onPlaybackReady: () -> Unit,
    val onPlaybackError: (String) -> Unit,
    val onFinished: () -> Unit,
    val onPositionChange: (positionMs: Long, durationMs: Long) -> Unit,
) {
    private var mpvHandle: Pointer? = null
    private var eventJob: Job? = null
    private val isDestroyed = AtomicBoolean(false)
    private var hasEverPlayed = false
    private var waitingForTimePosReset = false
    private var lastPos = 0.0
    private var lastDur = 0.0

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _volume = MutableStateFlow(100.0)
    val volume = _volume.asStateFlow()

    private val _speed = MutableStateFlow(1.0)
    val speed = _speed.asStateFlow()

    /**
     * Initializes the native MPV instance and configures properties before initialization.
     */
    fun createAndInitialize(
        canvasWid: Long,
        width: Int,
        height: Int,
        onPreInit: ((handle: Pointer, wid: Long, w: Int, h: Int) -> Unit)? = null,
        onPostInit: ((handle: Pointer) -> Unit)? = null,
    ): Pointer? {
        if (isDestroyed.get()) return null

        try {
            val handle = MpvLibrary.INSTANCE.mpv_create() ?: return null
            mpvHandle = handle

            // Base MPV configuration
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "input-default-bindings", "yes")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "input-vo-keyboard", "yes")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "keep-open", "yes")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "idle", "yes")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "hwdec", "auto-safe")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "hr-seek", "yes")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "hr-seek-framedrop", "yes")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "msg-level", "all=v")

            onPreInit?.invoke(handle, canvasWid, width, height)

            val initCode = MpvLibrary.INSTANCE.mpv_initialize(handle)
            if (initCode < 0) {
                AppLogger.e("DesktopMpvEngine", "mpv_initialize failed with code: $initCode")
                destroy()
                return null
            }

            onPostInit?.invoke(handle)
            startEventLoop(handle)
            return handle
        } catch (e: Throwable) {
            AppLogger.e("DesktopMpvEngine", "Failed to create MPV instance: ${e.message}")
            return null
        }
    }

    private fun startEventLoop(handle: Pointer) {
        eventJob = scope.launch(Dispatchers.IO) {
            MpvLibrary.INSTANCE.mpv_observe_property(handle, 1L, "time-pos", 5) // Double
            MpvLibrary.INSTANCE.mpv_observe_property(handle, 2L, "duration", 5) // Double
            MpvLibrary.INSTANCE.mpv_observe_property(handle, 3L, "pause", 3) // Flag
            MpvLibrary.INSTANCE.mpv_observe_property(handle, 4L, "eof-reached", 3) // Flag
            MpvLibrary.INSTANCE.mpv_observe_property(handle, 5L, "volume", 5) // Double
            MpvLibrary.INSTANCE.mpv_observe_property(handle, 6L, "speed", 5) // Double

            while (isActive && !isDestroyed.get()) {
                try {
                    val eventPtr = MpvLibrary.INSTANCE.mpv_wait_event(handle, 0.05)
                    if (eventPtr != null) {
                        val event = MpvLibrary.MpvEvent(eventPtr)
                        when (event.event_id) {
                            1 -> break // MPV_EVENT_SHUTDOWN
                            6 -> { // MPV_EVENT_START_FILE
                                waitingForTimePosReset = false
                                hasEverPlayed = false
                            }
                            7 -> { // MPV_EVENT_END_FILE
                                val endFilePtr = event.data
                                if (endFilePtr != null) {
                                    val endFile = MpvLibrary.MpvEventEndFile(endFilePtr)
                                    if (endFile.reason == 4) { // MPV_END_FILE_REASON_ERROR
                                        onPlaybackError("Stream connection error or timeout.")
                                    } else if (endFile.reason == 0) { // MPV_END_FILE_REASON_EOF
                                        if (hasEverPlayed) {
                                            onFinished()
                                        } else {
                                            onPlaybackError("Stream failed to load or instantly ended.")
                                        }
                                    }
                                }
                            }
                            8, 21 -> { // MPV_EVENT_FILE_LOADED, MPV_EVENT_PLAYBACK_RESTART
                                if (!hasEverPlayed && !waitingForTimePosReset) {
                                    hasEverPlayed = true
                                    onPlaybackReady()
                                }
                            }
                            22 -> { // MPV_EVENT_PROPERTY_CHANGE
                                val propPtr = event.data
                                if (propPtr != null) {
                                    val prop = MpvLibrary.MpvEventProperty(propPtr)
                                    when (event.reply_userdata) {
                                        1L -> { // time-pos
                                            val pos = prop.data?.getDouble(0) ?: 0.0
                                            lastPos = pos
                                            onPositionChange((pos * 1000).toLong(), (lastDur * 1000).toLong())
                                        }
                                        2L -> { // duration
                                            val dur = prop.data?.getDouble(0) ?: 0.0
                                            lastDur = dur
                                            onPositionChange((lastPos * 1000).toLong(), (dur * 1000).toLong())
                                        }
                                        3L -> { // pause
                                            val flag = prop.data?.getInt(0) ?: 0
                                            _isPaused.value = (flag != 0)
                                        }
                                        5L -> { // volume
                                            _volume.value = prop.data?.getDouble(0) ?: 100.0
                                        }
                                        6L -> { // speed
                                            _speed.value = prop.data?.getDouble(0) ?: 1.0
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Suppress transient loop errors
                }
            }
        }
    }

    fun loadFile(
        url: String,
        startPositionMs: Long = 0,
        headers: Map<String, String>? = null,
        subtitles: List<SubtitleFile> = emptyList(),
    ) {
        val handle = mpvHandle ?: return
        scope.launch(Dispatchers.IO) {
            waitingForTimePosReset = true
            hasEverPlayed = false

            // Set custom HTTP headers if present
            headers?.forEach { (key, value) ->
                MpvLibrary.INSTANCE.mpv_set_property_string(handle, "http-header-fields", "$key: $value")
            }

            if (startPositionMs > 0) {
                MpvLibrary.INSTANCE.mpv_set_property_string(handle, "start", "${startPositionMs / 1000.0}")
            }

            val command = "loadfile \"$url\" replace"
            MpvLibrary.INSTANCE.mpv_command_string(handle, command)

            // Inject subtitles
            subtitles.forEach { sub ->
                if (sub.url.isNotBlank()) {
                    val subCommand = "sub-add \"${sub.url}\" auto \"${sub.lang ?: ""}\""
                    MpvLibrary.INSTANCE.mpv_command_string(handle, subCommand)
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val handle = mpvHandle ?: return
        scope.launch(Dispatchers.IO) {
            val sec = (positionMs / 1000.0).toString()
            MpvLibrary.INSTANCE.mpv_command_string(handle, "seek $sec absolute")
        }
    }

    fun togglePause() {
        val handle = mpvHandle ?: return
        scope.launch(Dispatchers.IO) {
            val current = _isPaused.value
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "pause", if (current) "no" else "yes")
        }
    }

    fun setSpeed(newSpeed: Double) {
        val handle = mpvHandle ?: return
        scope.launch(Dispatchers.IO) {
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "speed", newSpeed.toString())
        }
    }

    fun setVolume(newVolume: Double) {
        val handle = mpvHandle ?: return
        scope.launch(Dispatchers.IO) {
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "volume", newVolume.toString())
        }
    }

    fun destroy() {
        if (!isDestroyed.compareAndSet(false, true)) return

        val handle = mpvHandle
        mpvHandle = null
        eventJob?.cancel()

        if (handle != null) {
            // Teardown asynchronously so UI thread never hitches
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    MpvLibrary.INSTANCE.mpv_command_string(handle, "stop")
                    MpvLibrary.INSTANCE.mpv_terminate_destroy(handle)
                } catch (e: Throwable) {
                    AppLogger.w("DesktopMpvEngine", "Error during MPV destroy: ${e.message}")
                }
            }
        }
    }
}
