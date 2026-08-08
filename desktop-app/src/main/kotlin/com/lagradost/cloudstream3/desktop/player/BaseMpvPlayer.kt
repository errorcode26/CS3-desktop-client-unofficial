package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.awt.Color
import java.awt.event.*
import java.io.File

@Composable
fun BaseMpvPlayer(
    link: ExtractorLink?,
    title: String?,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    startPositionMs: Long,
    shouldPauseForResume: Boolean = false,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    isExiting: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    playerState: com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    // Called once when the MPV event loop is running, before any loadfile.
    // Used by ComposeNativeWebPlayer to start the C++ sync thread.
    onEventLoopReady: ((handle: com.sun.jna.Pointer) -> Unit)? = null,
    // Called after mpv_create() but before mpv_initialize().
    // Use this to override VO, WID, or other pre-init options.
    onPreInitialize: ((handle: com.sun.jna.Pointer, canvasWid: Long, width: Int, height: Int) -> Unit)? = null,
    // Called immediately after mpv_initialize(). Use this to run post-init setup (like WebView).
    onPostInitialize: ((handle: com.sun.jna.Pointer) -> Unit)? = null,
    videoRenderer: @Composable (videoCanvas: Canvas, mpvHandle: com.sun.jna.Pointer?) -> Unit,
) {
    var mpvHandle by remember { mutableStateOf<com.sun.jna.Pointer?>(null) }
    var hasEverPlayed by remember { mutableStateOf(false) }
    // Guards against false-positive onPlaybackReady after a stop()+loadfile sequence.
    // Set to true just before loadfile, cleared on MPV_EVENT_START_FILE.
    var waitingForTimePosReset by remember { mutableStateOf(false) }

    val currentOnPlaybackReady by rememberUpdatedState(onPlaybackReady)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentOnFullscreenToggle by rememberUpdatedState(onFullscreenToggle)

    LaunchedEffect(mpvHandle) {
        val h = mpvHandle
        if (h != null) {
            onEventLoopReady?.invoke(h)
            var loops = 0
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Setup native observers for critical instant-response properties
                MpvLibrary.INSTANCE.mpv_observe_property(h, 1L, "time-pos", 5) // Double
                MpvLibrary.INSTANCE.mpv_observe_property(h, 2L, "duration", 5) // Double
                MpvLibrary.INSTANCE.mpv_observe_property(h, 3L, "pause", 3) // Flag
                MpvLibrary.INSTANCE.mpv_observe_property(h, 4L, "eof-reached", 3) // Flag
                MpvLibrary.INSTANCE.mpv_observe_property(h, 5L, "volume", 5) // Double
                MpvLibrary.INSTANCE.mpv_observe_property(h, 6L, "speed", 5) // Double
                MpvLibrary.INSTANCE.mpv_observe_property(h, 7L, "core-idle", 3) // Flag
                MpvLibrary.INSTANCE.mpv_observe_property(h, 8L, "paused-for-cache", 3) // Flag
                MpvLibrary.INSTANCE.mpv_observe_property(h, 9L, "mute", 3) // Flag

                var lastPos = 0.0
                var lastDur = 0.0
                // Separate timers so heavy track-list polling never blocks position updates
                var lastPositionPollMs = 0L // 100ms cadence — governs seek-bar smoothness
                var lastTrackPollMs = 0L // 2000ms cadence — track list / stats are slow
                var lastUiPositionEmit = 0L
                var diagnosticLogged = false
                var playbackStartedAt = 0L

                while (isActive) {
                    try {
                        // Block IO thread for up to 50ms waiting for an event.
                        // Keeping timeout short ensures the fallback position poll fires at ≥10 Hz
                        // even when mpv sends no events (e.g. during initial buffering).
                        val eventPtr = MpvLibrary.INSTANCE.mpv_wait_event(h, 0.05)
                        if (eventPtr != null) {
                            val event = MpvLibrary.MpvEvent(eventPtr)
                            val eventId = event.event_id

                            when (eventId) {
                                1 -> {
                                    com.lagradost.common.logging.AppLogger.i("Player:MPV", "MPV Engine shutting down (MPV_EVENT_SHUTDOWN)")
                                    break
                                }

                                6 -> { // MPV_EVENT_START_FILE — a new file is being loaded
                                    com.lagradost.common.logging.AppLogger.i("Player:MPV", "Starting new media stream (MPV_EVENT_START_FILE)")
                                    waitingForTimePosReset = false
                                    hasEverPlayed = false
                                    playbackStartedAt = 0L
                                    diagnosticLogged = false
                                }

                                7 -> { // MPV_EVENT_END_FILE
                                    val endFilePtr = event.data
                                    if (endFilePtr != null) {
                                        val endFile = MpvLibrary.MpvEventEndFile(endFilePtr)
                                        com.lagradost.common.logging.AppLogger.i("Player:MPV", "Media ended (reason=${endFile.reason}, error=${endFile.error})")

                                        // 0 = EOF, 2 = STOP, 3 = QUIT, 4 = ERROR
                                        if (endFile.reason == 4) { // MPV_END_FILE_REASON_ERROR
                                            com.lagradost.common.logging.AppLogger.e("Player:MPV", "MPV stream error (MPV_END_FILE_REASON_ERROR, code=${endFile.error})")
                                            currentOnPlaybackError("Stream is dead, timed out, or connection rejected.")
                                        } else if (endFile.reason == 0) { // MPV_END_FILE_REASON_EOF
                                            if (!hasEverPlayed) {
                                                com.lagradost.common.logging.AppLogger.e("Player:MPV", "Stream instantly ended (EOF) before ever playing.")
                                                currentOnPlaybackError("Stream failed to load or instantly ended.")
                                            } else {
                                                com.lagradost.common.logging.AppLogger.i("Player:MPV", "Stream reached EOF successfully.")
                                                currentOnFinished()
                                            }
                                        }
                                    }
                                }

                                2 -> { // MPV_EVENT_LOG_MESSAGE
                                    val logData = event.data
                                    if (logData != null) {
                                        val logMsg = MpvLibrary.MpvEventLogMessage(logData)
                                        val pfx = logMsg.prefix ?: "core"
                                        val text = logMsg.text?.trimEnd('\r', '\n') ?: ""
                                        if (text.isNotBlank()) {
                                            val tag = "Player:MPV:$pfx"
                                            when (logMsg.level?.lowercase()) {
                                                "error", "fatal" -> com.lagradost.common.logging.AppLogger.e(tag, text)
                                                "warn" -> com.lagradost.common.logging.AppLogger.w(tag, text)
                                                "info", "status" -> com.lagradost.common.logging.AppLogger.i(tag, text)
                                                else -> com.lagradost.common.logging.AppLogger.d(tag, text)
                                            }
                                        }
                                    }
                                }

                                // MPV_EVENT_FILE_LOADED (8) or MPV_EVENT_PLAYBACK_RESTART (21)
                                8, 21 -> {
                                    if (!hasEverPlayed && !waitingForTimePosReset) {
                                        hasEverPlayed = true
                                        playbackStartedAt = System.currentTimeMillis()
                                        com.lagradost.common.logging.AppLogger.i("Player:MPV", "Playback active (MPV_EVENT_FILE_LOADED / RESTART)")

                                        if (startPositionMs > 0) {
                                            com.lagradost.common.logging.AppLogger.i("Player:MPV", "Executing initial seek to $startPositionMs ms")
                                            playerState?.seekTo(startPositionMs)
                                        }

                                        playerState?._isBuffering?.value = false
                                        playerState?._isProbing?.value = false
                                        currentOnPlaybackReady()
                                    }
                                }

                                22 -> { // MPV_EVENT_PROPERTY_CHANGE
                                    val propPtr = event.data
                                    if (propPtr != null) {
                                        val prop = MpvLibrary.MpvEventProperty(propPtr)
                                        val name = prop.name
                                        if (name != null && prop.format != 0 && prop.data != null) {
                                            when (name) {
                                                "time-pos" -> {
                                                    if (prop.format == 5) { // MPV_FORMAT_DOUBLE
                                                        val newPos = prop.data!!.getDouble(0)
                                                        if (newPos >= 0.0) lastPos = newPos

                                                        if (!hasEverPlayed && lastPos > 0.1 && !waitingForTimePosReset) {
                                                            hasEverPlayed = true
                                                            playbackStartedAt = System.currentTimeMillis()
                                                            playerState?._isBuffering?.value = false
                                                            playerState?._isProbing?.value = false
                                                            currentOnPlaybackReady()
                                                        }

                                                        // One-shot 30s diagnostic
                                                        if (!diagnosticLogged && playbackStartedAt > 0 &&
                                                            System.currentTimeMillis() - playbackStartedAt > 30_000
                                                        ) {
                                                            diagnosticLogged = true
                                                            val dVo = MpvLibrary.getPropertyString(h, "current-vo")
                                                            val dHwdec = MpvLibrary.getPropertyString(h, "hwdec-current")
                                                            val dCodec = MpvLibrary.getPropertyString(h, "video-codec")
                                                            val dFps = MpvLibrary.getPropertyString(h, "estimated-vf-fps")
                                                            val dW = MpvLibrary.getPropertyString(h, "width")
                                                            val dH = MpvLibrary.getPropertyString(h, "height")
                                                            com.lagradost.common.logging.AppLogger.i(
                                                                "MPV 30s Diag -> VO: $dVo, HWDEC: $dHwdec, Codec: $dCodec, FPS: $dFps, Res: ${dW}x$dH",
                                                            )
                                                        }

                                                        // Push to Kotlin state (throttled to 100ms)
                                                        val now = System.currentTimeMillis()
                                                        if (now - lastUiPositionEmit >= 100) {
                                                            lastUiPositionEmit = now
                                                            val posMs = (lastPos * 1000).toLong()
                                                            playerState?.updatePositionFromPlayer(posMs)
                                                            currentOnPositionChange(posMs, (lastDur * 1000).toLong())
                                                        }
                                                    }
                                                }
                                                "duration" -> {
                                                    if (prop.format == 5) {
                                                        lastDur = prop.data!!.getDouble(0)
                                                        playerState?._durationMs?.value = (lastDur * 1000).toLong()
                                                    }
                                                }
                                                "pause" -> {
                                                    if (prop.format == 3) playerState?._isPaused?.value = prop.data!!.getInt(0) != 0
                                                }
                                                "paused-for-cache" -> {
                                                    if (prop.format == 3) {
                                                        playerState?._isBuffering?.value = prop.data!!.getInt(0) != 0
                                                    }
                                                }
                                                "mute" -> {
                                                    if (prop.format == 3) playerState?._isMuted?.value = prop.data!!.getInt(0) != 0
                                                }
                                                "volume" -> {
                                                    if (prop.format == 5) playerState?._volume?.value = prop.data!!.getDouble(0).toFloat()
                                                }
                                                "speed" -> {
                                                    if (prop.format == 5) playerState?._playbackSpeed?.value = prop.data!!.getDouble(0).toFloat()
                                                }
                                                "core-idle" -> {
                                                    if (prop.format == 3 && !hasEverPlayed) {
                                                        playerState?._isProbing?.value = prop.data!!.getInt(0) != 0
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } // end when(eventId)
                        }

                        // Fallback position poll (100ms)
                        // This fires even when mpv sends no events (buffering, idle).
                        // It is the PRIMARY seek-bar driver when native events are sparse.
                        val now = System.currentTimeMillis()
                        if (now - lastPositionPollMs >= 100L) {
                            lastPositionPollMs = now

                            // Poll duration first so seek bar percentage is correct
                            val currentDur = MpvLibrary.getPropertyDouble(h, "duration", -1.0)
                            if (currentDur > 0.0) {
                                lastDur = currentDur
                                playerState?._durationMs?.value = (currentDur * 1000).toLong()
                            }

                            // Poll position
                            val pollPos = MpvLibrary.getPropertyDouble(h, "time-pos", -1.0)
                            if (pollPos >= 0.0) lastPos = pollPos

                            if (pollPos >= 0.0) {
                                // Trigger playback-ready if native events haven't done so yet
                                if (!hasEverPlayed && lastPos > 0.1 && !waitingForTimePosReset) {
                                    hasEverPlayed = true
                                    playbackStartedAt = System.currentTimeMillis()
                                    playerState?._isBuffering?.value = false
                                    playerState?._isProbing?.value = false
                                    currentOnPlaybackReady()
                                }
                            }

                            // Always emit position to Kotlin state
                            if (now - lastUiPositionEmit >= 100) {
                                lastUiPositionEmit = now
                                val posMs = (lastPos * 1000).toLong()
                                playerState?.updatePositionFromPlayer(posMs)
                                currentOnPositionChange(posMs, (lastDur * 1000).toLong())
                                // Refresh buffer indicator
                                MpvLibrary.getPropertyString(h, "paused-for-cache")?.let { s ->
                                    playerState?._isBuffering?.value = s == "yes"
                                }
                            }
                        }

                        // Heavy track + stats poll (2000ms)
                        // Separated from position poll so ~20 mpv_get_property_string calls
                        // never stall the 100ms seek-bar update window.
                        if (now - lastTrackPollMs >= 2000L) {
                            lastTrackPollMs = now
                            val trackCountStr = MpvLibrary.getPropertyString(h, "track-list/count")
                            val trackCount = trackCountStr?.toIntOrNull() ?: 0

                            val audioTracks = mutableListOf<PlayerState.VideoTrack>()
                            val subTracks = mutableListOf<PlayerState.VideoTrack>()
                            val videoTracks = mutableListOf<PlayerState.VideoTrack>()

                            for (i in 0 until trackCount) {
                                val id = MpvLibrary.getPropertyString(h, "track-list/$i/id")?.toIntOrNull() ?: continue
                                val type = MpvLibrary.getPropertyString(h, "track-list/$i/type") ?: continue
                                val lang = MpvLibrary.getPropertyString(h, "track-list/$i/lang")
                                val title = MpvLibrary.getPropertyString(h, "track-list/$i/title")
                                val selected = MpvLibrary.getPropertyString(h, "track-list/$i/selected") == "yes"

                                val name = buildString {
                                    if (!lang.isNullOrBlank()) append(lang.uppercase())
                                    if (!title.isNullOrBlank()) {
                                        if (isNotEmpty()) append(" - ")
                                        append(title)
                                    }
                                    if (isEmpty()) {
                                        append(
                                            if (type == "audio") {
                                                "Audio $id"
                                            } else if (type == "video") {
                                                "Video $id"
                                            } else {
                                                "Subtitle $id"
                                            },
                                        )
                                    }
                                }
                                if (type == "audio") {
                                    audioTracks.add(PlayerState.VideoTrack(id, name, selected))
                                } else if (type == "sub") {
                                    subTracks.add(PlayerState.VideoTrack(id, name, selected))
                                } else if (type == "video") {
                                    val res = MpvLibrary.getPropertyString(h, "track-list/$i/demux-h") ?: ""
                                    val fpsVal = MpvLibrary.getPropertyString(h, "track-list/$i/demux-fps")?.toDoubleOrNull() ?: 0.0
                                    val finalName = if (res.isNotEmpty()) {
                                        if (fpsVal > 30.0) "${res}p ${fpsVal.toInt()}fps" else "${res}p"
                                    } else {
                                        name
                                    }
                                    videoTracks.add(PlayerState.VideoTrack(id, finalName, selected))
                                }
                            }
                            playerState?._audioTracks?.value = audioTracks
                            playerState?._subtitleTracks?.value = subTracks
                            playerState?._videoTracks?.value = videoTracks
                            // Poll Video Stats
                            if (playerState != null && playerState._showStats.value) {
                                playerState._videoCodec.value = MpvLibrary.getPropertyString(h, "video-codec") ?: "Unknown"
                                playerState._audioCodec.value = MpvLibrary.getPropertyString(h, "audio-codec") ?: "Unknown"
                                playerState._hwdecCurrent.value = MpvLibrary.getPropertyString(h, "hwdec-current") ?: "Unknown"
                                playerState._droppedFrames.value = MpvLibrary.getPropertyString(h, "vo-drop-frame-count")?.toLongOrNull() ?: 0L
                                playerState._fps.value = MpvLibrary.getPropertyString(h, "container-fps")?.toDoubleOrNull() ?: 0.0
                                val w = MpvLibrary.getPropertyString(h, "width") ?: "0"
                                val hw = MpvLibrary.getPropertyString(h, "height") ?: "0"
                                playerState._resolution.value = "${w}x$hw"
                                playerState._videoBitrate.value = MpvLibrary.getPropertyString(h, "video-bitrate")?.toLongOrNull() ?: 0L
                                playerState._audioBitrate.value = MpvLibrary.getPropertyString(h, "audio-bitrate")?.toLongOrNull() ?: 0L
                            }
                        } // End of periodic track poll block
                        loops++
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        break // Normal coroutine cancellation
                    } catch (e: Throwable) {
                        com.lagradost.common.logging.AppLogger.e("MPV event loop non-fatal error: ${e.message}")
                        kotlinx.coroutines.delay(100) // Prevent tight loop crash spam
                        continue
                    }
                }
            }
        }
    }

    LaunchedEffect(title, mpvHandle) {
        val handle = mpvHandle ?: return@LaunchedEffect
        if (!title.isNullOrBlank()) {
            val lib = MpvLibrary.INSTANCE
            val safeTitle = title ?: ""
            lib.mpv_set_property_string(handle, "force-media-title", safeTitle)
            lib.mpv_set_property_string(handle, "title", safeTitle)
        }
    }

    LaunchedEffect(link, mpvHandle) {
        // Reset guards IMMEDIATELY so the concurrent event loop never sees stale state
        // from the previous link attempt when event 8 fires for the new link.
        hasEverPlayed = false
        waitingForTimePosReset = true

        val handle = mpvHandle ?: return@LaunchedEffect
        if (link == null) {
            MpvLibrary.INSTANCE.mpv_command_string(handle, "stop")
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "pause", "yes")
            playerState?._isPaused?.value = true
            hasEverPlayed = false
            return@LaunchedEffect // Idle state — WebView player while scraping
        }

        val validated = PlayerLinkHandler.validate(link, title).getOrElse {
            currentOnPlaybackError(it.message ?: "Validation failed")
            return@LaunchedEffect
        }

        val lib = MpvLibrary.INSTANCE

        // NOTE: We intentionally do NOT inject headers into demuxer-lavf-o because the
        // option string is comma-delimited and header values containing commas (like
        // User-Agent with "(KHTML, like Gecko)") break AVOption parsing and corrupt all
        // subsequent options including cenc_decryption_key.
        // MPV's http-header-fields property IS forwarded by the stream callback handler
        // to every sub-request FFmpeg makes (init.mp4, segments, etc.) as proven by
        // segments downloading successfully without 403 in testing.

        // Clear previous lavf options to prevent bleeding across stream loads
        lib.mpv_set_property_string(handle, "demuxer-lavf-o", "")
        lib.mpv_set_property_string(handle, "stream-lavf-o", "")

        val headerStr = validated.headers.entries.joinToString(separator = "\r\n", postfix = "\r\n") { (k, v) ->
            "$k: ${v.replace("\r", "").replace("\n", "")}"
        }
        val byteLength = headerStr.toByteArray(Charsets.UTF_8).size

        when (validated.streamKind) {
            PlayerLinkHandler.StreamKind.HLS -> {
                lib.mpv_set_property_string(handle, "hls-bitrate", "max")
                // 100MB forward is plenty for most 1080p HLS streams (segments are typically 2-6MB each).
                // 400MB was causing CDN rate-limiting: MPV/FFmpeg would burst-request many segments
                // at once to fill the cache, hitting 429 errors from Cloudflare/Akamai/Fastly after 20-30s.
                lib.mpv_set_property_string(handle, "demuxer-max-bytes", "100000000") // 100MB forward
                lib.mpv_set_property_string(handle, "demuxer-max-back-bytes", "30000000") // 30MB back
                lib.mpv_set_property_string(handle, "cache", "yes")
                // 30s lookahead is aggressive but won't overwhelm CDNs the way 60s did.
                lib.mpv_set_property_string(handle, "cache-secs", "30")
                lib.mpv_set_property_string(handle, "demuxer-readahead-secs", "30")
                lib.mpv_set_property_string(handle, "cache-pause-wait", "3")

                // CRITICAL: Must use mpv_set_property_string here, NOT mpv_set_option_string!
                // Options can only be set before mpv_initialize(). This runs after init,
                // so mpv_set_option_string silently ignores the entire string.
                //
                // NOTE: Do NOT include fflags=+ignidx+igndts here. Those flags disable FFmpeg's
                // index loading and DTS decoding. For well-formed HLS this breaks packet reordering,
                // causing progressive A/V desync that only becomes visible once the initial buffer
                // drains (~20-30s). extension_picky=0 alone is sufficient to accept non-standard URLs.
                lib.mpv_set_property_string(
                    handle,
                    "demuxer-lavf-o",
                    "extension_picky=0,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4,reconnect_on_http_error=4xx",
                )
                // Allow demuxer to seek ahead aggressively:
                lib.mpv_set_property_string(handle, "demuxer-seekable-cache", "yes")
                lib.mpv_set_property_string(handle, "force-seekable", "yes")

                // Force fast startup and flawless cache rewinding for proxied HLS streams
                lib.mpv_set_property_string(handle, "stream-lavf-o", "seekable=1,icy=0")
                lib.mpv_set_property_string(handle, "demuxer-lavf-probesize", "5242880") // 5 MB
                lib.mpv_set_property_string(handle, "demuxer-lavf-analyzeduration", "2") // 2 s
            }
            PlayerLinkHandler.StreamKind.DASH -> {
                // Build DASH lavf options. cenc_decryption_key MUST be standalone —
                // do not mix with headers= as commas in header values corrupt the parse.
                val lavfDashOpts = buildString {
                    // Reconnect on HTTP errors. Commas MUST be avoided in the value to prevent
                    // corrupting MPV's option parser (which uses commas to separate key=val pairs).
                    append("extension_picky=0,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4,reconnect_on_http_error=4xx")
                    if (validated.clearKeyHex != null) {
                        append(",cenc_decryption_key=${validated.clearKeyHex}")
                    }
                }
                lib.mpv_set_property_string(handle, "demuxer-lavf-o", lavfDashOpts)
                // DASH: 30MB forward buffer is plenty for 1080p segments (~4MB each)
                // Back-buffer: 5MB for live, could be more for VOD but keep conservative
                lib.mpv_set_property_string(handle, "demuxer-max-bytes", "30000000") // 30MB forward
                lib.mpv_set_property_string(handle, "demuxer-max-back-bytes", "5000000") // 5MB back
                lib.mpv_set_property_string(handle, "cache", "yes")
            }
            PlayerLinkHandler.StreamKind.PROGRESSIVE -> {
                // Raw MPEG-TS / progressive HTTP live streams need reconnect too.
                // Without this, FFmpeg treats the end of an HTTP chunk as permanent EOF.
                lib.mpv_set_property_string(
                    handle,
                    "demuxer-lavf-o",
                    "extension_picky=0,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4,reconnect_on_http_error=4xx",
                )
                // Stream-level reconnect is critical for live MPEG-TS over HTTP.
                // Setting method=GET prevents Cloudflare Workers from returning 403 Forbidden to HEAD requests.
                lib.mpv_set_property_string(
                    handle,
                    "stream-lavf-o",
                    "reconnect=1,reconnect_streamed=1,reconnect_delay_max=4,method=GET",
                )
                // MKV/MP4 files from CDNs like Pixeldrain often use one-time download links.
                // Seeking forces MPV to open a new HTTP connection with a Range header, which returns 404.
                // By massively increasing the buffer and forcing seekable-cache, MPV can seek entirely in memory!
                lib.mpv_set_property_string(handle, "demuxer-max-bytes", "400000000") // 400MB forward
                lib.mpv_set_property_string(handle, "demuxer-max-back-bytes", "100000000") // 100MB back
                lib.mpv_set_property_string(handle, "cache", "yes")
                lib.mpv_set_property_string(handle, "demuxer-seekable-cache", "yes")
                lib.mpv_set_property_string(handle, "force-seekable", "yes")
            }
        }

        // Disable auto-probing of subtitles and pre-select English audio.
        // Critical for complex HLS streams with 20+ tracks.
        lib.mpv_set_property_string(handle, "alang", "eng,en")
        lib.mpv_set_property_string(handle, "sub-auto", "no")
        lib.mpv_set_property_string(handle, "sid", "no")
        lib.mpv_set_property_string(handle, "aid", "auto")
        lib.mpv_set_property_string(handle, "cursor-autohide", "1500")

        val startSec = startPositionMs / 1000L
        // ALWAYS start at 0 to prevent cold-seek timeouts on unsupported CDNs.
        // We will perform a deferred warm-seek in the initialization loop instead.
        lib.mpv_set_property_string(handle, "start", "0")

        if (validated.displayTitle.isNotBlank()) {
            lib.mpv_set_property_string(handle, "force-media-title", validated.displayTitle)
            lib.mpv_set_property_string(handle, "title", validated.displayTitle)
        }

        // Apply headers dynamically via property
        val headersStr = validated.headers.entries.joinToString(",") { "${it.key}: ${it.value.replace(",", "\\,")}" }
        if (headersStr.isNotBlank()) {
            lib.mpv_set_property_string(handle, "http-header-fields", headersStr)
        }

        val urlTarget = if (validated.useUrlFile) {
            PlayerLinkHandler.writeUrlListFile("cloudstream_mpv_url_", validated.displayTitle, validated.url).absolutePath
        } else {
            validated.url
        }

        val safeUrl = urlTarget.replace("\\", "/")
        com.lagradost.common.logging.AppLogger.i("Loading embedded MPV URL: $safeUrl")

        // Prevent "Immediate exit requested" double-load bug by safely stopping any existing internal playback first
        lib.mpv_command_string(handle, "stop")

        // Wait for MPV to clear the time-pos (so we don't accidentally fire onPlaybackReady for the old video)
        var waitAttempts = 0
        while (waitAttempts < 10) {
            try {
                val pos = MpvLibrary.getPropertyDouble(handle, "time-pos", 0.0)
                if (pos <= 0.0) break
            } catch (_: Exception) {
                break
            }
            kotlinx.coroutines.delay(50)
            waitAttempts++
        }

        // NOTE: hasEverPlayed and waitingForTimePosReset are already reset at the top
        // of this LaunchedEffect.

        lib.mpv_command_string(handle, "loadfile \"$safeUrl\"")

        // Ensure the player is unpaused when loading a new link.
        // However, if resuming from a saved position, pause it so the UI can show a "Resume" dialog.
        val shouldPause = shouldPauseForResume && startSec > 0
        lib.mpv_set_property_string(handle, "pause", if (shouldPause) "yes" else "no")
        playerState?._isPaused?.value = shouldPause

        // Subtitles handling
        val sessionId = validated.proxySessionId
        val videoUrlHost = try {
            java.net.URI(link.url).host
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.w("Failed to extract host from video URL", e)
            null
        }

        val finalSubtitles = subtitles.map { sub ->
            var fixedUrl = sub.url
            if (fixedUrl.contains("*") && videoUrlHost != null) {
                try {
                    val subUri = java.net.URI(fixedUrl)
                    if (subUri.host?.contains("*") == true) {
                        fixedUrl = fixedUrl.replace(subUri.host, videoUrlHost)
                    }
                } catch (e: Exception) {
                    com.lagradost.common.logging.AppLogger.w("Failed to parse subtitle URI: $fixedUrl", e)
                }
            }
            if (sessionId != null) {
                sub.copy(url = com.lagradost.player.impl.proxy.LocalStreamProxy.buildProxyUrl(sessionId, fixedUrl))
            } else {
                sub.copy(url = fixedUrl)
            }
        }
        val capturedHandle = handle

        // Let MPV handle network timeouts natively
        lib.mpv_set_property_string(capturedHandle, "network-timeout", "15")

        launch(kotlinx.coroutines.Dispatchers.IO) {
            val defaultSub = finalSubtitles.firstOrNull()
            finalSubtitles.forEach { sub ->
                if (mpvHandle != null) {
                    val escapedSub = sub.url.replace("\\", "\\\\").replace("\"", "\\\"")
                    val escapedTitle = sub.lang.replace("\\", "\\\\").replace("\"", "\\\"")
                    try {
                        // Load subtitle into MPV track list but DO NOT force select it
                        val flag = "auto"
                        lib.mpv_command_string(capturedHandle, "sub-add \"$escapedSub\" $flag \"$escapedTitle\"")
                    } catch (e: Error) {
                        // handle freed, ignore
                    }
                }
            }
        }
    }

    val videoCanvas = remember {
        object : java.awt.Canvas() {
            init {
                background = java.awt.Color.BLACK
            }
            var keyDispatcher: java.awt.KeyEventDispatcher? = null

            // CRITICAL: Override paint/update to prevent AWT from clearing
            // MPV's rendering surface. When Compose's SwingPanel triggers a
            // repaint, the default Canvas.update() fills the component with
            // the background color, causing a flash that interferes with MPV.
            // MPV handles all rendering via the wid child window.
            override fun paint(g: java.awt.Graphics?) { /* MPV renders via wid */ }
            override fun update(g: java.awt.Graphics?) { /* Do NOT clear — MPV owns the surface */ }

            override fun addNotify() {
                super.addNotify()

                if (mpvHandle != null) return // Prevent multiple initializations (multi-audio bug)

                // Find MPV directory and tell JNA where to find the DLL
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val mpvExe = resolveMpvExecutable(isWindows)
                if (mpvExe == null) {
                    currentOnPlaybackError("MPV executable not found.")
                    return
                }
                val mpvDir = mpvExe.parentFile
                System.setProperty("jna.library.path", mpvDir.absolutePath)

                // CRITICAL: JNA caches jna.library.path on initialization.
                // We MUST use addSearchPath to inject our bundled DLL path dynamically.
                val targets = listOf("libmpv-2", "mpv-2", "mpv-1", "mpv", "libmpv", "mpv-3.dll")
                targets.forEach { target ->
                    com.sun.jna.NativeLibrary.addSearchPath(target, mpvDir.absolutePath)
                }

                val lib = MpvLibrary.INSTANCE
                val handle = lib.mpv_create() ?: run {
                    currentOnPlaybackError("Failed to initialize MPV Engine.")
                    return
                }
                mpvHandle = handle
                playerState?.attachMpv(handle)

                // Disable native UI since we draw our own Compose UI
                lib.mpv_set_option_string(handle, "osc", "no")
                lib.mpv_set_option_string(handle, "osd-level", "0")
                lib.mpv_set_option_string(handle, "osd-bar", "no")

                // VO: use the battle-tested 'gpu' output, NOT 'gpu-next'.
                // gpu-next requires libplacebo which may not be in the bundled libmpv,
                // causing silent fallback to a broken/software pipeline.
                lib.mpv_set_option_string(handle, "vo", "gpu")

                // Apply User Settings & Logging
                PlayerConfig.applyMpvSettings(handle, lib)

                val wid = com.sun.jna.Native.getComponentID(this)
                lib.mpv_set_option_string(handle, "wid", wid.toString())

                lib.mpv_set_option_string(handle, "input-default-bindings", "no")
                lib.mpv_set_option_string(handle, "input-vo-keyboard", "no")
                lib.mpv_set_option_string(handle, "save-position-on-quit", "no")
                lib.mpv_set_option_string(handle, "resume-playback", "no")
                lib.mpv_set_option_string(handle, "keep-open", "yes")
                lib.mpv_set_option_string(handle, "ytdl", "no")
                lib.mpv_set_option_string(handle, "idle", "yes")

                // Allow caller to override WID, VO, etc before init
                onPreInitialize?.invoke(handle, wid, this.width, this.height)

                com.lagradost.common.logging.AppLogger.i("Player:MPV", "Initializing embedded MPV Engine")
                lib.mpv_initialize(handle)
                lib.mpv_request_log_messages(handle, "info")

                onPostInitialize?.invoke(handle)

                // Post-init diagnostics: log what MPV actually chose for VO/hwdec
                val actualVo = MpvLibrary.getPropertyString(handle, "current-vo")
                val actualHwdec = MpvLibrary.getPropertyString(handle, "hwdec-current")
                com.lagradost.common.logging.AppLogger.i("Player:MPV", "MPV Engine initialized: vo=$actualVo, hwdec=$actualHwdec")

                // Setup Mouse and Keyboard interactions
                val canvas = this
                canvas.addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseMoved(e: MouseEvent) {
                        mpvHandle?.let { h -> MpvLibrary.INSTANCE.mpv_command_string(h, "mouse ${e.x} ${e.y}") }
                    }
                    override fun mouseDragged(e: MouseEvent) {
                        mpvHandle?.let { h -> MpvLibrary.INSTANCE.mpv_command_string(h, "mouse ${e.x} ${e.y}") }
                    }
                })

                canvas.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        mpvHandle?.let { h ->
                            canvas.requestFocusInWindow()
                            val btn = when (e.button) {
                                MouseEvent.BUTTON1 -> "MBTN_LEFT"
                                MouseEvent.BUTTON2 -> "MBTN_MID"
                                MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                                else -> return
                            }
                            MpvLibrary.INSTANCE.mpv_command_string(h, "keydown $btn")
                        }
                    }
                    override fun mouseReleased(e: MouseEvent) {
                        mpvHandle?.let { h ->
                            val btn = when (e.button) {
                                MouseEvent.BUTTON1 -> "MBTN_LEFT"
                                MouseEvent.BUTTON2 -> "MBTN_MID"
                                MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                                else -> return
                            }
                            MpvLibrary.INSTANCE.mpv_command_string(h, "keyup $btn")
                        }
                    }
                })

                canvas.addMouseWheelListener { e ->
                    mpvHandle?.let { h ->
                        val key = if (e.wheelRotation < 0) "WHEEL_UP" else "WHEEL_DOWN"
                        MpvLibrary.INSTANCE.mpv_command_string(h, "keypress $key")
                    }
                }

                this.keyDispatcher = java.awt.KeyEventDispatcher { e ->
                    if (e.id == KeyEvent.KEY_PRESSED) {
                        mpvHandle?.let { h ->
                            val mpvKey = awtKeyToMpv(e)
                            if (mpvKey?.contains("QUIT_OVERRIDE") == true) {
                                currentOnCloseRequest()
                            } else if (mpvKey != null) {
                                when (mpvKey.lowercase()) {
                                    "space" -> MpvLibrary.INSTANCE.mpv_command_string(h, "cycle pause")
                                    "left" -> MpvLibrary.INSTANCE.mpv_command_string(h, "seek -10")
                                    "right" -> MpvLibrary.INSTANCE.mpv_command_string(h, "seek 10")
                                    "up" -> MpvLibrary.INSTANCE.mpv_command_string(h, "add volume 5")
                                    "down" -> MpvLibrary.INSTANCE.mpv_command_string(h, "add volume -5")
                                    "m" -> MpvLibrary.INSTANCE.mpv_command_string(h, "cycle mute")
                                    "f" -> currentOnFullscreenToggle?.invoke()
                                    else -> {
                                        // MpvLibrary.INSTANCE.mpv_command_string(h, "keydown $mpvKey")
                                    }
                                }
                            }
                        }
                    }
                    false
                }
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this.keyDispatcher)
            }

            override fun removeNotify() {
                // Instantly hide and shrink the component to 0x0 to prevent the Skia/AWT
                // teardown gap from exposing a white flash.
                this.isVisible = false
                this.bounds = java.awt.Rectangle(0, 0, 0, 0)

                // Find and remove the dispatcher to prevent memory leaks
                val focusManager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                this.keyDispatcher?.let {
                    focusManager.removeKeyEventDispatcher(it)
                }

                val h = mpvHandle
                mpvHandle = null
                playerState?.detachMpv()

                // Teardown the MPV handle off the EDT to avoid deadlocking with Compose
                if (h != null) {
                    com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                        } catch (e: Throwable) {
                            com.lagradost.common.logging.AppLogger.e("BaseMpvPlayer", "Failed to destroy mpv handle: ${e.message}")
                        }
                    }
                }
                super.removeNotify()
            }
        }.apply {
            background = Color.BLACK
            isFocusable = true
        }
    }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            videoCanvas.isVisible = false
            videoCanvas.bounds = java.awt.Rectangle(0, 0, 0, 0)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoCanvas.isVisible = false
            val h = mpvHandle
            if (h != null) {
                mpvHandle = null
                playerState?.detachMpv()

                // Push the heavy C++ teardown to a background daemon thread
                // to prevent blocking the Compose EDT.
                java.lang.Thread({
                    com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Destroying mpv engine on daemon thread...")
                    MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                }, "cs3-player-dispose").apply {
                    isDaemon = true
                    start()
                }
            }
        }
    }

    videoRenderer(videoCanvas, mpvHandle)
}

private fun resolveMpvExecutable(isWindows: Boolean): File? {
    val names = if (isWindows) listOf("libmpv-2.dll") else listOf("libmpv.so", "libmpv.dylib")

    val resDir = System.getProperty("compose.application.resources.dir")

    val candidates = listOfNotNull(
        resDir?.let { File(it, "mpv") },
        File("mpv"),
        File("2_cloudstream_desktop/mpv"),
        File("desktop-app/mpv"),
        File("desktop-app/appResources/mpv"),
        File("desktop-app/appResources/windows/mpv"),
    )
    for (base in candidates) {
        for (name in names) {
            val f = File(base, name)
            if (f.isFile) return f.absoluteFile
        }
    }
    return null
}

private fun awtKeyToMpv(e: KeyEvent): String? {
    if (e.isShiftDown) {
        when (e.keyCode) {
            KeyEvent.VK_3 -> return "#"
            KeyEvent.VK_1 -> return "!"
            KeyEvent.VK_2 -> return "@"
            KeyEvent.VK_4 -> return "$"
            KeyEvent.VK_5 -> return "%"
            KeyEvent.VK_6 -> return "^"
            KeyEvent.VK_7 -> return "&"
            KeyEvent.VK_8 -> return "*"
            KeyEvent.VK_9 -> return "("
            KeyEvent.VK_0 -> return ")"
            KeyEvent.VK_OPEN_BRACKET -> return "{"
            KeyEvent.VK_CLOSE_BRACKET -> return "}"
            KeyEvent.VK_COMMA -> return "<"
            KeyEvent.VK_PERIOD -> return ">"
            KeyEvent.VK_MINUS -> return "_"
            KeyEvent.VK_EQUALS -> return "+"
            KeyEvent.VK_Q -> return "QUIT_OVERRIDE"
            in KeyEvent.VK_A..KeyEvent.VK_Z -> {
                val letter = KeyEvent.getKeyText(e.keyCode).uppercase()
                val ctrl = if (e.isControlDown) "Ctrl+" else ""
                val alt = if (e.isAltDown) "Alt+" else ""
                return "$ctrl$alt$letter"
            }
        }
    }

    val baseKey = when (e.keyCode) {
        KeyEvent.VK_SPACE -> "SPACE"
        KeyEvent.VK_LEFT -> "LEFT"
        KeyEvent.VK_RIGHT -> "RIGHT"
        KeyEvent.VK_UP -> "UP"
        KeyEvent.VK_DOWN -> "DOWN"
        KeyEvent.VK_ENTER -> "ENTER"
        KeyEvent.VK_ESCAPE -> "ESC"
        KeyEvent.VK_BACK_SPACE -> "BS"
        KeyEvent.VK_DELETE -> "DEL"
        KeyEvent.VK_TAB -> "TAB"
        KeyEvent.VK_PAGE_UP -> "PGUP"
        KeyEvent.VK_PAGE_DOWN -> "PGDWN"
        KeyEvent.VK_HOME -> "HOME"
        KeyEvent.VK_END -> "END"

        KeyEvent.VK_Q -> "QUIT_OVERRIDE"

        in KeyEvent.VK_A..KeyEvent.VK_Z -> KeyEvent.getKeyText(e.keyCode).lowercase()
        in KeyEvent.VK_0..KeyEvent.VK_9 -> KeyEvent.getKeyText(e.keyCode)

        KeyEvent.VK_COMMA -> ","
        KeyEvent.VK_PERIOD -> "."
        KeyEvent.VK_SLASH, KeyEvent.VK_DIVIDE -> "/"
        KeyEvent.VK_MULTIPLY -> "*"
        KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> "-"
        KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> "+"
        KeyEvent.VK_OPEN_BRACKET -> "["
        KeyEvent.VK_CLOSE_BRACKET -> "]"
        KeyEvent.VK_BACK_SLASH -> "\\"
        KeyEvent.VK_SEMICOLON -> ";"
        KeyEvent.VK_QUOTE -> "'"

        else -> return null
    }

    val alt = if (e.isAltDown) "Alt+" else ""
    val ctrl = if (e.isControlDown) "Ctrl+" else ""
    val shift = if (e.isShiftDown && e.keyCode !in KeyEvent.VK_A..KeyEvent.VK_Z && baseKey.length > 1) "Shift+" else ""

    return "$ctrl$alt$shift$baseKey"
}
