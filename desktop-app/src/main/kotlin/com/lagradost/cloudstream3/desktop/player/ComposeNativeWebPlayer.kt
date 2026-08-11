package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.launch
import java.awt.event.*
import java.io.File

private val playerObjectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

@Composable
fun ComposeNativeWebPlayer(
    modifier: Modifier = Modifier.fillMaxSize(),
    link: ExtractorLink?,
    title: String? = null,
    seriesPosterUrl: String? = null,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile> = emptyList(),
    startPositionMs: Long,
    shouldPauseForResume: Boolean = false,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    isExiting: Boolean = false,
    onSkipScraping: (() -> Unit)? = null,
    onFullscreenToggle: (() -> Unit)? = null,
    playerState: com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState? = null,
    links: List<ExtractorLink> = emptyList(),
    currentLinkIndex: Int = 0,
    episodes: List<com.lagradost.cloudstream3.Episode> = emptyList(),
    currentEpisodeId: String? = null,
    isLoading: Boolean = false,
    loadingStatusText: String? = null,
    isProbing: Boolean = false,
    failedLinks: Map<Int, String> = emptyMap(),
    backdropUrl: String? = null,
    logoUrl: String? = null,
    onLinkChange: ((String) -> Unit)? = null,
    onEpisodeChange: ((String) -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onReplayEpisode: (() -> Unit)? = null,
    plot: String? = null,
    year: Int? = null,
    tags: List<String>? = null,
) {
    var mpvHandle by remember { mutableStateOf<com.sun.jna.Pointer?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val persistentSubtitles = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val window = com.lagradost.cloudstream3.desktop.ui.LocalComposeWindow.current

    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val accentColorHex = remember(primaryColor) {
        String.format("#%02X%02X%02X", (primaryColor.red * 255).toInt(), (primaryColor.green * 255).toInt(), (primaryColor.blue * 255).toInt())
    }
    val accentColorRgb = remember(primaryColor) {
        "${(primaryColor.red * 255).toInt()}, ${(primaryColor.green * 255).toInt()}, ${(primaryColor.blue * 255).toInt()}"
    }

    val currentOnPlaybackReady by rememberUpdatedState(onPlaybackReady)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentOnFullscreenToggle by rememberUpdatedState(onFullscreenToggle)

    var isUiReady by remember { mutableStateOf(false) }
    val audioTracks by (playerState?.audioTracks ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(emptyList())
    val subtitleTracks by (playerState?.subtitleTracks ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(emptyList())
    val videoTracks by (playerState?.videoTracks ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(emptyList())
    val isBuffering by (playerState?.isBuffering ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)

    val proxyAudioTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.collectAsState()
    val proxySubtitleTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazySubtitleTracks.collectAsState()
    val proxyVideoTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.collectAsState()

    val currentIsLoading by rememberUpdatedState(isLoading)
    val currentLoadingStatusText by rememberUpdatedState(loadingStatusText)
    val activeShader by (playerState?.activeShader ?: kotlinx.coroutines.flow.flowOf("None")).collectAsState("None")
    val activeLazyVideoTrackUrl by (playerState?.activeLazyVideoTrackUrl ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)
    val resolution by (playerState?.resolution ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)
    val activeSubtitleOverrideEnabled = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ENABLE_SUB_OVERRIDE) ?: false

    var hasAutoSelectedQuality by remember(link) { mutableStateOf(false) }

    LaunchedEffect(proxyVideoTracks, link) {
        if (!hasAutoSelectedQuality && proxyVideoTracks.isNotEmpty() && link?.quality != null && link.quality != com.lagradost.cloudstream3.utils.Qualities.Unknown.value) {
            val targetRes = link.quality
            // Try to find exact match first, fallback to closest resolution
            val match = proxyVideoTracks.find { it.name.contains("${targetRes}p") }
                ?: proxyVideoTracks.minByOrNull {
                    val res = it.name.substringBefore("p").toIntOrNull() ?: Int.MAX_VALUE
                    kotlin.math.abs(res - targetRes)
                }
            if (match != null) {
                playerState?.loadLazyVideoTrack(
                    com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(match.url, match.name, match.language, match.bitrate)
                )
            }
            hasAutoSelectedQuality = true
        }
    }

    LaunchedEffect(isUiReady, isLoading, links, currentLinkIndex, episodes, currentEpisodeId, audioTracks, subtitleTracks, videoTracks, proxyAudioTracks, proxySubtitleTracks, proxyVideoTracks, loadingStatusText, isProbing, failedLinks, backdropUrl, logoUrl, title, activeShader, activeLazyVideoTrackUrl, resolution, plot, year, tags, activeSubtitleOverrideEnabled) {
        if (isUiReady) {
            val payload = PlayerUiSyncState(
                plot = plot,
                year = year,
                tags = tags,
                isProbing = isProbing,
                backdropUrl = backdropUrl?.let { com.lagradost.player.impl.proxy.LocalStreamProxy.buildImageUrl(it) },
                logoUrl = logoUrl?.let { com.lagradost.player.impl.proxy.LocalStreamProxy.buildImageUrl(it) },
                currentLinkIndex = currentLinkIndex,
                failedLinks = failedLinks.map { FailedLinkPayload(it.key, it.value) },
                links = links.mapIndexed { index, l ->
                    LinkPayload(
                        index = index,
                        name = l.name,
                        quality = l.quality,
                        isActive = (index == currentLinkIndex),
                        isM3u8 = l.isM3u8,
                        isDash = l.isDash,
                        url = l.url,
                    )
                },
                episodes = episodes.map {
                    EpisodePayload(
                        id = it.data,
                        title = it.name ?: "Episode ${it.episode}",
                        season = it.season,
                        episode = it.episode,
                        isActive = (it.data == currentEpisodeId),
                        posterUrl = (it.posterUrl ?: seriesPosterUrl)?.let { url -> com.lagradost.player.impl.proxy.LocalStreamProxy.buildImageUrl(url) },
                        description = it.description,
                        runTime = it.runTime,
                    )
                },
                audioTracks = audioTracks.map {
                    SubtitleTrackPayload(it.id, it.name, it.isSelected)
                },
                subTracks = subtitleTracks.map {
                    SubtitleTrackPayload(it.id, it.name, it.isSelected)
                },
                videoTracks = if (proxyVideoTracks.isNotEmpty()) emptyList() else videoTracks.map {
                    SubtitleTrackPayload(it.id, it.name, it.isSelected)
                },
                lazyAudioTracks = proxyAudioTracks.map {
                    LazyTrackPayload(it.url, it.name, it.language)
                },
                lazySubTracks = proxySubtitleTracks.map {
                    LazyTrackPayload(it.url, it.name, it.language)
                },
                lazyVideoTracks = proxyVideoTracks.map {
                    LazyTrackPayload(it.url, it.name, it.language)
                },
                startPositionMs = startPositionMs,
                title = title ?: "CloudStream",
                shaders = com.lagradost.cloudstream3.desktop.player.ShaderManager.getAvailableShaders(),
                activeShader = activeShader,
                activeSubtitleFont = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_FONT),
                availableSubtitleFonts = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getAvailableFonts(),
                activeSubtitleBackground = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BG),
                activeSubtitleBorderColor = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_COLOR),
                activeSubtitleBorderSize = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_SIZE),
                activeSubtitleShadowColor = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_COLOR),
                activeSubtitleShadowOffset = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_OFFSET),
                activeSubtitleBlur = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BLUR),
                activeSubtitleBold = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BOLD),
                activeSubtitleItalic = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_ITALIC),
                activeLazyVideoTrackUrl = activeLazyVideoTrackUrl,
                resolution = resolution,
                activeSubtitleOverrideEnabled = activeSubtitleOverrideEnabled,
            )

            val wrapper = MetadataUpdatePayloadWrapper(
                value = payload,
            )
            NativePlayerBridge.postMessage(playerObjectMapper.writeValueAsString(wrapper))
        }
    }

    val toastMessage by (playerState?.toastMessage ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)
    LaunchedEffect(toastMessage) {
        if (isUiReady && toastMessage != null) {
            val payload = mapOf(
                "type" to "show_toast",
                "message" to toastMessage,
            )
            NativePlayerBridge.postMessage(playerObjectMapper.writeValueAsString(payload))
        }
    }

    fun pushMetadataToWebView() {
        try {
            val vol = playerState?._volume?.value ?: 100f
            val isMuted = playerState?._isMuted?.value ?: false
            val isBuf = playerState?._isBuffering?.value == true

            var currentlyLoading = isBuf
            var isAppScraping = false
            var escapedLoadingText: String? = null
            try {
                currentlyLoading = isBuf
                isAppScraping = currentIsLoading
                escapedLoadingText = currentLoadingStatusText?.replace("\"", "\\\"")?.replace("\n", "\\n")
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error escaping loading text", e)
            }

            val loadingTextJson = if (escapedLoadingText != null) "\"$escapedLoadingText\"" else "null"

            val interpolationEnabled = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_INTERPOLATION) ?: false
            val autoPlayEnabled = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true

            val payload = AppStateUpdatePayload(
                volume = vol,
                isMuted = isMuted,
                isAppLoading = isAppScraping,
                loadingStatusText = currentLoadingStatusText,
                debugWait = false,
                debugHasEver = true,
                debugPos = 0.0,
                interpolationEnabled = interpolationEnabled,
                autoPlayEnabled = autoPlayEnabled,
            )
            NativePlayerBridge.postMessage(playerObjectMapper.writeValueAsString(payload))
        } catch (e: Throwable) {
            com.lagradost.common.logging.AppLogger.e("pushMetadataToWebView error: ${e.message}")
        }
    }

    LaunchedEffect(isLoading, isBuffering, loadingStatusText) {
        if (isUiReady && mpvHandle != null) {
            pushMetadataToWebView()
        }
    }

    BaseMpvPlayer(
        modifier = modifier,
        link = link,
        title = title,
        subtitles = subtitles,
        startPositionMs = startPositionMs,
        shouldPauseForResume = shouldPauseForResume,
        onPlaybackReady = {
            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.__dismissProbingOverlay) window.__dismissProbingOverlay();")
            currentOnPlaybackReady()
        },
        onPlaybackError = currentOnPlaybackError,
        onFinished = currentOnFinished,
        onPositionChange = { posMs, durMs ->
            currentOnPositionChange(posMs, durMs)
            if (isUiReady) pushMetadataToWebView()
        },
        onCloseRequest = currentOnCloseRequest,
        isExiting = isExiting,
        onFullscreenToggle = currentOnFullscreenToggle,
        playerState = playerState,
        onEventLoopReady = { h ->
            if (isUiReady) {
                NativePlayerBridge.startMpvSync(com.sun.jna.Pointer.nativeValue(h))
            }
        },
        onPreInitialize = { handle, canvasWid, w, h ->
            val childHwnd = NativePlayerBridge.initWebView(canvasWid, w, h)
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "wid", childHwnd.toString())
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "vo", "gpu")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "gpu-api", "d3d11")

            val delaySec = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_DELAY) ?: 0f
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "audio-delay", delaySec.toString())

            kotlinx.coroutines.runBlocking { updateAudioFilters(handle) }
            val audioMax = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_VOLUME_MAX) ?: false
            if (audioMax) {
                MpvLibrary.INSTANCE.mpv_set_option_string(handle, "volume-max", "200")
            }
        },
        onPostInitialize = { handle ->
            val webView2DataDir = File(System.getProperty("java.io.tmpdir"), "CloudStreamWebView2")
            webView2DataDir.mkdirs()
            val tempFile = File(webView2DataDir, "cloudstream_controls.html")

            val htmlTemplate = NativePlayerBridge::class.java.getResourceAsStream("/player-ui/player.html")?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val cssContent = NativePlayerBridge::class.java.getResourceAsStream("/player-ui/player.css")?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val jsContent = NativePlayerBridge::class.java.getResourceAsStream("/player-ui/player.js")?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

            val initialBackdropUrl = backdropUrl ?: (episodes.find { it.data == currentEpisodeId }?.posterUrl ?: "")
            val initialBackdropClass = if (initialBackdropUrl.isNotEmpty()) "loaded" else ""

            val htmlContent = htmlTemplate
                .replace("/* CSS_INJECT */", cssContent)
                .replace("/* JS_INJECT */", jsContent)
                .replace("{{ACCENT_COLOR}}", accentColorHex)
                .replace("{{ACCENT_COLOR_RGB}}", accentColorRgb)
                .replace("{{INITIAL_BACKDROP_URL}}", initialBackdropUrl)
                .replace("{{INITIAL_BACKDROP_CLASS}}", initialBackdropClass)

            if (htmlContent.isNotEmpty() && htmlTemplate.isNotEmpty()) {
                tempFile.writeText(htmlContent, Charsets.UTF_8)
            } else {
                com.lagradost.common.logging.AppLogger.e("[NativePlayer] player-ui resources not found!")
            }

            NativePlayerBridge.setEventListener(object : NativePlayerBridge.NativePlayerEventListener {
                override fun onPlayerEvent(type: String, value: String) {
                    val h = mpvHandle ?: return
                    if (type != "message") return

                    val rootNode = try { playerObjectMapper.readTree(value) } catch (t: Throwable) { null }
                    val eventType = rootNode?.get("type")?.asText() ?: ""
                    val eventValue = rootNode?.get("value")?.let { if (it.isTextual) it.asText() else it.toString() } ?: ""

                    when (eventType) {
                        "ui_ready" -> {
                            isUiReady = true
                            pushMetadataToWebView()
                            NativePlayerBridge.startMpvSync(com.sun.jna.Pointer.nativeValue(h))
                            NativePlayerBridge.focusWebView()
                        }
                        "selectShader" -> {
                            val shaderName = rootNode?.get("value")?.asText() ?: ""
                            playerState?.setShader(shaderName)
                        }
                        "searchSubtitles" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val cleanValue = value.replace(Regex("[\\x00-\\x1F]"), "")
                                    val rootPayload = playerObjectMapper.readTree(cleanValue)
                                    val innerJson = rootPayload.get("value")?.asText()?.takeIf { it.isNotBlank() } ?: "{}"
                                    val parsed = playerObjectMapper.readTree(innerJson)

                                    val query = parsed["query"]?.asText() ?: ""
                                    val lang = parsed["lang"]?.asText()?.takeIf { it.isNotBlank() }
                                    val season = parsed["season"]?.asText()?.toIntOrNull()
                                    val episode = parsed["episode"]?.asText()?.toIntOrNull()

                                    val allResults = SubtitleExtractionService.searchSubtitles(query, lang, season, episode)

                                    val json = playerObjectMapper.writeValueAsString(
                                        mapOf("type" to "subtitle_search_results", "results" to allResults),
                                    )
                                    NativePlayerBridge.postMessage(json)
                                } catch (e: Exception) {
                                    com.lagradost.common.logging.AppLogger.e("Player:Web", "searchSubtitles error: ${e.message}", e)
                                }
                            }
                        }
                        "downloadSubtitle" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val cleanValue = value.replace(Regex("[\\x00-\\x1F]"), "")
                                    val rootPayload = playerObjectMapper.readTree(cleanValue)
                                    val innerJson = rootPayload.get("value")?.asText()?.takeIf { it.isNotBlank() } ?: "{}"
                                    val parsed = playerObjectMapper.readTree(innerJson)

                                    val idPrefix = parsed["idPrefix"]?.asText() ?: return@launch
                                    val data = parsed["data"]?.asText() ?: return@launch
                                    val name = parsed["name"]?.asText() ?: "subtitle"

                                    val safeUrl = SubtitleExtractionService.downloadAndExtractSubtitle(
                                        idPrefix = idPrefix,
                                        data = data,
                                        name = name,
                                        lang = parsed["lang"]?.asText() ?: "",
                                        source = parsed["source"]?.asText() ?: ""
                                    )

                                    if (safeUrl != null) {
                                        if (!persistentSubtitles.contains(safeUrl)) {
                                            persistentSubtitles.add(safeUrl)
                                        }
                                        val escapedSub = safeUrl.replace("\\", "\\\\").replace("\"", "\\\"")
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "sub-add \"$escapedSub\"")

                                        val toastJson = playerObjectMapper.writeValueAsString(
                                            mapOf("type" to "show_toast", "message" to "Successfully extracted and loaded subtitle"),
                                        )
                                        NativePlayerBridge.postMessage(toastJson)
                                    }
                                } catch (e: Exception) {
                                    com.lagradost.common.logging.AppLogger.e("Player:Web", "downloadSubtitle error: ${e.message}", e)
                                }
                            }
                        }
                        "seekTo" -> {
                            val pos = eventValue.toDoubleOrNull()
                            if (pos != null) {
                                playerState?.seekTo(pos.toLong())
                            }
                        }
                        "seekBy" -> {
                            val offset = eventValue.toDoubleOrNull()
                            if (offset != null) {
                                playerState?.seekBy(offset.toLong())
                            }
                        }
                        "togglePlay" -> {
                            val isMpvPaused = MpvLibrary.getPropertyString(h, "pause") == "yes"
                            com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Received togglePlay event. MPV state: pause=$isMpvPaused, Kotlin state: isPaused=${playerState?._isPaused?.value}")
                            if (isMpvPaused) {
                                playerState?.play()
                            } else {
                                playerState?.pause()
                            }
                        }
                        "play" -> {
                            com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Received play event")
                            playerState?.play()
                        }
                        "pause" -> {
                            com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Received pause event")
                            playerState?.pause()
                        }
                        "toggleMute" -> {
                            playerState?.let { it._isMuted.value = !it.isMuted.value }
                        }
                        "setVolume" -> {
                            val vol = eventValue.toDoubleOrNull()
                            if (vol != null) {
                                playerState?._volume?.value = vol.toFloat()
                            }
                        }
                        "setSpeed" -> {
                            val sp = eventValue.toDoubleOrNull()
                            if (sp != null) {
                                playerState?.setSpeed(sp.toFloat())
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "speed", sp.toString())
                            }
                        }
                        "setSubDelay" -> {
                            val d = eventValue.toDoubleOrNull()
                            if (d != null) {
                                MpvLibrary.INSTANCE.mpv_command_string(h, "add sub-delay $d")
                            }
                        }
                        "cycleSubtitles" -> {
                            MpvLibrary.INSTANCE.mpv_command_string(h, "cycle sub")
                        }
                        "toggleSubVisibility" -> {
                            MpvLibrary.INSTANCE.mpv_command_string(h, "cycle sub-visibility")
                        }
                        "togglePip" -> {
                            com.lagradost.cloudstream3.desktop.ui.PipState.isPipMode.value = !com.lagradost.cloudstream3.desktop.ui.PipState.isPipMode.value
                            val isPip = com.lagradost.cloudstream3.desktop.ui.PipState.isPipMode.value
                            NativePlayerBridge.executeScript("if(window.setPipUi) window.setPipUi($isPip);")
                        }
                        "startWindowDrag" -> {
                            val hwnd = com.sun.jna.Native.getComponentID(window)
                            val hWin = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(hwnd))
                            // 0x0112 is WM_SYSCOMMAND, 0xF012 is SC_MOVE | HTCAPTION
                            com.lagradost.cloudstream3.desktop.init.ExtUser32.INSTANCE.ReleaseCapture()
                            com.sun.jna.platform.win32.User32.INSTANCE.PostMessage(
                                hWin, 0x0112, 
                                com.sun.jna.platform.win32.WinDef.WPARAM(0xF012), 
                                com.sun.jna.platform.win32.WinDef.LPARAM(0)
                            )
                        }
                        "startWindowResize" -> {
                            val hwnd = com.sun.jna.Native.getComponentID(window)
                            val hWin = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(hwnd))
                            com.lagradost.cloudstream3.desktop.init.ExtUser32.INSTANCE.ReleaseCapture()
                            
                            val hitTest = when (eventValue) {
                                "left" -> 1 // WMSZ_LEFT
                                "right" -> 2 // WMSZ_RIGHT
                                "top" -> 3 // WMSZ_TOP
                                "top-left" -> 4 // WMSZ_TOPLEFT
                                "top-right" -> 5 // WMSZ_TOPRIGHT
                                "bottom" -> 6 // WMSZ_BOTTOM
                                "bottom-left" -> 7 // WMSZ_BOTTOMLEFT
                                "bottom-right" -> 8 // WMSZ_BOTTOMRIGHT
                                else -> 8
                            }
                            
                            // SC_SIZE is 0xF000.
                            com.sun.jna.platform.win32.User32.INSTANCE.PostMessage(
                                hWin, 0x0112, 
                                com.sun.jna.platform.win32.WinDef.WPARAM((0xF000 + hitTest).toLong()), 
                                com.sun.jna.platform.win32.WinDef.LPARAM(0)
                            )
                        }
                        "toggleFullscreen" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                currentOnFullscreenToggle?.invoke()
                            }
                        }
                        "focusWebView" -> {
                            NativePlayerBridge.focusWebView()
                        }
                        "exitPlayer" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                currentOnCloseRequest()
                            }
                        }
                        "changeLink" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                if (eventValue.isNotEmpty()) onLinkChange?.invoke(eventValue)
                            }
                        }
                        "loadEpisode" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onEpisodeChange?.invoke(eventValue)
                            }
                        }
                        "setAudioTrack" -> {
                            val id = eventValue.toIntOrNull()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setAudioTrack(id)
                            }
                        }
                        "setVideoTrack" -> {
                            val id = eventValue.toIntOrNull()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setVideoTrack(id)
                            }
                        }
                        "loadLazyAudioTrack" -> {
                            val url = eventValue
                            val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.value.find { it.url == url }
                            if (track != null) {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    playerState?.loadLazyAudioTrack(
                                        com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language),
                                    )
                                }
                            }
                        }
                        "loadLazySubtitleTrack" -> {
                            val url = eventValue
                            val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazySubtitleTracks.value.find { it.url == url }
                            if (track != null) {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    playerState?.loadLazySubtitleTrack(
                                        com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language),
                                    )
                                }
                            }
                        }
                        "loadLazyVideoTrack" -> {
                            val url = eventValue
                            val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.value.find { it.url == url }
                            if (track != null) {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    playerState?.loadLazyVideoTrack(
                                        com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language, track.bitrate),
                                    )
                                }
                            }
                        }
                        "setSubtitleTrack" -> {
                            val id = eventValue.toIntOrNull()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setSubtitleTrack(id)
                            }
                        }
                        "setSubtitleFont" -> {
                            val fontName = eventValue.takeIf { it.isNotBlank() }
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_FONT, fontName ?: "")
                                playerState?.setSubtitleFont(fontName)
                            }
                        }
                        "setSubtitleOverrideEnabled" -> {
                            val enabled = eventValue.toBoolean()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setSubtitleOverrideEnabled(enabled)
                            }
                        }
                        "resetSubtitleSettings" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_FONT)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BG)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_COLOR)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_SIZE)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_COLOR)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_OFFSET)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BLUR)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BOLD)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_ITALIC)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ENABLE_SUB_OVERRIDE)

                                playerState?.setSubtitleFont(null)
                                playerState?.setSubtitleOverrideEnabled(false)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-bg-color", "#00000000")
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-border-color", "#000000")
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-border-size", "3")
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-shadow-color", "#00000000")
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-shadow-offset", "0")
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-blur", "0")
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-bold", "no")
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-italic", "no")
                            }
                        }
                        "setSubtitleBackground" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BG, eventValue)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-bg-color", eventValue)
                            }
                        }
                        "setSubtitleBorderColor" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_COLOR, eventValue)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-border-color", eventValue)
                            }
                        }
                        "setSubtitleBorderSize" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_SIZE, eventValue)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-border-size", eventValue)
                            }
                        }
                        "setSubtitleShadowColor" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_COLOR, eventValue)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-shadow-color", eventValue)
                            }
                        }
                        "setSubtitleShadowOffset" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_OFFSET, eventValue)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-shadow-offset", eventValue)
                            }
                        }
                        "setSubtitleBlur" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BLUR, eventValue)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-blur", eventValue)
                            }
                        }
                        "setSubtitleBold" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BOLD, eventValue)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-bold", eventValue)
                            }
                        }
                        "setSubtitleItalic" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_ITALIC, eventValue)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "sub-italic", eventValue)
                            }
                        }
                        "toggleInterpolation" -> {
                            val enabled = eventValue.toBoolean()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_INTERPOLATION, enabled)
                                if (enabled) {
                                    MpvLibrary.INSTANCE.mpv_set_property_string(h, "video-sync", "display-resample")
                                    MpvLibrary.INSTANCE.mpv_set_property_string(h, "interpolation", "yes")
                                    MpvLibrary.INSTANCE.mpv_set_property_string(h, "tscale", "oversample")
                                } else {
                                    MpvLibrary.INSTANCE.mpv_set_property_string(h, "video-sync", "audio")
                                    MpvLibrary.INSTANCE.mpv_set_property_string(h, "interpolation", "no")
                                }
                            }
                        }
                        "setAudioNormalization" -> {
                            val enabled = eventValue.toBoolean()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_NORMALIZATION, enabled)
                                updateAudioFilters(h)
                            }
                        }
                        "setAudioNormStrength" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_NORM_STRENGTH, eventValue)
                                updateAudioFilters(h)
                            }
                        }
                        "setAudioSpatial" -> {
                            val enabled = eventValue.toBoolean()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_SPATIAL, enabled)
                                updateAudioFilters(h)
                            }
                        }
                        "setAudioEqPreset" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_EQ_PRESET, eventValue)
                                updateAudioFilters(h)
                            }
                        }
                        "setAudioVolumeMax" -> {
                            val enabled = eventValue.toBoolean()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_VOLUME_MAX, enabled)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "volume-max", if (enabled) "200" else "100")
                            }
                        }
                        "setAudioDelay" -> {
                            val delaySec = eventValue.toFloatOrNull() ?: 0f
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_DELAY, delaySec)
                                MpvLibrary.INSTANCE.mpv_set_property_string(h, "audio-delay", delaySec.toString())
                            }
                        }
                        "toggleAutoPlay" -> {
                            val enabled = eventValue.toBoolean()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY, enabled)
                            }
                        }
                        "loadNextEpisode", "nextEpisode" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onNextEpisode?.invoke()
                            }
                        }
                        "replayEpisode" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onReplayEpisode?.invoke()
                            }
                        }
                        "setMpvProperty" -> {
                            val parts = eventValue.split(":", limit = 2)
                            if (parts.size == 2) {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    MpvLibrary.INSTANCE.mpv_set_property_string(h, parts[0], parts[1])
                                }
                            }
                        }
                        "skipScraping" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onSkipScraping?.invoke()
                            }
                        }
                    }
                }
            })

            if (tempFile.exists()) {
                NativePlayerBridge.loadUrl(tempFile.absoluteFile.toURI().toString())
            }
        },
        videoRenderer = { videoCanvas, currentMpvHandle ->
            mpvHandle = currentMpvHandle

            DisposableEffect(Unit) {
                val componentListener = object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent) {
                        NativePlayerBridge.resizeWebView(e.component.width, e.component.height)
                    }
                }
                videoCanvas.addComponentListener(componentListener)
                // Force initial layout push so WebView isn't hidden until the first resize
                NativePlayerBridge.resizeWebView(videoCanvas.width, videoCanvas.height)
                onDispose {
                    videoCanvas.isVisible = false
                    videoCanvas.removeComponentListener(componentListener)
                    NativePlayerBridge.resizeWebView(0, 0)
                    NativePlayerBridge.stopMpvSync()
                    
                    // Push the heavy WebView teardown to a background daemon thread
                    // to prevent blocking the Compose EDT on first exit.
                    java.lang.Thread({
                        com.lagradost.common.logging.AppLogger.i("NativePlayer: Destroying WebView on daemon thread...")
                        NativePlayerBridge.destroyWebView()
                    }, "cs3-webview-dispose").apply {
                        isDaemon = true
                        start()
                    }
                }
            }

            LaunchedEffect(isExiting) {
                if (isExiting) {
                    videoCanvas.isVisible = false
                    videoCanvas.bounds = java.awt.Rectangle(0, 0, 0, 0)
                    NativePlayerBridge.resizeWebView(0, 0)
                }
            }

            SwingPanel(
                background = androidx.compose.ui.graphics.Color.Black,
                factory = { videoCanvas },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

private suspend fun updateAudioFilters(h: com.sun.jna.Pointer?) {
    if (h == null) return
    val filters = mutableListOf<String>()

    val audioNorm = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_NORMALIZATION) ?: false
    if (audioNorm) {
        val strength = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_NORM_STRENGTH) ?: "Medium"
        val params = when (strength) {
            "Low" -> "f=500:g=31:p=0.9:m=5"
            "Aggressive" -> "f=150:g=15:p=0.5:m=30" // Heavy compression for action scenes
            else -> "f=250:g=31:p=0.8:m=10" // Medium
        }
        filters.add("lavfi=[dynaudnorm=$params]")
    }

    val spatialAudio = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_SPATIAL) ?: false
    if (spatialAudio) {
        filters.add("lavfi=[extrastereo=m=2.5]")
    }

    val eqPreset = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_EQ_PRESET) ?: "Flat"
    when (eqPreset) {
        "Bass Boost" -> filters.add("lavfi=[bass=g=10:f=100]")
        "Vocal Boost" -> filters.add("lavfi=[equalizer=f=1000:w=500:g=7]")
        "Cinematic" -> filters.add("lavfi=[bass=g=5:f=80,treble=g=5:f=10000]")
    }

    com.lagradost.cloudstream3.desktop.player.MpvLibrary.INSTANCE.mpv_set_property_string(h, "af", filters.joinToString(","))
}
