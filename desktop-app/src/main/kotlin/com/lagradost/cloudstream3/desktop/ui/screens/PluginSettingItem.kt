package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.common.storage.PluginSettingSchema
import java.io.File

private fun isDomainOrUrlKey(key: String): Boolean {
    val l = key.lowercase()
    return l.contains("domain") || l.contains("url") || l.contains("host") || l.contains("mirror") || l.contains("baseurl") || l.contains("address")
}

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
        if (options != null && options.isNotEmpty() && schema.type != "StringSet") {
            val currentValueStr = currentValue?.toString() ?: schema.defaultValue?.toString() ?: options.values.firstOrNull() ?: ""
            val isSelectDialog = options.size > 3 || isDomainOrUrlKey(schema.key)

            if (isSelectDialog) {
                var showSelectDialog by remember { mutableStateOf(false) }
                val selectedEntry = options.entries.firstOrNull { it.value == currentValueStr }
                val selectedDisplay = selectedEntry?.key ?: currentValueStr

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

                    Surface(
                        onClick = { showSelectDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedDisplay.ifBlank { "Select an option" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (isDomainOrUrlKey(schema.key)) {
                                    Text(
                                        text = "Active mirror / endpoint",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                text = "›",
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                if (showSelectDialog) {
                    CloudstreamCustomDialog(
                        show = true,
                        onDismissRequest = { showSelectDialog = false },
                        modifier = Modifier
                            .widthIn(min = 450.dp, max = 560.dp)
                            .heightIn(max = 600.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = getFriendlyName(schema.key),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                IconButton(onClick = { showSelectDialog = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(options.entries.toList(), key = { it.key }) { (label, value) ->
                                    val isSelected = (value == currentValueStr)
                                    Surface(
                                        onClick = {
                                            onValueChanged(value)
                                            showSelectDialog = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    onValueChanged(value)
                                                    showSelectDialog = false
                                                },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = MaterialTheme.colorScheme.primary,
                                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(start = 10.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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

                            val optionsList = schema.options?.keys?.toList()?.sorted()
                                ?: memorySources.takeIf { it.isNotEmpty() }
                                ?: (defaultSet + (currentSet ?: emptySet())).toList().sorted()

                            if (optionsList.isNotEmpty()) {
                                var showProviderDialog by remember { mutableStateOf(false) }
                                val isDisablingKey = schema.key.lowercase().contains("disabled") ||
                                    schema.key.lowercase().contains("black")

                                val enabledCount = if (isDisablingKey) {
                                    optionsList.count { !resolvedSet.contains(it) }
                                } else {
                                    optionsList.count { if (currentSet == null && defaultSet.isEmpty()) true else resolvedSet.contains(it) }
                                }

                                Surface(
                                    onClick = { showProviderDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Sub-Providers & Channels",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "$enabledCount of ${optionsList.size} enabled",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Text(
                                            text = "›",
                                            fontSize = 22.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                }

                                if (showProviderDialog) {
                                    CloudstreamCustomDialog(
                                        show = true,
                                        onDismissRequest = { showProviderDialog = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .fillMaxHeight(0.85f),
                                    ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Column {
                                                    Text(
                                                        text = getFriendlyName(schema.key),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    Text(
                                                        text = "$enabledCount of ${optionsList.size} sources active",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                IconButton(onClick = { showProviderDialog = false }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Close",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                            var searchQuery by remember { mutableStateOf("") }
                                            val filteredOptions = if (searchQuery.isBlank()) {
                                                optionsList
                                            } else {
                                                optionsList.filter { opt ->
                                                    val label = schema.options?.get(opt) ?: opt
                                                    opt.contains(searchQuery, ignoreCase = true) || label.contains(searchQuery, ignoreCase = true)
                                                }
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
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

                                                LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    items(filteredOptions, key = { it }) { option ->
                                                        val isEnabled = if (isDisablingKey) {
                                                            !resolvedSet.contains(option)
                                                        } else {
                                                            if (currentSet == null && defaultSet.isEmpty()) true else resolvedSet.contains(option)
                                                        }

                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            val labelText = schema.options?.get(option) ?: option
                                                            Text(
                                                                text = labelText,
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
                                            }

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                Button(
                                                    onClick = { showProviderDialog = false },
                                                    shape = RoundedCornerShape(10.dp),
                                                ) {
                                                    Text("Done", fontWeight = FontWeight.Bold)
                                                }
                                            }
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
                                } else {
                                    null
                                },
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
