package com.lagradost.cloudstream3.desktop.domain.plugins.interactor

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.desktop.repo.SitePlugin

class GetAvailablePlugins(
    private val repository: PluginRepository,
) {
    fun get(): List<Pair<String, SitePlugin>> {
        return repository.getAllPlugins()
    }
}
