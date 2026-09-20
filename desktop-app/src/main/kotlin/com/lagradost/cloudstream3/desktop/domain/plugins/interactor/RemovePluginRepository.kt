package com.lagradost.cloudstream3.desktop.domain.plugins.interactor

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository

class RemovePluginRepository(
    private val repository: PluginRepository,
) {
    suspend fun await(url: String) {
        repository.removeRepository(url)
        repository.incrementSyncGeneration()
    }
}
