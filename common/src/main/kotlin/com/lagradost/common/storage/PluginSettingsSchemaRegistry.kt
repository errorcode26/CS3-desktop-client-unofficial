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

    fun registerPrefName(pluginPrefName: String) {
        schemas.getOrPut(pluginPrefName) { ConcurrentHashMap() }
    }

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

    fun resolvePrefName(prefName: String, pluginName: String? = null): String {
        if (schemas.containsKey(prefName) && schemas[prefName]!!.isNotEmpty()) {
            return prefName
        }
        val withUnderscore = if (prefName.endsWith("_")) prefName else "${prefName}_"
        if (schemas.containsKey(withUnderscore) && schemas[withUnderscore]!!.isNotEmpty()) {
            return withUnderscore
        }

        val cleanPref = prefName.removeSuffix("_")
        val cleanName = pluginName?.removeSuffix("_") ?: ""

        val allKeys = schemas.keys
        val match = allKeys.firstOrNull { cleanName.isNotEmpty() && schemas[it]?.isNotEmpty() == true && cleanName.contains(it.removeSuffix("_"), ignoreCase = true) }
            ?: allKeys.firstOrNull { schemas[it]?.isNotEmpty() == true && cleanPref.contains(it.removeSuffix("_"), ignoreCase = true) }
            ?: allKeys.firstOrNull { schemas[it]?.isNotEmpty() == true && it.removeSuffix("_").contains(cleanPref, ignoreCase = true) }
            ?: allKeys.firstOrNull { cleanName.isNotEmpty() && cleanName.contains(it.removeSuffix("_"), ignoreCase = true) }
            ?: allKeys.firstOrNull { cleanPref.contains(it.removeSuffix("_"), ignoreCase = true) }
            ?: prefName
        return match
    }

    fun getSettingsForPlugin(pluginPrefName: String, pluginName: String? = null): List<PluginSettingSchema> {
        val resolved = resolvePrefName(pluginPrefName, pluginName)
        return schemas[resolved]?.values?.toList() ?: emptyList()
    }

    fun hasSettings(pluginPrefName: String, pluginName: String? = null): Boolean {
        val resolved = resolvePrefName(pluginPrefName, pluginName)
        return schemas.containsKey(resolved) && schemas[resolved]!!.isNotEmpty()
    }
}
