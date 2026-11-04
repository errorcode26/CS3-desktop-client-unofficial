package com.lagradost.cloudstream3.desktop.updates

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrServerBinary
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.security.MessageDigest

enum class UpdateType {
    APP_CLIENT,
    TORRENT_ENGINE,
    STREAM_RESOLVER,
}

data class PendingUpdate(
    val id: String,
    val type: UpdateType,
    val title: String,
    val currentVersion: String,
    val newVersion: String,
    val releaseNotes: String?,
    val downloadUrl: String,
    val releaseHtmlUrl: String? = null,
    val publishedAt: String? = null,
    val assetName: String? = null,
    val assetSize: Long = 0L,
    val expectedSha256: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0L,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubApiRelease(
    val tag_name: String,
    val name: String,
    val body: String?,
    val html_url: String,
    val published_at: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

object UnifiedUpdateManager {
    const val PREF_TORRSERVER_VERSION = "TORRSERVER_INSTALLED_VERSION"
    const val DEFAULT_TORRSERVER_VERSION = "MatriX.144.1"
    const val PREF_YTDL_VERSION = "YTDL_INSTALLED_VERSION"
    const val DEFAULT_YTDL_VERSION = "2025.01.26"
    const val PREF_IGNORED_UPDATE_PREFIX = "pref_ignored_update_"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: OkHttpClient get() = com.lagradost.cloudstream3.app.baseClient
    private val mapper = jacksonObjectMapper()

    private val _availableUpdates = MutableStateFlow<List<PendingUpdate>>(emptyList())
    val availableUpdates: StateFlow<List<PendingUpdate>> = _availableUpdates.asStateFlow()

    private val _activeDialogUpdate = MutableStateFlow<PendingUpdate?>(null)
    val activeDialogUpdate: StateFlow<PendingUpdate?> = _activeDialogUpdate.asStateFlow()

    private val _releaseHistory = MutableStateFlow<List<GitHubApiRelease>>(emptyList())
    val releaseHistory: StateFlow<List<GitHubApiRelease>> = _releaseHistory.asStateFlow()

    private val _isFetchingHistory = MutableStateFlow(false)
    val isFetchingHistory: StateFlow<Boolean> = _isFetchingHistory.asStateFlow()

    fun resolveInstallerAsset(release: GitHubApiRelease): GitHubAsset? {
        return when (PlatformPaths.installationFlavor) {
            PlatformPaths.Flavor.PORTABLE -> {
                release.assets.firstOrNull { it.name.contains("portable", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true) }
            }
            PlatformPaths.Flavor.MSI -> {
                release.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
                    ?: release.assets.firstOrNull { it.name.startsWith("CloudStream-Setup", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true) }
            }
            PlatformPaths.Flavor.INNO_SETUP -> {
                release.assets.firstOrNull { it.name.startsWith("CloudStream-Setup", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true) }
                    ?: release.assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
            }
        }
    }

    suspend fun fetchReleaseHistory(force: Boolean = false): List<GitHubApiRelease> = withContext(Dispatchers.IO) {
        if (!force && _releaseHistory.value.isNotEmpty()) return@withContext _releaseHistory.value
        _isFetchingHistory.value = true
        try {
            val url = "https://api.github.com/repos/${AppConfig.GITHUB_REPO}/releases?per_page=30"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    val list = mapper.readValue<List<GitHubApiRelease>>(body).filterNot { it.draft }
                    _releaseHistory.value = list
                    return@withContext list
                }
            }
        } catch (t: Throwable) {
            AppLogger.e("UnifiedUpdateManager fetchReleaseHistory error: ${t.message}", t)
        } finally {
            _isFetchingHistory.value = false
        }
        _releaseHistory.value
    }

    fun installRelease(release: GitHubApiRelease) {
        scope.launch {
            val installerAsset = resolveInstallerAsset(release)
            val downloadUrl = installerAsset?.browser_download_url ?: release.html_url
            val expectedSha256 = if (installerAsset != null) {
                fetchReleaseChecksum(release, installerAsset.name)
            } else null
            val update = PendingUpdate(
                id = "app_client_${release.tag_name}",
                type = UpdateType.APP_CLIENT,
                title = "CS3 Desktop Client",
                currentVersion = "v${AppConfig.APP_VERSION}",
                newVersion = release.tag_name,
                releaseNotes = release.body,
                downloadUrl = downloadUrl,
                releaseHtmlUrl = release.html_url,
                publishedAt = release.published_at,
                assetName = installerAsset?.name,
                assetSize = installerAsset?.size ?: 0L,
                expectedSha256 = expectedSha256,
            )
            withContext(Dispatchers.Main) {
                installUpdate(update)
            }
        }
    }

    private var hasCheckedInitial = false

    fun isUpdateIgnored(update: PendingUpdate): Boolean {
        val ignored = DesktopDataStore.getKey<String>("${PREF_IGNORED_UPDATE_PREFIX}${update.id}")
        return ignored != null && ignored == update.newVersion
    }

    fun ignoreUpdate(update: PendingUpdate) {
        DesktopDataStore.setKey("${PREF_IGNORED_UPDATE_PREFIX}${update.id}", update.newVersion)
        _availableUpdates.update { list -> list.filterNot { it.id == update.id } }
        dismissDialog()
    }

    fun getTorrServerInstalledVersion(): String {
        if (!com.lagradost.cloudstream3.desktop.torrent.DesktopTorrServerBinary().isInstalled()) return "Not Installed"
        return DesktopDataStore.getKey<String>(PREF_TORRSERVER_VERSION) ?: DEFAULT_TORRSERVER_VERSION
    }

    fun setTorrServerInstalledVersion(version: String) {
        DesktopDataStore.setKey(PREF_TORRSERVER_VERSION, version)
    }

    fun getYtDlpInstalledVersion(): String {
        if (!com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary().isInstalled()) return "Not Installed"
        return DesktopDataStore.getKey<String>(PREF_YTDL_VERSION) ?: DEFAULT_YTDL_VERSION
    }

    fun setYtDlpInstalledVersion(version: String) {
        DesktopDataStore.setKey(PREF_YTDL_VERSION, version)
    }

    suspend fun checkAllUpdates(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (hasCheckedInitial && !force) return@withContext
        try {
            val appJob = async { checkAppUpdate(force = force) }
            val torrJob = async {
                if (DesktopTorrServerBinary().isInstalled()) {
                    checkTorrServerUpdate(force = force)
                } else {
                    null
                }
            }
            val ytdlJob = async {
                if (DesktopYtDlpBinary().isInstalled()) {
                    checkYtDlpUpdate(force = force)
                } else {
                    null
                }
            }

            val appUpdate = appJob.await()
            val torrUpdate = torrJob.await()
            val ytdlUpdate = ytdlJob.await()

            hasCheckedInitial = true

            if (_activeDialogUpdate.value == null) {
                val candidate = appUpdate ?: torrUpdate ?: ytdlUpdate
                if (candidate != null && !isUpdateIgnored(candidate)) {
                    _activeDialogUpdate.value = candidate
                }
            }
        } catch (t: Throwable) {
            AppLogger.e("UnifiedUpdateManager checkAllUpdates failed", t)
        }
    }

    suspend fun checkAppUpdate(force: Boolean = false): PendingUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/${AppConfig.GITHUB_REPO}/releases?per_page=5"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    val releases = mapper.readValue<List<GitHubApiRelease>>(body)
                    val release = releases.firstOrNull { !it.draft } ?: return@withContext null
                    val remoteVersion = release.tag_name.removePrefix("v")
                    val currentVersion = AppConfig.APP_VERSION

                    val installerAsset = when (PlatformPaths.installationFlavor) {
                        PlatformPaths.Flavor.PORTABLE -> {
                            release.assets.firstOrNull { it.name.contains("portable", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true) }
                        }
                        PlatformPaths.Flavor.MSI -> {
                            release.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
                                ?: release.assets.firstOrNull { it.name.startsWith("CloudStream-Setup", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true) }
                        }
                        PlatformPaths.Flavor.INNO_SETUP -> {
                            release.assets.firstOrNull { it.name.startsWith("CloudStream-Setup", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true) }
                                ?: release.assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
                        }
                    }
                    val downloadUrl = installerAsset?.browser_download_url ?: release.html_url
                    val expectedSha256 = if (installerAsset != null) {
                        fetchReleaseChecksum(release, installerAsset.name)
                    } else null

                    val isDevBuild = currentVersion.startsWith("0.0.0") || currentVersion.contains("dev", ignoreCase = true)
                    if (!isDevBuild && compareSemVer(remoteVersion, currentVersion) > 0) {
                        val update = PendingUpdate(
                            id = "app_client",
                            type = UpdateType.APP_CLIENT,
                            title = "CS3 Desktop Client",
                            currentVersion = "v$currentVersion",
                            newVersion = release.tag_name,
                            releaseNotes = release.body,
                            downloadUrl = downloadUrl,
                            releaseHtmlUrl = release.html_url,
                            publishedAt = release.published_at,
                            assetName = installerAsset?.name,
                            assetSize = installerAsset?.size ?: 0L,
                            expectedSha256 = expectedSha256,
                        )
                        if (!force && isUpdateIgnored(update)) {
                            _availableUpdates.update { list -> list.filterNot { it.id == update.id } }
                            return@withContext null
                        }
                        _availableUpdates.update { list -> list.filterNot { it.id == update.id } + update }
                        return@withContext update
                    } else {
                        _availableUpdates.update { list -> list.filterNot { it.id == "app_client" } }
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e("UnifiedUpdateManager checkAppUpdate error: ${t.message}", t)
        }
        null
    }

    suspend fun checkTorrServerUpdate(force: Boolean = false): PendingUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/YouROK/TorrServer/releases/latest"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    val release = mapper.readValue<GitHubApiRelease>(body)
                    val remoteTag = release.tag_name
                    val installedTag = getTorrServerInstalledVersion()

                    if (compareTorrVersions(remoteTag, installedTag) > 0) {
                        val update = PendingUpdate(
                            id = "torrserver",
                            type = UpdateType.TORRENT_ENGINE,
                            title = "TorrServer Streaming Engine",
                            currentVersion = installedTag,
                            newVersion = remoteTag,
                            releaseNotes = release.body,
                            downloadUrl = release.html_url,
                            releaseHtmlUrl = release.html_url,
                            publishedAt = release.published_at,
                        )
                        if (!force && isUpdateIgnored(update)) {
                            _availableUpdates.update { list -> list.filterNot { it.id == update.id } }
                            return@withContext null
                        }
                        _availableUpdates.update { list -> list.filterNot { it.id == update.id } + update }
                        return@withContext update
                    } else {
                        _availableUpdates.update { list -> list.filterNot { it.id == "torrserver" } }
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e("UnifiedUpdateManager checkTorrServerUpdate error: ${t.message}", t)
        }
        null
    }

    suspend fun checkYtDlpUpdate(force: Boolean = false): PendingUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    val release = mapper.readValue<GitHubApiRelease>(body)
                    val remoteTag = release.tag_name.removePrefix("v")
                    val installedTag = getYtDlpInstalledVersion().removePrefix("v")

                    if (compareSemVer(remoteTag, installedTag) > 0) {
                        val update = PendingUpdate(
                            id = "ytdl",
                            type = UpdateType.STREAM_RESOLVER,
                            title = "yt-dlp Stream Resolver",
                            currentVersion = installedTag,
                            newVersion = remoteTag,
                            releaseNotes = release.body,
                            downloadUrl = release.html_url,
                            releaseHtmlUrl = release.html_url,
                            publishedAt = release.published_at,
                        )
                        if (!force && isUpdateIgnored(update)) {
                            _availableUpdates.update { list -> list.filterNot { it.id == update.id } }
                            return@withContext null
                        }
                        _availableUpdates.update { list -> list.filterNot { it.id == update.id } + update }
                        return@withContext update
                    } else {
                        _availableUpdates.update { list -> list.filterNot { it.id == "ytdl" } }
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e("UnifiedUpdateManager checkYtDlpUpdate error: ${t.message}", t)
        }
        null
    }

    fun parseSha256(content: String, targetFileName: String): String? {
        val targetName = targetFileName.trim()
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

        // 1. Exact filename match: "<hash>  <filename>" or "<hash> *<filename>"
        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val hash = parts[0].trim().lowercase()
                val file = parts[1].trim().removePrefix("*")
                if (hash.length == 64 && file.equals(targetName, ignoreCase = true)) {
                    return hash
                }
            }
        }

        // 2. Loose filename match
        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val hash = parts[0].trim().lowercase()
                val file = parts[1].trim().removePrefix("*")
                if (hash.length == 64 && (file.contains(targetName, ignoreCase = true) || targetName.contains(file, ignoreCase = true))) {
                    return hash
                }
            }
        }

        // 3. Dedicated single-line hash file
        if (lines.size == 1) {
            val single = lines[0].split(Regex("\\s+"))[0].trim().lowercase()
            if (single.length == 64 && single.all { it in '0'..'9' || it in 'a'..'f' }) {
                return single
            }
        }

        return null
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fetchReleaseChecksum(release: GitHubApiRelease, targetFileName: String): String? {
        val checksumAsset = release.assets.firstOrNull {
            it.name.equals("checksums.txt", ignoreCase = true) ||
            it.name.equals("SHA256SUMS.txt", ignoreCase = true) ||
            it.name.equals("$targetFileName.sha256", ignoreCase = true)
        } ?: return null

        return try {
            val req = Request.Builder().url(checksumAsset.browser_download_url).build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    parseSha256(body, targetFileName)
                } else null
            }
        } catch (e: Exception) {
            AppLogger.w("UnifiedUpdateManager: Failed to fetch checksum from ${checksumAsset.name}: ${e.message}")
            null
        }
    }

    fun getUpdatesDirectory(): File {
        val dir = File(PlatformPaths.appDataDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAppInstallerFile(version: String): File {
        val cleanTag = version.removePrefix("v")
        val fileName = when (PlatformPaths.installationFlavor) {
            PlatformPaths.Flavor.PORTABLE -> "CloudStream-Portable-v$cleanTag-windows-x64.zip"
            PlatformPaths.Flavor.MSI -> "CloudStream-Desktop-$cleanTag.msi"
            PlatformPaths.Flavor.INNO_SETUP -> "CloudStream-Setup-v$cleanTag.exe"
        }
        return File(getUpdatesDirectory(), fileName)
    }

    fun isAppInstallerDownloaded(update: PendingUpdate): Boolean {
        val file = getAppInstallerFile(update.newVersion)
        if (!file.exists()) return false
        val len = file.length()
        if (len <= 0L) return false
        if (update.assetSize > 0L && len < update.assetSize) return false
        val expected = update.expectedSha256
        if (!expected.isNullOrBlank()) {
            val actual = computeSha256(file)
            if (!actual.equals(expected, ignoreCase = true)) {
                try { file.delete() } catch (e: Throwable) { AppLogger.w("UnifiedUpdateManager: Failed to delete corrupt installer ${file.name}", e) }
                return false
            }
        }
        return true
    }

    fun cleanOldInstallers(keepVersion: String) {
        try {
            val keepName = getAppInstallerFile(keepVersion).name
            getUpdatesDirectory().listFiles()?.forEach { f ->
                if (f.isFile && (f.name.startsWith("CloudStream-Setup-") || f.name.startsWith("CloudStream-Portable-") || f.name.startsWith("CloudStream-Desktop-") || f.name.endsWith(".tmp", ignoreCase = true)) && f.name != keepName) {
                    f.delete()
                }
            }
        } catch (e: Exception) {
            AppLogger.w("UnifiedUpdateManager: Failed to clean old installers", e)
        }
    }

    fun extractVersionFromInstallerFileName(fileName: String): String? {
        val lower = fileName.lowercase()
        if (!lower.endsWith(".exe") && !lower.endsWith(".msi") && !lower.endsWith(".zip")) return null
        if (!lower.startsWith("cloudstream-setup") &&
            !lower.startsWith("cloudstream-portable") &&
            !lower.startsWith("cloudstream-desktop")
        ) return null

        val stripped = fileName
            .removePrefix("CloudStream-Setup-v")
            .removePrefix("CloudStream-Setup-")
            .removePrefix("CloudStream-Desktop-v")
            .removePrefix("CloudStream-Desktop-")
            .removePrefix("CloudStream-Portable-v")
            .removePrefix("CloudStream-Portable-")
            .substringBefore("-windows")
            .substringBefore(".exe")
            .substringBefore(".msi")
            .substringBefore(".zip")

        return stripped.ifBlank { null }
    }

    fun cleanCompletedInstallers(targetDirectory: File = getUpdatesDirectory(), currentVersion: String = AppConfig.APP_VERSION) {
        try {
            if (!targetDirectory.exists()) return
            targetDirectory.listFiles()?.forEach { f ->
                if (f.isFile) {
                    if (f.name.endsWith(".tmp", ignoreCase = true) || f.name.endsWith(".part", ignoreCase = true)) {
                        f.delete()
                    } else {
                        val ver = extractVersionFromInstallerFileName(f.name)
                        if (ver != null && com.lagradost.cloudstream3.desktop.AppUpdater.compareVersions(ver, currentVersion) <= 0) {
                            f.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("UnifiedUpdateManager: Failed to clean completed installers", e)
        }
    }

    fun downloadAppUpdate(update: PendingUpdate, onComplete: ((File) -> Unit)? = null): Boolean {
        val isSupportedPackage = update.downloadUrl.endsWith(".exe", ignoreCase = true) ||
            update.downloadUrl.endsWith(".zip", ignoreCase = true) ||
            update.downloadUrl.endsWith(".msi", ignoreCase = true)
        if (!isSupportedPackage) {
            try {
                update.releaseHtmlUrl?.let { Desktop.getDesktop().browse(URI(it)) }
            } catch (e: Exception) {
                AppLogger.e("Failed to open update browser URL", e)
            }
            return false
        }

        cleanOldInstallers(update.newVersion)
        val targetFile = getAppInstallerFile(update.newVersion)
        AppDownloadManager.startDownload(
            id = "app_update",
            title = "CloudStream Desktop ${update.newVersion}",
            url = update.downloadUrl,
            targetFile = targetFile,
            onComplete = { file ->
                val expected = update.expectedSha256
                if (expected.isNullOrBlank()) {
                    AppLogger.e("UnifiedUpdateManager: No expected SHA-256 available for ${file.name}; refusing unverified installation.")
                    file.delete()
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                        "Update verification failed: Missing release checksum. Please update manually via GitHub."
                    )
                    return@startDownload
                }
                val actual = computeSha256(file)
                if (!actual.equals(expected, ignoreCase = true)) {
                    AppLogger.e("UnifiedUpdateManager: SHA-256 verification failed for ${file.name}. Expected: $expected, Actual: $actual")
                    file.delete()
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                        "Update verification failed: Checksum mismatch. Download discarded for security."
                    )
                    return@startDownload
                }
                AppLogger.i("UnifiedUpdateManager: SHA-256 verified successfully for ${file.name}: $actual")
                onComplete?.invoke(file)
            },
        )
        return true
    }

    fun launchInstallerAndExit(file: File) {
        if (!file.exists()) return
        try {
            com.lagradost.cloudstream3.desktop.sandbox.SandboxedPluginManager.close()
        } catch (t: Throwable) {
            AppLogger.w("UnifiedUpdateManager: Failed to close sandboxed plugin manager during update shutdown", t)
        }
        try {
            com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.binary.stop()
        } catch (t: Throwable) {
            AppLogger.w("UnifiedUpdateManager: Failed to stop torrent binary during update shutdown", t)
        }
        try {
            com.lagradost.runtime.loader.ExtensionLoader.unloadAllPlugins()
        } catch (t: Throwable) {
            AppLogger.w("UnifiedUpdateManager: Failed to unload plugins during update shutdown", t)
        }
        try {
            if (file.name.endsWith(".msi", ignoreCase = true)) {
                try {
                    ProcessBuilder("msiexec.exe", "/i", file.absolutePath).start()
                } catch (e: Exception) {
                    AppLogger.e("msiexec primary launch failed, falling back to desktop open", e)
                    Desktop.getDesktop().open(file)
                }
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
            } else {
                ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-WindowStyle", "Hidden",
                    "-Command",
                    "Start-Process",
                    "\"${file.absolutePath}\"",
                    "-Verb", "RunAs",
                ).start()
            }
        } catch (e: Exception) {
            AppLogger.e("UnifiedUpdateManager primary launch failed, trying cmd start fallback", e)
            try {
                ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "\"${file.absolutePath}\"").start()
            } catch (e2: Exception) {
                AppLogger.e("UnifiedUpdateManager cmd start fallback failed", e2)
                return
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            kotlinx.coroutines.delay(400)
            kotlin.system.exitProcess(0)
        }
    }

    fun showDialogForUpdate(update: PendingUpdate) {
        _activeDialogUpdate.value = update
    }

    fun dismissDialog() {
        _activeDialogUpdate.value = null
    }

    fun installUpdate(update: PendingUpdate) {
        when (update.type) {
            UpdateType.APP_CLIENT -> {
                if (isAppInstallerDownloaded(update)) {
                    val file = getAppInstallerFile(update.newVersion)
                    val expected = update.expectedSha256
                    if (expected.isNullOrBlank()) {
                        AppLogger.e("UnifiedUpdateManager: No expected SHA-256 available for ${file.name}; refusing unverified installation.")
                        file.delete()
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                            "Update verification failed: Missing release checksum. Please update manually via GitHub."
                        )
                        dismissDialog()
                        return
                    }
                    val actual = computeSha256(file)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        AppLogger.e("UnifiedUpdateManager: Cached installer SHA-256 mismatch. Expected: $expected, Actual: $actual")
                        file.delete()
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                            "Installer verification failed: Checksum mismatch. File discarded for security."
                        )
                        dismissDialog()
                        return
                    }
                    if (PlatformPaths.isPortable) {
                        try {
                            Desktop.getDesktop().open(file)
                        } catch (e: Exception) {
                            AppLogger.e("Failed to open portable zip", e)
                        }
                    } else {
                        dismissDialog()
                        launchInstallerAndExit(file)
                    }
                } else {
                    val started = downloadAppUpdate(update) {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess(
                            "Update ${update.newVersion} downloaded. Ready to install."
                        )
                        _activeDialogUpdate.value = update
                    }
                    if (!started) {
                        dismissDialog()
                    }
                }
            }
            UpdateType.TORRENT_ENGINE -> {
                dismissDialog()
                DesktopTorrServerBinary().downloadWithManager {
                    setTorrServerInstalledVersion(update.newVersion)
                    _availableUpdates.update { list -> list.filterNot { it.id == update.id } }
                }
            }
            UpdateType.STREAM_RESOLVER -> {
                dismissDialog()
                DesktopYtDlpBinary().downloadWithManager {
                    setYtDlpInstalledVersion(update.newVersion)
                    _availableUpdates.update { list -> list.filterNot { it.id == update.id } }
                }
            }
        }
    }

    fun compareSemVer(v1: String, v2: String): Int {
        val clean1 = v1.removePrefix("v").substringBefore('-')
        val clean2 = v2.removePrefix("v").substringBefore('-')
        val parts1 = clean1.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    internal fun compareTorrVersions(v1: String, v2: String): Int {
        val clean1 = v1.removePrefix("MatriX.").removePrefix("v")
        val clean2 = v2.removePrefix("MatriX.").removePrefix("v")

        val parts1 = clean1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = clean2.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
