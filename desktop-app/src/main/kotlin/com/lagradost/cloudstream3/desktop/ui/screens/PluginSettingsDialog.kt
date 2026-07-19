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
                                    com.lagradost.cloudstream3.desktop.ui.screens.PluginSettingItem(
                                        schema = schema,
                                        currentValue = currentValue,
                                        pluginName = pluginName,
                                        jarFile = jarFile,
                                        onValueChanged = { newValue ->
                                            currentValues[fullKey] = newValue
                                            hasChanged = true
                                            if (schema.isGlobal) {
                                                if (newValue == null || (newValue is String && newValue.isEmpty())) {
                                                    com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                                                } else {
                                                    com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, newValue)
                                                }
                                            } else {
                                                if (newValue == null || (newValue is String && newValue.isEmpty())) {
                                                    com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                                                } else {
                                                    com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, newValue)
                                                }
                                            }
                                        }
                                    )
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

internal fun getFriendlyName(key: String): String {
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

internal fun getDescription(key: String): String {
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
