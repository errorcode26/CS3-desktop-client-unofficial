package com.lagradost.runtime.executor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginCircuitBreakerTest {

    @BeforeEach
    fun setup() {
        PluginCircuitBreaker.resetAll()
    }

    @Test
    fun testCircuitTrippingOnConsecutiveFailures() = runTest {
        val provider = "TestScraper"

        // 3 failures -> DEGRADED
        repeat(3) {
            val res = SafePluginInvoker.invoke<String>(
                tag = "Search:$provider",
                timeoutMs = 500L,
                failureThreshold = 4
            ) {
                throw RuntimeException("Network 500")
            }
            assertTrue(res.isFailure)
        }

        val statsDegraded = PluginCircuitBreaker.getStats(provider)
        assertNotNull(statsDegraded)
        assertEquals(3, statsDegraded?.consecutiveFailures)
        assertEquals(PluginHealthStatus.DEGRADED, statsDegraded?.status)

        // 4th failure -> TRIPPED_AUTO_DISABLED
        val res4 = SafePluginInvoker.invoke<String>(
            tag = "Search:$provider",
            timeoutMs = 500L,
            failureThreshold = 4
        ) {
            throw RuntimeException("Network 500 again")
        }
        assertTrue(res4.isFailure)

        val statsTripped = PluginCircuitBreaker.getStats(provider)
        assertNotNull(statsTripped)
        assertEquals(4, statsTripped?.consecutiveFailures)
        assertEquals(PluginHealthStatus.TRIPPED_AUTO_DISABLED, statsTripped?.status)
    }

    @Test
    fun testFastFailWhenCircuitIsOpen() = runTest {
        val provider = "DeadProvider"

        // Trip the circuit manually or with 4 failures
        repeat(4) {
            PluginCircuitBreaker.recordFailure(provider, "Site offline", failureThreshold = 4)
        }
        assertEquals(PluginHealthStatus.TRIPPED_AUTO_DISABLED, PluginCircuitBreaker.getStats(provider)?.status)

        var executed = false
        val result = SafePluginInvoker.invoke<String>(
            tag = "Search:$provider",
            timeoutMs = 5000L
        ) {
            executed = true
            "should not execute"
        }

        // Fast-failed without executing block
        assertFalse(executed)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PluginCircuitOpenException)
    }

    @Test
    fun testRecoveryAfterCooldown() = runTest {
        val provider = "RecoveringProvider"

        repeat(4) {
            PluginCircuitBreaker.recordFailure(provider, "Site offline", failureThreshold = 4)
        }

        // Test with 0ms cooldown to simulate elapsed cooldown
        val (allowed, _) = PluginCircuitBreaker.isExecutionAllowed(provider, cooldownMs = 0L)
        assertTrue(allowed)
        assertEquals(PluginHealthStatus.HALF_OPEN, PluginCircuitBreaker.getStats(provider)?.status)

        // Successful execution restores HEALTHY state
        val res = SafePluginInvoker.invoke<String>(
            tag = "Search:$provider",
            timeoutMs = 1000L
        ) {
            "recovered data"
        }
        assertTrue(res.isSuccess)
        assertEquals("recovered data", res.getOrNull())

        val statsHealthy = PluginCircuitBreaker.getStats(provider)
        assertEquals(PluginHealthStatus.HEALTHY, statsHealthy?.status)
        assertEquals(0, statsHealthy?.consecutiveFailures)
    }

    @Test
    fun testManualReset() {
        val provider = "ManualResetProvider"
        repeat(4) {
            PluginCircuitBreaker.recordFailure(provider, "500 Error", failureThreshold = 4)
        }
        assertEquals(PluginHealthStatus.TRIPPED_AUTO_DISABLED, PluginCircuitBreaker.getStats(provider)?.status)

        PluginCircuitBreaker.resetProvider(provider)
        assertEquals(PluginHealthStatus.HEALTHY, PluginCircuitBreaker.getStats(provider)?.status)
        assertEquals(0, PluginCircuitBreaker.getStats(provider)?.consecutiveFailures)
    }

    @Test
    fun testLatencyTracking() = runTest {
        val provider = "LatencyProvider"

        SafePluginInvoker.invoke<String>(tag = "Search:$provider", timeoutMs = 1000L) {
            delay(50L)
            "done"
        }

        val stats = PluginCircuitBreaker.getStats(provider)
        assertNotNull(stats)
        assertEquals(1, stats?.successfulCalls)
        assertTrue((stats?.averageLatencyMs ?: 0L) >= 40L)
    }
}
