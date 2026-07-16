package com.lagradost.common.storage

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

data class PluginSettingSchema(
    val pluginPrefName: String,
    val key: String,
    val type: String, // "Boolean", "String", "Int", "Long", "Float", "StringSet"
    val defaultValue: Any?,
    val isGlobal: Boolean = false,
)

object PluginSettingsSchemaRegistry {
    // Map of pluginPrefName (e.g. "CineStream_") to a map of keys and their schemas
    val schemas = ConcurrentHashMap<String, ConcurrentHashMap<String, PluginSettingSchema>>()

    // Observable flow to trigger UI updates when new settings are detected
    val schemaUpdates = MutableStateFlow(0)

    fun register(pluginPrefName: String, key: String, type: String, defaultValue: Any?, isGlobal: Boolean = false) {
        val pluginMap = schemas.getOrPut(pluginPrefName) { ConcurrentHashMap() }

        // If the key is already registered with the same type, we don't need to do anything.
        // We only update if it's genuinely new to trigger a flow emission.
        val existing = pluginMap[key]
        if (existing == null || existing.type != type) {
            pluginMap[key] = PluginSettingSchema(pluginPrefName, key, type, defaultValue, isGlobal)
            schemaUpdates.value++
        }
    }

    fun getSettingsForPlugin(pluginPrefName: String): List<PluginSettingSchema> {
        return schemas[pluginPrefName]?.values?.toList() ?: emptyList()
    }

    fun hasSettings(pluginPrefName: String): Boolean {
        return schemas.containsKey(pluginPrefName) && schemas[pluginPrefName]!!.isNotEmpty()
    }
}
