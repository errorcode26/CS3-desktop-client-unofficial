package com.lagradost.cloudstream3.desktop.domain.plugins.interactor

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import kotlinx.coroutines.flow.StateFlow

class GetPluginRepositories(
    private val repository: PluginRepository,
) {
    fun subscribe(): StateFlow<List<RepositoryData>> = repository.savedRepositories

    fun get(): List<RepositoryData> = repository.getSavedRepositories()
}
