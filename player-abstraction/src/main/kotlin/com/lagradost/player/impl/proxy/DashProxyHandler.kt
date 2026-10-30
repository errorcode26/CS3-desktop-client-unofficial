package com.lagradost.player.impl.proxy

import com.lagradost.common.logging.AppLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

internal data class DashEntry(
    val url: String,
    val sessionId: String,
    val name: String,
) {
    val baseDir: String = url.substringBefore('?').substringBeforeLast('/') + "/"
    val last = ConcurrentHashMap<String, String>()

    @Volatile var live = false
    @Volatile var repeats = 0
    @Volatile var waits = 0
    @Volatile var timeline: DashTimeline? = null
    @Volatile var clockOffsetMs = 0L
    @Volatile var manifestAt = 0L
    val manifestLock = Any()

    fun serverNow(): Long = System.currentTimeMillis() + clockOffsetMs
}

internal object DashProxyHandler {
    private val dashEntries = ConcurrentHashMap<String, DashEntry>()
    private const val DASH_PUBLISH_WAIT_MS = 6_000L
    private val TIMED_SEGMENT_REGEX = Regex("""\d{6,}(\.[A-Za-z0-9]+)$""")

    fun clear() {
        dashEntries.clear()
    }

    fun isDashLive(address: String): Boolean {
        val id = address.substringAfter("/dash/", "").substringBefore("/")
        return dashEntries[id]?.live == true
    }

    fun buildDashProxyUrl(port: Int, sessionId: String, url: String): String {
        if (dashEntries.size > 50) dashEntries.clear()
        val id = Integer.toHexString(url.hashCode()) + "-" + System.nanoTime().toString(16)
        val name = url.substringBefore('?').substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifEmpty { "manifest.mpd" }
        dashEntries[id] = DashEntry(url, sessionId, name)
        return "http://127.0.0.1:$port/dash/$id/$name"
    }

    private fun learnTimeline(entry: DashEntry, bytes: ByteArray, date: String?) {
        entry.manifestAt = System.currentTimeMillis()
        date?.let { d ->
            runCatching {
                ZonedDateTime.parse(d, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }.getOrNull()
        }?.let { entry.clockOffsetMs = it - System.currentTimeMillis() }

        val parsed = DashTimeline.parse(String(bytes, Charsets.UTF_8))
        if (parsed != null) {
            entry.timeline = parsed
        }
    }

    private suspend fun refreshManifest(
        entry: DashEntry,
        session: LocalStreamProxy.ProxySession,
        getClient: (LocalStreamProxy.ProxySession?) -> OkHttpClient,
    ) {
        withContext(Dispatchers.IO) {
            synchronized(entry.manifestLock) {
                if (System.currentTimeMillis() - entry.manifestAt < 1000L) return@withContext
                runCatching {
                    val builder = Request.Builder().url(entry.url)
                    val mergedHeaders = session.headers.toMutableMap()
                    mergedHeaders.remove("Host")
                    mergedHeaders.remove("Range")
                    mergedHeaders.remove("Content-Length")
                    mergedHeaders.forEach { (k, v) -> builder.safeHeader(k, v) }
                    builder.cacheControl(CacheControl.FORCE_NETWORK)
                    val client = getClient(session)
                    val response = client.newCall(builder.build()).execute()
                    response.use { r ->
                        if (r.isSuccessful) {
                            val bodyBytes = r.body?.bytes()
                            if (bodyBytes != null) {
                                learnTimeline(entry, bodyBytes, r.header("Date"))
                            }
                        } else {
                            entry.manifestAt = System.currentTimeMillis()
                        }
                    }
                }.onFailure {
                    entry.manifestAt = System.currentTimeMillis()
                }
            }
        }
    }

    private suspend fun waitForListing(
        entry: DashEntry,
        session: LocalStreamProxy.ProxySession,
        trackId: String,
        value: Long,
        address: String,
        getClient: (LocalStreamProxy.ProxySession?) -> OkHttpClient,
    ) {
        fun track() = entry.timeline?.tracks?.firstOrNull { it.id == trackId }
        val first = track() ?: return
        if (value <= first.newest(entry.serverNow())) return

        val started = System.currentTimeMillis()
        val deadline = started + (3 * first.segmentMs + 4000L).coerceIn(6000L, 12_000L)
        while (System.currentTimeMillis() < deadline) {
            refreshManifest(entry, session, getClient)
            val t = track() ?: return
            if (value <= t.newest(entry.serverNow())) break
            kotlinx.coroutines.delay(250)
        }
        if (entry.waits++ % 50 == 0) {
            AppLogger.i("Proxy:LocalStream", "Live DASH segment held until listed (${entry.waits}): ${System.currentTimeMillis() - started}ms for ${address.takeLast(60)}")
        }
    }

    suspend fun handleDashRequest(
        call: ApplicationCall,
        sessions: Map<String, LocalStreamProxy.ProxySession>,
        getClient: (LocalStreamProxy.ProxySession?) -> OkHttpClient,
    ) {
        try {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }
            val entry = dashEntries[id]
            if (entry == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }
            val session = sessions[entry.sessionId]
            if (session == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val rest = call.parameters.getAll("name")?.joinToString("/") ?: ""
            val isManifest = rest == entry.name
            val rawQuery = call.request.queryParameters.let { qp ->
                qp.names().joinToString("&") { name ->
                    qp.getAll(name)?.joinToString("&") { value -> "$name=$value" } ?: name
                }
            }
            val querySuffix = if (rawQuery.isNotBlank()) "?$rawQuery" else ""
            val url = if (isManifest) entry.url else entry.baseDir + rest + querySuffix

            val relativeWithQuery = rest + querySuffix
            val matched = if (!isManifest && entry.live) entry.timeline?.match(relativeWithQuery) else null
            if (matched != null) {
                waitForListing(entry, session, matched.first.id, matched.second, relativeWithQuery, getClient)
            }

            // Deduplication check: live segments that were sent just now must not be repeated
            val key = if (matched != null) {
                "track:" + matched.first.id
            } else if (!isManifest && entry.live) {
                rest.replace(TIMED_SEGMENT_REGEX, "$1").takeIf { it != rest }
            } else {
                null
            }
            if (key != null && entry.last[key] == rest) {
                if (entry.repeats++ % 25 == 0) {
                    AppLogger.i("Proxy:LocalStream", "Live DASH segment requested again, refusing with 404 (${entry.repeats}): ${rest.takeLast(60)}")
                }
                kotlinx.coroutines.delay(250)
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val client = getClient(session)
            val mergedHeaders = session.headers.toMutableMap()
            mergedHeaders.remove("Host")
            mergedHeaders.remove("Content-Length")

            val rangeHeader = call.request.headers["Range"]

            val waitUntil = System.currentTimeMillis() + DASH_PUBLISH_WAIT_MS
            var attempt = 0
            var finalResponse: Response? = null

            while (attempt < 3) {
                attempt++
                val reqBuilder = Request.Builder().url(url)
                mergedHeaders.forEach { (k, v) -> reqBuilder.safeHeader(k, v) }
                if (rangeHeader != null) reqBuilder.safeHeader("Range", rangeHeader)
                if (attempt > 1) reqBuilder.safeHeader("Connection", "close")
                if (call.request.httpMethod.value.equals("HEAD", ignoreCase = true)) reqBuilder.head()

                val resp = try {
                    client.newCall(reqBuilder.build()).await()
                } catch (e: Exception) {
                    if (attempt >= 3) throw e
                    kotlinx.coroutines.delay(200L * attempt)
                    continue
                }

                if (resp.code == 404 && key != null && System.currentTimeMillis() < waitUntil) {
                    resp.body?.close()
                    attempt--
                    if (entry.waits++ % 50 == 0) {
                        AppLogger.i("Proxy:LocalStream", "Live DASH segment not published yet, waiting (${entry.waits}): ${rest.takeLast(60)}")
                    }
                    kotlinx.coroutines.delay(400)
                    continue
                }

                finalResponse = resp
                break
            }

            if (finalResponse == null || !finalResponse.isSuccessful) {
                val code = finalResponse?.code ?: 500
                finalResponse?.body?.close()
                call.respond(HttpStatusCode.fromValue(code))
                return
            }

            val response = finalResponse
            if (isManifest) {
                val bytes = withContext(Dispatchers.IO) {
                    response.body?.bytes() ?: ByteArray(0)
                }
                response.body?.close()

                if (!entry.live && Regex("""<MPD\b[^>]*\btype\s*=\s*["']dynamic["']""").containsMatchIn(String(bytes, 0, minOf(bytes.size, 4096), Charsets.UTF_8))) {
                    entry.live = true
                    AppLogger.i("Proxy:LocalStream", "Dynamic live DASH manifest detected: duplicate segment filtering active")
                }
                if (entry.live) {
                    learnTimeline(entry, bytes, response.header("Date"))
                }

                call.response.header("Content-Type", response.header("Content-Type") ?: "application/dash+xml")
                call.respondBytes(bytes, status = HttpStatusCode.OK)
                return
            }

            response.header("Content-Type")?.let { call.response.header("Content-Type", it) }
            response.header("Content-Range")?.let { call.response.header("Content-Range", it) }
            response.header("Accept-Ranges")?.let { call.response.header("Accept-Ranges", it) }

            val cl = response.body?.contentLength() ?: -1L
            if (call.request.httpMethod.value.equals("HEAD", ignoreCase = true)) {
                call.response.header("Content-Length", maxOf(cl, 0).toString())
                response.body?.close()
                call.respond(HttpStatusCode.fromValue(response.code))
                return
            }

            val parsedContentType = try {
                ContentType.parse(response.header("Content-Type") ?: "video/mp4")
            } catch (_: Exception) {
                ContentType.Application.OctetStream
            }

            var sentBytes = 0L
            try {
                call.respondBytesWriter(
                    contentType = parsedContentType,
                    status = HttpStatusCode.fromValue(response.code),
                    contentLength = if (cl >= 0) cl else null,
                ) {
                    val ktorChannel = this
                    val streamSource = response.body?.source() ?: return@respondBytesWriter
                    val buffer = ByteArray(65536)

                    withContext(Dispatchers.IO) {
                        while (true) {
                            val read = try {
                                streamSource.read(buffer)
                            } catch (e: Exception) {
                                break
                            }
                            if (read == -1) break
                            try {
                                ktorChannel.writeFully(buffer, 0, read)
                                ktorChannel.flush()
                                sentBytes += read
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }
                    try {
                        ktorChannel.flush()
                    } catch (_: Exception) {}
                }
            } finally {
                withContext(Dispatchers.IO) {
                    try {
                        response.body?.close()
                    } catch (_: Exception) {}
                }
            }

            if (key != null && (cl < 0 || sentBytes == cl)) {
                entry.last[key] = rest
            }
        } catch (e: Exception) {
            val isChannelClosed = e is io.ktor.utils.io.ClosedWriteChannelException ||
                e.cause is io.ktor.utils.io.ClosedWriteChannelException ||
                (e is java.io.IOException && e.message?.contains("Broken pipe", ignoreCase = true) == true)
            if (isChannelClosed) {
                AppLogger.d("Proxy:LocalStream", "Client disconnected from DASH stream")
            } else {
                AppLogger.e("Proxy:LocalStream", "DASH proxy error: ${e.message}", e)
                try {
                    call.respond(HttpStatusCode.InternalServerError)
                } catch (_: Exception) {}
            }
        }
    }
}
