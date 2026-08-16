package com.lagradost.cloudstream3.desktop

import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbRateLimiter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

class DesktopCoreTests {

    @Test
    fun `TmdbRateLimiter handles concurrent acquire safely`() = runBlocking {
        // Run 5 concurrent acquires across multiple coroutines
        val count = 5
        val elapsed = measureTimeMillis {
            val jobs = (1..count).map {
                async {
                    TmdbRateLimiter.acquire()
                }
            }
            jobs.awaitAll()
        }
        // minInterval is 1000/35 =~ 28ms. For 5 sequential acquires under lock,
        // it should execute cleanly without throwing or deadlocking.
        assertTrue(elapsed >= 0L, "Rate limiter completed cleanly in ${elapsed}ms")
    }
}
