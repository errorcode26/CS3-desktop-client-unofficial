package com.lagradost.common.net

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe ring buffer and reactive dispatcher for HTTP network requests in DevStudio.
 */
object NetworkTrafficBuffer {
    const val DEFAULT_MAX_CAPACITY: Int = 500

    private val idCounter = AtomicLong(1)
    private val buffer = ConcurrentLinkedDeque<NetworkRequestEntry>()
    private val activeRequests = ConcurrentHashMap<Long, NetworkRequestEntry>()

    private val _networkFlow = MutableSharedFlow<NetworkRequestEntry>(
        replay = 0,
        extraBufferCapacity = 200,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val networkFlow: SharedFlow<NetworkRequestEntry> = _networkFlow.asSharedFlow()

    fun recordStart(
        method: String,
        url: String,
        host: String,
        path: String,
        requestHeaders: Map<String, String>,
        requestBody: String? = null,
    ): Long {
        val id = idCounter.getAndIncrement()
        val entry = NetworkRequestEntry(
            id = id,
            timestamp = System.currentTimeMillis(),
            method = method,
            url = url,
            host = host,
            path = path,
            requestHeaders = requestHeaders,
            requestBody = requestBody,
        )

        activeRequests[id] = entry
        buffer.addLast(entry)
        while (buffer.size > DEFAULT_MAX_CAPACITY) {
            buffer.pollFirst()
        }

        _networkFlow.tryEmit(entry)
        return id
    }

    fun recordComplete(
        id: Long,
        statusCode: Int,
        statusMessage: String,
        durationMs: Long,
        responseHeaders: Map<String, String>,
        responseBody: String? = null,
        responseSize: Long = 0L,
        contentType: String? = null,
        error: String? = null,
    ) {
        val original = activeRequests.remove(id)
        val updated = (
            original ?: NetworkRequestEntry(
                id = id,
                method = "GET",
                url = "",
                host = "",
                path = "",
            )
            ).copy(
            statusCode = statusCode,
            statusMessage = statusMessage,
            durationMs = durationMs,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
            responseSize = responseSize,
            contentType = contentType,
            error = error,
        )

        // Replace in buffer
        val list = buffer.toList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            buffer.clear()
            for (i in list.indices) {
                if (i == index) {
                    buffer.addLast(updated)
                } else {
                    buffer.addLast(list[i])
                }
            }
        } else {
            buffer.addLast(updated)
            while (buffer.size > DEFAULT_MAX_CAPACITY) {
                buffer.pollFirst()
            }
        }

        _networkFlow.tryEmit(updated)
    }

    fun getSnapshot(): List<NetworkRequestEntry> {
        return buffer.toList()
    }

    fun clear() {
        buffer.clear()
        activeRequests.clear()
    }

    fun filter(
        snapshot: List<NetworkRequestEntry>,
        query: String? = null,
        methodFilter: String? = null,
        errorsOnly: Boolean = false,
    ): List<NetworkRequestEntry> {
        val cleanQuery = query?.trim()?.lowercase()
        return snapshot.filter { entry ->
            if (errorsOnly && !entry.isError) return@filter false
            if (!methodFilter.isNullOrBlank() && !entry.method.equals(methodFilter, ignoreCase = true)) return@filter false
            if (!cleanQuery.isNullOrBlank()) {
                val matches = entry.url.lowercase().contains(cleanQuery) ||
                    entry.host.lowercase().contains(cleanQuery) ||
                    entry.path.lowercase().contains(cleanQuery) ||
                    entry.statusCode.toString().contains(cleanQuery) ||
                    entry.method.lowercase().contains(cleanQuery) ||
                    (entry.error?.lowercase()?.contains(cleanQuery) == true)
                if (!matches) return@filter false
            }
            true
        }
    }

    fun exportAsText(entries: List<NetworkRequestEntry>): String {
        return buildString {
            for (entry in entries) {
                appendLine("[${entry.formattedTime}] [${entry.method}] [${entry.statusCode}] ${entry.durationMs}ms - ${entry.url}")
                if (entry.error != null) {
                    appendLine("  Error: ${entry.error}")
                }
            }
        }
    }
}
