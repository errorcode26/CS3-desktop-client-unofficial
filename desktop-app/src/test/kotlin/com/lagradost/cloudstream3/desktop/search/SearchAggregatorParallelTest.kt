package com.lagradost.cloudstream3.desktop.search

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.runtime.executor.PluginCircuitBreaker
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FastSyntheticProvider : MainAPI() {
    override var name = "FastSyntheticProvider"
    override var mainUrl = "https://fast.synthetic.local"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie)

    override suspend fun search(query: String): List<SearchResponse> {
        delay(10L)
        return listOf(
            newMovieSearchResponse("Fast Result 1", "https://fast.synthetic.local/movie1"),
            newMovieSearchResponse("Shared Title", "https://shared.synthetic.local/movie_shared")
        )
    }
}

class FaultySyntheticProvider : MainAPI() {
    override var name = "FaultySyntheticProvider"
    override var mainUrl = "https://faulty.synthetic.local"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie)

    override suspend fun search(query: String): List<SearchResponse> {
        throw IllegalStateException("Simulated scraper runtime crash")
    }
}

class HangingSyntheticProvider : MainAPI() {
    override var name = "HangingSyntheticProvider"
    override var mainUrl = "https://hanging.synthetic.local"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie)

    override suspend fun search(query: String): List<SearchResponse> {
        delay(3000L)
        return listOf(newMovieSearchResponse("Should Never Arrive", "https://hanging.synthetic.local/never"))
    }
}

class SearchAggregatorParallelTest {

    @BeforeEach
    fun setUp() {
        PluginCircuitBreaker.resetAll()
    }

    @AfterEach
    fun tearDown() {
        PluginCircuitBreaker.resetAll()
    }

    @Test
    fun testParallelSearchIsolatesFailuresAndHangs() = runBlocking {
        val fastProvider = FastSyntheticProvider()
        val faultyProvider = FaultySyntheticProvider()
        val hangingProvider = HangingSyntheticProvider()

        val providers = listOf(fastProvider, faultyProvider, hangingProvider)
        val aggregatedResults = ConcurrentHashMap<String, List<SearchResponse>>()

        val startTime = System.currentTimeMillis()

        val jobs = providers.map { p ->
            async(Dispatchers.IO) {
                val results = SafePluginInvoker.invokeOrNull(
                    tag = "Search:${p.name}",
                    providerName = p.name,
                    timeoutMs = 200L,
                ) {
                    p.search("Inception")
                }
                if (!results.isNullOrEmpty()) {
                    aggregatedResults[p.name] = results
                }
            }
        }

        jobs.awaitAll()
        val duration = System.currentTimeMillis() - startTime

        assertTrue(duration < 1500L, "Parallel search must not be blocked by the hanging provider (took ${duration}ms)")
        assertTrue(aggregatedResults.containsKey("FastSyntheticProvider"), "Fast provider results must be collected")
        assertEquals(2, aggregatedResults["FastSyntheticProvider"]?.size)
        assertFalse(aggregatedResults.containsKey("FaultySyntheticProvider"), "Faulty provider must not crash search")
        assertFalse(aggregatedResults.containsKey("HangingSyntheticProvider"), "Hanging provider must be cancelled by timeout")
    }

    @Test
    fun testResultDeduplication() = runBlocking {
        val fast1 = FastSyntheticProvider()
        val duplicateItem = fast1.newMovieSearchResponse("Shared Title", "https://shared.synthetic.local/movie_shared")
        val uniqueItem = fast1.newMovieSearchResponse("Unique Title", "https://fast.synthetic.local/unique")

        val rawList = listOf(duplicateItem, duplicateItem, uniqueItem)
        val deduplicated = rawList.distinctBy { "${it.url}::${it.name}" }

        assertEquals(2, deduplicated.size, "Duplicate items with identical URL and name must be deduplicated")
        assertTrue(deduplicated.any { it.name == "Unique Title" })
        assertTrue(deduplicated.any { it.name == "Shared Title" })
    }

    @Test
    fun testCircuitBreakerTripsAfterConsecutiveFailures() = runBlocking {
        val faultyProvider = FaultySyntheticProvider()

        for (i in 1..PluginCircuitBreaker.DEFAULT_FAILURE_THRESHOLD) {
            SafePluginInvoker.invokeOrNull(
                tag = "Search:${faultyProvider.name}",
                providerName = faultyProvider.name,
                timeoutMs = 100L,
            ) {
                faultyProvider.search("test")
            }
        }

        val (isAllowed, reason) = PluginCircuitBreaker.isExecutionAllowed(faultyProvider.name)
        assertFalse(isAllowed, "Circuit breaker must trip to OPEN after consecutive failures")
        assertNotNull(reason, "Circuit breaker must supply diagnostic reason")
    }
}
