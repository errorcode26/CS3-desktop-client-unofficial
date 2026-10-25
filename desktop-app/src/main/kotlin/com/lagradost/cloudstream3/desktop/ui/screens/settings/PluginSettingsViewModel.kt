package com.lagradost.cloudstream3.desktop.ui.screens.settings

import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.PluginSettingSchema
import com.lagradost.common.storage.PluginSettingsSchemaRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class PluginSettingsUiState(
    val pluginName: String = "",
    val prefName: String = "",
    val activePrefName: String = "",
    val settings: List<PluginSettingSchema> = emptyList(),
    val currentValues: Map<String, Any?> = emptyMap(),
    val pendingChanges: Map<String, Pair<PluginSettingSchema, Any?>> = emptyMap(),
    val hasChanged: Boolean = false,
    val isLoading: Boolean = true,
) : UiState

sealed interface PluginSettingsUiEvent : UiEvent {
    data class OnInit(val pluginName: String, val prefName: String) : PluginSettingsUiEvent
    data class OnSettingChanged(val schema: PluginSettingSchema, val newValue: Any?) : PluginSettingsUiEvent
    data class OnSchemaUpdated(val updateTick: Int) : PluginSettingsUiEvent
    data object OnApplyChanges : PluginSettingsUiEvent
}

sealed interface PluginSettingsUiEffect : UiEffect

class PluginSettingsViewModel : BaseMviViewModel<PluginSettingsUiState, PluginSettingsUiEvent, PluginSettingsUiEffect>(
    initialState = PluginSettingsUiState(),
) {

    override fun handleEvent(event: PluginSettingsUiEvent) {
        when (event) {
            is PluginSettingsUiEvent.OnInit -> {
                updateState { copy(pluginName = event.pluginName, prefName = event.prefName, isLoading = true) }
                reloadSettings()
            }
            is PluginSettingsUiEvent.OnSchemaUpdated -> reloadSettings()
            is PluginSettingsUiEvent.OnSettingChanged -> updateSetting(event.schema, event.newValue)
            is PluginSettingsUiEvent.OnApplyChanges -> applyChanges()
        }
    }

    private fun reloadSettings() {
        val state = uiState.value
        if (state.pluginName.isEmpty()) return

        val activePrefName = PluginSettingsSchemaRegistry.resolvePrefName(state.prefName, state.pluginName)
        val settings = PluginSettingsSchemaRegistry.getSettingsForPlugin(activePrefName, state.pluginName).sortedWith(
            compareBy<PluginSettingSchema> { getCategoryPriority(it.key) }
                .thenBy { getFriendlyName(it.key) },
        )

        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, Any?>()
            settings.forEach { schema ->
                val fullKey = schema.storageKey
                val value = if (schema.isGlobal) {
                    DataStore.getKey<Any>(fullKey) ?: schema.defaultValue
                } else {
                    DesktopDataStore.getKey<Any>(fullKey) ?: schema.defaultValue
                }
                map[fullKey] = value
            }

            updateState {
                copy(
                    activePrefName = activePrefName,
                    settings = settings,
                    currentValues = map,
                    isLoading = false,
                )
            }
        }
    }

    private fun updateSetting(schema: PluginSettingSchema, newValue: Any?) {
        val fullKey = schema.storageKey

        val allowed = try {
            androidx.preference.PreferenceManager.dispatchPreferenceChange(schema.key, newValue)
        } catch (e: Throwable) {
            com.lagradost.common.logging.AppLogger.e("Error invoking OnPreferenceChangeListener for ${schema.key}", e)
            true
        }
        if (!allowed) {
            com.lagradost.common.logging.AppLogger.w("Plugin rejected preference change for ${schema.key}")
            return
        }

        // Optimistic UI update — actual write deferred to applyChanges()
        updateState {
            val updatedValues = currentValues.toMutableMap().also { it[fullKey] = newValue }
            val updatedPending = pendingChanges.toMutableMap().also { it[fullKey] = schema to newValue }
            copy(currentValues = updatedValues, pendingChanges = updatedPending, hasChanged = true)
        }
    }

    private fun applyChanges() {
        val pending = uiState.value.pendingChanges
        if (pending.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            pending.forEach { (fullKey, pair) ->
                val (schema, newValue) = pair
                if (newValue == null) {
                    if (schema.isGlobal) {
                        DataStore.removeKey(fullKey)
                        DataStore.removeKey(schema.key)
                    } else {
                        DesktopDataStore.removeKey(fullKey)
                        DesktopDataStore.removeKey(schema.key)
                    }
                } else {
                    if (schema.isGlobal) {
                        DataStore.setKey(fullKey, newValue)
                    } else {
                        DesktopDataStore.setKey(fullKey, newValue)
                        if (fullKey != schema.key) DesktopDataStore.setKey(schema.key, newValue)
                    }
                }
            }
            com.lagradost.runtime.loader.ExtensionLoader.filterActiveProviders()
            com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.incrementSyncGeneration()
            com.lagradost.cloudstream3.desktop.sandbox.SandboxedPluginManager.syncSettings(DesktopDataStore.rawKeyCache)
            updateState { copy(pendingChanges = emptyMap(), hasChanged = false) }
        }
    }

    private fun getCategoryPriority(key: String): Int {
        val lKey = key.lowercase()
        return when {
            lKey.contains("domain") || lKey.contains("url") -> 0
            lKey.contains("account") || lKey.contains("login") || lKey.contains("email") -> 1
            lKey.contains("password") || lKey.contains("token") -> 2
            lKey.contains("channel") || lKey.contains("source") -> 3
            else -> 4
        }
    }

    private fun getFriendlyName(key: String): String {
        return key.replace("_", " ").replace("-", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
