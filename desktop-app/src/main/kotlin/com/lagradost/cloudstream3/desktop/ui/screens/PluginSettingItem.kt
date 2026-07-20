package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.common.storage.PluginSettingSchema
import java.io.File

@Composable
fun PluginSettingItem(
    schema: PluginSettingSchema,
    currentValue: Any?,
    pluginName: String,
    jarFile: File?,
    onValueChanged: (Any?) -> Unit,
) {
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
                        onValueChanged(finalValue)
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
                                    onValueChanged(parsed)
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
                                            if (schema.key.lowercase().contains("disabled")) {
                                                onValueChanged(emptySet<String>())
                                            } else {
                                                onValueChanged(optionsList.toSet())
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Enable All") }
                                    OutlinedButton(
                                        onClick = {
                                            if (schema.key.lowercase().contains("disabled")) {
                                                onValueChanged(optionsList.toSet())
                                            } else {
                                                onValueChanged(emptySet<String>())
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Disable All") }
                                }

                                optionsList.forEach { option ->
                                    val isEnabled = if (disabledSet != null) {
                                        !disabledSet.contains(option)
                                    } else {
                                        enabledSet?.contains(option) ?: true
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = option,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Switch(
                                            checked = isEnabled,
                                            onCheckedChange = { checked ->
                                                val newSet = currentSet.toMutableSet()
                                                if (schema.key.lowercase().contains("disabled")) {
                                                    if (checked) newSet.remove(option) else newSet.add(option)
                                                } else {
                                                    if (checked) newSet.add(option) else newSet.remove(option)
                                                }
                                                onValueChanged(newSet)
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = currentValue?.toString() ?: "",
                                onValueChange = { newValue ->
                                    onValueChanged(newValue)
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
                                onValueChanged(newValue)
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
