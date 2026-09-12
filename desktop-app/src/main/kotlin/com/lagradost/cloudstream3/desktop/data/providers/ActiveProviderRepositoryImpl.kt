package com.lagradost.cloudstream3.desktop.data.providers

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.domain.providers.repository.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
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

class ActiveProviderRepositoryImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ActiveProviderRepository {
    private val _allRealProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    override val allRealProviders: StateFlow<List<MainAPI>> = _allRealProviders.asStateFlow()

    private val _activeProviderKeys = MutableStateFlow<List<String>>(emptyList())
    override val activeProviderKeys: StateFlow<List<String>> = _activeProviderKeys.asStateFlow()

    private val _activeProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    override val activeProviders: StateFlow<List<MainAPI>> = _activeProviders.asStateFlow()

    private val _currentSelectedProvider = MutableStateFlow<MainAPI?>(null)
    override val currentSelectedProvider: StateFlow<MainAPI?> = _currentSelectedProvider.asStateFlow()

    init {
        // Initial load from storage
        scope.launch(Dispatchers.IO) {
            val savedKeys = DesktopDataStore.getKey<List<String>>(PreferenceKeys.PREF_ACTIVE_PROVIDERS)
            val fallbackKey = DesktopDataStore.getKey<String>(PreferenceKeys.PREF_SELECTED_PROVIDER)
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

    override fun isRealContentProvider(api: MainAPI): Boolean {
        if (api.name.equals("NONE", ignoreCase = true)) return false
        if (api.providerType == ProviderType.MetaProvider) return false
        return true
    }

    override fun getProviderKey(api: MainAPI): String {
        val src = api.sourcePlugin
        if (!src.isNullOrBlank() && src != "built-in") {
            val folder = File(src).parentFile?.name ?: ""
            if (folder.isNotBlank()) return "$folder::${api.name}"
        }
        return api.name
    }

    override fun matchesKey(api: MainAPI, key: String): Boolean {
        return getProviderKey(api) == key || api.name == key || api.name == key.substringAfter("::")
    }

    override fun refreshProviders() {
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

    override fun setActiveProviders(keys: List<String>) {
        _activeProviderKeys.value = keys
        refreshProviders()
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PreferenceKeys.PREF_ACTIVE_PROVIDERS, keys)
        }
    }

    override fun setSelectedProvider(api: MainAPI) {
        val previous = _currentSelectedProvider.value
        if (previous != null && previous.name != api.name) {
            com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.closeProxySession()
        }
        _currentSelectedProvider.value = api
        val key = getProviderKey(api)
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PreferenceKeys.PREF_SELECTED_PROVIDER, key)
        }
    }

    override fun setSelectedProviderByName(name: String, sourcePlugin: String?) {
        val all = _allRealProviders.value
        val matched = all.firstOrNull {
            it.name == name && (sourcePlugin == null || it.sourcePlugin == sourcePlugin)
        } ?: all.firstOrNull { it.name == name }
        if (matched != null) {
            setSelectedProvider(matched)
        }
    }
}
