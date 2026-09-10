package com.lagradost.common.storage

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

data class PluginSettingSchema(
    val pluginPrefName: String,
    val key: String,
    val type: String, // "Boolean", "String", "Int", "Long", "Float", "StringSet"
    val defaultValue: Any?,
    val isGlobal: Boolean = false,
    val options: Map<String, String>? = null,
)

object PluginSettingsSchemaRegistry {
    // Map of pluginPrefName (e.g. "CineStream_") to a map of keys and their schemas
    val schemas = ConcurrentHashMap<String, ConcurrentHashMap<String, PluginSettingSchema>>()

    // Observable flow to trigger UI updates when new settings are detected
    val schemaUpdates = MutableStateFlow(0)

    fun registerPrefName(pluginPrefName: String) {
        schemas.getOrPut(pluginPrefName) { ConcurrentHashMap() }
    }

    @JvmOverloads
    fun register(
        pluginPrefName: String,
        key: String,
        type: String,
        defaultValue: Any?,
        isGlobal: Boolean = false,
        options: Map<String, String>? = null,
    ) {
        val pluginMap = schemas.getOrPut(pluginPrefName) { ConcurrentHashMap() }

        val existing = pluginMap[key]
        if (existing == null || existing.type != type || (existing.options == null && options != null)) {
            pluginMap[key] = PluginSettingSchema(pluginPrefName, key, type, defaultValue, isGlobal, options ?: existing?.options)
            schemaUpdates.value++
        }
    }

    /**
     * Keys that represent internal cache, cookie state, or serialized internal ordering
     * rather than human-configurable settings.
     */
    fun isHiddenKey(key: String): Boolean {
        val lower = key.lowercase()
        return lower.startsWith("_") ||
            lower.contains("__") ||
            lower.contains("cookie") ||
            lower.contains("csrf") ||
            lower.contains("clearance") ||
            lower.contains("session_token") ||
            lower.endsWith("_order") ||
            lower.endsWith("_seen") ||
            lower.endsWith("_cache") ||
            lower.endsWith("_history") ||
            lower.endsWith("_index")
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
        val schemasList = schemas[resolved]?.values?.toList() ?: emptyList()
        return schemasList.filterNot { isHiddenKey(it.key) }
    }

    fun hasSettings(pluginPrefName: String, pluginName: String? = null): Boolean {
        val resolved = resolvePrefName(pluginPrefName, pluginName)
        val schemasList = schemas[resolved]?.values?.toList() ?: emptyList()
        return schemasList.any { !isHiddenKey(it.key) }
    }
}
