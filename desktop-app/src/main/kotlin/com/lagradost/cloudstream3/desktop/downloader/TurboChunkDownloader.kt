package com.lagradost.cloudstream3.desktop.downloader

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class TurboChunkDownloader(
    private val maxWorkers: Int = 8,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    data class ProbeResult(
        val totalBytes: Long,
        val supportsRange: Boolean,
        val contentType: String?,
    )

    suspend fun probe(url: String, headers: Map<String, String>): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            reqBuilder.addHeader("Range", "bytes=0-0")

            client.newCall(reqBuilder.build()).execute().use { res ->
                val contentRange = res.header("Content-Range")
                val contentLength = res.header("Content-Length")?.toLongOrNull() ?: 0L
                val acceptRanges = res.header("Accept-Ranges")

                val totalBytes = if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                    contentRange.substringAfter("/").toLongOrNull() ?: contentLength
                } else {
                    contentLength
                }

                val supportsRange = res.code == 206 || acceptRanges?.equals("bytes", ignoreCase = true) == true
                ProbeResult(
                    totalBytes = totalBytes,
                    supportsRange = supportsRange && totalBytes > 1_000_000L,
                    contentType = res.header("Content-Type"),
                )
            }
        } catch (e: Exception) {
            AppLogger.w("TurboChunkDownloader probe failed: ${e.message}")
            ProbeResult(0L, false, null)
        }
    }

    suspend fun download(
        url: String,
        headers: Map<String, String>,
        destinationFile: File,
        tempPartFile: File,
        totalBytesEstimated: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesSec: Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val probe = probe(url, headers)
        val totalBytes = if (probe.totalBytes > 0L) probe.totalBytes else totalBytesEstimated

        destinationFile.parentFile?.mkdirs()
        tempPartFile.parentFile?.mkdirs()

        val speedTracker = SpeedTracker()

        if (probe.supportsRange && totalBytes > 5_000_000L) {
            AppLogger.i("TurboChunkDownloader: Starting parallel $maxWorkers-chunk turbo download for ${totalBytes / 1024 / 1024} MB ($url)")
            downloadParallel(
                url = url,
                headers = headers,
                tempFile = tempPartFile,
                totalBytes = totalBytes,
                numWorkers = maxWorkers,
                speedTracker = speedTracker,
                onProgress = onProgress,
                isCancelled = isCancelled,
            )
        } else {
            AppLogger.i("TurboChunkDownloader: Server does not support ranges. Downloading single progressive stream ($url)")
            downloadSingleStream(
                url = url,
                headers = headers,
                tempFile = tempPartFile,
                totalBytes = totalBytes,
                speedTracker = speedTracker,
                onProgress = onProgress,
                isCancelled = isCancelled,
            )
        }

        if (isCancelled()) {
            return@withContext false
        }

        if (tempPartFile.exists() && tempPartFile.length() > 0L) {
            AppLogger.i("TurboChunkDownloader: Chunk writing completed -> ${tempPartFile.absolutePath} (${tempPartFile.length() / 1024 / 1024} MB)")
            true
        } else {
            false
        }
    }

    private suspend fun downloadParallel(
        url: String,
        headers: Map<String, String>,
        tempFile: File,
        totalBytes: Long,
        numWorkers: Int,
        speedTracker: SpeedTracker,
        onProgress: (Long, Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ) = coroutineScope {
        // Pre-allocate destination sparse file
        RandomAccessFile(tempFile, "rw").use { raf ->
            if (raf.length() != totalBytes) {
                raf.setLength(totalBytes)
            }
        }

        val chunkSize = (totalBytes + numWorkers - 1) / numWorkers
        val downloadedTotal = AtomicLong(0L)
        val hasError = AtomicBoolean(false)

        val workers = (0 until numWorkers).map { workerIdx ->
            val startByte = workerIdx * chunkSize
            val endByte = ((workerIdx + 1) * chunkSize - 1).coerceAtMost(totalBytes - 1)

            launch(Dispatchers.IO) {
                if (startByte > endByte || isCancelled() || hasError.get()) return@launch

                var attempts = 0
                while (attempts < 3 && !isCancelled() && !hasError.get()) {
                    try {
                        val reqBuilder = Request.Builder().url(url)
                        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                        reqBuilder.addHeader("Range", "bytes=$startByte-$endByte")

                        client.newCall(reqBuilder.build()).execute().use { response ->
                            if (response.code !in 200..299) {
                                throw IllegalStateException("Worker $workerIdx HTTP error ${response.code}")
                            }

                            val body = response.body
                            val buffer = ByteArray(64 * 1024)

                            RandomAccessFile(tempFile, "rw").use { raf ->
                                val channel: FileChannel = raf.channel
                                var currentOffset = startByte
                                body.byteStream().use { input ->
                                    while (!isCancelled() && !hasError.get()) {
                                        val read = input.read(buffer)
                                        if (read <= 0) break

                                        val byteBuffer = java.nio.ByteBuffer.wrap(buffer, 0, read)
                                        while (byteBuffer.hasRemaining()) {
                                            channel.write(byteBuffer, currentOffset)
                                        }

                                        currentOffset += read
                                        val totalDownloaded = downloadedTotal.addAndGet(read.toLong())
                                        speedTracker.record(read.toLong())

                                        onProgress(
                                            totalDownloaded.coerceAtMost(totalBytes),
                                            totalBytes,
                                            speedTracker.getCurrentSpeed(),
                                        )
                                    }
                                }
                            }
                        }
                        break // Worker finished chunk successfully
                    } catch (e: Exception) {
                        attempts++
                        AppLogger.w("Turbo worker $workerIdx attempt $attempts failed: ${e.message}")
                        if (attempts >= 3) {
                            hasError.set(true)
                        }
                        delay(1000L * attempts)
                    }
                }
            }
        }

        workers.joinAll()
    }

    private suspend fun downloadSingleStream(
        url: String,
        headers: Map<String, String>,
        tempFile: File,
        totalBytes: Long,
        speedTracker: SpeedTracker,
        onProgress: (Long, Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ) = withContext(Dispatchers.IO) {
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        if (existingBytes > 0L) {
            reqBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download HTTP ${response.code}")
            }
            val isPartial = response.code == 206
            val append = isPartial && existingBytes > 0L
            val body = response.body
            val buffer = ByteArray(64 * 1024)
            var downloaded = if (append) existingBytes else 0L

            java.io.FileOutputStream(tempFile, append).use { output ->
                body.byteStream().use { input ->
                    while (!isCancelled()) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        speedTracker.record(read.toLong())

                        onProgress(
                            downloaded,
                            if (totalBytes > 0) totalBytes else downloaded,
                            speedTracker.getCurrentSpeed(),
                        )
                    }
                    output.flush()
                }
            }
        }
    }

    private class SpeedTracker {
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
