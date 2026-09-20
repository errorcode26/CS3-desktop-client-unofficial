package com.lagradost.cloudstream3.desktop.domain.plugins.interactor

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import kotlinx.coroutines.flow.StateFlow

class GetPluginIcon(
    private val repository: PluginRepository,
) {
    fun subscribeIcons(): StateFlow<Map<String, String>> = repository.remotePluginIcons

    fun get(providerName: String?): String? = repository.getPluginIcon(providerName)
}
