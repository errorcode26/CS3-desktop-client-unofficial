package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge
import com.lagradost.cloudstream3.utils.ExtractorLink
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
    failedLinks: Set<Int> = emptySet(),
    backdropUrl: String? = null,
    logoUrl: String? = null,
    onLinkChange: ((Int) -> Unit)? = null,
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
    val isBuffering by (playerState?.isBuffering ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)

    val proxyAudioTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.collectAsState()
    val proxySubtitleTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazySubtitleTracks.collectAsState()
    val proxyVideoTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.collectAsState()

    val currentIsLoading by rememberUpdatedState(isLoading)
    val currentLoadingStatusText by rememberUpdatedState(loadingStatusText)
    val isProbing by playerState?.isProbing?.collectAsState(false) ?: mutableStateOf(false)
    val activeShader by (playerState?.activeShader ?: kotlinx.coroutines.flow.flowOf("None")).collectAsState("None")
    val activeLazyVideoTrackUrl by (playerState?.activeLazyVideoTrackUrl ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)
    val resolution by (playerState?.resolution ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)

    LaunchedEffect(isUiReady, links, currentLinkIndex, episodes, currentEpisodeId, audioTracks, subtitleTracks, proxyAudioTracks, proxySubtitleTracks, proxyVideoTracks, loadingStatusText, isProbing, failedLinks, backdropUrl, logoUrl, title, activeShader, activeLazyVideoTrackUrl, resolution, plot, year, tags) {
        if (isUiReady) {
            val payload = mapOf(
                "type" to "metadata_update",
                "plot" to plot,
                "year" to year,
                "tags" to tags,
                "isProbing" to isProbing,
                "backdropUrl" to backdropUrl,
                "logoUrl" to logoUrl,
                "currentLinkIndex" to currentLinkIndex,
                "failedLinks" to failedLinks.toList(),
                "links" to links.mapIndexed { index, l ->
                    mapOf("index" to index, "name" to l.name, "quality" to l.quality, "isActive" to (index == currentLinkIndex))
                },
                "episodes" to episodes.map {
                    mapOf(
                        "id" to it.data,
                        "title" to (it.name ?: "Episode ${it.episode}"),
                        "season" to it.season,
                        "episode" to it.episode,
                        "isActive" to (it.data == currentEpisodeId),
                        "posterUrl" to (it.posterUrl ?: seriesPosterUrl),
                        "description" to it.description,
                        "runTime" to it.runTime,
                    )
                },
                "audioTracks" to audioTracks.map {
                    mapOf("id" to it.id, "name" to it.name, "isSelected" to it.isSelected)
                },
                "subTracks" to subtitleTracks.map {
                    mapOf("id" to it.id, "name" to it.name, "isSelected" to it.isSelected)
                },
                "lazyAudioTracks" to proxyAudioTracks.map {
                    mapOf("url" to it.url, "name" to it.name, "language" to it.language)
                },
                "lazySubTracks" to proxySubtitleTracks.map {
                    mapOf("url" to it.url, "name" to it.name, "language" to it.language)
                },
                "lazyVideoTracks" to proxyVideoTracks.map {
                    mapOf("url" to it.url, "name" to it.name, "language" to it.language)
                },
                "startPositionMs" to startPositionMs,
                "title" to (title ?: "CloudStream"),
                "shaders" to com.lagradost.cloudstream3.desktop.player.ShaderManager.getAvailableShaders(),
                "activeShader" to activeShader,
                "activeLazyVideoTrackUrl" to activeLazyVideoTrackUrl,
                "resolution" to resolution,
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

            NativePlayerBridge.postMessage(
                "{\"type\":\"app_state_update\",\"volume\":$vol,\"isMuted\":$isMuted,\"isAppLoading\":$isAppScraping,\"loadingStatusText\":$loadingTextJson,\"debugWait\":false,\"debugHasEver\":true,\"debugPos\":0.0}",
            )
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
        onPlaybackReady = currentOnPlaybackReady,
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
            val htmlContent = NativePlayerBridge::class.java.getResourceAsStream("/player-ui/controls.html")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?.replace("{{ACCENT_COLOR}}", accentColorHex)
                ?.replace("{{ACCENT_COLOR_RGB}}", accentColorRgb)
                ?: ""
            if (htmlContent.isNotEmpty()) {
                tempFile.writeText(htmlContent, Charsets.UTF_8)
            } else {
                com.lagradost.common.logging.AppLogger.e("[NativePlayer] controls.html resource not found!")
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
                                        try {
                                            provider.search(auth, search)?.forEach { sub ->
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
                                        } catch (e: Exception) {
                                            com.lagradost.common.logging.AppLogger.e("SubSearch[${provider.name}]: ${e.message}")
                                        }
                                    }

                                    val json = playerObjectMapper.writeValueAsString(
                                        mapOf("type" to "subtitle_search_results", "results" to allResults),
                                    )
                                    NativePlayerBridge.postMessage(json)
                                } catch (e: Exception) {
                                    com.lagradost.common.logging.AppLogger.e("searchSubtitles: ${e.message}")
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

                                        val fileUrl = provider.load(auth, sub)
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
                                    com.lagradost.common.logging.AppLogger.e("downloadSubtitle: ${e.message}")
                                }
                            }
                        }
                        "seekTo" -> {
                            val pos = eventValue.toDoubleOrNull()
                            if (pos != null) {
                                playerState?.positionMs?.value = pos.toLong()
                            }
                        }
                        "seekBy" -> {
                            val offset = eventValue.toDoubleOrNull()
                            if (offset != null) {
                                val current = playerState?.positionMs?.value ?: 0L
                                playerState?.positionMs?.value = current + offset.toLong()
                            }
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
                            val idx = eventValue.toIntOrNull()
                            if (idx != null) {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    onLinkChange?.invoke(idx)
                                }
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
