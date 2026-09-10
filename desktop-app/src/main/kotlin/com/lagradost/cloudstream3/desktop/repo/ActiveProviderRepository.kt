package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

const val PREF_ACTIVE_PROVIDERS_KEY = PreferenceKeys.PREF_ACTIVE_PROVIDERS
const val PREF_SELECTED_PROVIDER_KEY = PreferenceKeys.PREF_SELECTED_PROVIDER

/**
 * Single source of truth for active content providers, selected provider state,
 * and key resolution across the entire Desktop application (Home, Search, Explore, Settings).
 */
object ActiveProviderRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _allRealProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    val allRealProviders: StateFlow<List<MainAPI>> = _allRealProviders.asStateFlow()

    private val _activeProviderKeys = MutableStateFlow<List<String>>(emptyList())
    val activeProviderKeys: StateFlow<List<String>> = _activeProviderKeys.asStateFlow()

    private val _activeProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    val activeProviders: StateFlow<List<MainAPI>> = _activeProviders.asStateFlow()

    private val _currentSelectedProvider = MutableStateFlow<MainAPI?>(null)
    val currentSelectedProvider: StateFlow<MainAPI?> = _currentSelectedProvider.asStateFlow()

    init {
        // Initial load from storage
        scope.launch(Dispatchers.IO) {
            val savedKeys = DesktopDataStore.getKey<List<String>>(PREF_ACTIVE_PROVIDERS_KEY)
            val fallbackKey = DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER_KEY)
            val initialKeys = savedKeys ?: listOfNotNull(fallbackKey)
            _activeProviderKeys.value = initialKeys
            refreshProviders()
        }

        // Re-sync whenever repositories/plugins change
        scope.launch {
            DesktopRepositoryManager.syncGeneration.collectLatest {
                refreshProviders()
            }
        }
    }

    fun isRealContentProvider(api: MainAPI): Boolean {
        if (api.name.equals("NONE", ignoreCase = true)) return false
        if (api.providerType == ProviderType.MetaProvider) return false
        return true
    }

    fun getProviderKey(api: MainAPI): String {
        val src = api.sourcePlugin
        if (!src.isNullOrBlank() && src != "built-in") {
            val folder = File(src).parentFile?.name ?: ""
            if (folder.isNotBlank()) return "$folder::${api.name}"
        }
        return api.name
    }

    fun matchesKey(api: MainAPI, key: String): Boolean {
        return getProviderKey(api) == key || api.name == key || api.name == key.substringAfter("::")
    }

    private fun refreshProviders() {
        val allApis = APIHolder.allProviders.filter { isRealContentProvider(it) }
        _allRealProviders.value = allApis

        val currentKeys = _activeProviderKeys.value
        val resolvedActive = currentKeys.mapNotNull { key ->
            allApis.firstOrNull { matchesKey(it, key) }
        }.ifEmpty {
            allApis.take(1)
        }

        _activeProviders.value = resolvedActive

        val selected = _currentSelectedProvider.value
        if (selected == null || allApis.none { it.name == selected.name && it.sourcePlugin == selected.sourcePlugin }) {
            _currentSelectedProvider.value = resolvedActive.firstOrNull() ?: allApis.firstOrNull()
        }
    }

    fun setActiveProviders(keys: List<String>) {
        _activeProviderKeys.value = keys
        refreshProviders()
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PREF_ACTIVE_PROVIDERS_KEY, keys)
        }
    }

    fun setSelectedProvider(api: MainAPI) {
        val previous = _currentSelectedProvider.value
        if (previous != null && previous.name != api.name) {
            com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.closeProxySession()
        }
        _currentSelectedProvider.value = api
        val key = getProviderKey(api)
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PREF_SELECTED_PROVIDER_KEY, key)
        }
    }

    fun setSelectedProviderByName(name: String, sourcePlugin: String? = null) {
        val all = _allRealProviders.value
        val matched = all.firstOrNull {
            it.name == name && (sourcePlugin == null || it.sourcePlugin == sourcePlugin)
        } ?: all.firstOrNull { it.name == name }
        if (matched != null) {
            setSelectedProvider(matched)
        }
    }
}
