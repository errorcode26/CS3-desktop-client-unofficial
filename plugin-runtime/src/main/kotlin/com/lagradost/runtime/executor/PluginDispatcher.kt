package com.lagradost.runtime.executor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dedicated thread pool and coroutine dispatcher for CloudStream plugin execution.
 * Isolates scraper I/O and CPU workload from the main application's Dispatchers.IO.
 */
object PluginDispatcherProvider {
    private val threadIndex = AtomicInteger(1)

    private val threadCount: Int = maxOf(8, minOf(32, Runtime.getRuntime().availableProcessors() * 2))

    private val executor = Executors.newFixedThreadPool(threadCount) { runnable ->
        Thread(runnable, "plugin-worker-${threadIndex.getAndIncrement()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1 // Slightly lower priority than UI/audio threads
        }
    }

    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    val supervisorScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}

val PluginDispatcher: CoroutineDispatcher get() = PluginDispatcherProvider.dispatcher
val PluginSupervisorScope: CoroutineScope get() = PluginDispatcherProvider.supervisorScope
