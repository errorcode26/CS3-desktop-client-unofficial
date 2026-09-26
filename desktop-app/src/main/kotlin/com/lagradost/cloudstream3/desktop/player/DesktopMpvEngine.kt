package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge
import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.player.impl.proxy.LocalStreamProxyState
import com.sun.jna.Pointer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Headless controller for the native MPV core engine.
 * Decouples JNI pointer manipulation, background event loops, and hardware lifecycle from Compose.
 */
class DesktopMpvEngine(
    val scope: CoroutineScope,
    var playerState: PlayerState? = null,
    var onPlaybackReady: () -> Unit = {},
    var onPlaybackError: (String) -> Unit = {},
    var onFinished: () -> Unit = {},
    var onPositionChange: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    var onEventLoopReady: ((handle: Pointer) -> Unit)? = null,
    var isLive: Boolean = false,
) {
    @Volatile
    private var mpvHandle: Pointer? = null
    private var eventJob: Job? = null
    private val isDestroyed = AtomicBoolean(false)

    val nativeHandleValue: Long?
        get() = mpvHandle?.let { Pointer.nativeValue(it) }

    fun isHandleValid(): Boolean = mpvHandle != null && !isDestroyed.get()

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
     * Initializes the native MPV instance and configures options before initialization.
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
            val lib = MpvLibrary.INSTANCE
            val handle = lib.mpv_create() ?: run {
                AppLogger.e("DesktopMpvEngine", "Failed to create MPV instance (mpv_create returned null)")
                onPlaybackError("Failed to initialize MPV Engine.")
                return null
            }
            mpvHandle = handle

            // Disable native UI since we draw our own Compose / WebView UI
            lib.mpv_set_option_string(handle, "osc", "no")
            lib.mpv_set_option_string(handle, "osd-level", "0")
            lib.mpv_set_option_string(handle, "osd-bar", "no")

            // VO: use battle-tested 'gpu' output
            lib.mpv_set_option_string(handle, "vo", "gpu")

            // Apply user preferences
            PlayerConfig.applyMpvSettings(handle, lib)

            lib.mpv_set_option_string(handle, "wid", canvasWid.toString())
            lib.mpv_set_option_string(handle, "input-default-bindings", "no")
            lib.mpv_set_option_string(handle, "input-vo-keyboard", "no")
            lib.mpv_set_option_string(handle, "save-position-on-quit", "no")
            lib.mpv_set_option_string(handle, "resume-playback", "no")
            lib.mpv_set_option_string(handle, "keep-open", "yes")
            val ytdlBinary = DesktopYtDlpBinary()
            if (ytdlBinary.isInstalled()) {
                val binaryPath = ytdlBinary.getBinaryFile().absolutePath.replace("\\", "/")
                lib.mpv_set_option_string(handle, "ytdl", "yes")
                lib.mpv_set_option_string(handle, "script-opts", "ytdl_hook-ytdl_path=$binaryPath")
                AppLogger.i("DesktopMpvEngine", "Embedded yt-dlp hook enabled: $binaryPath")
            } else {
                lib.mpv_set_option_string(handle, "ytdl", "no")
            }
            lib.mpv_set_option_string(handle, "idle", "yes")

            onPreInit?.invoke(handle, canvasWid, width, height)

            AppLogger.i("DesktopMpvEngine", "Initializing embedded MPV Engine")
            val initCode = lib.mpv_initialize(handle)
            if (initCode < 0) {
                AppLogger.e("DesktopMpvEngine", "mpv_initialize failed with code: $initCode")
                destroy()
                onPlaybackError("MPV initialization failed (code $initCode).")
                return null
            }

            lib.mpv_request_log_messages(handle, "info")
            onPostInit?.invoke(handle)

            val actualVo = MpvLibrary.getPropertyString(handle, "current-vo")
            val actualHwdec = MpvLibrary.getPropertyString(handle, "hwdec-current")
            AppLogger.i("DesktopMpvEngine", "MPV Engine initialized successfully: vo=$actualVo, hwdec=$actualHwdec")

            startEventLoop(handle)
            return handle
        } catch (e: Throwable) {
            AppLogger.e("DesktopMpvEngine", "Exception during MPV creation: ${e.message}", e)
            onPlaybackError("MPV Engine error: ${e.message}")
            return null
        }
    }

    private fun startEventLoop(handle: Pointer) {
        onEventLoopReady?.invoke(handle)

        eventJob = scope.launch(Dispatchers.IO) {
            val lib = MpvLibrary.INSTANCE
            lib.mpv_observe_property(handle, 1L, "time-pos", 5) // Double
            lib.mpv_observe_property(handle, 2L, "duration", 5) // Double
            lib.mpv_observe_property(handle, 3L, "pause", 3) // Flag
            lib.mpv_observe_property(handle, 4L, "eof-reached", 3) // Flag
            lib.mpv_observe_property(handle, 5L, "volume", 5) // Double
            lib.mpv_observe_property(handle, 6L, "speed", 5) // Double
            lib.mpv_observe_property(handle, 7L, "core-idle", 3) // Flag
            lib.mpv_observe_property(handle, 8L, "paused-for-cache", 3) // Flag
            lib.mpv_observe_property(handle, 9L, "mute", 3) // Flag

            var lastPositionPollMs = 0L
            var lastTrackPollMs = 0L
            var lastUiPositionEmit = 0L
            var diagnosticLogged = false
            var playbackStartedAt = 0L
            var hasAutoSwitchedAudio = false
            var hasAutoSwitchedSub = false
            var hasFiredFinished = false
            var lastStreamErrorReason: String? = null

            fun pollTracksAndChapters(h: Pointer) {
                val trackCountStr = MpvLibrary.getPropertyString(h, "track-list/count")
                val trackCount = trackCountStr?.toIntOrNull() ?: 0

                val audioTracks = mutableListOf<PlayerState.VideoTrack>()
                val subTracks = mutableListOf<PlayerState.VideoTrack>()
                val videoTracks = mutableListOf<PlayerState.VideoTrack>()
                val audioSearchInfo = mutableListOf<Triple<Int, Boolean, String>>()
                val subSearchInfo = mutableListOf<Triple<Int, Boolean, String>>()

                for (i in 0 until trackCount) {
                    val id = MpvLibrary.getPropertyString(h, "track-list/$i/id")?.toIntOrNull() ?: continue
                    val type = MpvLibrary.getPropertyString(h, "track-list/$i/type") ?: continue
                    val lang = MpvLibrary.getPropertyString(h, "track-list/$i/lang")
                    val title = MpvLibrary.getPropertyString(h, "track-list/$i/title")
                    val codec = MpvLibrary.getPropertyString(h, "track-list/$i/codec")
                    val isForced = MpvLibrary.getPropertyString(h, "track-list/$i/forced") == "yes"
                    val isDefault = MpvLibrary.getPropertyString(h, "track-list/$i/default") == "yes"
                    val isOriginal = MpvLibrary.getPropertyString(h, "track-list/$i/original") == "yes"
                    val isExternal = MpvLibrary.getPropertyString(h, "track-list/$i/external") == "yes"
                    val channels = MpvLibrary.getPropertyString(h, "track-list/$i/audio-channels")
                        ?: MpvLibrary.getPropertyString(h, "track-list/$i/demux-channel-count")
                    val selected = MpvLibrary.getPropertyString(h, "track-list/$i/selected") == "yes"

                    val name = cleanTrackDisplayName(
                        type = type,
                        id = id,
                        rawTitle = title,
                        rawLang = lang,
                        codec = codec,
                        isForced = isForced,
                        isDefault = isDefault,
                        isExternal = isExternal,
                        channels = channels,
                    )
                    if (type == "audio") {
                        audioTracks.add(PlayerState.VideoTrack(id, name, selected))
                        audioSearchInfo.add(Triple(id, selected, "${lang.orEmpty()} ${title.orEmpty()} $name ${if (isOriginal) "original" else ""}"))
                    } else if (type == "sub") {
                        subTracks.add(PlayerState.VideoTrack(id, name, selected))
                        subSearchInfo.add(Triple(id, selected, "${lang.orEmpty()} ${title.orEmpty()} $name"))
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

                // Disambiguate duplicates
                val subNameCounts = subTracks.groupingBy { it.name }.eachCount()
                val subDupTracker = mutableMapOf<String, Int>()
                val disambiguatedSubTracks = subTracks.map { track ->
                    if ((subNameCounts[track.name] ?: 0) > 1) {
                        val idx = (subDupTracker[track.name] ?: 0) + 1
                        subDupTracker[track.name] = idx
                        track.copy(name = "${track.name} #$idx")
                    } else {
                        track
                    }
                }

                val audioNameCounts = audioTracks.groupingBy { it.name }.eachCount()
                val audioDupTracker = mutableMapOf<String, Int>()
                val disambiguatedAudioTracks = audioTracks.map { track ->
                    if ((audioNameCounts[track.name] ?: 0) > 1) {
                        val idx = (audioDupTracker[track.name] ?: 0) + 1
                        audioDupTracker[track.name] = idx
                        track.copy(name = "${track.name} #$idx")
                    } else {
                        track
                    }
                }

                playerState?._audioTracks?.value = disambiguatedAudioTracks
                playerState?._subtitleTracks?.value = disambiguatedSubTracks
                playerState?._videoTracks?.value = videoTracks

                val vFormat = MpvLibrary.getPropertyString(h, "video-format")
                val hasVideo = videoTracks.isNotEmpty() || (vFormat?.isNotBlank() == true && !vFormat.equals("none", ignoreCase = true))
                val isAudioOnly = trackCount > 0 && !hasVideo
                if (isAudioOnly) {
                    playerState?._isAudioOnlyStream?.value = true
                } else if (trackCount > 0 && hasVideo) {
                    playerState?._isAudioOnlyStream?.value = false
                }

                // Auto audio track selection based on priority
                if (!hasAutoSwitchedAudio && audioSearchInfo.isNotEmpty()) {
                    val candidateLangs = LanguagePriorityHelper.getOrderedAudioLanguages()
                    for (langCode in candidateLangs) {
                        val target = audioSearchInfo.firstOrNull { (_, _, meta) ->
                            LanguageMatcher.matchesAudioTrack(null, null, meta, langCode)
                        }
                        if (target != null) {
                            if (!target.second) {
                                AppLogger.i("DesktopMpvEngine", "Auto-switching audio track to id=${target.first} for priority lang=$langCode")
                                lib.mpv_set_property_string(h, "aid", target.first.toString())
                            }
                            hasAutoSwitchedAudio = true
                            break
                        }
                    }
                }

                // Auto subtitle track selection based on priority
                val subEnabled = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_SUB_ENABLED)
                    ?: (DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_SUB_LANG) != "off")

                if (!subEnabled) {
                    if (!hasAutoSwitchedSub) {
                        val currentSid = MpvLibrary.getPropertyString(h, "sid")
                        if (currentSid != null && currentSid != "no") {
                            lib.mpv_set_property_string(h, "sid", "no")
                        }
                        hasAutoSwitchedSub = true
                    }
                } else if (!hasAutoSwitchedSub && !(playerState?.hasUserManuallyChangedSub ?: false) && subSearchInfo.isNotEmpty()) {
                    val candidateLangs = LanguagePriorityHelper.getOrderedSubtitleLanguages()
                    for (langCode in candidateLangs) {
                        val target = subSearchInfo.firstOrNull { (_, _, meta) ->
                            LanguageMatcher.matchesSubtitleTrack(null, null, meta, langCode)
                        }
                        if (target != null) {
                            if (!target.second) {
                                AppLogger.i("DesktopMpvEngine", "Auto-switching subtitle track to id=${target.first} for priority lang=$langCode")
                                lib.mpv_set_property_string(h, "sid", target.first.toString())
                                lib.mpv_set_property_string(h, "sub-visibility", "yes")
                            }
                            hasAutoSwitchedSub = true
                            break
                        }
                    }
                }

                // Lazy audio track fallback
                val lazyAudios = LocalStreamProxyState.lazyAudioTracks.value
                if (trackCount > 0 && audioTracks.isEmpty() && lazyAudios.isNotEmpty()) {
                    val currentActiveUrl = playerState?.activeLazyAudioTrackUrl?.value
                    val targetTrack = if (currentActiveUrl != null) {
                        lazyAudios.find { it.url == currentActiveUrl } ?: lazyAudios.first()
                    } else {
                        val candidateLangs = LanguagePriorityHelper.getOrderedAudioLanguages()
                        val matchedAudio = candidateLangs.firstNotNullOfOrNull { candidateCode ->
                            val keywords = LanguageMatcher.getKeywordsForCode(candidateCode)
                            lazyAudios.firstOrNull { audio ->
                                val combined = "${audio.language} ${audio.name}".lowercase()
                                keywords.any { kw -> combined.contains(kw) }
                            }
                        }
                        matchedAudio ?: lazyAudios.first()
                    }
                    AppLogger.i("DesktopMpvEngine", "Auto-attaching audio track: ${targetTrack.name}")
                    playerState?.loadLazyAudioTrack(PlayerState.LazyTrack(targetTrack.url, targetTrack.name, targetTrack.language, targetTrack.bitrate))
                }

                val w = MpvLibrary.getPropertyString(h, "width") ?: "0"
                val hw = MpvLibrary.getPropertyString(h, "height") ?: "0"
                if (w != "0" && hw != "0") {
                    playerState?._resolution?.value = "${w}x$hw"
                }

                // Poll Chapters
                val chapterCount = MpvLibrary.getPropertyString(h, "chapter-list/count")?.toIntOrNull() ?: 0
                if (chapterCount > 0) {
                    val chapters = mutableListOf<PlayerState.Chapter>()
                    for (c in 0 until chapterCount) {
                        val chTitle = MpvLibrary.getPropertyString(h, "chapter-list/$c/title") ?: "Chapter ${c + 1}"
                        val chTime = MpvLibrary.getPropertyString(h, "chapter-list/$c/time")?.toDoubleOrNull() ?: 0.0
                        chapters.add(PlayerState.Chapter(index = c, title = chTitle, timeMs = (chTime * 1000).toLong()))
                    }
                    playerState?.updateChaptersFromPlayer(chapters)
                    val currentChapter = MpvLibrary.getPropertyString(h, "chapter")?.toIntOrNull() ?: -1
                    playerState?._currentChapterIndex?.value = currentChapter
                } else {
                    playerState?.updateChaptersFromPlayer(emptyList())
                    playerState?._currentChapterIndex?.value = -1
                }
            }

            while (isActive && !isDestroyed.get()) {
                try {
                    val eventPtr = lib.mpv_wait_event(handle, 0.05)
                    if (eventPtr != null) {
                        val event = MpvLibrary.MpvEvent(eventPtr)
                        when (event.event_id) {
                            1 -> { // MPV_EVENT_SHUTDOWN
                                AppLogger.i("DesktopMpvEngine", "MPV Engine shutting down (MPV_EVENT_SHUTDOWN)")
                                break
                            }
                            6 -> { // MPV_EVENT_START_FILE
                                AppLogger.i("DesktopMpvEngine", "Starting new media stream (MPV_EVENT_START_FILE)")
                                lastStreamErrorReason = null
                                waitingForTimePosReset = false
                                hasEverPlayed = false
                                playbackStartedAt = 0L
                                diagnosticLogged = false
                                hasAutoSwitchedAudio = false
                                hasAutoSwitchedSub = false
                                hasFiredFinished = false
                                playerState?._isAudioOnlyStream?.value = false
                            }
                            7 -> { // MPV_EVENT_END_FILE
                                val endFilePtr = event.data
                                if (endFilePtr != null) {
                                    val endFile = MpvLibrary.MpvEventEndFile(endFilePtr)
                                    AppLogger.i("DesktopMpvEngine", "Media ended (reason=${endFile.reason}, error=${endFile.error})")
                                    if (endFile.reason == 4) { // MPV_END_FILE_REASON_ERROR
                                        val errDesc = lastStreamErrorReason ?: when (endFile.error) {
                                            -2 -> "Failed to load stream (Dead link or HTTP Error)"
                                            -3 -> "Stream format unsupported"
                                            -4 -> "No audio/video streams found"
                                            -8 -> "Connection timed out"
                                            else -> "Stream playback error (code ${endFile.error})"
                                        }
                                        AppLogger.e("DesktopMpvEngine", "Stream error: $errDesc")
                                        onPlaybackError(errDesc)
                                    } else if (endFile.reason == 0) { // MPV_END_FILE_REASON_EOF
                                        if (isLive) {
                                            try {
                                                lib.mpv_command_string(handle, "seek 100 absolute-percent")
                                            } catch (e: Exception) {
                                                AppLogger.e("DesktopMpvEngine", "Live recovery failed", e)
                                            }
                                        } else if (!hasEverPlayed) {
                                            val errDesc = lastStreamErrorReason ?: "Stream instantly closed (Empty / EOF)"
                                            AppLogger.e("DesktopMpvEngine", "Stream ended before playing: $errDesc")
                                            onPlaybackError(errDesc)
                                        } else {
                                            val hasExplicitError = lastStreamErrorReason != null
                                            val isPremature = lastDur > 0 && lastPos < lastDur * 0.85 && hasExplicitError
                                            if (isPremature) {
                                                AppLogger.w("DesktopMpvEngine", "Premature EOF at ${lastPos}s of ${lastDur}s: $lastStreamErrorReason")
                                                onPlaybackError(lastStreamErrorReason ?: "Playback interrupted")
                                            } else if (!hasFiredFinished) {
                                                hasFiredFinished = true
                                                AppLogger.i("DesktopMpvEngine", "Stream reached genuine EOF.")
                                                onFinished()
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> { // MPV_EVENT_LOG_MESSAGE
                                val logData = event.data
                                if (logData != null) {
                                    val logMsg = MpvLibrary.MpvEventLogMessage(logData)
                                    val text = logMsg.text?.trimEnd('\r', '\n') ?: ""
                                    val level = logMsg.level?.lowercase()
                                    if (level in listOf("error", "fatal", "warn")) {
                                        val lower = text.lowercase()
                                        when {
                                            lower.contains("403") || lower.contains("forbidden") -> lastStreamErrorReason = "HTTP 403 Forbidden"
                                            lower.contains("404") || lower.contains("not found") -> lastStreamErrorReason = "HTTP 404 Not Found"
                                            lower.contains("401") || lower.contains("unauthorized") -> lastStreamErrorReason = "HTTP 401 Unauthorized"
                                            lower.contains("429") || lower.contains("too many requests") -> lastStreamErrorReason = "HTTP 429 Rate Limited"
                                            lower.contains("502") || lower.contains("bad gateway") -> lastStreamErrorReason = "HTTP 502 Bad Gateway"
                                            lower.contains("503") || lower.contains("service unavailable") -> lastStreamErrorReason = "HTTP 503 Service Unavailable"
                                            lower.contains("500") || lower.contains("internal server error") -> lastStreamErrorReason = "HTTP 500 Server Error"
                                            lower.contains("timed out") || lower.contains("timeout") -> lastStreamErrorReason = "Connection Timed Out"
                                            lower.contains("certificate") || lower.contains("tls") || lower.contains("ssl") -> lastStreamErrorReason = "SSL/TLS Handshake Error"
                                            lower.contains("connection refused") -> lastStreamErrorReason = "Connection Refused"
                                            lower.contains("could not resolve") || lower.contains("name resolution") -> lastStreamErrorReason = "DNS / Host Resolution Failed"
                                            lower.contains("invalid data") || lower.contains("unsupported") -> lastStreamErrorReason = "Unsupported Stream Format"
                                        }
                                    }
                                }
                            }
                            8 -> { // MPV_EVENT_FILE_LOADED
                                waitingForTimePosReset = false
                                lastTrackPollMs = System.currentTimeMillis()
                                pollTracksAndChapters(handle)
                            }
                            21 -> { // MPV_EVENT_PLAYBACK_RESTART
                                waitingForTimePosReset = false
                                lastTrackPollMs = System.currentTimeMillis()
                                pollTracksAndChapters(handle)

                                val isPausedForCache = MpvLibrary.getPropertyString(handle, "paused-for-cache") == "yes"
                                if (!hasEverPlayed && !isPausedForCache) {
                                    hasEverPlayed = true
                                    playbackStartedAt = System.currentTimeMillis()
                                    playerState?._isBuffering?.value = false
                                    playerState?._isProbing?.value = false
                                    onPlaybackReady()
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
                                                if (prop.format == 5) {
                                                    val newPos = prop.data!!.getDouble(0)
                                                    if (newPos >= 0.0) lastPos = newPos
                                                    if (lastPos > 0.1) waitingForTimePosReset = false
                                                    val isPausedForCache = MpvLibrary.getPropertyString(handle, "paused-for-cache") == "yes"
                                                    if (!hasEverPlayed && lastPos > 0.1 && !isPausedForCache) {
                                                        hasEverPlayed = true
                                                        playbackStartedAt = System.currentTimeMillis()
                                                        playerState?._isBuffering?.value = false
                                                        playerState?._isProbing?.value = false
                                                        onPlaybackReady()
                                                    }

                                                    // 30s diagnostic
                                                    if (!diagnosticLogged && playbackStartedAt > 0 &&
                                                        System.currentTimeMillis() - playbackStartedAt > 30_000
                                                    ) {
                                                        diagnosticLogged = true
                                                        val dVo = MpvLibrary.getPropertyString(handle, "current-vo")
                                                        val dHwdec = MpvLibrary.getPropertyString(handle, "hwdec-current")
                                                        val dCodec = MpvLibrary.getPropertyString(handle, "video-codec")
                                                        val dFps = MpvLibrary.getPropertyString(handle, "estimated-vf-fps")
                                                        val dW = MpvLibrary.getPropertyString(handle, "width")
                                                        val dH = MpvLibrary.getPropertyString(handle, "height")
                                                        AppLogger.i("DesktopMpvEngine", "MPV 30s Diag -> VO: $dVo, HWDEC: $dHwdec, Codec: $dCodec, FPS: $dFps, Res: ${dW}x$dH")
                                                    }

                                                    val now = System.currentTimeMillis()
                                                    if (now - lastUiPositionEmit >= 100) {
                                                        lastUiPositionEmit = now
                                                        val posMs = (lastPos * 1000).toLong()
                                                        playerState?.updatePositionFromPlayer(posMs)
                                                        onPositionChange(posMs, (lastDur * 1000).toLong())
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
                                                if (prop.format == 3) {
                                                    val paused = prop.data!!.getInt(0) != 0
                                                    _isPaused.value = paused
                                                    playerState?._isPaused?.value = paused
                                                }
                                            }
                                            "paused-for-cache" -> {
                                                if (prop.format == 3) {
                                                    val isBuffering = prop.data!!.getInt(0) != 0
                                                    playerState?._isBuffering?.value = isBuffering
                                                    if (!isBuffering && !hasEverPlayed && lastPos > 0.05) {
                                                        hasEverPlayed = true
                                                        playbackStartedAt = System.currentTimeMillis()
                                                        playerState?._isBuffering?.value = false
                                                        playerState?._isProbing?.value = false
                                                        onPlaybackReady()
                                                    }
                                                }
                                            }
                                            "mute" -> {
                                                if (prop.format == 3) playerState?._isMuted?.value = prop.data!!.getInt(0) != 0
                                            }
                                            "volume" -> {
                                                if (prop.format == 5) {
                                                    val vol = prop.data!!.getDouble(0).toFloat()
                                                    _volume.value = vol.toDouble()
                                                    playerState?._volume?.value = vol
                                                }
                                            }
                                            "speed" -> {
                                                if (prop.format == 5) {
                                                    val sp = prop.data!!.getDouble(0).toFloat()
                                                    _speed.value = sp.toDouble()
                                                    playerState?._playbackSpeed?.value = sp
                                                }
                                            }
                                            "core-idle" -> {
                                                if (prop.format == 3 && !hasEverPlayed) {
                                                    playerState?._isProbing?.value = prop.data!!.getInt(0) != 0
                                                }
                                            }
                                            "eof-reached" -> {
                                                if (prop.format == 3) {
                                                    val isEof = prop.data!!.getInt(0) != 0
                                                    if (isEof && hasEverPlayed && !hasFiredFinished && !waitingForTimePosReset) {
                                                        hasFiredFinished = true
                                                        AppLogger.i("DesktopMpvEngine", "Stream reached EOF (property).")
                                                        onFinished()
                                                    } else if (!isEof && lastDur > 0 && lastPos < lastDur - 5.0) {
                                                        hasFiredFinished = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 100ms fallback position poll
                    val now = System.currentTimeMillis()
                    if (now - lastPositionPollMs >= 100L) {
                        lastPositionPollMs = now
                        val currentDur = MpvLibrary.getPropertyDouble(handle, "duration", -1.0)
                        if (currentDur > 0.0) {
                            lastDur = currentDur
                            playerState?._durationMs?.value = (currentDur * 1000).toLong()
                        }
                        val pollPos = MpvLibrary.getPropertyDouble(handle, "time-pos", -1.0)
                        if (pollPos >= 0.0) {
                            lastPos = pollPos
                            if (lastPos > 0.1) waitingForTimePosReset = false
                            val isPausedForCache = MpvLibrary.getPropertyString(handle, "paused-for-cache") == "yes"
                            if (!hasEverPlayed && lastPos > 0.1 && !isPausedForCache) {
                                hasEverPlayed = true
                                playbackStartedAt = System.currentTimeMillis()
                                playerState?._isBuffering?.value = false
                                playerState?._isProbing?.value = false
                                onPlaybackReady()
                            }
                        }

                        if (now - lastUiPositionEmit >= 100) {
                            lastUiPositionEmit = now
                            if (!waitingForTimePosReset && hasEverPlayed) {
                                val posMs = (lastPos * 1000).toLong()
                                playerState?.updatePositionFromPlayer(posMs)
                                onPositionChange(posMs, (lastDur * 1000).toLong())
                            }
                            MpvLibrary.getPropertyString(handle, "paused-for-cache")?.let { s ->
                                playerState?._isBuffering?.value = (s == "yes")
                            }
                            val isEofPolled = MpvLibrary.getPropertyString(handle, "eof-reached") == "yes"
                            if (isEofPolled && hasEverPlayed && !hasFiredFinished && !waitingForTimePosReset) {
                                hasFiredFinished = true
                                AppLogger.i("DesktopMpvEngine", "Stream reached EOF (polled).")
                                onFinished()
                            } else if (!isEofPolled && lastDur > 0 && lastPos < lastDur - 5.0) {
                                hasFiredFinished = false
                            }
                        }
                    }

                    // 2000ms periodic track & video stats poll
                    if (now - lastTrackPollMs >= 2000L) {
                        lastTrackPollMs = now
                        pollTracksAndChapters(handle)

                        val ps = playerState
                        if (ps != null && ps._showStats.value) {
                            ps._videoCodec.value = MpvLibrary.getPropertyString(handle, "video-codec") ?: "Unknown"
                            ps._audioCodec.value = MpvLibrary.getPropertyString(handle, "audio-codec") ?: "Unknown"
                            ps._hwdecCurrent.value = MpvLibrary.getPropertyString(handle, "hwdec-current") ?: "Unknown"
                            ps._droppedFrames.value = MpvLibrary.getPropertyString(handle, "vo-drop-frame-count")?.toLongOrNull() ?: 0L
                            ps._fps.value = MpvLibrary.getPropertyString(handle, "container-fps")?.toDoubleOrNull() ?: 0.0
                            val w = MpvLibrary.getPropertyString(handle, "width") ?: "0"
                            val hw = MpvLibrary.getPropertyString(handle, "height") ?: "0"
                            ps._resolution.value = "${w}x$hw"
                            ps._videoBitrate.value = MpvLibrary.getPropertyString(handle, "video-bitrate")?.toLongOrNull() ?: 0L
                            ps._audioBitrate.value = MpvLibrary.getPropertyString(handle, "audio-bitrate")?.toLongOrNull() ?: 0L
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Throwable) {
                    AppLogger.e("DesktopMpvEngine", "Event loop error: ${e.message}")
                    delay(100)
                }
            }
        }
    }

    fun executeCommand(command: String): Int {
        val handle = mpvHandle ?: return -1
        if (isDestroyed.get()) return -1
        return try {
            MpvLibrary.INSTANCE.mpv_command_string(handle, command)
        } catch (e: Throwable) {
            AppLogger.e("DesktopMpvEngine", "executeCommand error for '$command': ${e.message}")
            -1
        }
    }

    fun executeCommandArray(args: Array<String?>): Int {
        val handle = mpvHandle ?: return -1
        if (isDestroyed.get()) return -1
        return try {
            MpvLibrary.INSTANCE.mpv_command(handle, args)
        } catch (e: Throwable) {
            AppLogger.e("DesktopMpvEngine", "executeCommandArray error: ${e.message}")
            -1
        }
    }

    fun setPropertyString(property: String, value: String): Int {
        val handle = mpvHandle ?: return -1
        if (isDestroyed.get()) return -1
        return try {
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, property, value)
        } catch (e: Throwable) {
            AppLogger.e("DesktopMpvEngine", "setPropertyString error for '$property': ${e.message}")
            -1
        }
    }

    fun getPropertyString(property: String): String? {
        val handle = mpvHandle ?: return null
        if (isDestroyed.get()) return null
        return try {
            MpvLibrary.getPropertyString(handle, property)
        } catch (e: Throwable) {
            null
        }
    }

    fun getPropertyDouble(property: String, def: Double = 0.0): Double {
        val handle = mpvHandle ?: return def
        if (isDestroyed.get()) return def
        return try {
            MpvLibrary.getPropertyDouble(handle, property, def)
        } catch (e: Throwable) {
            def
        }
    }

    fun configureYtDlpIfInstalled() {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        val ytdlBinary = DesktopYtDlpBinary()
        if (ytdlBinary.isInstalled()) {
            val binaryPath = ytdlBinary.getBinaryFile().absolutePath.replace("\\", "/")
            setPropertyString("ytdl", "yes")
            setPropertyString("script-opts", "ytdl_hook-ytdl_path=$binaryPath")
            AppLogger.i("DesktopMpvEngine", "Dynamic yt-dlp hook applied: $binaryPath")
        }
    }

    fun loadFile(
        url: String,
        startPositionMs: Long = 0,
        headers: Map<String, String>? = null,
        subtitles: List<SubtitleFile> = emptyList(),
    ) {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return

        scope.launch(Dispatchers.IO) {
            waitingForTimePosReset = true
            hasEverPlayed = false

            headers?.forEach { (key, value) ->
                MpvLibrary.INSTANCE.mpv_set_property_string(handle, "http-header-fields", "$key: $value")
            }

            if (startPositionMs > 0) {
                MpvLibrary.INSTANCE.mpv_set_property_string(handle, "start", "${startPositionMs / 1000.0}")
            } else {
                MpvLibrary.INSTANCE.mpv_set_property_string(handle, "start", "none")
            }

            val command = "loadfile \"$url\" replace"
            MpvLibrary.INSTANCE.mpv_command_string(handle, command)

            val candidateLangs = LanguagePriorityHelper.getOrderedSubtitleLanguages()
            val subEnabled = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_SUB_ENABLED)
                ?: (DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_SUB_LANG) != "off")
            var hasSelectedOne = false

            subtitles.forEach { sub ->
                if (sub.url.isNotBlank()) {
                    val langTag = sub.langTag ?: sub.lang
                    val matchesPreferred = subEnabled && !hasSelectedOne && candidateLangs.any { prefLang ->
                        LanguageMatcher.matchesSubtitleTrack(lang = sub.langTag, title = sub.lang, name = sub.lang, prefLangCode = prefLang)
                    }
                    val flag = when {
                        !subEnabled -> "no"
                        matchesPreferred -> {
                            hasSelectedOne = true
                            "select"
                        }
                        else -> "auto"
                    }
                    val subCommand = "sub-add \"${sub.url}\" $flag \"${sub.lang}\" \"$langTag\""
                    MpvLibrary.INSTANCE.mpv_command_string(handle, subCommand)
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            val sec = (positionMs / 1000.0).toString()
            MpvLibrary.INSTANCE.mpv_command_string(handle, "seek $sec absolute")
        }
    }

    fun seekBy(offsetMs: Long) {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            val offsetSec = offsetMs / 1000.0
            MpvLibrary.INSTANCE.mpv_command_string(handle, "seek $offsetSec relative")
        }
    }

    fun togglePause() {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            val current = _isPaused.value
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "pause", if (current) "no" else "yes")
        }
    }

    fun pause() {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            val res = MpvLibrary.INSTANCE.mpv_set_property_string(handle, "pause", "yes")
            if (res < 0) {
                MpvLibrary.INSTANCE.mpv_command_string(handle, "set pause yes")
            }
        }
    }

    fun play() {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            val res = MpvLibrary.INSTANCE.mpv_set_property_string(handle, "pause", "no")
            if (res < 0) {
                MpvLibrary.INSTANCE.mpv_command_string(handle, "set pause no")
            }
        }
    }

    fun setSpeed(newSpeed: Double) {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "speed", newSpeed.toString())
        }
    }

    fun setVolume(newVolume: Double) {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "volume", newVolume.toString())
        }
    }

    fun toggleMute() {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            MpvLibrary.INSTANCE.mpv_command_string(handle, "cycle mute")
        }
    }

    fun setMute(isMuted: Boolean) {
        val handle = mpvHandle ?: return
        if (isDestroyed.get()) return
        scope.launch(Dispatchers.IO) {
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "mute", if (isMuted) "yes" else "no")
        }
    }

    fun destroy() {
        if (!isDestroyed.compareAndSet(false, true)) return

        val handle = mpvHandle
        mpvHandle = null
        eventJob?.cancel()

        // Clear C++ sync pointer first so it never polls a destroyed handle
        try {
            NativePlayerBridge.stopMpvSync()
        } catch (e: Throwable) {
            AppLogger.w("DesktopMpvEngine", "Error stopping WebView sync: ${e.message}")
        }

        if (handle != null) {
            // Teardown asynchronously off the Compose EDT
            Thread({
                try {
                    AppLogger.i("DesktopMpvEngine", "Terminating native MPV instance off EDT...")
                    MpvLibrary.INSTANCE.mpv_command_string(handle, "stop")
                    MpvLibrary.INSTANCE.mpv_terminate_destroy(handle)
                } catch (e: Throwable) {
                    AppLogger.w("DesktopMpvEngine", "Error during MPV destroy: ${e.message}")
                }
            }, "cs3-mpv-destroy").apply {
                isDaemon = true
                start()
            }
        }
    }
}
