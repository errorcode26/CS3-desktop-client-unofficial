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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val ProxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    // Converting callbacks to coroutines is always a nightmare. If OkHttp hangs here, good luck debugging it.
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (ex: Throwable) {
            com.lagradost.common.logging.AppLogger.w("Proxy:LocalStream", "Failed to cancel OkHttp call: ${ex.message}", ex)
        }
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
                com.lagradost.common.logging.AppLogger.e("Proxy:LocalStream", "Error resuming coroutine onResponse: ${e.message}", e)
                response.body?.close()
            }
        }
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            try {
                continuation.resumeWithException(e)
            } catch (ignored: Exception) {
                com.lagradost.common.logging.AppLogger.e("Proxy:LocalStream", "Error resuming coroutine onFailure: ${ignored.message}", ignored)
            }
        }
    })
}

object LocalStreamProxy {
    var tracksListener: ProxyTracksListener? = LocalStreamProxyState
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    var port: Int = 0
        private set

    data class ProxySession(
        val headers: Map<String, String>,
        val masterCache: java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<ByteArray>> = java.util.concurrent.ConcurrentHashMap(),
        val mpdCache: java.util.concurrent.ConcurrentHashMap<String, String> = java.util.concurrent.ConcurrentHashMap(),
    )

    // Fast in-memory init segment cache (10 minutes TTL)
    data class InitCacheEntry(val data: ByteArray, val timestamp: Long)
    private val initSegmentCache = java.util.concurrent.ConcurrentHashMap<String, InitCacheEntry>()
    private const val INIT_CACHE_TTL_MS = 600_000L // 10 minutes

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
            .fastFallback(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(128, 300, java.util.concurrent.TimeUnit.SECONDS))
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 256
                    // Video chunking hits the same CDN host repeatedly, requiring high parallel limits
                    maxRequestsPerHost = 64
                },
            )
            .build()
    }

    private val imageProxyClient by lazy {
        val cacheDir = java.io.File(com.lagradost.common.platform.PlatformPaths.appDataDir, "image_cache_http").also { it.mkdirs() }
        app.baseClient.newBuilder()
            .fastFallback(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .cache(okhttp3.Cache(cacheDir, 256L * 1024 * 1024))
            .apply {
                interceptors().removeAll {
                    it.javaClass.simpleName == "RateLimitInterceptor" ||
                        it.javaClass.simpleName == "DevNetworkInterceptor"
                }
            }
            .build()
    }

    fun start() {
        if (server != null) return
        server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                get("/proxy") {
                    handleRequest(call)
                }
                get("/image") {
                    handleImageRequest(call)
                }
                get("/trailer") {
                    val id = call.request.queryParameters["id"] ?: ""
                    val u = call.request.queryParameters["u"] ?: ""
                    val html = if (id.isNotBlank()) {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <meta name="referrer" content="strict-origin-when-cross-origin">
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                                iframe { width: 100%; height: 100%; border: none; }
                            </style>
                        </head>
                        <body>
                            <iframe
                                src="https://www.youtube-nocookie.com/embed/$id?autoplay=1&mute=1&playsinline=1&rel=0&modestbranding=1&fs=1"
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen"
                                allowfullscreen="true"
                                referrerpolicy="strict-origin-when-cross-origin">
                            </iframe>
                        </body>
                        </html>
                        """.trimIndent()
                    } else {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                html, body { width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                                video { width: 100%; height: 100%; object-fit: contain; }
                            </style>
                        </head>
                        <body>
                            <video src="$u" autoplay muted controls playsinline></video>
                        </body>
                        </html>
                        """.trimIndent()
                    }
                    call.respondText(html, ContentType.Text.Html)
                }
            }
        }.start(wait = false)

        port = kotlinx.coroutines.runBlocking {
            server?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: 0
        }
        AppLogger.i("Proxy:LocalStream", "LocalStreamProxy started on port $port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        sessions.clear()
        initSegmentCache.clear()
    }

    fun registerSession(headers: Map<String, String>): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = ProxySession(headers)

        // Clear previous session tracks to prevent ghost subtitles from showing in the UI for the new stream
        LocalStreamProxyState.reset()

        return sessionId
    }

    fun buildProxyUrl(sessionId: String, url: String, action: String? = null, clearKey: String? = null): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        var proxy = "http://127.0.0.1:$port/proxy?s=$sessionId&u=$encodedUrl"
        if (action != null) proxy += "&action=$action"
        if (clearKey != null) proxy += "&ck=$clearKey"
        return proxy
    }

    fun buildImageUrl(url: String): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        return "http://127.0.0.1:$port/image?u=$encodedUrl"
    }

    fun prefetchM3u8(sessionId: String, url: String) {
        val session = sessions[sessionId] ?: return
        if (session.masterCache.containsKey(url)) return

        val deferred = ProxyScope.async(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val requestBuilder = okhttp3.Request.Builder().url(url).cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                val mergedHeaders = session.headers.toMutableMap()
                val keysToRemove = mergedHeaders.keys.filter {
                    it.equals("Accept-Encoding", ignoreCase = true) ||
                        it.equals("Host", ignoreCase = true)
                }
                keysToRemove.forEach { mergedHeaders.remove(it) }
                mergedHeaders["Accept-Encoding"] = "identity"
                if (mergedHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    mergedHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                }
                mergedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

                var response: okhttp3.Response? = null
                var lastError: Exception? = null
                for (attempt in 1..4) {
                    try {
                        response = proxyClient.newCall(requestBuilder.build()).await()
                        if (response.isSuccessful || response.code in 400..499) break
                    } catch (e: Exception) {
                        lastError = e
                    }
                    if (attempt < 4) {
                        response?.body?.close()
                        kotlinx.coroutines.delay(200L * attempt)
                    }
                }

                if (response == null || !response.isSuccessful) {
                    val code = response?.code
                    response?.body?.close()
                    throw Exception("Prefetch HTTP failed. Code: $code Error: ${lastError?.message}")
                }

                val m3u8Content = response.body?.source()?.readUtf8() ?: ""
                val finalUrl = response.request.url.toString()
                response.body?.close()

                val rewritten = HlsRewriter.rewriteM3u8(m3u8Content, finalUrl, sessionId, tracksListener)
                rewritten.toByteArray(Charsets.UTF_8)
            } catch (e: Exception) {
                AppLogger.e("Proxy:LocalStream", "Prefetch failed for $url", e)
                ByteArray(0)
            }
        }
        // Immediately store in masterCache to prevent race conditions when MPV requests it right away.
        // If after analysis we find it is not a master playlist, we remove it.
        session.masterCache[url] = deferred
        ProxyScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bytes = deferred.await()
                val content = String(bytes, Charsets.UTF_8)
                if (!content.contains("#EXT-X-STREAM-INF")) {
                    // Media playlist — do NOT keep in masterCache, let handleRequest fetch fresh on subsequent refreshes
                    session.masterCache.remove(url)
                }
            } catch (e: Exception) {
                session.masterCache.remove(url)
                com.lagradost.common.logging.AppLogger.w("Proxy:LocalStream", "Error analyzing prefetch payload for caching: ${e.message}", e)
            }
        }
    }

    private suspend fun handleImageRequest(call: io.ktor.server.application.ApplicationCall) {
        try {
            val encodedUrl = call.request.queryParameters["u"]
            if (encodedUrl == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
                return
            }
            var url = String(java.util.Base64.getUrlDecoder().decode(encodedUrl), Charsets.UTF_8).trim()
            if (url.startsWith("//")) {
                url = "https:$url"
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                return
            }

            val requestBuilder = okhttp3.Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")

            val isThirdPartyCdn = url.contains("image.tmdb.org", ignoreCase = true) ||
                url.contains("anilist.co", ignoreCase = true) ||
                url.contains("kitsu.app", ignoreCase = true) ||
                url.contains("kitsu.io", ignoreCase = true) ||
                url.contains("fanart.tv", ignoreCase = true) ||
                url.contains("imgur.com", ignoreCase = true)

            if (!isThirdPartyCdn) {
                try {
                    val uri = java.net.URI(url)
                    requestBuilder.header("Referer", "${uri.scheme}://${uri.host}/")
                } catch (_: Exception) {}
            }

            val response = imageProxyClient.newCall(requestBuilder.build()).await()
            if (!response.isSuccessful) {
                response.body?.close()
                call.respond(io.ktor.http.HttpStatusCode.fromValue(response.code))
                return
            }
            val contentType = response.header("Content-Type") ?: "image/jpeg"
            val bytes = response.body?.bytes()
            if (bytes != null) {
                call.respondBytes(bytes, io.ktor.http.ContentType.parse(contentType), io.ktor.http.HttpStatusCode.fromValue(response.code))
            } else {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            }
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("Proxy:LocalStream", "Image proxy failed", e)
            call.respond(io.ktor.http.HttpStatusCode.InternalServerError)
        }
    }

    private suspend fun handleRequest(call: io.ktor.server.application.ApplicationCall) {
        try {
            val sessionId = call.request.queryParameters["s"]
            val encodedUrl = call.request.queryParameters["u"]
            val isFlatVtt = call.request.queryParameters["flatvtt"] == "true"
            val action = call.request.queryParameters["action"]
            val rep = call.request.queryParameters["rep"]
            val clearKey = call.request.queryParameters["ck"]
            val kid = call.request.queryParameters["kid"]
            val k = call.request.queryParameters["k"]

            com.lagradost.common.logging.AppLogger.i("Proxy:LocalStream", "Action=$action, rep=$rep, hasCk=${clearKey != null}, hasKid=${kid != null}, hasK=${k != null}, encodedUrl=$encodedUrl")

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
            val cachedDeferred = session.masterCache.remove(url)
            if (cachedDeferred != null) {
                val bytes = cachedDeferred.await()
                if (bytes.isNotEmpty()) {
                    call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                    call.respondBytes(bytes, status = HttpStatusCode.OK)
                    return
                }
            }

            if (action == "init_decrypt" || action == "init") {
                val cached = initSegmentCache[url]
                if (cached != null && System.currentTimeMillis() - cached.timestamp < INIT_CACHE_TTL_MS) {
                    call.response.header("Content-Type", "video/mp4")
                    call.respondBytes(cached.data, status = HttpStatusCode.OK)
                    return
                }
            }

            if (action == "dash" && rep != null) {
                val cachedMpd = session.mpdCache[url]
                val isLive = cachedMpd?.contains("type=\"dynamic\"") == true || cachedMpd?.contains("type='dynamic'") == true
                if (cachedMpd != null && !isLive) {
                    val m3u8 = NativeMpdConverter().convertMediaPlaylist(cachedMpd, rep, port, sessionId, url, clearKey)
                    call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                    call.respondBytes(m3u8.toByteArray(Charsets.UTF_8), status = HttpStatusCode.OK)
                    return
                }
            }

            val mergedHeaders = session.headers.toMutableMap()

            val keysToRemove = mergedHeaders.keys.filter {
                it.equals("Accept-Encoding", ignoreCase = true) ||
                    it.equals("Host", ignoreCase = true)
            }
            keysToRemove.forEach { mergedHeaders.remove(it) }

            // Removed explicitly requesting identity encoding. OkHttp will handle gzip natively.
            // Chunked transfer encoding is fine since we close the connection anyway.

            val isM3u8Url = url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".m3u", ignoreCase = true) ||
                url.contains("m3u8", ignoreCase = true) ||
                url.contains("playlist", ignoreCase = true) ||
                url.contains("manifest", ignoreCase = true)

            if (!isM3u8Url) {
                call.request.headers["Range"]?.let {
                    mergedHeaders["Range"] = it
                }
            } else {
                mergedHeaders.keys.filter { it.equals("Range", ignoreCase = true) }.forEach { mergedHeaders.remove(it) }
            }

            if (mergedHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                mergedHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            }

            val requestBuilder = okhttp3.Request.Builder().url(url).cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
            mergedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

            // Use completely async OkHttp fetch with internal retries to prevent ThreadPool exhaustion
            // and handle CDN connection drops smoothly without breaking FFmpeg.
            var response: okhttp3.Response? = null
            var lastError: Exception? = null
            for (attempt in 1..4) {
                try {
                    response = proxyClient.newCall(requestBuilder.build()).await()
                    if (response.isSuccessful || response.code in 400..499) break
                } catch (e: Exception) {
                    lastError = e
                }
                if (attempt < 4) {
                    response?.body?.close()
                    kotlinx.coroutines.delay(200L * attempt)
                }
            }

            // If the request had a Range header and failed with 403, 400, 416, or 405 (method/range not allowed),
            // retry the request WITHOUT the Range header and let the proxy skip the bytes manually.
            // NOTE: Do NOT include 500 here — CDNs that return 500 do so regardless of Range headers,
            // so retrying without Range just wastes 3-4 extra seconds on a permanently dead segment.
            if (response != null && !response.isSuccessful && mergedHeaders.containsKey("Range")) {
                val code = response.code
                if (code == 403 || code == 400 || code == 416 || code == 405) {
                    AppLogger.w("Proxy:LocalStream", "Range request failed with HTTP $code, retrying WITHOUT Range header for URL: $url")
                    response.body?.close()
                    val retryHeaders = mergedHeaders.toMutableMap()
                    retryHeaders.remove("Range")
                    val retryBuilder = okhttp3.Request.Builder().url(url)
                    retryHeaders.forEach { (k, v) -> retryBuilder.header(k, v) }

                    var retryResponse: okhttp3.Response? = null
                    for (attempt in 1..3) {
                        try {
                            retryResponse = proxyClient.newCall(retryBuilder.build()).await()
                            if (retryResponse.isSuccessful || retryResponse.code in 400..499) break
                        } catch (e: Exception) {
                            lastError = e
                        }
                        if (attempt < 3) {
                            retryResponse?.body?.close()
                            kotlinx.coroutines.delay(200L * attempt)
                        }
                    }
                    if (retryResponse != null && retryResponse.isSuccessful) {
                        response = retryResponse
                    } else {
                        retryResponse?.body?.close()
                    }
                }
            }

            if (response == null) {
                AppLogger.e("Proxy:LocalStream", "Proxy Request Failed after 4 attempts! URL: $url Error: ${lastError?.message}")
                call.respond(HttpStatusCode.InternalServerError)
                return
            }

            if (!response.isSuccessful) {
                AppLogger.e("Proxy:LocalStream", "Proxy Request Failed! Code: ${response.code} URL: $url")
                response.body?.close()
                call.respond(HttpStatusCode.fromValue(response.code))
                return
            }

            if (action == "init_decrypt") {
                val rawBytes = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body?.source()?.readByteArray() ?: ByteArray(0)
                }
                response.body?.close()
                val cleaned = StreamDecryptor.cleanInitSegment(rawBytes)
                initSegmentCache[url] = InitCacheEntry(cleaned, System.currentTimeMillis())
                call.response.header("Content-Type", "video/mp4")
                call.respondBytes(cleaned, status = HttpStatusCode.OK)
                return
            }

            if (action == "decrypt") {
                call.response.header("Content-Type", "video/mp4")
                try {
                    call.respondBytesWriter(status = HttpStatusCode.OK) {
                        try {
                            val streamSource = response.body?.source() ?: return@respondBytesWriter
                            StreamDecryptor.streamingDecryptMediaSegment(streamSource, kid ?: "", k ?: "") { bytes ->
                                try {
                                    writeFully(bytes)
                                    flush()
                                } catch (e: Exception) {
                                    throw Exception("CLIENT_DISCONNECT", e)
                                }
                            }
                        } catch (e: Exception) {
                            if (e.message != "CLIENT_DISCONNECT" && e !is java.io.EOFException && e !is java.net.SocketException) {
                                AppLogger.e("Proxy:LocalStream", "Decryption streaming error for $url", e)
                            }
                        } finally {
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    response.body?.close()
                                } catch (ignored: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e.message != "CLIENT_DISCONNECT") {
                        AppLogger.e("Proxy:LocalStream", "Failed to respond to decrypt request", e)
                    }
                }
                return
            }

            if (action == "dash") {
                val mpdContent = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body?.source()?.readUtf8() ?: ""
                }
                response.body?.close()
                session.mpdCache[url] = mpdContent
                val m3u8 = if (rep == null) {
                    NativeMpdConverter().convertMasterPlaylist(mpdContent, port, sessionId, url, clearKey, tracksListener)
                } else {
                    NativeMpdConverter().convertMediaPlaylist(mpdContent, rep, port, sessionId, url, clearKey)
                }
                call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                call.respondBytes(m3u8.toByteArray(Charsets.UTF_8), status = HttpStatusCode.OK)
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
                url.contains("m3u8", ignoreCase = true) ||
                url.contains("playlist", ignoreCase = true) ||
                url.contains("manifest", ignoreCase = true) ||
                rawContentType.contains("mpegurl", ignoreCase = true) ||
                rawContentType.contains("x-mpegURL", ignoreCase = true) ||
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val s = response.body?.source()
                        if (s != null && s.request(32)) {
                            val peeked = s.peek().readUtf8(32).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                            peeked.startsWith("#EXTM3U", ignoreCase = true) || peeked.startsWith("#EXT-X-", ignoreCase = true)
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }

            if (isM3u8) {
                try {
                    val m3u8Content = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        response.body?.source()?.readUtf8() ?: ""
                    }

                    val finalUrl = response.request.url.toString()

                    if (isFlatVtt) {
                        if (m3u8Content.contains("#EXT-X-KEY") || m3u8Content.contains("#EXT-X-MAP")) {
                            // Edge Case 1: Encrypted or fMP4 subtitles cannot be flattened to text!
                            // Fallback to normal M3U8 proxying for these.
                        } else {
                            call.response.header("Content-Type", "text/vtt")
                            call.respondBytesWriter(status = HttpStatusCode.OK) {
                                writeFully("WEBVTT\n\n".toByteArray(Charsets.UTF_8))
                                val lines = m3u8Content.lines()
                                val vttUrls = lines.filter { !it.startsWith("#") && it.trim().isNotEmpty() }.map { HlsRewriter.resolveUrl(finalUrl, it.trim()) }

                                for (url in vttUrls) {
                                    val requestBuilder = okhttp3.Request.Builder().url(url)
                                    session.headers.forEach { (k, v) ->
                                        if (!k.equals("Accept-Encoding", true) && !k.equals("Host", true)) {
                                            requestBuilder.header(k, v)
                                        }
                                    }
                                    var result = ""
                                    for (attempt in 1..3) {
                                        try {
                                            val response = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                proxyClient.newCall(requestBuilder.build()).await()
                                            }
                                            result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                response.body?.source()?.readUtf8() ?: ""
                                            }
                                            withContext(kotlinx.coroutines.Dispatchers.IO) { response.body?.close() }
                                            if (response.isSuccessful) break
                                        } catch (e: Exception) { }
                                    }

                                    if (result.isNotBlank()) {
                                        // Edge Case 2: Strip BOM (\uFEFF) which breaks header trimming
                                        val segmentLines = result.trimStart('\uFEFF').lines()
                                        for (line in segmentLines) {
                                            val trimmed = line.trim()
                                            if (trimmed == "WEBVTT" || trimmed.startsWith("X-TIMESTAMP-MAP")) continue
                                            writeFully((line + "\n").toByteArray(Charsets.UTF_8))
                                        }
                                        writeFully("\n".toByteArray(Charsets.UTF_8))
                                    }
                                    // Edge Case 3: Stream the chunks to MPV instantly rather than waiting for all of them!
                                    flush()

                                    // Completely eliminate bandwidth starvation by adding a tiny delay
                                    kotlinx.coroutines.delay(20)
                                }
                            }
                            return
                        }
                    }

                    val rewritten = HlsRewriter.rewriteM3u8(m3u8Content, finalUrl, sessionId, tracksListener)

                    val bytes = rewritten.toByteArray(Charsets.UTF_8)

                    // Cache it for subsequent FFmpeg probes to prevent network hit,
                    // BUT ONLY if it's a MASTER playlist. Media playlists MUST NOT be cached,
                    // otherwise mpv will never discover new segments for live streams.
                    if (m3u8Content.contains("#EXT-X-STREAM-INF")) {
                        session.masterCache[url] = ProxyScope.async { bytes }
                    }

                    call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                    call.respondBytes(bytes, status = HttpStatusCode.OK)
                } finally {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            response.body?.close()
                        } catch (ignored: Exception) {}
                    }
                }
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
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                streamSource.skip(skipBytes)
                            }
                        }

                        val ktorChannel = this
                        var totalBytesRead = skipBytes
                        val buffer = ByteArray(65536)
                        // Clear loading popup since we are now streaming data directly to MPV!
                        tracksListener?.onLoadingComplete()

                        var isFirstChunk = (skipBytes == 0L)

                        try {
                            while (true) {
                                try {
                                    var bytesRead: Int
                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        while (true) {
                                            val readBytes = try {
                                                streamSource.read(buffer)
                                            } catch (e: java.net.SocketException) {
                                                // CDN closed connection unexpectedly (e.g. timeout or reset).
                                                // We must throw CDN_ERROR to trigger the transparent proxy retry below.
                                                throw Exception("CDN_ERROR", e)
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

                                            if (isFirstChunk) {
                                                isFirstChunk = false
                                                if (readBytes > 8) {
                                                    // CDNs often prepend fake image signatures (PNG/JPG/GIF/WEBP) to bypass hotlink protection.
                                                    // FFmpeg's format prober will mistakenly identify the stream as an image and fail to demux the HLS TS chunks.
                                                    // ExoPlayer on Android naturally ignores these by scanning for TS sync bytes (0x47).
                                                    // We corrupt the fake signature so FFmpeg's image probe fails, forcing it to fallback to scanning for TS sync bytes!
                                                    val isFakeImage = (buffer[0] == 0x89.toByte() && buffer[1] == 0x50.toByte() && buffer[2] == 0x4E.toByte()) || // PNG
                                                        (buffer[0] == 0xFF.toByte() && buffer[1] == 0xD8.toByte() && buffer[2] == 0xFF.toByte()) || // JPG
                                                        (buffer[0] == 0x47.toByte() && buffer[1] == 0x49.toByte() && buffer[2] == 0x46.toByte() && buffer[3] == 0x38.toByte()) || // GIF8
                                                        (buffer[0] == 0x52.toByte() && buffer[1] == 0x49.toByte() && buffer[2] == 0x46.toByte() && buffer[3] == 0x46.toByte()) // WEBP (RIFF)
                                                    if (isFakeImage) {
                                                        for (i in 0..7) buffer[i] = 0x00.toByte()
                                                        AppLogger.i("Corrupted fake image signature to force FFmpeg MPEG-TS fallback")
                                                    }
                                                }
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

                                    AppLogger.w("Proxy:LocalStream", "CDN connection dropped mid-stream at $totalBytesRead/$cl bytes. Resuming transparently...")
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
                                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        streamSource.skip(totalBytesRead)
                                                    }
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
                                        AppLogger.e("Proxy:LocalStream", "Failed to transparently resume CDN stream.")
                                        throw e // Abort and let MPV handle the error
                                    }
                                }
                            }
                        } finally {
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    currentResponse?.body?.close()
                                } catch (ignored: Exception) {
                                    com.lagradost.common.logging.AppLogger.w("Proxy:LocalStream", "Failed to close response body: ${ignored.message}", ignored)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!streamStarted) {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                response.body?.close()
                            } catch (ignored: Exception) {
                                com.lagradost.common.logging.AppLogger.w("Proxy:LocalStream", "Failed to close response body on early error: ${ignored.message}", ignored)
                            }
                        }
                    }
                    throw e
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Proxy:LocalStream", "LocalStreamProxy error", e)
            try {
                call.respond(HttpStatusCode.InternalServerError)
            } catch (ex: Exception) {
                com.lagradost.common.logging.AppLogger.e("Proxy:LocalStream", "Failed to send 500 status to client", ex)
            }
        }
    }

    fun resolveUrl(base: String, uri: String): String = HlsRewriter.resolveUrl(base, uri)
}
