package com.lagradost.common.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopDataStoreConcurrencyTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("cloudstream_test_db_").toFile()
        System.setProperty("cloudstream.data.dir", tempDir.absolutePath)
        DesktopDataStore.init()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testReentrantLockSafety() {
        // Verifies that reentrant calls on the same thread execute without self-deadlock
        DesktopDataStore.withDbLock {
            DesktopDataStore.setKey("reentrant_outer", "outer_val")
            DesktopDataStore.withDbLock {
                DesktopDataStore.setKey("reentrant_inner", "inner_val")
                assertEquals("outer_val", DesktopDataStore.getKey<String>("reentrant_outer"))
                assertEquals("inner_val", DesktopDataStore.getKey<String>("reentrant_inner"))
            }
        }
    }

    @Test
    fun testConcurrentWritesNoBusyError() = runBlocking {
        val numThreads = 40
        val successCount = AtomicInteger(0)

        val jobs = (1..numThreads).map { threadIdx ->
            launch(Dispatchers.IO) {
                // Key-value write
                DesktopDataStore.setKey("concurrent_key_$threadIdx", "concurrent_val_$threadIdx")

                // Bookmark write
                DesktopDataStore.addBookmark(
                    DesktopBookmark(
                        id = "bm_$threadIdx",
                        name = "Bookmark $threadIdx",
                        url = "http://example.com/show/$threadIdx",
                        apiName = "TestApi",
                        posterUrl = "http://example.com/poster/$threadIdx.jpg",
                        watchType = 1,
                        dateAdded = System.currentTimeMillis(),
                    ),
                )

                // Watch history write
                DesktopDataStore.setLastWatched(
                    WatchHistory(
                        parentId = "parent_$threadIdx",
                        showName = "Show $threadIdx",
                        showUrl = "http://example.com/show/$threadIdx",
                        apiName = "TestApi",
                        posterUrl = null,
                        episodeThumbnailUrl = null,
                        screenshotUrl = null,
                        episode = 1,
                        season = 1,
                        episodeId = "ep_$threadIdx",
                        position = 100L * threadIdx,
                        duration = 1000L * threadIdx,
                    ),
                )

                // Update history write
                DesktopDataStore.addUpdateHistory(
                    listOf(
                        PluginUpdateRecord(
                            pluginName = "Plugin_$threadIdx",
                            version = threadIdx,
                            iconUrl = null,
                            timestamp = System.currentTimeMillis(),
                        ),
                    ),
                )

                successCount.incrementAndGet()
            }
        }

        jobs.joinAll()
        assertEquals(numThreads, successCount.get(), "All concurrent writes must complete successfully without SQLITE_BUSY")
    }

    @Test
    fun testConcurrentReadsWhileWriting() = runBlocking {
        val numWriters = 25
        val numReaders = 25
        val writeSuccesses = AtomicInteger(0)
        val readSuccesses = AtomicInteger(0)

        // Seed some initial data
        DesktopDataStore.setKey("seed_key", "seed_val")
        DesktopDataStore.addBookmark(
            DesktopBookmark(
                id = "seed_bm",
                name = "Seed Bookmark",
                url = "http://example.com/seed",
                apiName = "TestApi",
                posterUrl = null,
                watchType = 1,
                dateAdded = System.currentTimeMillis(),
            ),
        )

        val writerJobs = (1..numWriters).map { idx ->
            launch(Dispatchers.IO) {
                DesktopDataStore.setKey("rw_key_$idx", "rw_val_$idx")
                DesktopDataStore.addBookmark(
                    DesktopBookmark(
                        id = "rw_bm_$idx",
                        name = "Bookmark $idx",
                        url = "http://example.com/$idx",
                        apiName = "TestApi",
                        posterUrl = null,
                        watchType = 1,
                        dateAdded = System.currentTimeMillis(),
                    ),
                )
                writeSuccesses.incrementAndGet()
            }
        }

        val readerJobs = (1..numReaders).map { idx ->
            launch(Dispatchers.IO) {
                val seedVal = DesktopDataStore.getKey<String>("seed_key")
                assertEquals("seed_val", seedVal)
                val bookmarks = DesktopDataStore.getBookmarks()
                assertTrue(bookmarks.isNotEmpty(), "Bookmarks should not be empty")
                readSuccesses.incrementAndGet()
            }
        }

        (writerJobs + readerJobs).joinAll()
        assertEquals(numWriters, writeSuccesses.get())
        assertEquals(numReaders, readSuccesses.get())
    }

    @Test
    fun testOverlappingTransactions() = runBlocking {
        val numJobs = 20
        val completedCount = AtomicInteger(0)

        val jobs = (1..numJobs).map { idx ->
            launch(Dispatchers.IO) {
                if (idx % 3 == 0) {
                    DesktopDataStore.clearAllWatchHistory()
                } else if (idx % 3 == 1) {
                    DesktopDataStore.setMultipleLastWatched(
                        (1..5).map { ep ->
                            WatchHistory(
                                parentId = "multi_parent_${idx}_$ep",
                                showName = "Show $idx",
                                showUrl = "http://example.com/show/$idx",
                                apiName = "TestApi",
                                posterUrl = null,
                                episodeThumbnailUrl = null,
                                screenshotUrl = null,
                                episode = ep,
                                season = 1,
                                episodeId = "ep_${idx}_$ep",
                                position = 50L,
                                duration = 100L,
                            )
                        },
                    )
                } else {
                    DesktopDataStore.addUpdateHistory(
                        listOf(
                            PluginUpdateRecord(
                                pluginName = "BatchPlugin_$idx",
                                version = idx,
                                iconUrl = null,
                                timestamp = System.currentTimeMillis(),
                            ),
                        ),
                    )
                }
                completedCount.incrementAndGet()
            }
        }

        jobs.joinAll()
        assertEquals(numJobs, completedCount.get(), "Overlapping transactions must execute cleanly without deadlock")
    }

    @Test
    fun testBatchTrustUpdates() = runBlocking {
        val numBatches = 10
        val aliasesPerBatch = 20
        val completedCount = AtomicInteger(0)

        val jobs = (1..numBatches).map { batchIdx ->
            launch(Dispatchers.IO) {
                val aliases = (1..aliasesPerBatch).map { aliasIdx ->
                    "com.example.plugin.batch${batchIdx}_alias$aliasIdx-jvm"
                }
                DesktopDataStore.setPluginsTrusted(aliases, true)
                completedCount.incrementAndGet()
            }
        }

        jobs.joinAll()
        assertEquals(numBatches, completedCount.get(), "All batch trust update operations must complete successfully")

        val trusted = DesktopDataStore.getTrustedPlugins()
        assertTrue(trusted.isNotEmpty(), "Trusted plugins set must not be empty")
        assertTrue(DesktopDataStore.isPluginTrusted("com.example.plugin.batch1_alias1-jvm"))
        assertTrue(DesktopDataStore.isPluginTrusted("com.example.plugin.batch5_alias10-jvm"))
    }
}
