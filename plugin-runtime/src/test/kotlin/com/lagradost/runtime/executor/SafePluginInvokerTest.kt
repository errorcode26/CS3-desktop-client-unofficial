package com.lagradost.runtime.executor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class SafePluginInvokerTest {

    @Test
    fun testSuccessfulExecution() = runTest {
        val result = SafePluginInvoker.invoke(timeoutMs = 1000L) {
            "hello from plugin"
        }
        assertTrue(result.isSuccess)
        assertEquals("hello from plugin", result.getOrNull())
    }

    @Test
    fun testTimeoutHandling() = runTest {
        val result = SafePluginInvoker.invoke(timeoutMs = 100L) {
            delay(500L)
            "should not reach"
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TimeoutException)
    }

    @Test
    fun testThrowableCatching() = runTest {
        val result = SafePluginInvoker.invoke<String>(timeoutMs = 1000L) {
            throw NoClassDefFoundError("android/widget/Toast")
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoClassDefFoundError)
    }

    @Test
    fun testInvokeOrNull() = runTest {
        val success = SafePluginInvoker.invokeOrNull(timeoutMs = 1000L) { 42 }
        assertEquals(42, success)

        val failure = SafePluginInvoker.invokeOrNull<Int>(timeoutMs = 1000L) {
            throw RuntimeException("Boom")
        }
        assertNull(failure)
    }

    @Test
    fun testWrapCallbackSuppressesError() {
        val called = AtomicBoolean(false)
        val safeCb = SafePluginInvoker.wrapCallback<String> { item ->
            called.set(true)
            throw IllegalStateException("UI glitch in callback")
        }

        // Should not throw
        assertDoesNotThrow {
            safeCb("test")
        }
        assertTrue(called.get())
    }
}
