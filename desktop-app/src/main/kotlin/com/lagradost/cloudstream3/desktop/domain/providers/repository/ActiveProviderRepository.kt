package com.lagradost.cloudstream3.desktop.domain.providers.repository

import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for active content providers, selected provider state,
 * and key resolution across the application.
 */
interface ActiveProviderRepository {
    val allRealProviders: StateFlow<List<MainAPI>>
    val activeProviderKeys: StateFlow<List<String>>
    val activeProviders: StateFlow<List<MainAPI>>
    val currentSelectedProvider: StateFlow<MainAPI?>

    fun isRealContentProvider(api: MainAPI): Boolean
    fun getProviderKey(api: MainAPI): String
    fun matchesKey(api: MainAPI, key: String): Boolean
    fun refreshProviders()
    fun setActiveProviders(keys: List<String>)
    fun setSelectedProvider(api: MainAPI)
    fun setSelectedProviderByName(name: String, sourcePlugin: String? = null)
}
