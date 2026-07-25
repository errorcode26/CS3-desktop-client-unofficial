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
    val hasChanged: Boolean = false,
) : UiState

sealed interface PluginSettingsUiEvent : UiEvent {
    data class OnInit(val pluginName: String, val prefName: String) : PluginSettingsUiEvent
    data class OnSettingChanged(val schema: PluginSettingSchema, val newValue: Any?) : PluginSettingsUiEvent
    data class OnSchemaUpdated(val updateTick: Int) : PluginSettingsUiEvent
}

sealed interface PluginSettingsUiEffect : UiEffect

class PluginSettingsViewModel : BaseMviViewModel<PluginSettingsUiState, PluginSettingsUiEvent, PluginSettingsUiEffect>(
    initialState = PluginSettingsUiState(),
) {

    override fun handleEvent(event: PluginSettingsUiEvent) {
        when (event) {
            is PluginSettingsUiEvent.OnInit -> {
                updateState { copy(pluginName = event.pluginName, prefName = event.prefName) }
                reloadSettings()
            }
            is PluginSettingsUiEvent.OnSchemaUpdated -> reloadSettings()
            is PluginSettingsUiEvent.OnSettingChanged -> updateSetting(event.schema, event.newValue)
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
                val fullKey = if (schema.isGlobal) schema.key else schema.pluginPrefName + schema.key
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
                )
            }
        }
    }

    private fun updateSetting(schema: PluginSettingSchema, newValue: Any?) {
        val fullKey = if (schema.isGlobal) schema.key else schema.pluginPrefName + schema.key

        // Optimistic UI update
        updateState {
            val updatedValues = currentValues.toMutableMap()
            updatedValues[fullKey] = newValue
            copy(currentValues = updatedValues, hasChanged = true)
        }

        // DB update
        viewModelScope.launch(Dispatchers.IO) {
            if (newValue == null) {
                if (schema.isGlobal) {
                    DataStore.removeKey(fullKey)
                } else {
                    DesktopDataStore.removeKey(fullKey)
                }
            } else {
                if (schema.isGlobal) {
                    DataStore.setKey(fullKey, newValue)
                } else {
                    DesktopDataStore.setKey(fullKey, newValue)
                }
            }
            // Force a refresh of the Home screen provider dropdown
            com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.incrementSyncGeneration()
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
