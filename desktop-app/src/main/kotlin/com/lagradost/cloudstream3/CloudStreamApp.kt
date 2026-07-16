package com.lagradost.cloudstream3

import android.content.Context
import android.content.DesktopContextProvider
import java.lang.ref.WeakReference

/** Minimal stub of CloudStreamApp used by some plugins. Provides access to a global context
 * and helpers for storing simple keys in SharedPreferences. This is intentionally minimal
 * and may be expanded if plugins require more functionality. */
class CloudStreamApp {
    companion object {
        private var _context: WeakReference<Context>? = null
        var context: Context?
            get() = _context?.get() ?: DesktopContextProvider.context
            private set(value) {
                _context = if (value == null) null else WeakReference(value)
            }

        /** Provide activity retrieval similar to Android implementation */
        tailrec fun Context?.getActivity(): android.app.Activity? {
            val ctx = this ?: return null
            return when (ctx) {
                is android.app.Activity -> ctx
                is android.content.ContextWrapper -> ctx.baseContext.getActivity()
                else -> null
            }
        }

        // Simple key/value helpers using DesktopKVStore
        fun <T> setKey(folder: String, path: String, value: T) {
            if (value == null) {
                com.lagradost.common.storage.DesktopDataStore.removeKey(path)
            } else {
                com.lagradost.common.storage.DesktopDataStore.setKey(path, value)
            }
        }

        fun <T> setKey(path: String, value: T) {
            if (value == null) {
                com.lagradost.common.storage.DesktopDataStore.removeKey(path)
            } else {
                com.lagradost.common.storage.DesktopDataStore.setKey(path, value)
            }
        }

        fun removeKey(folder: String, path: String) {
            com.lagradost.common.storage.DesktopDataStore.removeKey(path)
        }

        inline fun <reified T> getKey(path: String, defVal: T?): T? {
            val type = when (defVal) {
                is Boolean -> "Boolean"
                is Int -> "Int"
                is Long -> "Long"
                is Float -> "Float"
                is String -> "String"
                else -> "String"
            }
            // For CloudStreamApp.getKey, plugins usually prefix the path themselves (e.g., "CineStream_key").
            // We can just use "global" as the prefName, but we want it to show up on the provider's gear icon.
            // If the key has an underscore, we can assume the first part is the plugin name.
            val prefName = if (path.contains("_")) path.substringBefore("_") + "_" else "global_"
            val keyWithoutPrefix = if (path.contains("_")) path.substringAfter("_") else path

            com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(prefName, keyWithoutPrefix, type, defVal, false)

            return com.lagradost.common.storage.DesktopDataStore.getKey<T>(path) ?: defVal
        }
    }
}
