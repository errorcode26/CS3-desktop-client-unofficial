package com.lagradost.cloudstream3.desktop.downloader

import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance streaming media downloader powered by yt-dlp.
 * Supports HLS (.m3u8), DASH (.mpd), YouTube, and complex extractor streams
 * with live progress parsing and process lifecycle control.
 */
class YtDlpStreamDownloader(
    private val ytDlpBinary: DesktopYtDlpBinary = DesktopYtDlpBinary(),
    private val concurrentFragments: Int = 5,
) {

    suspend fun download(
        url: String,
        headers: Map<String, String>,
        destinationFile: File,
        stagingDir: File,
        totalBytesEstimated: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesSec: Long) -> Unit,
        onProcessSpawned: ((Process) -> Unit)? = null,
        isCancelled: () -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val exe = ytDlpBinary.getBinaryFile()
        if (!exe.exists()) {
            AppLogger.e("YtDlpStreamDownloader: yt-dlp binary not found at ${exe.absolutePath}")
            return@withContext false
        }

        stagingDir.mkdirs()

        val threads = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(
            com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS,
        )?.toInt()?.coerceIn(1, 16) ?: concurrentFragments

        val args = mutableListOf(
            exe.absolutePath,
            "-c",
            "--part",
            "--no-mtime",
            "--no-warnings",
            "--retries", "10",
            "--fragment-retries", "10",
            "--concurrent-fragments", threads.toString(),
            "--newline",
            "--progress-template", "DOWNLOAD_PROGRESS:%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.speed)s",
            "-o", File(stagingDir, "stream.%(ext)s").absolutePath,
        )

        if (DesktopYtDlpBinary.isYouTubeUrl(url)) {
            args.add("-f")
            args.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
        }

        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank() && !key.startsWith(":")) {
                args.add("--add-header")
                args.add("$key: $value")
            }
        }

        args.add(url)

        AppLogger.i("YtDlpStreamDownloader executing for $url")
        val process = try {
            ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            AppLogger.e("YtDlpStreamDownloader failed to start process: ${e.message}", e)
            return@withContext false
        }

        onProcessSpawned?.invoke(process)

        val reader = process.inputStream.bufferedReader()
        val errorLog = StringBuilder()

        try {
            while (true) {
                if (isCancelled()) {
                    try { process.destroy() } catch (_: Exception) {}
                    return@withContext false
                }
                val line = reader.readLine() ?: break
                if (line.startsWith("DOWNLOAD_PROGRESS:")) {
                    val raw = line.removePrefix("DOWNLOAD_PROGRESS:")
                    val parts = raw.split("|")
                    if (parts.size >= 3) {
                        val downloaded = parts[0].trim().toLongOrNull() ?: 0L
                        val totalParsed = parts[1].trim().toLongOrNull()
                        val total = if (totalParsed != null && totalParsed > 0L) totalParsed else totalBytesEstimated
                        val speed = parts[2].trim().toDoubleOrNull()?.toLong() ?: 0L
                        onProgress(downloaded, total, speed)
                    }
                } else {
                    if (errorLog.length < 2048) {
                        errorLog.appendLine(line)
                    }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0 && !isCancelled()) {
                AppLogger.e("YtDlpStreamDownloader process exited with code $exitCode: $errorLog")
                return@withContext false
            }
        } catch (e: CancellationException) {
            try { process.destroy() } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            try { process.destroy() } catch (_: Exception) {}
            AppLogger.e("YtDlpStreamDownloader stream reading exception: ${e.message}", e)
            return@withContext false
        } finally {
            try { process.destroy() } catch (_: Exception) {}
        }

        val producedFile = stagingDir.listFiles()?.firstOrNull {
            it.isFile && it.length() > 0L && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl")
        }

        if (producedFile != null && producedFile.exists() && producedFile.length() > 0L) {
            AppLogger.i("YtDlpStreamDownloader finished: ${producedFile.name} (${producedFile.length()} bytes)")
            true
        } else {
            AppLogger.e("YtDlpStreamDownloader completed but no valid file found in ${stagingDir.absolutePath}")
            false
        }
    }
}
