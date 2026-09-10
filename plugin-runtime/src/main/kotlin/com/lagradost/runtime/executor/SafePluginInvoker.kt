package com.lagradost.runtime.executor

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

sealed interface PluginCallResult<out T> {
    data class Success<T>(val data: T, val latencyMs: Long) : PluginCallResult<T>
    data class Timeout(val limitMs: Long, val elapsedMs: Long) : PluginCallResult<Nothing>
    data class CircuitOpen(val provider: String, val reason: String) : PluginCallResult<Nothing>
    data class Failure(val error: Throwable, val message: String, val elapsedMs: Long) : PluginCallResult<Nothing>

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    val isSuccess: Boolean get() = this is Success
}

/**
 * Safe execution boundary for CloudStream plugins and scrapers.
 * Guarantees isolation from UI threads, provides timeout protection with thread interruption,
 * measures latency, integrates with [PluginCircuitBreaker], and traps all Throwables / Errors
 * so a plugin failure or infinite loop cannot crash or stall the host app.
 */
object SafePluginInvoker {

    const val TIMEOUT_SEARCH_MS: Long = 25_000L
    const val TIMEOUT_LOAD_MS: Long = 90_000L

    // Scraping uses callbacks that stream results incrementally — the timeout is a safety cap
    // on the TOTAL call, not an indicator of failure. Links may have already been delivered.
    const val TIMEOUT_SCRAPE_MS: Long = 60_000L
    const val TIMEOUT_DEFAULT_MS: Long = 30_000L

    /**
     * Executes a plugin operation with full typed diagnostic results.
     */
    suspend fun <T> invokeDetailed(
        tag: String = "PluginCall",
        providerName: String? = null,
        timeoutMs: Long = TIMEOUT_DEFAULT_MS,
        failureThreshold: Int = PluginCircuitBreaker.DEFAULT_FAILURE_THRESHOLD,
        penalizeOnTimeout: Boolean = true,
        block: suspend CoroutineScope.() -> T,
    ): PluginCallResult<T> {
        val startMs = System.currentTimeMillis()
        val loggerTag = if (tag.startsWith("SafePluginInvoker:") || tag.startsWith("Plugin:")) tag else "Plugin:$tag"
        val resolvedProvider = providerName ?: extractProviderName(tag)

        if (resolvedProvider != null) {
            val (isAllowed, reason) = PluginCircuitBreaker.isExecutionAllowed(resolvedProvider)
            if (!isAllowed) {
                AppLogger.w(loggerTag, "⚡ Fast-failing: $reason")
                return PluginCallResult.CircuitOpen(resolvedProvider, reason ?: "Circuit OPEN")
            }
        }

        val executingThread = AtomicReference<Thread?>(null)
        val compositeClassLoader = com.lagradost.runtime.loader.ExtensionLoader.createCompositeClassLoader(
            Thread.currentThread().contextClassLoader,
        )
        return try {
            val result = withContext(PluginDispatcher + PluginClassLoaderElement(compositeClassLoader)) {
                executingThread.set(Thread.currentThread())
                try {
                    withTimeout(timeoutMs) {
                        block()
                    }
                } finally {
                    executingThread.set(null)
                }
            }
            val elapsedMs = System.currentTimeMillis() - startMs
            if (resolvedProvider != null) {
                PluginCircuitBreaker.recordSuccess(resolvedProvider, elapsedMs)
            }
            PluginCallResult.Success(result, elapsedMs)
        } catch (e: TimeoutCancellationException) {
            executingThread.get()?.interrupt()
            val elapsedMs = System.currentTimeMillis() - startMs
            if (penalizeOnTimeout && resolvedProvider != null) {
                PluginCircuitBreaker.recordFailure(
                    providerName = resolvedProvider,
                    reason = "Timeout after ${elapsedMs}ms",
                    failureThreshold = failureThreshold,
                )
            }
            PluginCallResult.Timeout(timeoutMs, elapsedMs)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val elapsedMs = System.currentTimeMillis() - startMs
            val reason = t.message ?: t.javaClass.simpleName
            if (resolvedProvider != null) {
                PluginCircuitBreaker.recordFailure(
                    providerName = resolvedProvider,
                    reason = reason,
                    throwable = t,
                    failureThreshold = failureThreshold,
                )
            }
            PluginCallResult.Failure(t, reason, elapsedMs)
        }
    }

    /**
     * Executes a plugin operation on [PluginDispatcher] with timeout, thread interruption,
     * latency tracking, circuit-breaker fast-fail, and error containment.
     * Returns [Result.success] with the value or [Result.failure] on error/timeout.
     * Re-throws active coroutine [CancellationException] to preserve lifecycle cancellation.
     */
    suspend fun <T> invoke(
        tag: String = "PluginCall",
        providerName: String? = null,
        timeoutMs: Long = TIMEOUT_DEFAULT_MS,
        failureThreshold: Int = PluginCircuitBreaker.DEFAULT_FAILURE_THRESHOLD,
        /**
         * When false, a timeout will NOT count as a circuit-breaker failure.
         * Use for streaming scrape calls (loadLinks) where results are delivered
         * incrementally via callbacks — a timeout just means slow/dead extractors,
         * not that the provider itself is broken.
         */
        penalizeOnTimeout: Boolean = true,
        block: suspend CoroutineScope.() -> T,
    ): Result<T> {
        val startMs = System.currentTimeMillis()
        val loggerTag = if (tag.startsWith("SafePluginInvoker:") || tag.startsWith("Plugin:")) tag else "Plugin:$tag"
        val resolvedProvider = providerName ?: extractProviderName(tag)

        // 1. Fast-fail check via PluginCircuitBreaker
        if (resolvedProvider != null) {
            val (isAllowed, reason) = PluginCircuitBreaker.isExecutionAllowed(resolvedProvider)
            if (!isAllowed) {
                val elapsedMs = System.currentTimeMillis() - startMs
                AppLogger.w(loggerTag, "⚡ Fast-failing: $reason")
                return Result.failure(PluginCircuitOpenException(resolvedProvider, reason ?: "Circuit OPEN"))
            }
        }

        AppLogger.d(loggerTag, "Starting execution (timeout: ${timeoutMs}ms)")
        val executingThread = AtomicReference<Thread?>(null)
        val compositeClassLoader = com.lagradost.runtime.loader.ExtensionLoader.createCompositeClassLoader(
            Thread.currentThread().contextClassLoader,
        )

        return try {
            val result = withContext(PluginDispatcher + PluginClassLoaderElement(compositeClassLoader)) {
                executingThread.set(Thread.currentThread())
                try {
                    withTimeout(timeoutMs) {
                        block()
                    }
                } finally {
                    executingThread.set(null)
                }
            }
            val elapsedMs = System.currentTimeMillis() - startMs
            AppLogger.i(loggerTag, "Completed successfully in ${elapsedMs}ms")

            if (resolvedProvider != null) {
                PluginCircuitBreaker.recordSuccess(resolvedProvider, elapsedMs)
            }
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            // Forcibly interrupt worker thread to break infinite synchronous loops or stuck native locks
            executingThread.get()?.interrupt()

            val elapsedMs = System.currentTimeMillis() - startMs
            val msg = "Operation timed out after ${elapsedMs}ms (limit was ${timeoutMs}ms)"

            if (penalizeOnTimeout) {
                AppLogger.w(loggerTag, "Timed out: $msg")
                if (resolvedProvider != null) {
                    PluginCircuitBreaker.recordFailure(
                        providerName = resolvedProvider,
                        reason = "Timeout after ${elapsedMs}ms",
                        failureThreshold = failureThreshold,
                    )
                }
            } else {
                // Not penalizing — this is a streaming scrape and results may have been
                // delivered via callback before the timeout fired (partial success).
                AppLogger.d(loggerTag, "Scrape timed out (no penalty): $msg")
                if (resolvedProvider != null) {
                    // Still record success so latency stats remain healthy
                    PluginCircuitBreaker.recordSuccess(resolvedProvider, elapsedMs)
                }
            }
            Result.failure(TimeoutException(msg))
        } catch (e: CancellationException) {
            // Coroutine lifecycle cancellation (e.g. navigation or query change) — do NOT penalize provider
            val elapsedMs = System.currentTimeMillis() - startMs
            AppLogger.d(loggerTag, "Cancelled after ${elapsedMs}ms")
            throw e
        } catch (t: Throwable) {
            val elapsedMs = System.currentTimeMillis() - startMs
            val reason = t.message ?: t.javaClass.simpleName
            AppLogger.e(loggerTag, "Plugin execution failed after ${elapsedMs}ms: $reason", t)

            if (resolvedProvider != null) {
                PluginCircuitBreaker.recordFailure(
                    providerName = resolvedProvider,
                    reason = reason,
                    throwable = t,
                    failureThreshold = failureThreshold,
                )
            }
            Result.failure(t)
        }
    }

    /**
     * Executes a plugin operation and returns null if it fails, times out, or is circuit-tripped.
     */
    suspend fun <T> invokeOrNull(
        tag: String = "PluginCall",
        providerName: String? = null,
        timeoutMs: Long = TIMEOUT_DEFAULT_MS,
        failureThreshold: Int = PluginCircuitBreaker.DEFAULT_FAILURE_THRESHOLD,
        penalizeOnTimeout: Boolean = true,
        block: suspend CoroutineScope.() -> T,
    ): T? {
        return invoke(tag, providerName, timeoutMs, failureThreshold, penalizeOnTimeout, block).getOrNull()
    }

    /**
     * Wraps a UI callback lambda so that exceptions or invalid invocations from
     * third-party plugin code cannot disrupt the scraping coroutine or crash the app.
     */
    fun <T> wrapCallback(
        tag: String = "Callback",
        callback: (T) -> Unit,
    ): (T) -> Unit {
        val loggerTag = if (tag.startsWith("SafePluginInvoker:") || tag.startsWith("Plugin:")) tag else "Plugin:$tag"
        return { item ->
            try {
                if (item != null) {
                    callback(item)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AppLogger.w(loggerTag, "Exception inside callback: ${t.message ?: t.javaClass.simpleName}", t)
            }
        }
    }

    /**
     * Helper to parse provider name from standard tags (e.g. "Search:Sflix", "HomeCategory:Sflix:Movies", "Plugin:Sflix")
     */
    fun extractProviderName(tag: String): String? {
        val clean = tag.removePrefix("SafePluginInvoker:").removePrefix("Plugin:")
        val parts = clean.split(":")
        return when {
            parts.size >= 2 && (parts[0] == "Search" || parts[0] == "Load" || parts[0] == "Scrape" || parts[0] == "HomeCategory") -> parts[1]
            parts.size == 1 && parts[0].isNotBlank() && parts[0] != "PluginCall" && parts[0] != "Callback" -> parts[0]
            else -> null
        }
    }
}
