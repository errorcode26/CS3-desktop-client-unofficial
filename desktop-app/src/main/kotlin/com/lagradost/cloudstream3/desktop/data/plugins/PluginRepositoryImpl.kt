package com.lagradost.cloudstream3.desktop.data.plugins

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.Repository
import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class PluginRepositoryImpl : PluginRepository {
    override val savedRepositories: StateFlow<List<RepositoryData>>
        get() = DesktopRepositoryManager.savedRepositories

    override val remotePluginIcons: StateFlow<Map<String, String>>
        get() = DesktopRepositoryManager.remotePluginIcons

    override val syncGeneration: StateFlow<Int>
        get() = DesktopRepositoryManager.syncGeneration

    override fun getSavedRepositories(): List<RepositoryData> {
        return DesktopRepositoryManager.getSavedRepositories()
    }

    override suspend fun addRepository(input: String): List<Repository>? = withContext(Dispatchers.IO) {
        DesktopRepositoryManager.addRepositoryFromInput(input)
    }

    override suspend fun removeRepository(url: String) = withContext(Dispatchers.IO) {
        DesktopRepositoryManager.removeRepository(url)
    }

    override fun getAllPlugins(): List<Pair<String, SitePlugin>> {
        return DesktopRepositoryManager.getAllPlugins()
    }

    override suspend fun syncAll(onProgress: (suspend (completed: Int, total: Int) -> Unit)?): DesktopRepositoryManager.SyncReport = withContext(Dispatchers.IO) {
        DesktopRepositoryManager.syncAll(onProgress)
    }

    override suspend fun downloadPlugin(repoName: String, plugin: SitePlugin): File? = withContext(Dispatchers.IO) {
        DesktopRepositoryManager.downloadPlugin(repoName, plugin)
    }

    override fun getPluginIcon(providerName: String?): String? {
        return DesktopRepositoryManager.getPluginIcon(providerName)
    }

    override fun incrementSyncGeneration() {
        DesktopRepositoryManager.incrementSyncGeneration()
    }

    override fun getExtensionsDir(): File {
        return DesktopRepositoryManager.getExtensionsDir()
    }

    override fun readPluginManifest(jarFile: File): Map<String, Any>? {
        return DesktopRepositoryManager.readPluginManifest(jarFile)
    }
}
