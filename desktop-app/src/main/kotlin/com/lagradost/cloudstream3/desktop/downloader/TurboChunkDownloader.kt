package com.lagradost.cloudstream3.desktop.downloader

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
        val chunkDir = File(tempFile.parentFile, "${tempFile.name}.chunks").apply { mkdirs() }
        val chunkSize = (totalBytes + numWorkers - 1) / numWorkers

        // Calculate already-downloaded bytes from previously saved chunk files
        val initialBytes = (0 until numWorkers).sumOf { workerIdx ->
            val chunkFile = File(chunkDir, "chunk_%03d.part".format(workerIdx))
            if (chunkFile.exists()) chunkFile.length() else 0L
        }

        val downloadedTotal = AtomicLong(initialBytes)
        val hasError = AtomicBoolean(false)

        onProgress(initialBytes.coerceAtMost(totalBytes), totalBytes, 0L)

        val workers = (0 until numWorkers).map { workerIdx ->
            val startByte = workerIdx * chunkSize
            val endByte = ((workerIdx + 1) * chunkSize - 1).coerceAtMost(totalBytes - 1)
            val expectedChunkLength = (endByte - startByte + 1).coerceAtLeast(0L)

            launch(Dispatchers.IO) {
                if (startByte > endByte || isCancelled() || hasError.get()) return@launch

                val chunkFile = File(chunkDir, "chunk_%03d.part".format(workerIdx))
                val existingLength = if (chunkFile.exists()) chunkFile.length() else 0L

                if (existingLength >= expectedChunkLength) {
                    // Chunk already fully downloaded in previous attempt
                    return@launch
                }

                var attempts = 0
                val maxAttempts = 5
                while (attempts < maxAttempts && !isCancelled() && !hasError.get()) {
                    val currentOffsetOnDisk = if (chunkFile.exists()) chunkFile.length() else 0L
                    val requestStartByte = startByte + currentOffsetOnDisk
                    if (requestStartByte > endByte) {
                        break // Done
                    }

                    try {
                        val reqBuilder = Request.Builder().url(url)
                        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                        reqBuilder.addHeader("Range", "bytes=$requestStartByte-$endByte")

                        client.newCall(reqBuilder.build()).execute().use { response ->
                            if (response.code !in 200..299) {
                                throw IllegalStateException("Thread $workerIdx HTTP ${response.code}")
                            }

                            val isPartial = response.code == 206
                            val append = isPartial && currentOffsetOnDisk > 0L
                            val body = response.body
                            val buffer = ByteArray(64 * 1024)

                            FileOutputStream(chunkFile, append).use { outStream ->
                                body.byteStream().use { input ->
                                    while (!isCancelled() && !hasError.get()) {
                                        val read = input.read(buffer)
                                        if (read <= 0) break
                                        outStream.write(buffer, 0, read)
                                        val total = downloadedTotal.addAndGet(read.toLong())
                                        speedTracker.record(read.toLong())

                                        onProgress(
                                            total.coerceAtMost(totalBytes),
                                            totalBytes,
                                            speedTracker.getCurrentSpeed(),
                                        )
                                    }
                                    outStream.flush()
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

        if (isCancelled() || hasError.get()) {
            return@coroutineScope false
        }

        // Verify all chunks completed
        val allChunksValid = (0 until numWorkers).all { workerIdx ->
            val startByte = workerIdx * chunkSize
            val endByte = ((workerIdx + 1) * chunkSize - 1).coerceAtMost(totalBytes - 1)
            val expectedChunkLength = (endByte - startByte + 1).coerceAtLeast(0L)
            val chunkFile = File(chunkDir, "chunk_%03d.part".format(workerIdx))
            chunkFile.exists() && chunkFile.length() == expectedChunkLength
        }

        if (!allChunksValid) {
            AppLogger.w("Chunk validation failed, some chunks incomplete")
            return@coroutineScope false
        }

        // Assemble chunks into final tempPartFile
        AppLogger.i("Assembling $numWorkers chunks into ${tempFile.name}...")
        try {
            FileOutputStream(tempFile).use { outStream ->
                for (workerIdx in 0 until numWorkers) {
                    val chunkFile = File(chunkDir, "chunk_%03d.part".format(workerIdx))
                    chunkFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                outStream.flush()
            }
            chunkDir.deleteRecursively()
            true
        } catch (e: Exception) {
            AppLogger.e("Failed to assemble chunks: ${e.message}", e)
            false
        }
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
