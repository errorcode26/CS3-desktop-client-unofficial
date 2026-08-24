package com.lagradost.common.logging

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe ring buffer and reactive log dispatcher for CloudStream Dev Studio.
 * Maintains an in-memory window of recent logs with zero memory leaks.
 */
object LogBuffer {
    const val DEFAULT_MAX_CAPACITY: Int = 5000

    private val idCounter = AtomicLong(1)
    private val buffer = ConcurrentLinkedDeque<LogEntry>()

    private val _logFlow = MutableSharedFlow<LogEntry>(
        replay = 0,
        extraBufferCapacity = 500,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val logFlow: SharedFlow<LogEntry> = _logFlow.asSharedFlow()

    private val SENSITIVE_PARAM_REGEX = Regex(
        "(?i)(token|password|secret|auth|apikey|api_key|access_token|authorization)=([^&\\s\"',]+)",
    )
    private val AUTH_HEADER_REGEX = Regex(
        "(?i)(Authorization:\\s*(?:Bearer|Basic)\\s+)([^\\s\\r\\n]+)",
    )

    fun sanitize(text: String): String {
        var sanitized = text
        sanitized = SENSITIVE_PARAM_REGEX.replace(sanitized) { matchResult ->
            "${matchResult.groupValues[1]}=***MASKED***"
        }
        sanitized = AUTH_HEADER_REGEX.replace(sanitized) { matchResult ->
            "${matchResult.groupValues[1]}***MASKED***"
        }
        return sanitized
    }

    fun record(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        threadName: String = Thread.currentThread().name,
    ): LogEntry {
        val sanitizedMsg = sanitize(message)
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = sanitizedMsg,
            throwable = throwable,
            threadName = threadName,
        )

        buffer.addLast(entry)
        while (buffer.size > DEFAULT_MAX_CAPACITY) {
            buffer.pollFirst()
        }

        _logFlow.tryEmit(entry)
        return entry
    }

    fun getSnapshot(): List<LogEntry> {
        return buffer.toList()
    }

    fun clear() {
        buffer.clear()
    }

    fun filter(
        snapshot: List<LogEntry>,
        minLevel: LogLevel = LogLevel.VERBOSE,
        subsystem: LogSubsystem = LogSubsystem.ALL,
        pluginFilter: String? = null,
        query: String? = null,
        exceptionsOnly: Boolean = false,
    ): List<LogEntry> {
        val cleanQuery = query?.trim()?.lowercase()
        return snapshot.filter { entry ->
            if (exceptionsOnly && entry.throwable == null && entry.level != LogLevel.ERROR) return@filter false
            if (!entry.level.isAtLeast(minLevel)) return@filter false
            if (subsystem != LogSubsystem.ALL && entry.subsystem != subsystem) return@filter false
            if (!pluginFilter.isNullOrBlank() && entry.pluginName != pluginFilter) return@filter false
            if (!cleanQuery.isNullOrBlank()) {
                val matches = entry.tag.lowercase().contains(cleanQuery) ||
                    entry.message.lowercase().contains(cleanQuery) ||
                    entry.threadName.lowercase().contains(cleanQuery) ||
                    (entry.throwable?.message?.lowercase()?.contains(cleanQuery) == true)
                if (!matches) return@filter false
            }
            true
        }
    }

    /**
     * Builds a structured, sanitized AI diagnostic context snapshot.
     * Extracts the target error or latest logs plus preceding context,
     * formatted as markdown without leaking private, regional, or telecom info.
     */
    fun buildAiDebugSnapshot(targetEntryId: Long? = null, precedingCount: Int = 20): String {
        val all = getSnapshot()
        if (all.isEmpty()) return "No logs recorded in current session."

        val targetIndex = if (targetEntryId != null) {
            all.indexOfFirst { it.id == targetEntryId }.takeIf { it >= 0 } ?: (all.size - 1)
        } else {
            // Find latest error or use the last entry
            all.indexOfLast { it.level == LogLevel.ERROR }.takeIf { it >= 0 } ?: (all.size - 1)
        }

        val startIndex = (targetIndex - precedingCount).coerceAtLeast(0)
        val contextEntries = all.subList(startIndex, (targetIndex + 1).coerceAtMost(all.size))
        val targetEntry = all[targetIndex]

        return buildString {
            appendLine("### CloudStream Diagnostics & AI Debug Snapshot")
            appendLine("- **Timestamp (UTC):** ${targetEntry.formattedTime}")
            appendLine("- **Focus Level:** ${targetEntry.level.name}")
            appendLine("- **Subsystem:** ${targetEntry.subsystem.displayName}")
            appendLine("- **Thread:** `${targetEntry.threadName}`")
            appendLine("- **Tag:** `${targetEntry.tag}`")
            appendLine("- **Message:** `${targetEntry.message}`")
            appendLine()

            if (targetEntry.throwable != null) {
                appendLine("#### Exception Stack Trace:")
                appendLine("```text")
                appendLine(targetEntry.stackTraceString ?: targetEntry.throwable.toString())
                appendLine("```")
                appendLine()
            }

            appendLine("#### Contextual Log Stream (Preceding ${contextEntries.size} events):")
            appendLine("```text")
            for (entry in contextEntries) {
                val marker = if (entry.id == targetEntry.id) ">>" else "  "
                appendLine("$marker [${entry.formattedTime}] [${entry.level.shortLabel}] [${entry.tag}] [${entry.threadName}] ${entry.message}")
            }
            appendLine("```")
        }
    }

    /**
     * Exports entries as clean text lines.
     */
    fun exportLogsAsText(entries: List<LogEntry>): String {
        return buildString {
            for (entry in entries) {
                appendLine("[${entry.formattedTime}] [${entry.level.shortLabel}] [${entry.tag}] [${entry.threadName}] ${entry.message}")
                if (entry.throwable != null) {
                    appendLine(entry.stackTraceString)
                }
            }
        }
    }
}
