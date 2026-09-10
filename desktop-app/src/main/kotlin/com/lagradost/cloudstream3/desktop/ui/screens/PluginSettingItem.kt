package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
        val options = schema.options
        if (options != null && options.isNotEmpty()) {
            val currentValueStr = currentValue?.toString() ?: schema.defaultValue?.toString() ?: options.values.firstOrNull() ?: ""
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
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    options.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onValueChanged(value) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (value == currentValueStr),
                                onClick = { onValueChanged(value) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        } else {
            val isBooleanLike = schema.type == "Boolean" ||
                schema.defaultValue is Boolean ||
                schema.defaultValue == "true" || schema.defaultValue == "false" ||
                currentValue is Boolean ||
                currentValue == "true" || currentValue == "false" ||
                schema.key.startsWith("Provider") || schema.key.endsWith("Enable")

            if (isBooleanLike) {
                val defaultVal = schema.defaultValue
                val isChecked = when (currentValue) {
                is Boolean -> currentValue
                is String -> currentValue.equals("true", ignoreCase = true)
                else -> when (defaultVal) {
                    is Boolean -> defaultVal
                    is String -> defaultVal.equals("true", ignoreCase = true)
                    else -> false
                }
            }

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
                    checked = isChecked,
                    onCheckedChange = { newValue ->
                        val finalValue: Any = if (schema.type == "Boolean" || schema.defaultValue is Boolean || currentValue is Boolean) newValue else newValue.toString()
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
                            value = currentValue?.toString() ?: schema.defaultValue?.toString() ?: "",
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
                        // 0ms In-Memory Provider Lookup (Replaces all JAR dissection)
                        val memorySources = remember(pluginName, jarFile) {
                            val jarName = jarFile?.name
                            val jarBaseName = jarFile?.nameWithoutExtension
                            val jarPath = jarFile?.absolutePath

                            val apis = com.lagradost.cloudstream3.APIHolder.apis
                                .filter { api ->
                                    val src = api.sourcePlugin
                                    src == pluginName || src == jarName || src == jarBaseName || src == jarPath
                                }
                                .map { it.name }
                                .ifEmpty {
                                    synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                                        com.lagradost.cloudstream3.APIHolder.allProviders
                                            .filter { api ->
                                                val src = api.sourcePlugin
                                                src == pluginName || src == jarName || src == jarBaseName || src == jarPath
                                            }
                                            .map { it.name }
                                    }
                                }
                                .sorted()
                            apis
                        }

                        val defaultSet = (schema.defaultValue as? Set<*>)?.map { it.toString() }?.toSet()
                            ?: (schema.defaultValue as? List<*>)?.map { it.toString() }?.toSet()
                            ?: emptySet()
                        val currentSet = (currentValue as? Set<*>)?.map { it.toString() }?.toSet()
                            ?: (currentValue as? List<*>)?.map { it.toString() }?.toSet()
                        val resolvedSet = currentSet ?: defaultSet

                        val optionsList = memorySources.takeIf { it.isNotEmpty() }
                            ?: (defaultSet + (currentSet ?: emptySet())).toList().sorted()

                        if (optionsList.isNotEmpty()) {
                            val isDisablingKey = schema.key.lowercase().contains("disabled") ||
                                schema.key.lowercase().contains("black")

                            var searchQuery by remember { mutableStateOf("") }
                            val filteredOptions = if (searchQuery.isBlank()) {
                                optionsList
                            } else {
                                optionsList.filter { it.contains(searchQuery, ignoreCase = true) }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Search bar if more than 6 providers
                                if (optionsList.size > 6) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Filter ${optionsList.size} sources...", style = MaterialTheme.typography.bodySmall) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        ),
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isDisablingKey) {
                                                onValueChanged(emptySet<String>())
                                            } else {
                                                onValueChanged(optionsList.toSet())
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Enable All") }
                                    OutlinedButton(
                                        onClick = {
                                            if (isDisablingKey) {
                                                onValueChanged(optionsList.toSet())
                                            } else {
                                                onValueChanged(emptySet<String>())
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Disable All") }
                                }

                                filteredOptions.forEach { option ->
                                    val isEnabled = if (isDisablingKey) {
                                        !resolvedSet.contains(option)
                                    } else {
                                        if (currentSet == null && defaultSet.isEmpty()) true else resolvedSet.contains(option)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = option,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Switch(
                                            checked = isEnabled,
                                            onCheckedChange = { checked ->
                                                val newSet = (currentSet ?: (if (isDisablingKey) emptySet() else optionsList.toSet())).toMutableSet()
                                                if (isDisablingKey) {
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
                                value = currentValue?.toString() ?: schema.defaultValue?.toString() ?: "",
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
                        // String / Sensitive key handling with password reveal
                        val isSensitive = schema.key.lowercase().let { k ->
                            k.contains("token") || k.contains("password") || k.contains("secret") ||
                                k.contains("auth") || k.contains("api_key") || k.contains("apikey")
                        }
                        var showPassword by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = currentValue?.toString() ?: schema.defaultValue?.toString() ?: "",
                            onValueChange = { newValue ->
                                onValueChanged(newValue)
                            },
                            visualTransformation = if (isSensitive && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                            trailingIcon = if (isSensitive) {
                                {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showPassword) "Hide" else "Show",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
}


