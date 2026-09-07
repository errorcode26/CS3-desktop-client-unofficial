package com.lagradost.cloudstream3.desktop.downloader

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
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
            AppLogger.w("Probe failed: ${e.message}")
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

        val downloadOk = if (probe.supportsRange && totalBytes > 5_000_000L) {
            AppLogger.i("Starting parallel $maxWorkers-connection download for ${totalBytes / 1024 / 1024} MB ($url)")
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
            AppLogger.i("Server does not support range requests. Downloading progressive stream ($url)")
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

        if (isCancelled() || !downloadOk) {
            return@withContext false
        }

        if (tempPartFile.exists() && tempPartFile.length() > 0L) {
            AppLogger.i("Download writing completed -> ${tempPartFile.absolutePath} (${tempPartFile.length() / 1024 / 1024} MB)")
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
    ): Boolean = coroutineScope {
        // Clean up any legacy chunk directory from older versions
        val legacyChunkDir = File(tempFile.parentFile, "${tempFile.name}.chunks")
        if (legacyChunkDir.exists()) {
            legacyChunkDir.deleteRecursively()
        }

        val stateFile = File(tempFile.parentFile, "${tempFile.name}.offsets")
        val chunkSize = (totalBytes + numWorkers - 1) / numWorkers

        // Pre-allocate file directly to exact length
        tempFile.parentFile?.mkdirs()
        val channel = FileChannel.open(
            tempFile.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
        )

        try {
            if (channel.size() != totalBytes) {
                channel.truncate(totalBytes)
            }
        } catch (e: Exception) {
            AppLogger.w("FileChannel pre-allocation failed: ${e.message}")
        }

        // Restore worker progress from state file if resuming
        val workerProgress = Array(numWorkers) { AtomicLong(0L) }
        if (stateFile.exists() && tempFile.exists() && tempFile.length() == totalBytes) {
            try {
                stateFile.readLines().forEach { line ->
                    val parts = line.split(":")
                    if (parts.size == 2) {
                        val idx = parts[0].toIntOrNull()
                        val bytes = parts[1].toLongOrNull()
                        if (idx != null && bytes != null && idx in 0 until numWorkers) {
                            val startByte = idx * chunkSize
                            val endByte = ((idx + 1) * chunkSize - 1).coerceAtMost(totalBytes - 1)
                            val expectedLength = (endByte - startByte + 1).coerceAtLeast(0L)
                            workerProgress[idx].set(bytes.coerceIn(0L, expectedLength))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val initialBytes = (0 until numWorkers).sumOf { workerProgress[it].get() }
        val downloadedTotal = AtomicLong(initialBytes)
        val hasError = AtomicBoolean(false)

        onProgress(initialBytes.coerceAtMost(totalBytes), totalBytes, 0L)

        val saveState = {
            try {
                val content = workerProgress.indices.joinToString("\n") { "$it:${workerProgress[it].get()}" }
                stateFile.writeText(content)
            } catch (_: Exception) {}
        }

        val workers = (0 until numWorkers).map { workerIdx ->
            val startByte = workerIdx * chunkSize
            val endByte = ((workerIdx + 1) * chunkSize - 1).coerceAtMost(totalBytes - 1)
            val expectedChunkLength = (endByte - startByte + 1).coerceAtLeast(0L)

            launch(Dispatchers.IO) {
                if (startByte > endByte || isCancelled() || hasError.get()) return@launch

                val alreadyDownloaded = workerProgress[workerIdx].get()
                if (alreadyDownloaded >= expectedChunkLength) {
                    return@launch
                }

                var attempts = 0
                val maxAttempts = 5
                while (attempts < maxAttempts && !isCancelled() && !hasError.get()) {
                    val currentOffset = workerProgress[workerIdx].get()
                    val requestStartByte = startByte + currentOffset
                    if (requestStartByte > endByte) {
                        break
                    }

                    try {
                        val reqBuilder = Request.Builder().url(url)
                        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                        reqBuilder.addHeader("Range", "bytes=$requestStartByte-$endByte")

                        client.newCall(reqBuilder.build()).execute().use { response ->
                            if (response.code !in 200..299) {
                                throw IllegalStateException("Thread $workerIdx HTTP ${response.code}")
                            }

                            val body = response.body
                            val buffer = ByteArray(64 * 1024)
                            val byteBuffer = ByteBuffer.wrap(buffer)
                            var writePos = requestStartByte

                            body.byteStream().use { input ->
                                while (!isCancelled() && !hasError.get()) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break

                                    byteBuffer.position(0)
                                    byteBuffer.limit(read)
                                    var writtenTotal = 0
                                    while (byteBuffer.hasRemaining()) {
                                        val written = channel.write(byteBuffer, writePos + writtenTotal)
                                        writtenTotal += written
                                    }

                                    writePos += read
                                    workerProgress[workerIdx].addAndGet(read.toLong())
                                    val total = downloadedTotal.addAndGet(read.toLong())
                                    speedTracker.record(read.toLong())

                                    onProgress(
                                        total.coerceAtMost(totalBytes),
                                        totalBytes,
                                        speedTracker.getCurrentSpeed(),
                                    )
                                }
                            }
                        }
                        break // Success on this worker
                    } catch (e: Exception) {
                        attempts++
                        AppLogger.w("Thread $workerIdx attempt $attempts failed: ${e.message}")
                        if (attempts >= maxAttempts) {
                            hasError.set(true)
                        }
                        delay(1000L * attempts)
                    }
                }
            }
        }

        workers.joinAll()

        try {
            channel.force(true)
            channel.close()
        } catch (_: Exception) {}

        if (isCancelled() || hasError.get()) {
            saveState()
            return@coroutineScope false
        }

        // Verify all chunks completed in-place
        val allChunksValid = (0 until numWorkers).all { workerIdx ->
            val startByte = workerIdx * chunkSize
            val endByte = ((workerIdx + 1) * chunkSize - 1).coerceAtMost(totalBytes - 1)
            val expectedChunkLength = (endByte - startByte + 1).coerceAtLeast(0L)
            workerProgress[workerIdx].get() >= expectedChunkLength
        }

        if (!allChunksValid) {
            AppLogger.w("Zero-copy download validation failed: some chunks incomplete")
            saveState()
            return@coroutineScope false
        }

        // Download completed in-place: zero assembly time, zero disk duplication
        AppLogger.i("Zero-copy download completed directly in-place -> ${tempFile.name} ($totalBytes bytes)")
        stateFile.delete()
        true
    }

    private suspend fun downloadSingleStream(
        url: String,
        headers: Map<String, String>,
        tempFile: File,
        totalBytes: Long,
        speedTracker: SpeedTracker,
        onProgress: (Long, Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        if (totalBytes > 0 && existingBytes >= totalBytes) {
            return@withContext true
        }

        var attempts = 0
        val maxAttempts = 5

        while (attempts < maxAttempts && !isCancelled()) {
            val currentExisting = if (tempFile.exists()) tempFile.length() else 0L
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            if (currentExisting > 0L) {
                reqBuilder.addHeader("Range", "bytes=$currentExisting-")
            }

            try {
                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download HTTP ${response.code}")
                    }
                    val isPartial = response.code == 206
                    val append = isPartial && currentExisting > 0L
                    val body = response.body
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = if (append) currentExisting else 0L

                    FileOutputStream(tempFile, append).use { output ->
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
                return@withContext tempFile.exists() && tempFile.length() > 0L
            } catch (e: Exception) {
                attempts++
                AppLogger.w("Single stream download attempt $attempts failed: ${e.message}")
                delay(1000L * attempts)
            }
        }
        false
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
