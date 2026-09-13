package com.lagradost.cloudstream3.desktop.player.ytdl

import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.Job
import java.io.File
import java.util.Locale

class DesktopYtDlpBinary {

    fun getBinaryFile(): File {
        val binDir = File(PlatformPaths.appDataDir, "ytdl/bin")
        val exeName = when (PlatformPaths.currentOS) {
            PlatformPaths.OS.WINDOWS -> "yt-dlp.exe"
            else -> "yt-dlp"
        }
        return File(binDir, exeName)
    }

    fun isInstalled(): Boolean {
        val file = getBinaryFile()
        return file.exists() && file.length() > 5_000_000L
    }

    fun getFileSizeMB(): Float {
        val file = getBinaryFile()
        return if (file.exists()) file.length().toFloat() / (1024f * 1024f) else 0f
    }

    fun deleteBinary(): Boolean {
        val file = getBinaryFile()
        return if (file.exists()) file.delete() else false
    }

    fun downloadWithManager(onComplete: ((File) -> Unit)? = null): Job {
        val target = getBinaryFile()
        val downloadUrl = getReleaseDownloadUrl()
        return AppDownloadManager.startDownload(
            id = "ytdl",
            title = "yt-dlp Stream Resolver",
            url = downloadUrl,
            targetFile = target,
            onComplete = { file ->
                file.setExecutable(true)
                val releaseTag = UnifiedUpdateManager.availableUpdates.value.firstOrNull { it.id == "ytdl" }?.newVersion
                    ?: UnifiedUpdateManager.DEFAULT_YTDL_VERSION
                UnifiedUpdateManager.setYtDlpInstalledVersion(releaseTag)
                AppLogger.i("DesktopYtDlpBinary", "yt-dlp installed successfully: version=$releaseTag, size=${file.length()} bytes")
                onComplete?.invoke(file)
            },
        )
    }

    private fun getReleaseDownloadUrl(): String {
        return when (PlatformPaths.currentOS) {
            PlatformPaths.OS.WINDOWS -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
            PlatformPaths.OS.MACOS -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos"
            PlatformPaths.OS.LINUX -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
            else -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
        }
    }

    companion object {
        fun isYouTubeUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val lower = url.lowercase(Locale.ROOT)
            return lower.contains("youtube.com/watch") ||
                lower.contains("youtu.be/") ||
                lower.contains("youtube.com/embed") ||
                lower.contains("youtube.com/v/") ||
                lower.contains("youtube.com/live/") ||
                lower.contains("youtube.com/shorts/")
        }
    }
}
