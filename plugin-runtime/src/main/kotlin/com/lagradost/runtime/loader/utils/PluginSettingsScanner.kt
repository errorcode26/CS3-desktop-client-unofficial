package com.lagradost.runtime.loader.utils

import java.io.File

object PluginSettingsScanner {
    fun scanJarForSettings(pluginName: String, jarFile: File) {
        // No-op: SharedPreferences keys and default values are dynamically registered at runtime
        // when plugins call SharedPreferences methods (e.g. getBoolean, getString).
        // Pre-scanning bytecode with heuristics creates false keys and overrides default values to false,
        // preventing sub-plugins from auto-registering by default.
    }
}

