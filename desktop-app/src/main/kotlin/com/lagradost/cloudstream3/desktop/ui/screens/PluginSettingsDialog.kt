package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagradost.common.storage.PluginSettingsSchemaRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSettingsDialog(
    pluginName: String,
    prefName: String,
    jarFile: java.io.File? = null,
    onDismiss: () -> Unit,
) {
    val schemaUpdates by PluginSettingsSchemaRegistry.schemaUpdates.collectAsState()

    val activePrefName = remember(schemaUpdates, prefName, pluginName) {
        PluginSettingsSchemaRegistry.resolvePrefName(prefName, pluginName)
    }

    val settings = remember(schemaUpdates, activePrefName) {
        PluginSettingsSchemaRegistry.getSettingsForPlugin(activePrefName, pluginName).sortedWith(
            compareBy<com.lagradost.common.storage.PluginSettingSchema> { getCategoryPriority(it.key) }
                .thenBy { getFriendlyName(it.key) },
        )
    }

    val currentValues = remember(settings) {
        val map = mutableStateMapOf<String, Any?>()
        settings.forEach { schema ->
            val fullKey = if (schema.isGlobal) schema.key else schema.pluginPrefName + schema.key
            val value = if (schema.isGlobal) {
                com.lagradost.cloudstream3.utils.DataStore.getKey<Any>(fullKey) ?: schema.defaultValue
            } else {
                com.lagradost.common.storage.DesktopDataStore.getKey<Any>(fullKey) ?: schema.defaultValue
            }
            map[fullKey] = value
        }
        map
    }

    var hasChanged by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .width(550.dp)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$pluginName Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Configure sub-providers, accounts, and scraper channels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (hasChanged) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "ℹ️ Changes saved. Reload the plugin or close this settings box to apply new provider configurations in real-time.",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(14.dp),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        if (settings.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No configurable options or sub-providers found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            val grouped = settings.groupBy { getCategory(it.key) }

                            // Sort categories so General settings show first, then Stremio, then APIs, then Providers
                            val sortedCategories = grouped.keys.sortedBy { category ->
                                when (category) {
                                    "General Configurations" -> 0
                                    "Accounts & API Integrations" -> 1
                                    "Stremio Catalogs & Addons" -> 2
                                    "Sub-Providers & Channels" -> 3
                                    else -> 4
                                }
                            }

                            sortedCategories.forEach { category ->
                                item {
                                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 1.sp,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), thickness = 2.dp)
                                    }
                                }

                                items(grouped[category]!!, key = { it.key }) { schema ->
                                    val fullKey = if (schema.isGlobal) schema.key else schema.pluginPrefName + schema.key
                                    val currentValue = currentValues[fullKey]

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(16.dp),
                                    ) {
                                        val strVal = currentValue?.toString()
                                        val isBooleanLike = schema.type == "Boolean" ||
                                            strVal == "true" || strVal == "false" ||
                                            schema.defaultValue == "true" || schema.defaultValue == "false" ||
                                            schema.key.startsWith("Provider") || schema.key.endsWith("Enable")

                                        if (isBooleanLike) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                                    Text(
                                                        text = getFriendlyName(schema.key),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    val desc = getDescription(schema.key)
                                                    if (desc.isNotEmpty()) {
                                                        Text(
                                                            text = desc,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                                Switch(
                                                    checked = strVal == "true" || currentValue == true || (currentValue == null && schema.defaultValue == "true"),
                                                    onCheckedChange = { newValue ->
                                                        val finalValue: Any = if (schema.type == "Boolean") newValue else newValue.toString()
                                                        currentValues[fullKey] = finalValue
                                                        hasChanged = true
                                                        if (schema.isGlobal) {
                                                            com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, finalValue)
                                                        } else {
                                                            com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, finalValue)
                                                        }
                                                    },
                                                )
                                            }
                                        } else {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = getFriendlyName(schema.key),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                val desc = getDescription(schema.key)
                                                if (desc.isNotEmpty()) {
                                                    Text(
                                                        text = desc,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(bottom = 12.dp),
                                                    )
                                                }

                                                when (schema.type) {
                                                    "Int", "Long", "Float" -> {
                                                        OutlinedTextField(
                                                            value = currentValue?.toString() ?: "",
                                                            onValueChange = { newValue ->
                                                                val parsed = when (schema.type) {
                                                                    "Int" -> newValue.toIntOrNull()
                                                                    "Long" -> newValue.toLongOrNull()
                                                                    "Float" -> newValue.toFloatOrNull()
                                                                    else -> newValue
                                                                }
                                                                if (parsed != null || newValue.isEmpty()) {
                                                                    currentValues[fullKey] = parsed
                                                                    hasChanged = true
                                                                    if (schema.isGlobal) {
                                                                        if (parsed == null) {
                                                                            com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                                                                        } else {
                                                                            com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, parsed)
                                                                        }
                                                                    } else {
                                                                        if (parsed == null) {
                                                                            com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                                                                        } else {
                                                                            com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, parsed)
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                                            ),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth(),
                                                        )
                                                    }
                                                    "StringSet" -> {
                                                        val reflectedSources = remember(pluginName, schema.key) {
                                                            if (schema.key.lowercase().contains("provider") || schema.key.lowercase().contains("source")) {
                                                                try {
                                                                    val classLoader = com.lagradost.runtime.loader.ExtensionLoader.classLoaderToJar.entries
                                                                        .firstOrNull { it.value.absolutePath == jarFile?.absolutePath }?.key
                                                                    val loadedJarFile = if (jarFile != null) {
                                                                        val jvmJar = java.io.File(jarFile.parentFile, jarFile.nameWithoutExtension + "-jvm.jar")
                                                                        if (jvmJar.exists()) jvmJar else jarFile
                                                                    } else {
                                                                        null
                                                                    }
                                                                    if (classLoader != null && loadedJarFile != null) {
                                                                        var foundList: List<String>? = null
                                                                        java.util.zip.ZipFile(loadedJarFile).use { zip ->
                                                                            val entries = zip.entries()
                                                                            while (entries.hasMoreElements()) {
                                                                                val entry = entries.nextElement()
                                                                                if (entry.name.endsWith(".class") && !entry.name.contains("$")) {
                                                                                    val className = entry.name.removeSuffix(".class").replace("/", ".")
                                                                                    try {
                                                                                        val clazz = classLoader.loadClass(className)
                                                                                        val method = clazz.methods.firstOrNull {
                                                                                            (it.name == "buildProviders" || it.name == "getProviders" || it.name == "getSources" || it.name == "buildSources" || it.name == "listProviders" || it.name == "listSources") &&
                                                                                                it.parameterCount == 0 &&
                                                                                                java.lang.reflect.Modifier.isStatic(it.modifiers)
                                                                                        }
                                                                                        if (method != null) {
                                                                                            val list = method.invoke(null) as? List<*>
                                                                                            if (list != null) {
                                                                                                foundList = list.mapNotNull { provider ->
                                                                                                    if (provider == null) return@mapNotNull null
                                                                                                    try {
                                                                                                        provider.javaClass.getMethod("getId").invoke(provider)?.toString()
                                                                                                    } catch (e: Exception) {
                                                                                                        try {
                                                                                                            provider.javaClass.getMethod("getName").invoke(provider)?.toString()
                                                                                                        } catch (e: Exception) {
                                                                                                            try {
                                                                                                                provider.javaClass.getMethod("getKey").invoke(provider)?.toString()
                                                                                                            } catch (e: Exception) {
                                                                                                                try {
                                                                                                                    provider.javaClass.getField("id").get(provider)?.toString()
                                                                                                                } catch (e: Exception) {
                                                                                                                    try {
                                                                                                                        provider.javaClass.getField("name").get(provider)?.toString()
                                                                                                                    } catch (e: Exception) {
                                                                                                                        try {
                                                                                                                            provider.javaClass.getField("key").get(provider)?.toString()
                                                                                                                        } catch (e: Exception) {
                                                                                                                            provider.toString()
                                                                                                                        }
                                                                                                                    }
                                                                                                                }
                                                                                                            }
                                                                                                        }
                                                                                                    }
                                                                                                }.sorted()
                                                                                                break
                                                                                            }
                                                                                        }
                                                                                    } catch (t: Throwable) {
                                                                                        // Keep scanning
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                        foundList
                                                                    } else {
                                                                        null
                                                                    }
                                                                } catch (e: Exception) {
                                                                    null
                                                                }
                                                            } else {
                                                                null
                                                            }
                                                        }

                                                        val defaultSet = (schema.defaultValue as? Set<*>)?.map { it.toString() }?.toSet() ?: emptySet()
                                                        val currentSet = (currentValue as? Set<*>)?.map { it.toString() }?.toSet() ?: emptySet()
                                                        val optionsList = reflectedSources?.takeIf { it.isNotEmpty() } ?: (defaultSet + currentSet).toList().sorted()

                                                        if (optionsList.isNotEmpty()) {
                                                            val disabledSet = if (schema.key.lowercase().contains("disabled")) {
                                                                currentSet
                                                            } else {
                                                                null
                                                            }

                                                            val enabledSet = if (schema.key.lowercase().contains("disabled")) {
                                                                null
                                                            } else {
                                                                currentSet
                                                            }

                                                            Column(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                                ) {
                                                                    OutlinedButton(
                                                                        onClick = {
                                                                            val nextSet = if (disabledSet != null) emptySet<String>() else optionsList.toSet()
                                                                            currentValues[fullKey] = nextSet
                                                                            hasChanged = true
                                                                            if (schema.isGlobal) {
                                                                                com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, nextSet)
                                                                            } else {
                                                                                com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, nextSet)
                                                                            }
                                                                        },
                                                                        modifier = Modifier.weight(1f),
                                                                    ) {
                                                                        Text(if (disabledSet != null) "Enable All" else "Select All", style = MaterialTheme.typography.labelMedium)
                                                                    }
                                                                    OutlinedButton(
                                                                        onClick = {
                                                                            val nextSet = if (disabledSet != null) optionsList.toSet() else emptySet<String>()
                                                                            currentValues[fullKey] = nextSet
                                                                            hasChanged = true
                                                                            if (schema.isGlobal) {
                                                                                com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, nextSet)
                                                                            } else {
                                                                                com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, nextSet)
                                                                            }
                                                                        },
                                                                        modifier = Modifier.weight(1f),
                                                                    ) {
                                                                        Text(if (disabledSet != null) "Disable All" else "Deselect All", style = MaterialTheme.typography.labelMedium)
                                                                    }
                                                                }

                                                                Spacer(modifier = Modifier.height(4.dp))

                                                                optionsList.chunked(2).forEach { rowSources ->
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                                    ) {
                                                                        rowSources.forEach { source ->
                                                                            val isChecked = if (disabledSet != null) {
                                                                                !disabledSet.contains(source)
                                                                            } else {
                                                                                enabledSet?.contains(source) == true
                                                                            }
                                                                            Row(
                                                                                modifier = Modifier.weight(1f),
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                            ) {
                                                                                Checkbox(
                                                                                    checked = isChecked,
                                                                                    onCheckedChange = { checked ->
                                                                                        val nextSet = if (disabledSet != null) {
                                                                                            if (checked) disabledSet - source else disabledSet + source
                                                                                        } else {
                                                                                            val base = enabledSet ?: emptySet()
                                                                                            if (checked) base + source else base - source
                                                                                        }
                                                                                        currentValues[fullKey] = nextSet
                                                                                        hasChanged = true
                                                                                        if (schema.isGlobal) {
                                                                                            com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, nextSet)
                                                                                        } else {
                                                                                            com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, nextSet)
                                                                                        }
                                                                                    },
                                                                                )
                                                                                Text(
                                                                                    text = source.replace("API", "").replace("Api", ""),
                                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                                )
                                                                            }
                                                                        }
                                                                        if (rowSources.size < 2) {
                                                                            Spacer(modifier = Modifier.weight(1f))
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            OutlinedTextField(
                                                                value = (currentValue as? Set<*>)?.joinToString(", ") ?: "",
                                                                onValueChange = { newValue ->
                                                                    val parsed = newValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                                                    currentValues[fullKey] = parsed
                                                                    hasChanged = true
                                                                    if (schema.isGlobal) {
                                                                        if (parsed.isEmpty()) {
                                                                            com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                                                                        } else {
                                                                            com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, parsed)
                                                                        }
                                                                    } else {
                                                                        if (parsed.isEmpty()) {
                                                                            com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                                                                        } else {
                                                                            com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, parsed)
                                                                        }
                                                                    }
                                                                },
                                                                colors = OutlinedTextFieldDefaults.colors(
                                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                                                ),
                                                                modifier = Modifier.fillMaxWidth(),
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        OutlinedTextField(
                                                            value = currentValue?.toString() ?: "",
                                                            onValueChange = { newValue ->
                                                                currentValues[fullKey] = newValue
                                                                hasChanged = true
                                                                if (schema.isGlobal) {
                                                                    if (newValue.isEmpty()) {
                                                                        com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                                                                    } else {
                                                                        com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, newValue)
                                                                    }
                                                                } else {
                                                                    if (newValue.isEmpty()) {
                                                                        com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                                                                    } else {
                                                                        com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, newValue)
                                                                    }
                                                                }
                                                            },
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                                            ),
                                                            modifier = Modifier.fillMaxWidth(),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Bottom Footer Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("Apply & Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Helpers for Logical Categorization and Visual Polish
private fun getCategory(key: String): String {
    val lower = key.lowercase()
    return when {
        lower.contains("stremio") || lower.contains("addon") || lower.contains("catalog") -> "Stremio & External Catalogs"
        lower.contains("key") || lower.contains("token") || lower.contains("auth") || lower.contains("api") || lower.contains("password") || lower.contains("username") -> "Accounts & API Integrations"
        lower.contains("provider") || lower.contains("source") || lower.contains("channel") || lower.contains("extractor") || lower.endsWith("enable") || lower.contains("concurrency") || lower.startsWith("scrape") -> "Scrapers & Engines"
        else -> "General Configurations"
    }
}

private fun getCategoryPriority(key: String): Int {
    return when (getCategory(key)) {
        "General Configurations" -> 0
        "Accounts & API Integrations" -> 1
        "Stremio & External Catalogs" -> 2
        "Scrapers & Engines" -> 3
        else -> 4
    }
}

private fun getFriendlyName(key: String): String {
    var clean = key
    if (clean.startsWith("Provider")) {
        clean = clean.removePrefix("Provider")
    }

    val friendly = clean.replace("_", " ")
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .trim()
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    return friendly
        .replace(" Saved Links", " Links Cache")
        .replace(" Concurrency", " Simultaneous Connections")
}

private fun getDescription(key: String): String {
    val friendly = getFriendlyName(key)
    return when {
        key.startsWith("Provider") -> "Enable or disable the $friendly search scraper channel."
        key.lowercase().contains("concurrency") -> "Set maximum simultaneous connection threads to speed up retrieval."
        key.lowercase().contains("token") || key.lowercase().contains("key") -> "Configure authentication credentials/API key for $friendly."
        key.lowercase().contains("stremio") -> "Configure external streaming catalog source links."
        key.lowercase().contains("disabled") -> "Toggle individual sub-scrapers and data sources for this plugin."
        key.lowercase().contains("enabled") -> "Select which sub-engines are active."
        else -> "Adjust configuration setting for $friendly."
    }
}
