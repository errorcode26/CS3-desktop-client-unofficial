package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.providers.repository.ActiveProviderRepository as DomainActiveProviderRepository
import kotlinx.coroutines.flow.StateFlow

const val PREF_ACTIVE_PROVIDERS_KEY = PreferenceKeys.PREF_ACTIVE_PROVIDERS
const val PREF_SELECTED_PROVIDER_KEY = PreferenceKeys.PREF_SELECTED_PROVIDER

/**
 * Single source of truth for active content providers, selected provider state,
 * and key resolution across the entire Desktop application.
 *
 * Backward-compatible delegation wrapper routing to the DI-managed instance in [AppContainerHolder].
 */
object ActiveProviderRepository : DomainActiveProviderRepository {
    private val delegate: DomainActiveProviderRepository
        get() = AppContainerHolder.container.activeProviderRepository

    override val allRealProviders: StateFlow<List<MainAPI>>
        get() = delegate.allRealProviders

    override val activeProviderKeys: StateFlow<List<String>>
        get() = delegate.activeProviderKeys

    override val activeProviders: StateFlow<List<MainAPI>>
        get() = delegate.activeProviders

    override val currentSelectedProvider: StateFlow<MainAPI?>
        get() = delegate.currentSelectedProvider

    override fun isRealContentProvider(api: MainAPI): Boolean = delegate.isRealContentProvider(api)

    override fun getProviderKey(api: MainAPI): String = delegate.getProviderKey(api)

    override fun matchesKey(api: MainAPI, key: String): Boolean = delegate.matchesKey(api, key)

    override fun refreshProviders() = delegate.refreshProviders()

    override fun setActiveProviders(keys: List<String>) = delegate.setActiveProviders(keys)

    override fun setSelectedProvider(api: MainAPI) = delegate.setSelectedProvider(api)

    override fun setSelectedProviderByName(name: String, sourcePlugin: String?) =
        delegate.setSelectedProviderByName(name, sourcePlugin)
}
