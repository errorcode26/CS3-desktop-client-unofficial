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

/**
 * Extract a string-typed value from a flat JSON object.
 * Handles: {"key":"value", ...}
 * More robust than substringAfter/Before which breaks if field order changes or
 * the value contains special characters.
 */
private fun extractJsonString(json: String, key: String): String {
    // Match "key":"<value>" — value stops at the next unescaped quote
    val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
}

/**
 * Extract a value (string OR numeric) from a flat JSON object.
 * For string values ("value":"123") returns the inner string.
 * For numeric values ("value":123) returns the numeric literal as a string.
 */
private fun extractJsonValue(json: String, key: String): String {
    // Try string value first
    val strResult = extractJsonString(json, key)
    if (strResult.isNotEmpty()) return strResult
    // Fall back to numeric/boolean value: "key": 123 or "key": true
    val numRegex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*([0-9.eE+\\-]+|true|false|null)")
    return numRegex.find(json)?.groupValues?.getOrNull(1) ?: ""
}

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
                videoTracks = videoTracks.map {
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
            val vol = playerState?.volume?.value ?: 100f
            val isMuted = playerState?.isMuted?.value ?: false
            val isBuf = playerState?.isBuffering?.value == true

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

            val payload = AppStateUpdatePayload(
                volume = vol,
                isMuted = isMuted,
                isAppLoading = isAppScraping,
                loadingStatusText = currentLoadingStatusText,
                debugWait = false,
                debugHasEver = true,
                debugPos = 0.0,
                interpolationEnabled = interpolationEnabled,
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
        },
        onPostInitialize = { handle ->
            val webView2DataDir = File(System.getProperty("java.io.tmpdir"), "CloudStreamWebView2")
            webView2DataDir.mkdirs()
            val tempFile = File(webView2DataDir, "cloudstream_controls.html")

            val htmlTemplate = NativePlayerBridge::class.java.getResourceAsStream("/player-ui/player.html")?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val cssContent = NativePlayerBridge::class.java.getResourceAsStream("/player-ui/player.css")?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val jsContent = NativePlayerBridge::class.java.getResourceAsStream("/player-ui/player.js")?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

            val htmlContent = htmlTemplate
                .replace("/* CSS_INJECT */", cssContent)
                .replace("/* JS_INJECT */", jsContent)
                .replace("{{ACCENT_COLOR}}", accentColorHex)
                .replace("{{ACCENT_COLOR_RGB}}", accentColorRgb)

            if (htmlContent.isNotEmpty() && htmlTemplate.isNotEmpty()) {
                tempFile.writeText(htmlContent, Charsets.UTF_8)
            } else {
                com.lagradost.common.logging.AppLogger.e("[NativePlayer] player-ui resources not found!")
            }

            NativePlayerBridge.setEventListener(object : NativePlayerBridge.NativePlayerEventListener {
                override fun onPlayerEvent(type: String, value: String) {
                    val h = mpvHandle ?: return
                    if (type != "message") return

                    val eventType = extractJsonString(value, "type")
                    val eventValue = extractJsonValue(value, "value")

                    when (eventType) {
                        "ui_ready" -> {
                            isUiReady = true
                            pushMetadataToWebView()
                            NativePlayerBridge.startMpvSync(com.sun.jna.Pointer.nativeValue(h))
                            NativePlayerBridge.focusWebView()
                        }
                        "selectShader" -> {
                            val shaderName = extractJsonString(value, "value")
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

                                    val search = com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch(
                                        query = query,
                                        lang = lang,
                                        seasonNumber = season,
                                        epNumber = episode,
                                    )

                                    val allResults = mutableListOf<Map<String, Any?>>()
                                    for (provider in com.lagradost.cloudstream3.syncproviders.AccountManager.subtitleProviders) {
                                        val auth = com.lagradost.cloudstream3.syncproviders.AccountManager.cachedAccounts[provider.idPrefix]?.firstOrNull()
                                        val subList = SafePluginInvoker.invokeOrNull(
                                            tag = "SubSearch:${provider.name}",
                                            providerName = provider.name,
                                            timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                                        ) {
                                            provider.search(auth, search)
                                        }
                                        subList?.forEach { sub ->
                                            allResults.add(
                                                mapOf(
                                                    "idPrefix" to sub.idPrefix,
                                                    "name" to sub.name,
                                                    "lang" to sub.lang,
                                                    "data" to sub.data,
                                                    "source" to sub.source,
                                                    "seasonNumber" to sub.seasonNumber,
                                                    "epNumber" to sub.epNumber,
                                                ),
                                            )
                                        }
                                    }

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

                                    val provider = com.lagradost.cloudstream3.syncproviders.AccountManager.subtitleProviders.firstOrNull { it.idPrefix == idPrefix }
                                    if (provider != null) {
                                        val auth = com.lagradost.cloudstream3.syncproviders.AccountManager.cachedAccounts[idPrefix]?.firstOrNull()

                                        val sub = com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity(
                                            idPrefix = idPrefix,
                                            name = name,
                                            data = data,
                                            lang = parsed["lang"]?.asText() ?: "",
                                            source = parsed["source"]?.asText() ?: "",
                                        )

                                        val fileUrl = SafePluginInvoker.invokeOrNull(
                                            tag = "SubLoad:${provider.name}",
                                            providerName = provider.name,
                                            timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
                                        ) {
                                            provider.load(auth, sub)
                                        }
                                        if (fileUrl != null) {
                                            var finalUrl: String = fileUrl
                                            val cleanUrl = fileUrl.substringBefore("?")
                                            if (cleanUrl.endsWith(".zip", ignoreCase = true)) {
                                                val zipFile = if (fileUrl.startsWith("http", ignoreCase = true)) {
                                                    val tmp = java.io.File.createTempFile("sub", ".zip")
                                                    val res = com.lagradost.cloudstream3.app.get(fileUrl).okhttpResponse
                                                    val bytes = res.body.bytes()
                                                    if (bytes != null) {
                                                        tmp.writeBytes(bytes)
                                                        tmp
                                                    } else {
                                                        null
                                                    }
                                                } else if (fileUrl.startsWith("file://", ignoreCase = true)) {
                                                    java.io.File(java.net.URI(fileUrl))
                                                } else {
                                                    java.io.File(fileUrl)
                                                }

                                                if (zipFile != null && zipFile.exists()) {
                                                    java.util.zip.ZipFile(zipFile).use { zip ->
                                                        val entry = zip.entries().toList().firstOrNull {
                                                            it.name.endsWith(".srt", true) || it.name.endsWith(".vtt", true) || it.name.endsWith(".ass", true)
                                                        }
                                                        if (entry != null) {
                                                            val ext = "." + entry.name.substringAfterLast('.', "srt")
                                                            val extracted = java.io.File.createTempFile("sub_ext", ext)
                                                            zip.getInputStream(entry).use { input ->
                                                                extracted.outputStream().use { output ->
                                                                    input.copyTo(output)
                                                                }
                                                            }
                                                            finalUrl = extracted.absolutePath
                                                        }
                                                    }
                                                }
                                            }
                                            val safeUrl = finalUrl.replace("\\", "/")
                                            if (!persistentSubtitles.contains(safeUrl)) {
                                                persistentSubtitles.add(safeUrl)
                                            }
                                            MpvLibrary.INSTANCE.mpv_command_string(h, "sub-add \"$safeUrl\"")

                                            val toastJson = playerObjectMapper.writeValueAsString(
                                                mapOf("type" to "show_toast", "message" to "Successfully extracted and loaded subtitle"),
                                            )
                                            NativePlayerBridge.postMessage(toastJson)
                                        }
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
                            com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Received togglePlay event. MPV state: pause=$isMpvPaused, Kotlin state: isPaused=${playerState?.isPaused?.value}")
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
                            playerState?.let { it.isMuted.value = !it.isMuted.value }
                        }
                        "setVolume" -> {
                            val vol = eventValue.toDoubleOrNull()
                            if (vol != null) {
                                playerState?.volume?.value = vol.toFloat()
                            }
                        }
                        "toggleFullscreen" -> {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                currentOnFullscreenToggle?.invoke()
                            }
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
                    videoCanvas.removeComponentListener(componentListener)
                    NativePlayerBridge.resizeWebView(0, 0)
                    NativePlayerBridge.stopMpvSync()
                    NativePlayerBridge.destroyWebView()
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
