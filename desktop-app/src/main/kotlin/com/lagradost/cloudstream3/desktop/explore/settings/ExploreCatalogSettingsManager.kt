package com.lagradost.cloudstream3.desktop.explore.settings

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class ExploreCatalogPreference(
    val key: String,
    val customTitle: String = "",
    val enabled: Boolean = true,
    val order: Int = 0,
)

object ExploreCatalogSettingsManager {
    private const val TAG = "ExploreCatalogSettingsManager"
    private const val PREF_KEY = "cs_desktop_explore_catalog_settings"

    private val mapper = jacksonObjectMapper()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isLoaded = AtomicBoolean(false)

    private val _preferences = MutableStateFlow<Map<String, ExploreCatalogPreference>>(emptyMap())
    val preferences: StateFlow<Map<String, ExploreCatalogPreference>> = _preferences.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        try {
            val json = DesktopDataStore.getKey<String>(PREF_KEY)
            if (!json.isNullOrBlank()) {
                val list = mapper.readValue<List<ExploreCatalogPreference>>(json)
                _preferences.value = list.associateBy { it.key }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed loading catalog preferences: ${e.message}")
        } finally {
            isLoaded.set(true)
        }
    }

    private fun savePreferences() {
        scope.launch {
            try {
                val list = _preferences.value.values.sortedBy { it.order }
                val json = mapper.writeValueAsString(list)
                DesktopDataStore.setKey(PREF_KEY, json)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed saving catalog preferences: ${e.message}")
            }
        }
    }

    /**
     * Synchronizes live discovered catalog descriptors with persisted user preferences.
     * Preserves custom ordering, custom names, and enabled toggles for existing keys.
     * Newly discovered catalogs are appended in order.
     */
    fun syncWithDiscovered(discovered: List<ManifestCatalogDescriptor>) {
        val current = _preferences.value.toMutableMap()
        var maxOrder = current.values.maxOfOrNull { it.order } ?: -1
        var changed = false

        discovered.forEachIndexed { index, descriptor ->
            val key = descriptor.key
            if (!current.containsKey(key)) {
                maxOrder += 1
                current[key] = ExploreCatalogPreference(
                    key = key,
                    customTitle = "",
                    enabled = true,
                    order = maxOrder,
                )
                changed = true
            }
        }

        if (changed || _preferences.value.isEmpty()) {
            _preferences.value = current
            savePreferences()
        }
    }

    fun setEnabled(key: String, enabled: Boolean) {
        val current = _preferences.value.toMutableMap()
        val existing = current[key] ?: ExploreCatalogPreference(key = key, enabled = enabled)
        current[key] = existing.copy(enabled = enabled)
        _preferences.value = current
        savePreferences()
    }

    fun setCustomTitle(key: String, title: String) {
        val current = _preferences.value.toMutableMap()
        val existing = current[key] ?: ExploreCatalogPreference(key = key, customTitle = title)
        current[key] = existing.copy(customTitle = title.trim())
        _preferences.value = current
        savePreferences()
    }

    fun setAllEnabled(keys: List<String>, enabled: Boolean) {
        val current = _preferences.value.toMutableMap()
        keys.forEach { key ->
            val existing = current[key] ?: ExploreCatalogPreference(key = key, enabled = enabled)
            current[key] = existing.copy(enabled = enabled)
        }
        _preferences.value = current
        savePreferences()
    }

    fun moveByIndex(orderedKeys: List<String>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in orderedKeys.indices || toIndex !in orderedKeys.indices || fromIndex == toIndex) return

        val mutableKeys = orderedKeys.toMutableList()
        val item = mutableKeys.removeAt(fromIndex)
        mutableKeys.add(toIndex, item)

        val current = _preferences.value.toMutableMap()
        mutableKeys.forEachIndexed { index, key ->
            val existing = current[key] ?: ExploreCatalogPreference(key = key, order = index)
            current[key] = existing.copy(order = index)
        }
        _preferences.value = current
        savePreferences()
    }

    fun resetToDefaults(discovered: List<ManifestCatalogDescriptor>) {
        val resetMap = discovered.mapIndexed { index, descriptor ->
            descriptor.key to ExploreCatalogPreference(
                key = descriptor.key,
                customTitle = "",
                enabled = true,
                order = index,
            )
        }.toMap()
        _preferences.value = resetMap
        savePreferences()
    }

    /**
     * Filters out disabled descriptors, sorts them according to user order, and
     * applies any user-defined custom title.
     */
    fun filterAndSort(descriptors: List<ManifestCatalogDescriptor>): List<ManifestCatalogDescriptor> {
        val prefs = _preferences.value
        return descriptors
            .filter { descriptor ->
                prefs[descriptor.key]?.enabled != false
            }
            .sortedBy { descriptor ->
                prefs[descriptor.key]?.order ?: Int.MAX_VALUE
            }
            .map { descriptor ->
                val custom = prefs[descriptor.key]?.customTitle
                if (!custom.isNullOrBlank()) {
                    descriptor.copy(name = custom)
                } else {
                    descriptor
                }
            }
    }
}
