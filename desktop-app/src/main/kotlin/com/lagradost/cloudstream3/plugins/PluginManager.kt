package com.lagradost.cloudstream3.plugins

import com.lagradost.runtime.loader.ExtensionLoader

/**
 * Stub for Android's PluginManager class.
 */
object PluginManager {
    fun getPluginsOnline(): Array<PluginData> {
        // Return empty array because desktop app handles plugins differently
        return emptyArray()
    }

    fun getPluginsLocal(): Array<PluginData> {
        return emptyArray()
    }

    fun unloadPlugin(absolutePath: String) {
        ExtensionLoader.unloadPlugin(absolutePath)
    }
}
