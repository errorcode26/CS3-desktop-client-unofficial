package com.lagradost.cloudstream3.desktop.ui.screens.home

internal fun fuzzyMatchPluginIcon(providerName: String, pluginIcons: Map<String, String>): String? {
    val cleanName = if (providerName.contains("::")) providerName.substringAfter("::") else providerName
    pluginIcons[cleanName]?.let { return it }
    pluginIcons[providerName]?.let { return it }
    val pName = cleanName.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
    return pluginIcons.entries.firstOrNull { (k, _) ->
        val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
        if (kName.length < 3) return@firstOrNull false
        pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
    }?.value
}
