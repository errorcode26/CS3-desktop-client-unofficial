package com.lagradost.player.impl.proxy

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (ex: Throwable) {}
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (continuation.isCancelled) {
                response.body?.close()
                return
            }
            try {
                // Resume with onCancellation block to prevent leaks if cancelled during dispatch
                continuation.resume(response) {
                    response.body?.close()
                }
            } catch (e: Exception) {
                response.body?.close()
            }
        }
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            try {
                continuation.resumeWithException(e)
            } catch (ignored: Exception) {}
        }
    })
}

data class ProxyTrack(
    val url: String,
    val name: String,
    val language: String,
    val bitrate: Int? = null,
)

private val BW_REGEX = Regex("""BANDWIDTH=(\d+)""")
private val RES_REGEX = Regex("""RESOLUTION=(\d+)x(\d+)""")
private val URI_REGEX = Regex("""URI="([^"]+)"""")
private val NAME_REGEX = Regex("""NAME="([^"]+)"""")
private val LANG_REGEX = Regex("""LANGUAGE="([^"]+)"""")

object LocalStreamProxyState {
    val loadingStatus = MutableStateFlow<String?>(null)
    val lazyAudioTracks = MutableStateFlow<List<ProxyTrack>>(emptyList())
    val lazySubtitleTracks = MutableStateFlow<List<ProxyTrack>>(emptyList())
    val lazyVideoTracks = MutableStateFlow<List<ProxyTrack>>(emptyList())
}

object LocalStreamProxy {
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    var port: Int = 0
        private set

    data class ProxySession(
        val headers: Map<String, String>,
        val masterCache: java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<ByteArray>> = java.util.concurrent.ConcurrentHashMap(),
    )

    // Capped LRU cache to prevent memory leaks from abandoned video sessions
    private val sessions = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, ProxySession>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ProxySession>): Boolean {
                return size > 100
            }
        },
    )

    private val proxyClient by lazy {
        app.baseClient.newBuilder()
            .connectTimeout(15000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(60000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 64
                    // With HLS stripping, MPV only sees 2 streams (1 video + 1 audio),
                    // so we can safely allow more concurrent CDN connections for faster buffering.
                    maxRequestsPerHost = 8
                },
            )
            .build()
    }

    fun start() {
        if (server != null) return
        server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                get("/proxy") {
                    handleRequest(call)
                }
            }
        }.start(wait = false)

        port = kotlinx.coroutines.runBlocking {
            server?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: 0
        }
        AppLogger.i("LocalStreamProxy started on port $port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        sessions.clear()
    }

    fun registerSession(headers: Map<String, String>): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = ProxySession(headers)

        // Clear previous session tracks to prevent ghost subtitles from showing in the UI for the new stream
        LocalStreamProxyState.lazyAudioTracks.value = emptyList()
        LocalStreamProxyState.lazySubtitleTracks.value = emptyList()

        return sessionId
    }

    fun buildProxyUrl(sessionId: String, url: String): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        return "http://127.0.0.1:$port/proxy?s=$sessionId&u=$encodedUrl"
    }

    fun prefetchM3u8(sessionId: String, url: String) {
        val session = sessions[sessionId] ?: return
        if (session.masterCache.containsKey(url)) return

        val deferred = GlobalScope.async(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val requestBuilder = okhttp3.Request.Builder().url(url)
                val mergedHeaders = session.headers.toMutableMap()
                val keysToRemove = mergedHeaders.keys.filter { 
                    it.equals("Accept-Encoding", ignoreCase = true) || 
                    it.equals("Host", ignoreCase = true) 
                }
                keysToRemove.forEach { mergedHeaders.remove(it) }
                mergedHeaders["Accept-Encoding"] = "identity"
                mergedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

                var response: okhttp3.Response? = null
                for (attempt in 1..4) {
                    try {
                        response = proxyClient.newCall(requestBuilder.build()).await()
                        if (response.isSuccessful) break
                    } catch (e: Exception) {}
                    if (attempt < 4) {
                        response?.body?.close()
                        kotlinx.coroutines.delay(200L * attempt)
                    }
                }

                if (response == null || !response.isSuccessful) {
                    response?.body?.close()
                    throw Exception("Prefetch HTTP failed")
                }

                val m3u8Content = response.body?.source()?.readUtf8() ?: ""
                val finalUrl = response.request.url.toString()
                response.body?.close()

                val rewritten = rewriteM3u8(m3u8Content, finalUrl, session, sessionId)
                rewritten.toByteArray(Charsets.UTF_8)
            } catch (e: Exception) {
                AppLogger.e("Prefetch failed for $url", e)
                ByteArray(0)
            }
        }
        // ONLY cache if this is a MASTER playlist (contains #EXT-X-STREAM-INF).
        // Media playlists MUST NOT be cached because:
        // 1. CDN segment URLs contain auth tokens that expire after ~30s
        // 2. Live/EVENT streams need fresh segment lists
        // The deferred is still awaited on first request (speeds up startup)
        // but is NOT stored in masterCache for media playlists.
        // We launch a coroutine to check the content type after fetch completes.
        GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bytes = deferred.await()
                val content = String(bytes, Charsets.UTF_8)
                if (content.contains("#EXT-X-STREAM-INF")) {
                    // Master playlist — safe to cache indefinitely
                    session.masterCache[url] = GlobalScope.async { bytes }
                }
                // Media playlist — do NOT cache, let handleRequest fetch fresh each time
            } catch (_: Exception) {}
        }
    }
    private suspend fun handleRequest(call: io.ktor.server.application.ApplicationCall) {
        try {
            val sessionId = call.request.queryParameters["s"]
            val encodedUrl = call.request.queryParameters["u"]

            if (sessionId == null || encodedUrl == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val url = String(Base64.getUrlDecoder().decode(encodedUrl), Charsets.UTF_8)
            val session = sessions[sessionId]

            if (session == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            // Check if we have an exact cache hit for the exact URL (useful for m3u8 requests)
            val cachedDeferred = session.masterCache[url]
            if (cachedDeferred != null) {
                val bytes = cachedDeferred.await()
                if (bytes.isNotEmpty()) {
                    call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                    call.respondBytes(bytes, status = HttpStatusCode.OK)
                    return
                }
                session.masterCache.remove(url)
            }

            val mergedHeaders = session.headers.toMutableMap()

            val keysToRemove = mergedHeaders.keys.filter { 
                it.equals("Accept-Encoding", ignoreCase = true) || 
                it.equals("Host", ignoreCase = true) 
            }
            keysToRemove.forEach { mergedHeaders.remove(it) }
            
            // Explicitly request identity encoding to prevent OkHttp from transparently adding Accept-Encoding: gzip.
            // If OkHttp uses gzip, it strips the Content-Length header, forcing Ktor to use Chunked Transfer Encoding,
            // which breaks FFmpeg's HLS keep-alive parsing and corrupts the video sync!
            mergedHeaders["Accept-Encoding"] = "identity"

            call.request.headers["Range"]?.let {
                mergedHeaders["Range"] = it
            }

            val requestBuilder = okhttp3.Request.Builder().url(url)
            mergedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

            // Use completely async OkHttp fetch with internal retries to prevent ThreadPool exhaustion
            // and handle CDN connection drops smoothly without breaking FFmpeg.
            var response: okhttp3.Response? = null
            var lastError: Exception? = null
            for (attempt in 1..4) {
                try {
                    response = proxyClient.newCall(requestBuilder.build()).await()
                    if (response.isSuccessful) break
                } catch (e: Exception) {
                    lastError = e
                }
                if (attempt < 4) {
                    response?.body?.close()
                    kotlinx.coroutines.delay(200L * attempt)
                }
            }

            if (response == null) {
                AppLogger.e("LocalStreamProxy Request Failed after 4 attempts! URL: $url Error: ${lastError?.message}")
                call.respond(HttpStatusCode.InternalServerError)
                return
            }

            if (!response.isSuccessful) {
                AppLogger.e("LocalStreamProxy Request Failed! Code: ${response.code} URL: $url")
                response.body?.close()
                call.respond(HttpStatusCode.fromValue(response.code))
                return
            }

            val upstreamContentType = response.header("Content-Type") ?: ""
            // CDNs disguise MPEG-TS/AAC segments as .jpg, .js, etc. to evade hotlink protection.
            // FFmpeg's HLS demuxer checks the MIME type and rejects non-media types like
            // 'application/javascript' or 'image/jpeg' even if the binary content is valid TS.
            // Normalize any non-media, non-m3u8 type to application/octet-stream so FFmpeg
            // always tries to decode the actual binary content.
            val rawContentType = upstreamContentType.ifBlank { "application/octet-stream" }
            val isNonMediaType = rawContentType.contains("javascript", ignoreCase = true) ||
                rawContentType.contains("text/", ignoreCase = true) ||
                (rawContentType.contains("image/", ignoreCase = true) && !rawContentType.contains("mpegurl", ignoreCase = true))
            val contentTypeStr = if (isNonMediaType) "application/octet-stream" else rawContentType
            val isM3u8 = url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".m3u", ignoreCase = true) ||
                rawContentType.contains("mpegurl", ignoreCase = true) ||
                rawContentType.contains("x-mpegURL", ignoreCase = true) ||
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val s = response.body?.source()
                        s != null && s.request(7) && s.peek().readUtf8(7) == "#EXTM3U"
                    } catch (e: Exception) {
                        false
                    }
                }

            if (isM3u8) {
                val m3u8Content = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body?.source()?.readUtf8() ?: ""
                }
                val finalUrl = response.request.url.toString()

                val rewritten = rewriteM3u8(m3u8Content, finalUrl, session, sessionId)

                val bytes = rewritten.toByteArray(Charsets.UTF_8)
                
                // Cache it for subsequent FFmpeg probes to prevent network hit,
                // BUT ONLY if it's a MASTER playlist. Media playlists MUST NOT be cached,
                // otherwise mpv will never discover new segments for live streams.
                if (m3u8Content.contains("#EXT-X-STREAM-INF")) {
                    session.masterCache[url] = kotlinx.coroutines.GlobalScope.async { bytes }
                }

                call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                call.respondBytes(bytes, status = HttpStatusCode.OK)
                return
            } else {
                var finalCode = response.code
                var skipBytes = 0L
                val origRange = call.request.headers["Range"]
                if (origRange != null && origRange.startsWith("bytes=", ignoreCase = true) && response.code == 200) {
                    skipBytes = origRange.substringAfter("=").substringBefore("-").toLongOrNull() ?: 0L
                    if (skipBytes > 0) {
                        finalCode = 206
                    }
                }

                val cl = response.body?.contentLength() ?: -1L
                val contentLengthParam = if (cl >= 0) (cl - skipBytes).coerceAtLeast(0) else null

                val parsedContentType = try {
                    ContentType.parse(contentTypeStr)
                } catch (e: Exception) {
                    ContentType.Application.OctetStream
                }

                if (skipBytes > 0) {
                    val endPart = origRange?.substringAfter("-")
                    val endPos = if (endPart.isNullOrBlank()) (if (cl > 0) cl - 1 else "") else endPart
                    call.response.header("Content-Range", "bytes $skipBytes-$endPos/${if (cl > 0) cl else "*"}")
                } else {
                    response.header("Content-Range")?.let { call.response.header("Content-Range", it) }
                }
                
                response.header("Accept-Ranges")?.let { call.response.header("Accept-Ranges", it) }

                // Since OkHttp's readTimeout is robust (60s), we no longer need the unbounded 
                // channel buffer. Stream directly to Ktor to avoid GC allocation churn from 
                // array copies.
                call.response.header("Connection", "close")
                
                var streamStarted = false
                try {
                    call.respondBytesWriter(
                        contentType = parsedContentType,
                        status = HttpStatusCode.fromValue(finalCode),
                        contentLength = contentLengthParam,
                    ) {
                        streamStarted = true
                        var currentResponse: okhttp3.Response? = response
                        var streamSource = currentResponse?.body?.source() ?: throw Exception("No body")
                        
                        if (skipBytes > 0) {
                            streamSource.skip(skipBytes)
                        }

                        val ktorChannel = this
                        var totalBytesRead = skipBytes
                        val buffer = ByteArray(65536)
                        // Clear loading popup since we are now streaming data directly to MPV!
                        LocalStreamProxyState.loadingStatus.value = null

                        try {
                            while (true) {
                                try {
                                    var bytesRead: Int
                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        while (true) {
                                            val readBytes = try {
                                                streamSource.read(buffer)
                                            } catch (e: java.net.SocketException) {
                                                // Normal: MPV seeks forcefully drop the connection
                                                break
                                            } catch (e: Exception) {
                                                throw Exception("CDN_ERROR", e)
                                            }
                                            if (readBytes == -1) {
                                                val expectedCl = currentResponse?.body?.contentLength() ?: -1L
                                                if (expectedCl != -1L && (totalBytesRead - skipBytes) < expectedCl) {
                                                    throw Exception("CDN_ERROR_PREMATURE_EOF")
                                                }
                                                break
                                            }

                                            try {
                                                ktorChannel.writeFully(buffer, 0, readBytes)
                                                ktorChannel.flush()
                                                totalBytesRead += readBytes
                                            } catch (e: Exception) {
                                                throw Exception("CLIENT_DISCONNECT", e)
                                            }
                                        }
                                    }
                                    break // EOF reached naturally
                                } catch (e: Exception) {
                                    // If Ktor's channel is closed, or we specifically got a write error, the client (MPV) disconnected. Stop proxying.
                                    if (e.message == "CLIENT_DISCONNECT" || ktorChannel.isClosedForWrite) {
                                        break
                                    }

                                    // If we don't know the total size and it's chunked, or we reached the known size, we're done.
                                    val cl = currentResponse?.body?.contentLength() ?: -1L
                                    if (cl != -1L && (totalBytesRead - skipBytes) >= cl) {
                                        break
                                    }

                                    AppLogger.w("CDN connection dropped mid-stream at $totalBytesRead/$cl bytes. Resuming transparently...")
                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        currentResponse?.body?.close()
                                    }

                                    // Transparently reconnect and resume from totalBytesRead
                                    val resumeBuilder = requestBuilder.build().newBuilder()
                                    val origRangeHeader = mergedHeaders["Range"]
                                    if (origRangeHeader != null && origRangeHeader.startsWith("bytes=", ignoreCase = true)) {
                                        val startPart = origRangeHeader.substringAfter("=").substringBefore("-").toLongOrNull() ?: 0L
                                        val endPart = origRangeHeader.substringAfter("-")
                                        val newStart = startPart + (totalBytesRead - skipBytes)
                                        resumeBuilder.header("Range", "bytes=$newStart-$endPart")
                                    } else {
                                        resumeBuilder.header("Range", "bytes=$totalBytesRead-")
                                    }

                                    var retrySuccess = false
                                    for (attempt in 1..3) {
                                        try {
                                            currentResponse = proxyClient.newCall(resumeBuilder.build()).await()
                                            if (currentResponse!!.isSuccessful) {
                                                streamSource = currentResponse!!.body?.source() ?: throw Exception("No body")
                                                if (currentResponse!!.code == 200 && totalBytesRead > 0) {
                                                    // The CDN ignored our Range request and returned the full file.
                                                    // We MUST manually skip the bytes we've already streamed to MPV!
                                                    streamSource.skip(totalBytesRead)
                                                }
                                                retrySuccess = true
                                                break
                                            }
                                        } catch (retryEx: Exception) {
                                            kotlinx.coroutines.delay(500L * attempt)
                                        }
                                        if (attempt < 3) {
                                            currentResponse?.body?.close()
                                        }
                                    }

                                    if (!retrySuccess) {
                                        AppLogger.e("Failed to transparently resume CDN stream.")
                                        throw e // Abort and let MPV handle the error
                                    }
                                }
                            }
                        } finally {
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    currentResponse?.body?.close()
                                } catch (ignored: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!streamStarted) {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try { response.body?.close() } catch (ignored: Exception) {}
                        }
                    }
                    throw e
                }
            }
        } catch (e: Exception) {
            AppLogger.e("LocalStreamProxy error: ${e.message}")
            try {
                call.respond(HttpStatusCode.InternalServerError)
            } catch (_: Exception) {}
        }
    }

    private fun rewriteM3u8(content: String, baseUrl: String, session: ProxySession, sessionId: String): String {
        val lines = content.split("\n")
        val isMaster = content.contains("#EXT-X-STREAM-INF")

        var nextLineIsVariantUrl = false

        if (isMaster) {
            val lazyAudios = mutableListOf<ProxyTrack>()
            val lazySubs = mutableListOf<ProxyTrack>()
            val lazyVideoTracks = mutableListOf<ProxyTrack>()
            var hasKeptAudio = false

            // Pass 1: Find best video variant and default audio variant
            var maxScore = -1
            var bestVariantUrl: String? = null
            var currentVariantLine: String? = null
            var bestAudioUrl: String? = null
            var firstAudioUrl: String? = null

            for (line in lines) {
                val trim = line.trim()
                if (trim.startsWith("#EXT-X-STREAM-INF")) {
                    currentVariantLine = trim
                } else if (currentVariantLine != null && !trim.startsWith("#")) {
                    val bwMatch = BW_REGEX.find(currentVariantLine)
                    val resMatch = RES_REGEX.find(currentVariantLine)
                    val bw = bwMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val res = resMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val score = res * 1000000 + bw
                    if (score > maxScore) {
                        maxScore = score
                        bestVariantUrl = resolveUrl(baseUrl, trim)
                    }
                    currentVariantLine = null
                } else if (trim.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                    val uriMatch = URI_REGEX.find(trim)
                    if (uriMatch != null) {
                        val uri = uriMatch.groupValues[1]
                        if (firstAudioUrl == null) firstAudioUrl = uri
                        if (trim.contains("DEFAULT=YES", ignoreCase = true)) {
                            bestAudioUrl = uri
                        }
                    }
                }
            }

            if (bestAudioUrl == null) bestAudioUrl = firstAudioUrl

            // Pass 2: Reconstruct playlist keeping only best video variant, ONE audio variant, and ALL subtitles
            val rewritten = buildString {
                var pendingVariantLine: String? = null

                for (line in lines) {
                    val trim = line.trim()
                    if (trim.isEmpty()) continue

                    if (trim.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES")) {
                        // Strip ALL subtitles — FFmpeg aggressively probes every single one at startup!
                        val name = NAME_REGEX.find(trim)?.groupValues?.get(1) ?: "Unknown Sub"
                        val lang = LANG_REGEX.find(trim)?.groupValues?.get(1) ?: "unk"
                        val uriMatch = URI_REGEX.find(trim)
                        if (uriMatch != null) {
                            val absolute = resolveUrl(baseUrl, uriMatch.groupValues[1])
                            val proxied = buildProxyUrl(sessionId, absolute)
                            lazySubs.add(ProxyTrack(proxied, name, lang))
                        }
                        continue
                    }

                    if (trim.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                        val name = NAME_REGEX.find(trim)?.groupValues?.get(1) ?: "Unknown Audio"
                        val lang = LANG_REGEX.find(trim)?.groupValues?.get(1) ?: "unk"
                        val uriMatch = URI_REGEX.find(trim)

                        if (uriMatch != null) {
                            val uri = uriMatch.groupValues[1]
                            val absolute = resolveUrl(baseUrl, uri)
                            val proxied = buildProxyUrl(sessionId, absolute)

                            // Keep ALL audio variants in the proxy M3U8 so MPV can natively switch them via `aid`!
                            val newLine = trim.replace(uriMatch.groupValues[0], "URI=\"$proxied\"")
                            appendLine(newLine)
                        } else {
                            // If there is no URI, it's embedded in the video stream, keep it
                            appendLine(trim)
                        }
                        continue
                    }

                    if (trim.startsWith("#")) {
                        if (trim.contains("URI=\"")) {
                            val uriRegex = Regex("""URI="([^"]+)"""")
                            val newLine = trim.replace(uriRegex) { result ->
                                val uri = result.groupValues[1]
                                val absolute = resolveUrl(baseUrl, uri)
                                val proxied = buildProxyUrl(sessionId, absolute)
                                "URI=\"$proxied\""
                            }
                            appendLine(newLine)
                        } else {
                            if (trim.startsWith("#EXT-X-STREAM-INF")) {
                                pendingVariantLine = trim
                            } else {
                                appendLine(trim)
                            }
                        }
                    } else {
                        // URL line
                        if (pendingVariantLine != null) {
                            val absolute = resolveUrl(baseUrl, trim)
                            val proxied = buildProxyUrl(sessionId, absolute)

                            // Keep ALL variants in the proxy M3U8 so MPV can natively and seamlessly switch them!
                            // We do not add them to lazyVideoTracks, because native track switching via `vid` is much more optimized than `video-add`.
                            appendLine(pendingVariantLine)
                            appendLine(proxied)
                            
                            // Expose to Compose UI so we can use `hls-bitrate` property
                            val bwMatch = BW_REGEX.find(pendingVariantLine!!)
                            val resMatch = RES_REGEX.find(pendingVariantLine!!)
                            val res = resMatch?.groupValues?.get(1) ?: "Unknown"
                            val bw = bwMatch?.groupValues?.get(1)?.toIntOrNull()
                            val bwLabel = if (bw != null) " ${bw / 1000}kbps" else ""
                            val name = if (res != "Unknown") "${res}p$bwLabel" else "Variant$bwLabel"
                            lazyVideoTracks.add(ProxyTrack(proxied, name, "eng", bw))
                        }
                        pendingVariantLine = null
                    }
                }
            }

            if (bestVariantUrl != null) {
                prefetchM3u8(sessionId, resolveUrl(baseUrl, bestVariantUrl!!))
            }

            LocalStreamProxyState.lazyAudioTracks.value = lazyAudios
            LocalStreamProxyState.lazySubtitleTracks.value = lazySubs
            LocalStreamProxyState.lazyVideoTracks.value = lazyVideoTracks
            LocalStreamProxyState.loadingStatus.value = null

            return rewritten
        }

        // --- Non-Master Playlist Processing ---
        val rewritten = buildString {
            for (line in lines) {
                val trim = line.trim()
                if (trim.isEmpty()) continue

                if (trim.startsWith("#")) {
                    if (trim.contains("URI=\"")) {
                        val uriRegex = Regex("""URI="([^"]+)"""")
                        val newLine = trim.replace(uriRegex) { result ->
                            val uri = result.groupValues[1]
                            val absolute = resolveUrl(baseUrl, uri)
                            val proxied = buildProxyUrl(sessionId, absolute)
                            "URI=\"$proxied\""
                        }
                        appendLine(newLine)
                    } else {
                        appendLine(trim)
                    }
                } else {
                    val absolute = resolveUrl(baseUrl, trim)
                    // Proxy video segments through OkHttp to benefit from TLS connection pooling
                    // and keep-alive, which FFmpeg natively struggles with on HTTPS streams.
                    val proxied = buildProxyUrl(sessionId, absolute)
                    appendLine(proxied)
                }
            }
        }
        return rewritten
    }

    private fun resolveUrl(base: String, uri: String): String {
        if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
            return uri
        }

        val baseUrl = base.toHttpUrlOrNull()
        if (baseUrl != null) {
            val resolved = baseUrl.resolve(uri)
            if (resolved != null) {
                val resolvedStr = resolved.toString()
                if (baseUrl.query != null && resolved.query == null) {
                    return "$resolvedStr?${baseUrl.query}"
                }
                return resolvedStr
            }
        }

        return try {
            val baseUri = URI(base)
            val resolved = baseUri.resolve(uri).toString()

            // Inherit query parameters from the base URL (for auth tokens like md5/expires)
            if (baseUri.query != null && !resolved.contains("?")) {
                "$resolved?${baseUri.query}"
            } else {
                resolved
            }
        } catch (e: Exception) {
            if (base.contains("/")) {
                base.substringBeforeLast('/') + "/" + uri
            } else {
                uri
            }
        }
    }
}
