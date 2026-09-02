package com.lagradost.cloudstream3.desktop.downloader

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class HlsDownloader(
    private val maxConcurrentSegments: Int = 8,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    suspend fun download(
        playlistUrl: String,
        headers: Map<String, String>,
        destinationFile: File,
        tempPartFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesSec: Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val segmentUrls = resolveMediaSegments(playlistUrl, headers)
        if (segmentUrls.isEmpty()) {
            AppLogger.e("HlsDownloader: No segments found in playlist: $playlistUrl")
            return@withContext false
        }

        AppLogger.i("HlsDownloader: Found ${segmentUrls.size} HLS segments to download concurrently")
        destinationFile.parentFile?.mkdirs()
        tempPartFile.parentFile?.mkdirs()

        val tempSegmentDir = File(tempPartFile.parentFile, "${tempPartFile.name}.segments").apply { mkdirs() }
        val semaphore = Semaphore(maxConcurrentSegments)

        // Scan for existing segments on disk to resume smoothly without restarting from zero
        val initialExistingBytes = (segmentUrls.indices).sumOf { index ->
            val segFile = File(tempSegmentDir, "seg_%06d.ts".format(index))
            if (segFile.exists()) segFile.length() else 0L
        }
        val initialCompletedCount = (segmentUrls.indices).count { index ->
            val segFile = File(tempSegmentDir, "seg_%06d.ts".format(index))
            segFile.exists() && segFile.length() > 0L
        }

        val downloadedBytes = AtomicLong(initialExistingBytes)
        val completedSegments = AtomicInteger(initialCompletedCount)
        val speedTracker = HlsSpeedTracker()

        if (initialCompletedCount > 0) {
            val avgSize = initialExistingBytes / initialCompletedCount.coerceAtLeast(1)
            val estimatedTotal = avgSize * segmentUrls.size
            onProgress(initialExistingBytes, estimatedTotal, 0L)
            AppLogger.i("HlsDownloader: Resuming from $initialCompletedCount / ${segmentUrls.size} segments ($initialExistingBytes bytes)")
        }

        try {
            coroutineScope {
                val jobs = segmentUrls.mapIndexed { index, segUrl ->
                    launch(Dispatchers.IO) {
                        if (isCancelled()) return@launch
                        val segmentFile = File(tempSegmentDir, "seg_%06d.ts".format(index))

                        // If segment file already downloaded and valid, skip immediately
                        if (segmentFile.exists() && segmentFile.length() > 0L) {
                            return@launch
                        }

                        semaphore.withPermit {
                            if (isCancelled()) return@withPermit
                            var attempts = 0
                            val maxAttempts = 5
                            while (attempts < maxAttempts && !isCancelled() && (!segmentFile.exists() || segmentFile.length() == 0L)) {
                                try {
                                    val reqBuilder = Request.Builder().url(segUrl)
                                    headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                                    client.newCall(reqBuilder.build()).execute().use { res ->
                                        if (!res.isSuccessful) throw IllegalStateException("Segment HTTP ${res.code}")
                                        val body = res.body
                                        val bytes = body.bytes()
                                        segmentFile.writeBytes(bytes)
                                        downloadedBytes.addAndGet(bytes.size.toLong())
                                        speedTracker.record(bytes.size.toLong())
                                        val done = completedSegments.incrementAndGet()

                                        // Estimate total bytes dynamically based on average segment size
                                        val avgSize = downloadedBytes.get() / done.coerceAtLeast(1)
                                        val estimatedTotal = avgSize * segmentUrls.size

                                        onProgress(
                                            downloadedBytes.get(),
                                            estimatedTotal,
                                            speedTracker.getCurrentSpeed(),
                                        )
                                    }
                                    break
                                } catch (e: Exception) {
                                    attempts++
                                    if (attempts >= maxAttempts) {
                                        AppLogger.w("Failed to download segment $index ($segUrl): ${e.message}")
                                    }
                                    delay(500L * attempts)
                                }
                            }
                        }
                    }
                }
                jobs.joinAll()
            }

            if (isCancelled()) {
                // Keep tempSegmentDir so resuming can proceed later!
                return@withContext false
            }

            val allSegmentsExist = (segmentUrls.indices).all { i ->
                val segmentFile = File(tempSegmentDir, "seg_%06d.ts".format(i))
                segmentFile.exists() && segmentFile.length() > 0L
            }

            if (!allSegmentsExist) {
                AppLogger.w("HlsDownloader: Incomplete segment count (${completedSegments.get()} / ${segmentUrls.size}). Keeping segments for resume.")
                return@withContext false
            }

            // Concatenate all segments into the staging output file
            AppLogger.i("HlsDownloader: Assembling ${segmentUrls.size} segments into ${destinationFile.name}...")
            FileOutputStream(tempPartFile).use { outStream ->
                for (i in segmentUrls.indices) {
                    val segmentFile = File(tempSegmentDir, "seg_%06d.ts".format(i))
                    segmentFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                outStream.flush()
            }

            tempSegmentDir.deleteRecursively()
            AppLogger.i("HlsDownloader: Assembly complete -> ${tempPartFile.absolutePath} (${tempPartFile.length() / 1024 / 1024} MB)")
            true
        } catch (e: Exception) {
            AppLogger.e("HlsDownloader error: ${e.message}", e)
            false
        }
    }

    private suspend fun resolveMediaSegments(url: String, headers: Map<String, String>): List<String> = withContext(Dispatchers.IO) {
        val content = fetchText(url, headers) ?: return@withContext emptyList()
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }

        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            // Master playlist: find highest bandwidth variant
            var maxBandwidth = 0L
            var variantUrl: String? = null

            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                    val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val nextLine = lines.getOrNull(i + 1)
                    if (nextLine != null && !nextLine.startsWith("#") && bw >= maxBandwidth) {
                        maxBandwidth = bw
                        variantUrl = resolveRelativeUrl(url, nextLine)
                    }
                }
            }

            if (variantUrl != null) {
                return@withContext resolveMediaSegments(variantUrl, headers)
            }
        }

        // Media playlist: extract segment URLs
        val segments = mutableListOf<String>()
        for (line in lines) {
            if (!line.startsWith("#")) {
                segments.add(resolveRelativeUrl(url, line))
            }
        }
        segments
    }

    private fun fetchText(url: String, headers: Map<String, String>): String? {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        return try {
            client.newCall(reqBuilder.build()).execute().use { it.body.string() }
        } catch (_: Exception) { null }
    }

    private fun resolveRelativeUrl(base: String, relative: String): String {
        return try {
            if (relative.startsWith("http://") || relative.startsWith("https://")) relative
            else URI(base).resolve(relative).toString()
        } catch (_: Exception) {
            relative
        }
    }

    private class HlsSpeedTracker {
        private var lastTime = System.currentTimeMillis()
        private var bytesSinceLast = 0L
        private var currentSpeed = 0L

        @Synchronized
        fun record(bytes: Long) {
            bytesSinceLast += bytes
            val now = System.currentTimeMillis()
            val elapsed = now - lastTime
            if (elapsed >= 500) {
                currentSpeed = (bytesSinceLast * 1000) / elapsed
                bytesSinceLast = 0L
                lastTime = now
            }
        }

        @Synchronized
        fun getCurrentSpeed(): Long = currentSpeed
    }
}
