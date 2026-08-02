package com.lagradost.common.logging

import org.slf4j.LoggerFactory

/**
 * Centralized logging utility for CloudStream Desktop.
 * Wraps SLF4J to provide an API similar to android.util.Log,
 * and simultaneously pipes logs into [LogBuffer] for live Dev Studio LogCat.
 */
object AppLogger {
    private val logger = LoggerFactory.getLogger("CloudStreamDesktop")

    @JvmOverloads
    fun v(tag: String, message: String, t: Throwable? = null) {
        if (t != null) logger.trace("[$tag] $message", t) else logger.trace("[$tag] $message")
        LogBuffer.record(LogLevel.VERBOSE, tag, message, t)
    }

    @JvmOverloads
    fun v(message: String, t: Throwable? = null) {
        if (t != null) logger.trace(message, t) else logger.trace(message)
        LogBuffer.record(LogLevel.VERBOSE, "General", message, t)
    }

    @JvmOverloads
    fun d(tag: String, message: String, t: Throwable? = null) {
        if (t != null) logger.debug("[$tag] $message", t) else logger.debug("[$tag] $message")
        LogBuffer.record(LogLevel.DEBUG, tag, message, t)
    }

    @JvmOverloads
    fun d(message: String, t: Throwable? = null) {
        if (t != null) logger.debug(message, t) else logger.debug(message)
        LogBuffer.record(LogLevel.DEBUG, "General", message, t)
    }

    @JvmOverloads
    fun i(tag: String, message: String, t: Throwable? = null) {
        if (t != null) logger.info("[$tag] $message", t) else logger.info("[$tag] $message")
        LogBuffer.record(LogLevel.INFO, tag, message, t)
    }

    @JvmOverloads
    fun i(message: String, t: Throwable? = null) {
        if (t != null) logger.info(message, t) else logger.info(message)
        LogBuffer.record(LogLevel.INFO, "General", message, t)
    }

    private fun isIgnorableException(t: Throwable?): Boolean {
        if (t == null) return false
        val name = t::class.qualifiedName ?: ""
        if (name.contains("CancellationException", ignoreCase = true)) return true
        if (name.contains("ForgottenCoroutineScopeException", ignoreCase = true)) return true
        if (t.message?.contains("StandaloneCoroutine was cancelled", ignoreCase = true) == true) return true
        return false
    }

    @JvmOverloads
    fun w(tag: String, message: String, t: Throwable? = null) {
        if (isIgnorableException(t)) return
        if (t != null) logger.warn("[$tag] $message", t) else logger.warn("[$tag] $message")
        LogBuffer.record(LogLevel.WARN, tag, message, t)
    }

    @JvmOverloads
    fun w(message: String, t: Throwable? = null) {
        if (isIgnorableException(t)) return
        if (t != null) logger.warn(message, t) else logger.warn(message)
        LogBuffer.record(LogLevel.WARN, "General", message, t)
    }

    @JvmOverloads
    fun e(tag: String, message: String, t: Throwable? = null) {
        if (isIgnorableException(t)) return
        if (t != null) logger.error("[$tag] $message", t) else logger.error("[$tag] $message")
        LogBuffer.record(LogLevel.ERROR, tag, message, t)
    }

    @JvmOverloads
    fun e(message: String, t: Throwable? = null) {
        if (isIgnorableException(t)) return
        if (t != null) logger.error(message, t) else logger.error(message)
        LogBuffer.record(LogLevel.ERROR, "General", message, t)
    }
}
