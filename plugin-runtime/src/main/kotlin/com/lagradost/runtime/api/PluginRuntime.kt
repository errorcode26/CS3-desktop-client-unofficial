package com.lagradost.runtime.api

import com.lagradost.cloudstream3.plugins.Plugin
import java.io.File

interface PluginRuntime {
    fun loadPlugin(file: File): Plugin
}
