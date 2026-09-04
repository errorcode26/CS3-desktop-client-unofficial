package com.lagradost.cloudstream3.desktop.torrent

import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DesktopTorrServerBinary {
    private var process: Process? = null
    private val isStarting = AtomicBoolean(false)

    val port: Int
        get() = DesktopDataStore.getKey<Int>(DesktopDataStore.PREF_P2P_PORT) ?: 8091

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    private val healthClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(500))
        .build()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { stop() }
            }.apply {
                name = "cloudstream-torrserver-shutdown"
            },
        )
    }

    suspend fun start(): Unit = withContext(Dispatchers.IO) {
        if (isRunning()) {
            AppLogger.d("TorrServer already running on port $port")
            return@withContext
        }

        if (!isStarting.compareAndSet(false, true)) {
            // Another coroutine is already starting it, wait up to 10s
            for (i in 0..50) {
                if (isRunning()) return@withContext
                delay(200)
            }
            return@withContext
        }

        try {
            killOrphanedProcess()
            val binaryFile = resolveOrDownloadBinary()

            val cacheDir = File(PlatformPaths.appDataDir, "torrserver/cache").apply { mkdirs() }
            val logFile = File(PlatformPaths.logsDir, "torrserver.log")

            val command = listOf(
                binaryFile.absolutePath,
                "-p", port.toString(),
                "-d", cacheDir.absolutePath,
                "--ssl=false",
            )

            AppLogger.i("Starting TorrServer: ${command.joinToString(" ")}")

            val procBuilder = ProcessBuilder(command)
                .directory(binaryFile.parentFile)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))

            val proc = procBuilder.start()
            process = proc

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < STARTUP_TIMEOUT_MS) {
                if (isRunning()) {
                    AppLogger.i("TorrServer started successfully on port $port (${System.currentTimeMillis() - startTime}ms)")
                    return@withContext
                }

                if (!proc.isAlive) {
                    val exitCode = runCatching { proc.exitValue() }.getOrNull()
                    process = null
                    throw IllegalStateException("TorrServer process exited prematurely with code $exitCode. See log: ${logFile.absolutePath}")
                }
                delay(HEALTH_CHECK_INTERVAL_MS)
            }

            stop()
            throw IllegalStateException("TorrServer failed to respond to health check within ${STARTUP_TIMEOUT_MS / 1000}s")
        } finally {
            isStarting.set(false)
        }
    }

    fun isRunning(): Boolean {
        return try {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/echo"))
                .timeout(Duration.ofMillis(800))
                .GET()
                .build()
            val response = healthClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        try {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/shutdown"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()
            healthClient.send(request, HttpResponse.BodyHandlers.discarding())
        } catch (_: Exception) {
        }

        process?.let { proc ->
            try {
                if (!proc.waitFor(2_000L, TimeUnit.MILLISECONDS) && proc.isAlive) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {
                proc.destroyForcibly()
            }
        }
        process = null
        AppLogger.d("TorrServer stopped")
    }

    private fun killOrphanedProcess() {
        try {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/shutdown"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()
            healthClient.send(request, HttpResponse.BodyHandlers.discarding())
            Thread.sleep(300)
        } catch (_: Exception) {
        }
    }

    fun getBinaryFile(): File {
        val binDir = File(PlatformPaths.appDataDir, "torrserver/bin")
        val exeName = if (PlatformPaths.currentOS == PlatformPaths.OS.WINDOWS) "TorrServer.exe" else "TorrServer"
        return File(binDir, exeName)
    }

    fun isInstalled(): Boolean {
        val file = getBinaryFile()
        return file.exists() && file.length() > 1_000_000L
    }

    fun getFileSizeMB(): Float {
        val file = getBinaryFile()
        return if (file.exists()) file.length().toFloat() / (1024f * 1024f) else 0f
    }

    fun deleteBinary(): Boolean {
        stop()
        val file = getBinaryFile()
        return if (file.exists()) file.delete() else false
    }

    fun downloadWithManager(onComplete: ((File) -> Unit)? = null): kotlinx.coroutines.Job {
        val target = getBinaryFile()
        val downloadUrl = getReleaseDownloadUrl()
        return com.lagradost.cloudstream3.desktop.download.AppDownloadManager.startDownload(
            id = "torrserver",
            title = "TorrServer P2P Engine",
            url = downloadUrl,
            targetFile = target,
            onComplete = { file ->
                val releaseTag = com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.availableUpdates.value.firstOrNull { it.id == "torrserver" }?.newVersion
                    ?: com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.DEFAULT_TORRSERVER_VERSION
                com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.setTorrServerInstalledVersion(releaseTag)
                onComplete?.invoke(file)
            },
        )
    }

    private suspend fun resolveOrDownloadBinary(): File = withContext(Dispatchers.IO) {
        val targetFile = getBinaryFile()
        if (targetFile.exists() && targetFile.length() > 1_000_000L) {
            targetFile.setExecutable(true)
            return@withContext targetFile
        }

        // Auto-download via AppDownloadManager so it tracks in TopBar with live progress
        AppLogger.i("TorrServer binary not found. Delegating download to AppDownloadManager...")
        val job = downloadWithManager()
        job.join()

        if (!targetFile.exists() || targetFile.length() < 1_000_000L) {
            val error = com.lagradost.cloudstream3.desktop.download.AppDownloadManager.getTask("torrserver")?.errorMessage
            throw IllegalStateException(error ?: "Failed to download TorrServer binary")
        }

        targetFile.setExecutable(true)
        targetFile
    }

    private fun getReleaseDownloadUrl(): String {
        val os = PlatformPaths.currentOS
        val arch = System.getProperty("os.arch").orEmpty().lowercase(Locale.ROOT)
        val archName = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("64") -> "amd64"
            else -> "386"
        }

        return when (os) {
            PlatformPaths.OS.WINDOWS -> "https://github.com/YouROK/TorrServer/releases/latest/download/TorrServer-windows-$archName.exe"
            PlatformPaths.OS.MACOS -> "https://github.com/YouROK/TorrServer/releases/latest/download/TorrServer-darwin-$archName"
            PlatformPaths.OS.LINUX -> "https://github.com/YouROK/TorrServer/releases/latest/download/TorrServer-linux-$archName"
            else -> "https://github.com/YouROK/TorrServer/releases/latest/download/TorrServer-windows-amd64.exe"
        }
    }

    companion object {
        private const val STARTUP_TIMEOUT_MS = 15_000L
        private const val HEALTH_CHECK_INTERVAL_MS = 250L
    }
}
