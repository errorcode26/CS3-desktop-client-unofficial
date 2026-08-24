package com.lagradost.cloudstream3.desktop.network

import com.lagradost.common.net.NetworkTrafficBuffer
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Non-intrusive OkHttp Interceptor that streams structured HTTP request and response
 * events to the DevStudio Network Inspector.
 */
class DevNetworkInterceptor : Interceptor {

    private val SENSITIVE_HEADERS = setOf(
        "authorization", "cookie", "set-cookie", "x-api-key", "api-key", "token", "x-auth-token", "proxy-authorization",
    )

    private fun isSensitiveHeader(name: String): Boolean {
        return SENSITIVE_HEADERS.contains(name.lowercase())
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val rawUrl = request.url.toString()
        val url = com.lagradost.common.logging.LogBuffer.sanitize(rawUrl)
        val host = request.url.host
        val path = request.url.encodedPath

        val reqHeadersMap = headersToMap(request.headers)
        val reqBodyString = extractRequestBody(request)?.let { com.lagradost.common.logging.LogBuffer.sanitize(it) }

        val requestId = NetworkTrafficBuffer.recordStart(
            method = method,
            url = url,
            host = host,
            path = path,
            requestHeaders = reqHeadersMap,
            requestBody = reqBodyString,
        )

        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            NetworkTrafficBuffer.recordComplete(
                id = requestId,
                statusCode = -1,
                statusMessage = "Network Failed",
                durationMs = durationMs,
                responseHeaders = emptyMap(),
                responseBody = null,
                responseSize = 0L,
                contentType = null,
                error = e.message ?: e.javaClass.simpleName,
            )
            throw e
        }

        val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val respHeadersMap = headersToMap(response.headers)
        val contentType = response.header("Content-Type")
        val (respBodyString, respSize) = extractResponseBody(response, contentType)

        NetworkTrafficBuffer.recordComplete(
            id = requestId,
            statusCode = response.code,
            statusMessage = response.message.ifBlank { if (response.isSuccessful) "OK" else "HTTP ${response.code}" },
            durationMs = durationMs,
            responseHeaders = respHeadersMap,
            responseBody = respBodyString?.let { com.lagradost.common.logging.LogBuffer.sanitize(it) },
            responseSize = respSize,
            contentType = contentType,
            error = if (!response.isSuccessful) "HTTP ${response.code} ${response.message}" else null,
        )

        return response
    }

    private fun headersToMap(headers: Headers): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val value = if (isSensitiveHeader(name)) "***MASKED***" else headers.value(i)
            map[name] = value
        }
        return map
    }

    private fun extractRequestBody(request: okhttp3.Request): String? {
        val body = request.body ?: return null
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            val str = buffer.readString(charset)
            if (str.length > 50_000) str.take(50_000) + "\n... [truncated]" else str
        } catch (t: Throwable) {
            null
        }
    }

    private fun extractResponseBody(response: Response, contentType: String?): Pair<String?, Long> {
        val body = response.body
        val contentLength = body.contentLength()

        val isTextOrJson = contentType != null && (
            contentType.contains("json", ignoreCase = true) ||
                contentType.contains("text", ignoreCase = true) ||
                contentType.contains("xml", ignoreCase = true) ||
                contentType.contains("javascript", ignoreCase = true) ||
                contentType.contains("mpegurl", ignoreCase = true) ||
                contentType.contains("html", ignoreCase = true)
            )

        if (!isTextOrJson) {
            val size = if (contentLength >= 0) contentLength else 0L
            return Pair("[Binary Stream (${if (size > 0) "$size bytes" else "chunked"})]", size)
        }

        return try {
            val peek = response.peekBody(MAX_PEEK_BYTES)
            val str = peek.string()
            val actualSize = if (contentLength >= 0) contentLength else str.toByteArray().size.toLong()
            val preview = if (str.length > 100_000) str.take(100_000) + "\n... [truncated]" else str
            Pair(preview, actualSize)
        } catch (t: Throwable) {
            Pair(null, if (contentLength >= 0) contentLength else 0L)
        }
    }

    companion object {
        private const val MAX_PEEK_BYTES = 512L * 1024L // 512 KB
    }
}
