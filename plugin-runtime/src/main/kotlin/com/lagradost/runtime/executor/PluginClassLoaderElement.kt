package com.lagradost.runtime.executor

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A [ThreadContextElement] that installs a specific [ClassLoader] as the
 * thread context classloader for every thread-slice this coroutine runs on.
 *
 * Kotlin coroutines can resume on different threads after each suspend point.
 * A plain `Thread.currentThread().contextClassLoader = x` only affects the
 * initial thread. This element ensures the swap is re-applied (and restored)
 * on EVERY thread the coroutine touches — solving kotlin-reflect failures
 * that occur when Jackson deserializes plugin inner classes across classloader
 * boundaries after an async OkHttp callback resumes the coroutine.
 */
class PluginClassLoaderElement(
    private val classLoader: ClassLoader,
) : ThreadContextElement<ClassLoader> {

    companion object Key : CoroutineContext.Key<PluginClassLoaderElement>

    override val key: CoroutineContext.Key<*> = Key

    override fun updateThreadContext(context: CoroutineContext): ClassLoader {
        val prev = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoader
        return prev
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: ClassLoader) {
        Thread.currentThread().contextClassLoader = oldState
    }
}
