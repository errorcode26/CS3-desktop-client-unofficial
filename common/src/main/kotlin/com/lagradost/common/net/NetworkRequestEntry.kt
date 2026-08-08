package com.lagradost.common.net

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Structured HTTP network traffic event for the DevStudio Network Inspector.
 */
data class NetworkRequestEntry(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val statusCode: Int = -1,
    val statusMessage: String = "Pending",
    val durationMs: Long = 0L,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val responseBody: String? = null,
    val responseSize: Long = 0L,
    val contentType: String? = null,
    val error: String? = null,
) {
    val formattedTime: String by lazy {
        TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC))
    }

    val isPending: Boolean get() = statusCode == -1 && error == null
    val isSuccess: Boolean get() = statusCode in 200..299
    val isRedirect: Boolean get() = statusCode in 300..399
    val isClientError: Boolean get() = statusCode in 400..499
    val isServerError: Boolean get() = statusCode in 500..599 || (statusCode != -1 && statusCode !in 200..499)
    val isError: Boolean get() = error != null || isClientError || isServerError

    val formattedSize: String get() {
        if (responseSize <= 0) return "0 B"
        val kb = responseSize / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.1f MB".format(mb)
    }

    fun toCurlCommand(): String {
        val sb = StringBuilder("curl")
        sb.append(" -X ").append(method.uppercase())
        sb.append(" \"").append(url).append("\"")

        for ((k, v) in requestHeaders) {
            sb.append(" -H \"").append(k).append(": ").append(v.replace("\"", "\\\"")).append("\"")
        }

        if (!requestBody.isNullOrBlank()) {
            sb.append(" --data \"").append(requestBody.replace("\"", "\\\"")).append("\"")
        }

        return sb.toString()
    }

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
