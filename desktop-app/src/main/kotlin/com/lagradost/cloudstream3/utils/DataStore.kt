package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object DataStore {
    const val PREFERENCES_NAME = "rebuild_preference"

    @PublishedApi internal val mapper = jacksonObjectMapper()
    private val prefsFile = File(PlatformPaths.sharedPrefsDir, "shared_prefs.json")

    @PublishedApi internal val cache = ConcurrentHashMap<String, Any?>()

    init {
        if (prefsFile.exists()) {
            try {
                val map: Map<String, Any?> = mapper.readValue(prefsFile)
                cache.putAll(map)
            } catch (e: Exception) {
                AppLogger.e("Failed to load shared_prefs.json", e)
            }
        }
    }

    private fun save() {
        try {
            mapper.writeValue(prefsFile, cache.toMap())
        } catch (e: Exception) {
            AppLogger.e("Failed to save shared_prefs.json", e)
        }
    }

    private fun registerKey(key: String, value: Any?) {
        val pluginName = com.lagradost.runtime.loader.ExtensionLoader.getCallingPluginName()
        if (pluginName != null) {
            val type = when (value) {
                is Boolean -> "Boolean"
                is Int -> "Int"
                is Long -> "Long"
                is Float -> "Float"
                is String -> "String"
                is Set<*> -> "StringSet"
                else -> "String"
            }
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(pluginName + "_", key, type, value, true)
        }
    }

    fun <T> setKey(path: String, key: String, value: T) {
        val fullKey = "$path/$key"
        cache[fullKey] = value
        save()
        registerKey(fullKey, value)
    }

    fun android.content.Context.getSharedPrefs(): android.content.SharedPreferences {
        return this.getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
    }

    fun android.content.Context.getDefaultSharedPrefs(): android.content.SharedPreferences {
        return this.getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
    }

    fun <T> setKey(key: String, value: T) {
        cache[key] = value
        save()
        registerKey(key, value)
    }

    inline fun <reified T> getKey(path: String, key: String): T? {
        val fullKey = "$path/$key"
        val v = cache[fullKey]
        val pluginName = com.lagradost.runtime.loader.ExtensionLoader.getCallingPluginName()
        if (pluginName != null) {
            val typeName = T::class.simpleName ?: "String"
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(pluginName + "_", fullKey, typeName, v, true)
        }

        if (v != null) {
            try {
                val json = mapper.writeValueAsString(v)
                return mapper.readValue(json)
            } catch (e: Exception) {
                AppLogger.e("Failed to deserialize key $key", e)
                return v as? T
            }
        }
        return null
    }

    inline fun <reified T> getKey(path: String, key: String, default: T): T {
        return getKey<T>(path, key) ?: default
    }

    inline fun <reified T> getKey(key: String): T? {
        val v = cache[key]
        val pluginName = com.lagradost.runtime.loader.ExtensionLoader.getCallingPluginName()
        if (pluginName != null) {
            val typeName = T::class.simpleName ?: "String"
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(pluginName + "_", key, typeName, v, true)
        }

        if (v != null) {
            try {
                val json = mapper.writeValueAsString(v)
                return mapper.readValue(json)
            } catch (e: Exception) {
                AppLogger.e("Failed to deserialize key $key", e)
                return v as? T
            }
        }
        return null
    }

    inline fun <reified T> getKey(key: String, default: T): T {
        return getKey<T>(key) ?: default
    }

    fun removeKey(path: String, key: String) {
        cache.remove("$path/$key")
        save()
    }

    fun removeKey(key: String) {
        cache.remove(key)
        save()
    }

    fun getFolderName(folder: String, path: String): String {
        return "$folder/$path"
    }

    fun android.content.Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return this.getSharedPrefs().all.keys.filter { it.startsWith(fixedFolder) }
    }

    fun android.content.Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun android.content.Context.containsKey(folder: String, path: String): Boolean {
        return this.getSharedPrefs().contains(getFolderName(folder, path))
    }

    fun android.content.Context.containsKey(path: String): Boolean {
        return this.getSharedPrefs().contains(path)
    }

    fun android.content.Context.removeKey(path: String) {
        val prefs = getSharedPrefs()
        if (prefs.contains(path)) {
            val editor = prefs.edit()
            editor.remove(path)
            editor.apply()
        }
    }

    fun android.content.Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        val editor = getSharedPrefs().edit()
        keys.forEach { value ->
            editor.remove(value)
        }
        editor.apply()
        return keys.size
    }

    fun <T> android.content.Context.setKey(path: String, value: T) {
        val editor = getSharedPrefs().edit()
        try {
            val json = mapper.writeValueAsString(value)
            editor.putString(path, json)
            editor.apply()
        } catch (e: Exception) {
            AppLogger.e("Failed to set pref key $path", e)
        }
    }

    fun <T : Any> android.content.Context.getKey(path: String, valueType: Class<T>): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return null
            return mapper.readValue(json, valueType)
        } catch (e: Exception) {
            AppLogger.e("Failed to get pref key $path", e)
            return null
        }
    }

    fun <T> android.content.Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> android.content.Context.getKey(path: String, defVal: T?): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            return mapper.readValue(json)
        } catch (e: Exception) {
            AppLogger.e("Failed to get pref key $path", e)
            return null
        }
    }

    inline fun <reified T : Any> android.content.Context.getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> android.content.Context.getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> android.content.Context.getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal) ?: defVal
    }
}
