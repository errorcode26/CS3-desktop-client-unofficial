package com.lagradost.common.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Structured log entry for Dev Studio, Live LogCat, and AI diagnostic exports.
 */
data class LogEntry(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val threadName: String = Thread.currentThread().name,
    val subsystem: LogSubsystem = LogSubsystem.fromTag(tag),
    val pluginName: String? = extractPluginName(tag, message),
) {
    val formattedTime: String by lazy {
        TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC))
    }

    val stackTraceString: String? by lazy {
        throwable?.let { t ->
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            sw.toString()
        }
    }

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

        private fun extractPluginName(tag: String, message: String): String? {
            return when {
                tag.startsWith("Plugin:", ignoreCase = true) -> tag.substringAfter(":").trim()
                tag.startsWith("Search:", ignoreCase = true) -> tag.substringAfter(":").trim()
                tag.startsWith("LinksViewModel:", ignoreCase = true) -> tag.substringAfter(":").trim()
                tag.startsWith("EmbeddedPlayerViewModel:", ignoreCase = true) -> tag.substringAfter(":").trim()
                tag.startsWith("DetailsRepo:Load:", ignoreCase = true) -> tag.substringAfter("DetailsRepo:Load:").trim()
                tag.startsWith("DetailsRepo:Search:", ignoreCase = true) -> tag.substringAfter("DetailsRepo:Search:").trim()
                tag.startsWith("SafePluginInvoker:", ignoreCase = true) -> tag.substringAfter(":").substringBefore(":").trim()
                tag.startsWith("Provider:", ignoreCase = true) -> tag.substringAfter(":").trim()
                else -> null
            }?.takeIf { it.isNotBlank() }
        }
    }
}
