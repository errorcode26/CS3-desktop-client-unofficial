package com.lagradost.cloudstream3.desktop.sandbox.worker

import com.lagradost.cloudstream3.desktop.sandbox.protocol.SerializableCookie
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerCallbackRequest
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerCallbackResponse
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerProtocolSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class HostSolveResult(
    val cookies: List<Cookie>,
    val userAgent: String?,
)

data class ProxyFetchResult(
    val statusCode: Int,
    val contentType: String?,
    val body: String?,
    val error: String?,
)

/**
 * Bidirectional IPC bridge enabling the sandboxed worker to request host-level services
 * (e.g. interactive Cloudflare / CAPTCHA clearance via WebView2) over standard anonymous pipes.
 */
object WorkerHostBridge {
    private val logger = LoggerFactory.getLogger(WorkerHostBridge::class.java)
    private val nextCallbackId = AtomicLong(1)
    val pendingCallbacks = ConcurrentHashMap<Long, CompletableDeferred<WorkerCallbackResponse>>()
    @Volatile var rpcWriter: BufferedWriter? = null

    suspend fun requestCloudflareSolve(
        url: String,
        host: String,
        headers: Map<String, String> = emptyMap()
    ): HostSolveResult? {
        val writer = rpcWriter
        if (writer == null) {
            logger.warn("Cannot request Cloudflare solve: RPC writer is not available.")
            return null
        }

        val callbackId = nextCallbackId.getAndIncrement()
        val deferred = CompletableDeferred<WorkerCallbackResponse>()
        pendingCallbacks[callbackId] = deferred

        val cbReq = WorkerCallbackRequest(
            callbackId = callbackId,
            action = WorkerCallbackRequest.ACTION_SOLVE_CLOUDFLARE,
            url = url,
            host = host,
            headers = headers
        )

        try {
            synchronized(writer) {
                val json = WorkerProtocolSerializer.serializeCallbackRequest(cbReq)
                writer.write(json)
                writer.newLine()
                writer.flush()
            }
        } catch (e: Throwable) {
            logger.error("Failed to write callback request to host stdout", e)
            pendingCallbacks.remove(callbackId)
            return null
        }

        val response = withTimeoutOrNull(75_000L) {
            deferred.await()
        }
        pendingCallbacks.remove(callbackId)

        if (response == null || !response.success) {
            logger.warn("Cloudflare solve callback failed or timed out for $host")
            return null
        }

        val okCookies = response.cookies.orEmpty().mapNotNull { sc ->
            try {
                Cookie.Builder()
                    .name(sc.name)
                    .value(sc.value)
                    .domain(sc.domain)
                    .path(sc.path)
                    .apply {
                        if (sc.secure) secure()
                        if (sc.httpOnly) httpOnly()
                        sc.expiresAt?.let { expiresAt(it) }
                    }
                    .build()
            } catch (_: Throwable) {
                null
            }
        }

        return HostSolveResult(
            cookies = okCookies,
            userAgent = response.userAgent
        )
    }

    suspend fun requestProxyFetch(
        url: String,
        host: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): ProxyFetchResult? {
        val writer = rpcWriter
        if (writer == null) {
            logger.warn("Cannot request proxy fetch: RPC writer is not available.")
            return null
        }

        val callbackId = nextCallbackId.getAndIncrement()
        val deferred = CompletableDeferred<WorkerCallbackResponse>()
        pendingCallbacks[callbackId] = deferred

        val cbReq = WorkerCallbackRequest(
            callbackId = callbackId,
            action = WorkerCallbackRequest.ACTION_PROXY_FETCH,
            url = url,
            host = host,
            headers = headers,
            method = method,
            body = body,
        )

        try {
            synchronized(writer) {
                val json = WorkerProtocolSerializer.serializeCallbackRequest(cbReq)
                writer.write(json)
                writer.newLine()
                writer.flush()
            }
        } catch (e: Throwable) {
            logger.error("Failed to write proxy fetch callback request to host stdout", e)
            pendingCallbacks.remove(callbackId)
            return null
        }

        val response = withTimeoutOrNull(20_000L) {
            deferred.await()
        }
        pendingCallbacks.remove(callbackId)

        if (response == null) {
            logger.warn("Proxy fetch callback timed out for $url")
            return null
        }

        return ProxyFetchResult(
            statusCode = response.statusCode ?: if (response.success) 200 else 500,
            contentType = response.contentType,
            body = response.body,
            error = response.error,
        )
    }

    fun emitLink(requestId: Long, link: com.lagradost.cloudstream3.utils.ExtractorLink) {
        val writer = rpcWriter ?: return
        try {
            val linkJson = WorkerProtocolSerializer.mapper.writeValueAsString(link)
            val cbReq = WorkerCallbackRequest(
                callbackId = nextCallbackId.getAndIncrement(),
                action = WorkerCallbackRequest.ACTION_EMIT_LINK,
                requestId = requestId,
                linkJson = linkJson
            )
            val line = WorkerProtocolSerializer.serializeCallbackRequest(cbReq)
            synchronized(writer) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        } catch (e: Throwable) {
            logger.warn("Failed to stream ExtractorLink over RPC: ${e.message}")
        }
    }

    fun emitSubtitle(requestId: Long, subtitle: com.lagradost.cloudstream3.SubtitleFile) {
        val writer = rpcWriter ?: return
        try {
            val subJson = WorkerProtocolSerializer.mapper.writeValueAsString(subtitle)
            val cbReq = WorkerCallbackRequest(
                callbackId = nextCallbackId.getAndIncrement(),
                action = WorkerCallbackRequest.ACTION_EMIT_SUBTITLE,
                requestId = requestId,
                subtitleJson = subJson
            )
            val line = WorkerProtocolSerializer.serializeCallbackRequest(cbReq)
            synchronized(writer) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        } catch (e: Throwable) {
            logger.warn("Failed to stream SubtitleFile over RPC: ${e.message}")
        }
    }

    fun saveSetting(key: String, valueJson: String) {
        val writer = rpcWriter ?: return
        try {
            val cbReq = WorkerCallbackRequest(
                callbackId = nextCallbackId.getAndIncrement(),
                action = WorkerCallbackRequest.ACTION_SAVE_SETTING,
                key = key,
                valueJson = valueJson
            )
            val line = WorkerProtocolSerializer.serializeCallbackRequest(cbReq)
            synchronized(writer) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        } catch (e: Throwable) {
            logger.warn("Failed to stream saveSetting over RPC: ${e.message}")
        }
    }

    fun removeSetting(key: String) {
        val writer = rpcWriter ?: return
        try {
            val cbReq = WorkerCallbackRequest(
                callbackId = nextCallbackId.getAndIncrement(),
                action = WorkerCallbackRequest.ACTION_REMOVE_SETTING,
                key = key
            )
            val line = WorkerProtocolSerializer.serializeCallbackRequest(cbReq)
            synchronized(writer) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        } catch (e: Throwable) {
            logger.warn("Failed to stream removeSetting over RPC: ${e.message}")
        }
    }
}
