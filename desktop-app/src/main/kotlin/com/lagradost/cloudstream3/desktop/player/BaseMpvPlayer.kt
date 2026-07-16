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
    link: ExtractorLink,
    title: String?,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    startPositionMs: Long,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    isExiting: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    playerState: com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    videoRenderer: @Composable (videoCanvas: Canvas, mpvHandle: com.sun.jna.Pointer?) -> Unit,
) {
    var mpvHandle by remember { mutableStateOf<com.sun.jna.Pointer?>(null) }
    var hasEverPlayed by remember { mutableStateOf(false) }
    var loadStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    // Tracks when the last loadfile command was sent. 0L = no loadfile issued yet.
    // Used to gate the idle-active fast-fail check — MPV starts in idle-active=yes
    // and also returns to idle-active=yes after stop(), so we must not check until
    // AFTER loadfile has been issued and had time to take effect.
    var loadfileIssuedAt by remember { mutableStateOf(0L) }

    val currentOnPlaybackReady by rememberUpdatedState(onPlaybackReady)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentOnFullscreenToggle by rememberUpdatedState(onFullscreenToggle)

    LaunchedEffect(mpvHandle) {
        val h = mpvHandle
        if (h != null) {
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

                var lastPos = 0.0
                var lastDur = 0.0
                var lastEofReached = false
                var lastPeriodicUpdate = 0L
                var lastUiPositionEmit = 0L
                var diagnosticLogged = false
                var playbackStartedAt = 0L
                var lastErrorLoadfileAt = -1L

                while (isActive) {
                    try {
                        // Block IO thread for up to 200ms waiting for an event.
                        // This allows instant response (0ms latency) if an event arrives!
                        val eventPtr = MpvLibrary.INSTANCE.mpv_wait_event(h, 0.2)
                        if (eventPtr != null) {
                            val event = MpvLibrary.MpvEvent(eventPtr)
                            val eventId = event.event_id

                            if (eventId == 2) { // MPV_EVENT_SHUTDOWN
                                break
                            }

                            if (eventId == 7) { // MPV_EVENT_END_FILE
                                val endFilePtr = event.data
                                if (endFilePtr != null) {
                                    val endFile = MpvLibrary.MpvEventEndFile(endFilePtr)
                                    if (endFile.reason == 4) { // MPV_END_FILE_REASON_ERROR
                                        // 403 Forbidden or generic network error
                                        com.lagradost.common.logging.AppLogger.e("MPV instant failure (MPV_END_FILE_REASON_ERROR)")
                                        val currentLoad = loadfileIssuedAt
                                        if (lastErrorLoadfileAt != currentLoad) {
                                            lastErrorLoadfileAt = currentLoad
                                            currentOnPlaybackError("Connection rejected by source (HTTP error or dead link).")
                                        }
                                    }
                                }
                            }

                            if (eventId == 21 || eventId == 8) { // MPV_EVENT_PLAYBACK_RESTART or MPV_EVENT_FILE_LOADED
                                if (!hasEverPlayed) {
                                    hasEverPlayed = true
                                    playbackStartedAt = System.currentTimeMillis()
                                    playerState?.isBuffering?.value = false
                                    playerState?.isProbing?.value = false
                                    currentOnPlaybackReady()
                                }
                            }

                            if (eventId == 22) { // MPV_EVENT_PROPERTY_CHANGE
                                val propPtr = event.data
                                if (propPtr != null) {
                                    val prop = MpvLibrary.MpvEventProperty(propPtr)
                                    val name = prop.name
                                    if (name != null && prop.format != 0 && prop.data != null) {
                                        when (name) {
                                            "time-pos" -> {
                                                if (prop.format == 5) {
                                                    lastPos = prop.data!!.getDouble(0)
                                                    if (!hasEverPlayed && lastPos > 0.1) {
                                                        hasEverPlayed = true
                                                        playbackStartedAt = System.currentTimeMillis()
                                                        playerState?.isBuffering?.value = false
                                                        playerState?.isProbing?.value = false
                                                        currentOnPlaybackReady()
                                                    }
                                                }

                                                // One-shot diagnostic: 30s after playback starts,
                                                // log the actual rendering pipeline and frame stats.
                                                // This fires exactly once to help debug stutter.
                                                if (!diagnosticLogged && playbackStartedAt > 0 &&
                                                    System.currentTimeMillis() - playbackStartedAt > 30000
                                                ) {
                                                    diagnosticLogged = true
                                                    val dVo = MpvLibrary.getPropertyString(h, "current-vo")
                                                    val dHwdec = MpvLibrary.getPropertyString(h, "hwdec-current")
                                                    val dCodec = MpvLibrary.getPropertyString(h, "video-codec")
                                                    val dDropped = MpvLibrary.getPropertyString(h, "vo-drop-frame-count")
                                                    val dFps = MpvLibrary.getPropertyString(h, "estimated-vf-fps")
                                                    val dW = MpvLibrary.getPropertyString(h, "width")
                                                    val dH = MpvLibrary.getPropertyString(h, "height")
                                                    val dCache = MpvLibrary.getPropertyString(h, "demuxer-cache-duration")
                                                    com.lagradost.common.logging.AppLogger.i(
                                                        "MPV 30s DIAGNOSTIC: vo=$dVo hwdec=$dHwdec codec=$dCodec " +
                                                            "dropped=$dDropped fps=$dFps res=${dW}x$dH cache=${dCache}s",
                                                    )
                                                }

                                                // Throttle UI position updates to 4 Hz max.
                                                // MPV fires time-pos at video framerate (24-60 Hz),
                                                // and each update triggers Compose recomposition of
                                                // the seekbar + time labels, competing with MPV's
                                                // render thread for GPU time.
                                                val now = System.currentTimeMillis()
                                                if (now - lastUiPositionEmit > 16) {
                                                    lastUiPositionEmit = now
                                                    val posMs = (lastPos * 1000).toLong()
                                                    // Route through debouncer so stale time-pos events
                                                    // from before a seek don't visually snap the slider back.
                                                    playerState?.updatePositionFromPlayer(posMs)
                                                    currentOnPositionChange(posMs, (lastDur * 1000).toLong())
                                                }
                                            }
                                            "duration" -> {
                                                if (prop.format == 5) lastDur = prop.data!!.getDouble(0)
                                                playerState?.durationMs?.value = (lastDur * 1000).toLong()
                                            }
                                            "pause" -> {
                                                if (prop.format == 3) playerState?.isPaused?.value = prop.data!!.getInt(0) != 0
                                            }
                                            "paused-for-cache" -> {
                                                if (prop.format == 3) {
                                                    val isBuffering = prop.data!!.getInt(0) != 0
                                                    // Only update buffering state if we have actually started playing
                                                    if (hasEverPlayed) {
                                                        playerState?.isBuffering?.value = isBuffering
                                                    }
                                                }
                                            }
                                            "eof-reached" -> {
                                                if (prop.format == 3) lastEofReached = prop.data!!.getInt(0) != 0
                                            }
                                            "volume" -> {
                                                if (prop.format == 5) playerState?.volume?.value = prop.data!!.getDouble(0).toFloat()
                                            }
                                            "speed" -> {
                                                if (prop.format == 5) playerState?.playbackSpeed?.value = prop.data!!.getDouble(0).toFloat()
                                            }
                                            "core-idle" -> {
                                                if (prop.format == 3) {
                                                    val isCoreIdle = prop.data!!.getInt(0) != 0
                                                    // If hasn't played yet and core is idle, it is probing the network
                                                    if (!hasEverPlayed) {
                                                        playerState?.isProbing?.value = isCoreIdle
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Throttle non-critical string property polling to at most every 1s
                        // (was 200ms — 5Hz Compose recomposition of the seekbar buffer indicator
                        // was competing with MPV's render thread for GPU time)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastPeriodicUpdate >= 1000L) {
                            lastPeriodicUpdate = currentTime

                            // Check buffer state (demuxer cache is noisy, we poll it manually)
                            val demuxerCacheStr = MpvLibrary.getPropertyString(h, "demuxer-cache-duration")
                            val demuxerCacheSec = demuxerCacheStr?.toDoubleOrNull() ?: 0.0
                            if (demuxerCacheSec > 0.0) {
                                playerState?.bufferMs?.value = ((lastPos + demuxerCacheSec) * 1000).toLong()
                            } else {
                                playerState?.bufferMs?.value = ((lastPos) * 1000).toLong()
                            }

                            val currentDur = MpvLibrary.getPropertyString(h, "duration")?.toDoubleOrNull()
                            if (currentDur != null && currentDur > 0.0) {
                                lastDur = currentDur
                                playerState?.durationMs?.value = (lastDur * 1000).toLong()
                            }

                            val currentPos = MpvLibrary.getPropertyString(h, "time-pos")?.toDoubleOrNull()
                            if (currentPos != null && currentPos >= 0.0) {
                                lastPos = currentPos

                                // Emit to UI using the same throttle so it works even if native events are dropped!
                                val now = System.currentTimeMillis()
                                if (now - lastUiPositionEmit > 16) {
                                    lastUiPositionEmit = now
                                    val posMs = (lastPos * 1000).toLong()
                                    playerState?.updatePositionFromPlayer(posMs)
                                    currentOnPositionChange(posMs, (lastDur * 1000).toLong())
                                }
                            }

                            // Poll tracks and stats (already throttled to 1000ms outside this)
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
                            playerState?.audioTracks?.value = audioTracks
                            playerState?.subtitleTracks?.value = subTracks
                            playerState?.videoTracks?.value = videoTracks
                            // Poll Video Stats
                            if (playerState != null && playerState.showStats.value) {
                                playerState.videoCodec.value = MpvLibrary.getPropertyString(h, "video-codec") ?: "Unknown"
                                playerState.audioCodec.value = MpvLibrary.getPropertyString(h, "audio-codec") ?: "Unknown"
                                playerState.hwdecCurrent.value = MpvLibrary.getPropertyString(h, "hwdec-current") ?: "Unknown"
                                playerState.droppedFrames.value = MpvLibrary.getPropertyString(h, "vo-drop-frame-count")?.toLongOrNull() ?: 0L
                                playerState.fps.value = MpvLibrary.getPropertyString(h, "container-fps")?.toDoubleOrNull() ?: 0.0
                                val w = MpvLibrary.getPropertyString(h, "width") ?: "0"
                                val hw = MpvLibrary.getPropertyString(h, "height") ?: "0"
                                playerState.resolution.value = "${w}x$hw"
                                playerState.videoBitrate.value = MpvLibrary.getPropertyString(h, "video-bitrate")?.toLongOrNull() ?: 0L
                                playerState.audioBitrate.value = MpvLibrary.getPropertyString(h, "audio-bitrate")?.toLongOrNull() ?: 0L
                            }
                        } // End of periodic update block
                        loops++

                        // Check for completion
                        if (lastEofReached) {
                            // For live/non-seekable streams, EOF just means the current HTTP chunk ended.
                            // Don't treat it as "finished" — the stream may resume.
                            val seekableStr = MpvLibrary.getPropertyString(h, "seekable")
                            val isSeekable = seekableStr == "yes"
                            val currentLoad = loadfileIssuedAt
                            if (lastErrorLoadfileAt != currentLoad) {
                                if (isSeekable) {
                                    // Normal VOD stream — genuinely finished
                                    // If it finished suspiciously fast (under 2 seconds), it's a dead/corrupt stream
                                    // that MPV instantly skipped to the end of due to bad packets.
                                    val timeSinceLoad = System.currentTimeMillis() - loadStartTime
                                    if (timeSinceLoad < 2000) {
                                        lastErrorLoadfileAt = currentLoad
                                        currentOnPlaybackError("Stream is empty or corrupt.")
                                    } else if (hasEverPlayed) {
                                        lastErrorLoadfileAt = currentLoad
                                        currentOnFinished()
                                    } else {
                                        lastErrorLoadfileAt = currentLoad
                                        currentOnPlaybackError("Stream failed to load or instantly ended.")
                                    }
                                } else if (!hasEverPlayed) {
                                    // Non-seekable stream that never played — it's truly dead
                                    lastErrorLoadfileAt = currentLoad
                                    currentOnPlaybackError("Stream failed to load or instantly ended.")
                                }
                            }
                            // else: non-seekable stream that HAS played before — likely a live stream
                            // that hit a temporary EOF. Don't break, let MPV's internal reconnect handle it.
                        }

                        // Fast-fail detection
                        val currentLoad = loadfileIssuedAt
                        if (lastErrorLoadfileAt != currentLoad && !hasEverPlayed && currentLoad > 0 &&
                            System.currentTimeMillis() - currentLoad > 800
                        ) {
                            val idleActive = MpvLibrary.getPropertyString(h, "idle-active")
                            if (idleActive == "yes") {
                                lastErrorLoadfileAt = currentLoad
                                com.lagradost.common.logging.AppLogger.e("MPV went idle-active after loadfile — stream failed (likely 403/404)")
                                currentOnPlaybackError("Stream failed to load (connection rejected or forbidden).")
                            }
                        }

                        // Check timeout. Allow user to strictly control how long they wait before skipping.
                        val timeoutStr = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT)
                        val userTimeoutMs = timeoutStr?.toLongOrNull() ?: 20000L

                        // For complex HLS streams with many tracks, probing all of them in safe batches takes 10-15s.
                        // Enforce a minimum of 90s timeout here to ensure FFmpeg isn't killed prematurely.
                        val timeoutMs = maxOf(userTimeoutMs, 90000L)

                        if (lastErrorLoadfileAt != currentLoad && !hasEverPlayed && System.currentTimeMillis() - loadStartTime > timeoutMs) {
                            lastErrorLoadfileAt = currentLoad
                            com.lagradost.common.logging.AppLogger.e("MPV timeout reached while buffering ($timeoutMs ms)")
                            currentOnPlaybackError("Connection timed out. The stream might be dead or too slow.")
                        }
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

    LaunchedEffect(link, title, mpvHandle) {
        // IMPORTANT: Prevent the watcher loop from false-firing its idle-active check
        // while we are setting up the new link.
        loadfileIssuedAt = 0L

        val handle = mpvHandle ?: return@LaunchedEffect

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
        if (startSec > 0) {
            lib.mpv_set_property_string(handle, "start", startSec.toString())
        }

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
            val posStr = MpvLibrary.getPropertyString(handle, "time-pos")
            if (posStr == null || posStr.toDoubleOrNull() == null) break
            kotlinx.coroutines.delay(50)
            waitAttempts++
        }

        hasEverPlayed = false // Now it's safe to reset the playback flag for the new link
        loadStartTime = System.currentTimeMillis() // Reset the buffering timeout clock
        loadfileIssuedAt = System.currentTimeMillis() // Mark that loadfile is about to be sent

        lib.mpv_command_string(handle, "loadfile \"$safeUrl\"")

        // Ensure the player is unpaused when loading a new link,
        // since the UI might have paused it while buffering
        lib.mpv_set_property_string(handle, "pause", "no")
        playerState?.isPaused?.value = false

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
        launch(kotlinx.coroutines.Dispatchers.IO) {
            var attempts = 0
            while (attempts < 150) {
                // Guard: mpv handle may be destroyed if user navigates away quickly.
                // Calling mpv_get_property_string on a freed handle causes JNA Invalid memory access.
                if (mpvHandle == null) break
                val posStr = try {
                    MpvLibrary.getPropertyString(capturedHandle, "time-pos")
                } catch (e: Error) {
                    break // JNA native crash guard — handle was freed
                }
                if ((posStr?.toDoubleOrNull() ?: 0.0) > 0.0) {
                    if (!hasEverPlayed) {
                        hasEverPlayed = true
                        playerState?.isBuffering?.value = false
                        playerState?.isProbing?.value = false
                        currentOnPlaybackReady()
                    }
                    break
                }
                kotlinx.coroutines.delay(200)
                attempts++
            }

            val defaultSub = finalSubtitles.firstOrNull()
            finalSubtitles.forEach { sub ->
                if (mpvHandle != null) {
                    val escapedSub = sub.url.replace("\\", "\\\\").replace("\"", "\\\"")
                    val escapedTitle = sub.lang.replace("\\", "\\\\").replace("\"", "\\\"")
                    try {
                        val flag = if (sub == defaultSub) "select" else "auto"
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

                com.lagradost.common.logging.AppLogger.i("Initializing embedded MPV Engine")
                lib.mpv_initialize(handle)

                // Post-init diagnostics: log what MPV actually chose for VO/hwdec
                // so we can debug rendering issues without enabling verbose logging
                val actualVo = MpvLibrary.getPropertyString(handle, "current-vo")
                val actualHwdec = MpvLibrary.getPropertyString(handle, "hwdec-current")
                com.lagradost.common.logging.AppLogger.i("MPV initialized: vo=$actualVo, hwdec=$actualHwdec")

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

                // DEFERRED CLEANUP: Wait 150ms for Compose/Skia to draw the DetailsScreen
                // over the empty space before actually destroying the native window.
                if (h != null) {
                    Thread {
                        Thread.sleep(150)
                        MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                    }.start()
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
            val h = mpvHandle
            if (h != null) {
                mpvHandle = null
                MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                playerState?.detachMpv()
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
