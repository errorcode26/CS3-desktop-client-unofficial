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
    val storageKey: String = if (isGlobal) key else "$pluginPrefName$key",
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
        storageKey: String? = null,
    ) {
        val pluginMap = schemas.getOrPut(pluginPrefName) { ConcurrentHashMap() }

        val actualStorageKey = storageKey ?: if (isGlobal) key else "$pluginPrefName$key"
        val existing = pluginMap[key]
        if (existing == null || existing.type != type || (existing.options == null && options != null)) {
            pluginMap[key] = PluginSettingSchema(pluginPrefName, key, type, defaultValue, isGlobal, options ?: existing?.options, actualStorageKey)
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
        val exactMatch = schemas.keys.firstOrNull { it.equals(prefName, ignoreCase = true) && schemas[it]?.isNotEmpty() == true }
        if (exactMatch != null) return exactMatch

        val withUnderscore = if (prefName.endsWith("_")) prefName else "${prefName}_"
        val underscoreMatch = schemas.keys.firstOrNull { it.equals(withUnderscore, ignoreCase = true) && schemas[it]?.isNotEmpty() == true }
        if (underscoreMatch != null) return underscoreMatch

        val cleanPref = prefName.removeSuffix("_")
        val cleanPrefMatch = schemas.keys.firstOrNull { it.equals(cleanPref, ignoreCase = true) && schemas[it]?.isNotEmpty() == true }
        if (cleanPrefMatch != null) return cleanPrefMatch

        val cleanName = pluginName?.removeSuffix("_") ?: ""
        if (cleanName.isNotBlank()) {
            val nameWithUnderscore = "${cleanName}_"
            val nameUnderscoreMatch = schemas.keys.firstOrNull { it.equals(nameWithUnderscore, ignoreCase = true) && schemas[it]?.isNotEmpty() == true }
            if (nameUnderscoreMatch != null) return nameUnderscoreMatch

            val nameMatch = schemas.keys.firstOrNull { it.equals(cleanName, ignoreCase = true) && schemas[it]?.isNotEmpty() == true }
            if (nameMatch != null) return nameMatch
        }
        return withUnderscore
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
