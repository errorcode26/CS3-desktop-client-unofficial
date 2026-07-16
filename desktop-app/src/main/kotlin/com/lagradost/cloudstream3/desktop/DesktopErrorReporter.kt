package com.lagradost.cloudstream3.desktop

import com.lagradost.common.logging.AppLogger

object DesktopErrorReporter {
    private val lock = Any()
    private val errors = mutableListOf<String>()

    @Volatile
    var onUpdate: ((Int) -> Unit)? = null

    fun report(title: String, throwable: Throwable? = null) {
        val message = buildString {
            append("[")
            append(System.currentTimeMillis())
            append("] ")
            append(title)
            if (throwable != null) {
                append("\n")
                append(throwable.stackTraceToString())
            }
        }
        synchronized(lock) {
            errors.add(message)
        }
        AppLogger.i(message)
        onUpdate?.invoke(getCount())
    }

    fun getSnapshot(): String {
        synchronized(lock) {
            return if (errors.isEmpty()) {
                "No errors yet."
            } else {
                errors.joinToString("\n\n")
            }
        }
    }

    fun getCount(): Int {
        synchronized(lock) {
            return errors.size
        }
    }
}
