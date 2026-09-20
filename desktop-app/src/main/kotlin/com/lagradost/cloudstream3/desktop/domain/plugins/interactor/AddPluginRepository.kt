package com.lagradost.cloudstream3.desktop.domain.plugins.interactor

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.desktop.repo.Repository

class AddPluginRepository(
    private val repository: PluginRepository,
) {
    suspend fun await(input: String): List<Repository>? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val added = repository.addRepository(trimmed)
        if (!added.isNullOrEmpty()) {
            repository.incrementSyncGeneration()
        }
        return added
    }
}
