package com.lagradost.cloudstream3.desktop.domain.plugins.interactor

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager

class SyncPluginRepositories(
    private val repository: PluginRepository,
) {
    suspend fun await(onProgress: (suspend (completed: Int, total: Int) -> Unit)? = null): DesktopRepositoryManager.SyncReport {
        val report = repository.syncAll(onProgress)
        repository.incrementSyncGeneration()
        return report
    }
}
