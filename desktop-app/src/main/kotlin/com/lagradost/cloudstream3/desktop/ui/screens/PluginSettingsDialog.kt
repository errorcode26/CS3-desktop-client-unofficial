package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    val settings = remember(schemaUpdates) {
        PluginSettingsSchemaRegistry.getSettingsForPlugin(prefName).sortedBy { it.key }
    }

    var showAddCustom by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf("Boolean") }
    var newIsGlobal by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$pluginName Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (settings.isEmpty()) {
                    Text("No settings found for this plugin. You can add them manually below.")
                } else {
                    settings.forEach { schema ->
                        val fullKey = if (schema.isGlobal) schema.key else schema.pluginPrefName + schema.key
                        var currentValue by remember {
                            mutableStateOf(
                                if (schema.isGlobal) {
                                    com.lagradost.cloudstream3.utils.DataStore.getKey<Any>(fullKey) ?: schema.defaultValue
                                } else {
                                    com.lagradost.common.storage.DesktopDataStore.getKey<Any>(fullKey) ?: schema.defaultValue
                                },
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            when (schema.type) {
                                "Boolean" -> {
                                    Text(
                                        text = schema.key.replace("_", " ").capitalize(),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = (currentValue as? Boolean) == true,
                                        onCheckedChange = { newValue ->
                                            currentValue = newValue
                                            if (schema.isGlobal) {
                                                if (newValue == null) {
                                                    com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                                                } else {
                                                    com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, newValue)
                                                }
                                            } else {
                                                if (newValue == null) {
                                                    com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                                                } else {
                                                    com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, newValue)
                                                }
                                            }
                                        },
                                    )
                                }
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
                                                currentValue = parsed
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
                                        label = { Text(schema.key) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                "StringSet" -> {
                                    OutlinedTextField(
                                        value = (currentValue as? Set<*>)?.joinToString(", ") ?: "",
                                        onValueChange = { newValue ->
                                            val parsed = newValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                            currentValue = parsed
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
                                        label = { Text(schema.key + " (comma separated)") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                else -> {
                                    // String and others
                                    OutlinedTextField(
                                        value = currentValue?.toString() ?: "",
                                        onValueChange = { newValue ->
                                            currentValue = newValue
                                            if (schema.isGlobal) {
                                                if (newValue == null) {
                                                    com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                                                } else {
                                                    com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, newValue)
                                                }
                                            } else {
                                                if (newValue == null) {
                                                    com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                                                } else {
                                                    com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, newValue)
                                                }
                                            }
                                        },
                                        label = { Text(schema.key) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }

                Divider()
                
                if (!showAddCustom) {
                    OutlinedButton(
                        onClick = { showAddCustom = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Custom Setting")
                    }
                } else {
                    Text("Add Custom Setting (Advanced)", style = MaterialTheme.typography.titleMedium)
                    
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("Exact Key Name (e.g. 'Hindi')") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Boolean", "String", "Int", "StringSet").forEach { t ->
                            FilterChip(
                                selected = newType == t,
                                onClick = { newType = t },
                                label = { Text(t) }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newIsGlobal, onCheckedChange = { newIsGlobal = it })
                        Text("Is Global (usually yes)")
                    }

                    Button(
                        onClick = {
                            if (newKey.isNotBlank()) {
                                val defVal: Any = when (newType) {
                                    "Boolean" -> true
                                    "Int" -> 0
                                    "StringSet" -> emptySet<String>()
                                    else -> ""
                                }
                                PluginSettingsSchemaRegistry.register(prefName, newKey, newType, defVal, newIsGlobal)
                                showAddCustom = false
                                newKey = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
