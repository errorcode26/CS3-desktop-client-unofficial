package com.lagradost.cloudstream3.desktop.updates

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrServerBinary
import com.lagradost.common.logging.AppLogger
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
import java.net.URI

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
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class GitHubApiRelease(
    val tag_name: String,
    val name: String,
    val body: String?,
    val html_url: String,
    val published_at: String,
)

object UnifiedUpdateManager {
    const val PREF_TORRSERVER_VERSION = "TORRSERVER_INSTALLED_VERSION"
    const val DEFAULT_TORRSERVER_VERSION = "MatriX.144.1"
    const val PREF_YTDL_VERSION = "YTDL_INSTALLED_VERSION"
    const val DEFAULT_YTDL_VERSION = "2025.01.26"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()

    private val _availableUpdates = MutableStateFlow<List<PendingUpdate>>(emptyList())
    val availableUpdates: StateFlow<List<PendingUpdate>> = _availableUpdates.asStateFlow()

    private val _activeDialogUpdate = MutableStateFlow<PendingUpdate?>(null)
    val activeDialogUpdate: StateFlow<PendingUpdate?> = _activeDialogUpdate.asStateFlow()

    private var hasCheckedInitial = false

    fun getTorrServerInstalledVersion(): String {
        return DesktopDataStore.getKey<String>(PREF_TORRSERVER_VERSION) ?: DEFAULT_TORRSERVER_VERSION
    }

    fun setTorrServerInstalledVersion(version: String) {
        DesktopDataStore.setKey(PREF_TORRSERVER_VERSION, version)
    }

    fun getYtDlpInstalledVersion(): String {
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
                // Only check TorrServer updates if it is installed or P2P is enabled
                if (DesktopTorrServerBinary().isInstalled()) {
                    checkTorrServerUpdate(force = force)
                } else null
            }
            val ytdlJob = async {
                if (DesktopYtDlpBinary().isInstalled()) {
                    checkYtDlpUpdate(force = force)
                } else null
            }

            val appUpdate = appJob.await()
            val torrUpdate = torrJob.await()
            val ytdlUpdate = ytdlJob.await()

            hasCheckedInitial = true

            // Automatically queue update dialog for app update first, or engine updates
            if (_activeDialogUpdate.value == null) {
                _activeDialogUpdate.value = appUpdate ?: torrUpdate ?: ytdlUpdate
            }
        } catch (e: Exception) {
            AppLogger.e("UnifiedUpdateManager checkAllUpdates failed", e)
        }
    }

    suspend fun checkAppUpdate(force: Boolean = false): PendingUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/${AppConfig.GITHUB_REPO}/releases/latest"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val release = mapper.readValue<GitHubApiRelease>(body)
                    val remoteVersion = release.tag_name.removePrefix("v")
                    val currentVersion = AppConfig.APP_VERSION

                    if (compareSemVer(remoteVersion, currentVersion) > 0) {
                        val update = PendingUpdate(
                            id = "app_client",
                            type = UpdateType.APP_CLIENT,
                            title = "CS3 Desktop Client",
                            currentVersion = "v$currentVersion",
                            newVersion = release.tag_name,
                            releaseNotes = release.body,
                            downloadUrl = release.html_url,
                            releaseHtmlUrl = release.html_url,
                            publishedAt = release.published_at,
                        )
                        _availableUpdates.update { list -> list.filterNot { it.id == update.id } + update }
                        return@withContext update
                    } else {
                        _availableUpdates.update { list -> list.filterNot { it.id == "app_client" } }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("UnifiedUpdateManager checkAppUpdate error: ${e.message}", e)
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
                    val body = response.body?.string() ?: return@withContext null
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
                        _availableUpdates.update { list -> list.filterNot { it.id == update.id } + update }
                        return@withContext update
                    } else {
                        _availableUpdates.update { list -> list.filterNot { it.id == "torrserver" } }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("UnifiedUpdateManager checkTorrServerUpdate error: ${e.message}", e)
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
                    val body = response.body?.string() ?: return@withContext null
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
                        _availableUpdates.update { list -> list.filterNot { it.id == update.id } + update }
                        return@withContext update
                    } else {
                        _availableUpdates.update { list -> list.filterNot { it.id == "ytdl" } }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("UnifiedUpdateManager checkYtDlpUpdate error: ${e.message}", e)
        }
        null
    }

    fun showDialogForUpdate(update: PendingUpdate) {
        _activeDialogUpdate.value = update
    }

    fun dismissDialog() {
        _activeDialogUpdate.value = null
    }

    fun installUpdate(update: PendingUpdate) {
        dismissDialog()
        when (update.type) {
            UpdateType.APP_CLIENT -> {
                // Open official release page for installer download
                try {
                    update.releaseHtmlUrl?.let { Desktop.getDesktop().browse(URI(it)) }
                } catch (e: Exception) {
                    AppLogger.e("Failed to open update browser URL", e)
                }
            }
            UpdateType.TORRENT_ENGINE -> {
                // Download in background using AppDownloadManager with TopBar progress
                DesktopTorrServerBinary().downloadWithManager {
                    setTorrServerInstalledVersion(update.newVersion)
                    _availableUpdates.update { list -> list.filterNot { it.id == update.id } }
                }
            }
            UpdateType.STREAM_RESOLVER -> {
                DesktopYtDlpBinary().downloadWithManager {
                    setYtDlpInstalledVersion(update.newVersion)
                    _availableUpdates.update { list -> list.filterNot { it.id == update.id } }
                }
            }
        }
    }

    internal fun compareSemVer(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
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
