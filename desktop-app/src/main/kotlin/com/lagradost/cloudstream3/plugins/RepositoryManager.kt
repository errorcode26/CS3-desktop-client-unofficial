package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.utils.appScope
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Stub for Android's RepositoryManager class.
 * Aggregator plugins (like MegaProvider) call this to inject repositories.
 */
object RepositoryManager {

    private var syncJob: kotlinx.coroutines.Job? = null
    private val syncMutex = kotlinx.coroutines.sync.Mutex()

    private fun scheduleCatalogSync() {
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            syncMutex.withLock {
                syncJob?.cancel()
                syncJob = appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.delay(600) // Debounce batch repository additions
                    AppLogger.i("RepositoryManager Stub: Triggering debounced catalog sync for newly added repositories...")
                    try {
                        DesktopRepositoryManager.rebuildRemotePluginCatalog()
                        DesktopRepositoryManager.incrementSyncGeneration()
                    } catch (e: Exception) {
                        AppLogger.e("RepositoryManager Stub: Debounced catalog sync failed", e)
                    }
                }
            }
        }
    }

    suspend fun addRepository(repository: RepositoryData) {
        val icon = repository.iconUrl?.trim()?.takeIf { it.isNotEmpty() }
        val name = if (repository.name.isNotBlank()) repository.name else repository.url
        val normalized = RepositoryData(iconUrl = icon, name = name, url = repository.url)
        AppLogger.i("RepositoryManager Stub: Intercepted addRepository for ${normalized.name}")
        // Execute the actual write on a separate thread to escape the plugin's SecurityManager context
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopRepositoryManager.saveRepository(normalized)
            scheduleCatalogSync()
        }.join()
    }

    suspend fun parseRepository(url: String): Repository? {
        AppLogger.i("RepositoryManager Stub: parseRepository called for $url")
        val repo = com.lagradost.cloudstream3.desktop.repo.PluginNetworkClient.fetchRepository(url) ?: return null
        return Repository(
            iconUrl = repo.iconUrl,
            name = repo.name,
            description = repo.description,
            manifestVersion = repo.manifestVersion,
            pluginLists = repo.pluginLists ?: emptyList(),
        ).also {
            AppLogger.i("RepositoryManager Stub: successfully parsed ${it.name}")
        }
    }

    fun getRepositories(): Array<RepositoryData> {
        return DesktopRepositoryManager.getSavedRepositories().toTypedArray()
    }
}

/**
 * Android's internal Repository object used by RepositoryManager
 */
data class Repository(
    val iconUrl: String?,
    val name: String,
    val description: String?,
    val manifestVersion: Int,
    val pluginLists: List<String>,
)
