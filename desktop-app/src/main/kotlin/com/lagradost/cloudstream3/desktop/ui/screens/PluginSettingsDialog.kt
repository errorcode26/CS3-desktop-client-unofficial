package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsViewModel
import com.lagradost.common.storage.PluginSettingsSchemaRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSettingsDialog(
    pluginName: String,
    prefName: String,
    jarFile: java.io.File? = null,
    hasNativeSettings: Boolean = false,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
) {
    val schemaUpdates by PluginSettingsSchemaRegistry.schemaUpdates.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val viewModel = remember(pluginName, prefName) { PluginSettingsViewModel() }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    LaunchedEffect(viewModel, pluginName, prefName) {
        viewModel.onEvent(PluginSettingsUiEvent.OnInit(pluginName, prefName))
    }

    LaunchedEffect(viewModel, schemaUpdates) {
        viewModel.onEvent(PluginSettingsUiEvent.OnSchemaUpdated(schemaUpdates))
    }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            if (effect is PluginSettingsUiEffect.Applied) onApplied()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val currentValues = uiState.currentValues
    val hasChanged = uiState.hasChanged
    val isLoading = uiState.isLoading
    val isApplying = uiState.isApplying

    var sandboxedDialog by remember { mutableStateOf<com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedDialogData?>(null) }
    var nativeLoading by remember(jarFile) { mutableStateOf(false) }

    fun dispatchSandboxedUiAction(dialogId: Long, whichButton: Int? = null, selectedIndex: Int? = null, isChecked: Boolean? = null) {
        val path = jarFile?.absolutePath ?: return
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val host = com.lagradost.cloudstream3.desktop.sandbox.SandboxedPluginManager.getHost()
                    host.sendPluginUiAction(path, dialogId, whichButton, selectedIndex, isChecked)
                } catch (t: Throwable) {
                    com.lagradost.common.logging.AppLogger.w("Failed dispatching sandboxed UI action: ${t.message}")
                }
            }
        }
    }

    fun requestNativeSetup() {
        if (nativeLoading) return
        val path = jarFile?.absolutePath ?: return
        nativeLoading = true
        coroutineScope.launch {
            try {
                val host = com.lagradost.cloudstream3.desktop.sandbox.SandboxedPluginManager.getHost()
                val result = withContext(Dispatchers.IO) {
                    host.invokePluginSettings(path)
                }
                if (result?.dialog != null) {
                    sandboxedDialog = result.dialog
                }
            } catch (t: Throwable) {
                com.lagradost.common.logging.AppLogger.w("Failed to invoke sandboxed settings: ${t.message}")
            } finally {
                nativeLoading = false
            }
        }
    }

    /* private fun legacyOpenNativeSetup() {
        onDismiss()
        com.lagradost.cloudstream3.desktop.shadowui.ShadowUi.beginSession(pluginName)
        try {
            val pluginCtx = instance.activity
                ?: android.content.DesktopContextProvider.createPluginContext(
                    pluginName,
                    com.lagradost.runtime.loader.stubs.PluginFileSecurityStub.getStorageRootForPlugin(pluginName),
                )
            instance.openSettings?.invoke(pluginCtx)
        } catch (e: Throwable) {
            com.lagradost.common.logging.AppLogger.e("openSettings error", e)
        }
    }

    fun requestNativeSetup() {
        if (nativeLoading) return
        nativeInstance?.let { instance ->
            if (instance.openSettings != null) {
                openNativeSetup(instance)
                return
            }
        }
        nativeLoading = true
        coroutineScope.launch {
            val instance = withContext(Dispatchers.IO) {
                jarFile?.let { file ->
                    val existing = com.lagradost.runtime.loader.ExtensionLoader.getPlugin(file.absolutePath) as? com.lagradost.cloudstream3.plugins.Plugin
                    if (existing?.openSettings != null) {
                        existing
                    } else {
                        try {
                            com.lagradost.runtime.loader.ExtensionLoader.getSettingsOnlyPluginInstance(file)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
            }
            nativeInstance = instance
            nativeLoading = false
            if (instance?.openSettings != null) {
                openNativeSetup(instance)
            }
        }
    }

    */

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = { if (!isApplying) onDismiss() },
        modifier = Modifier
            .width(750.dp)
            .fillMaxHeight(0.85f),
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
                    onClick = { if (!isApplying) onDismiss() },
                    enabled = !isApplying,
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
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Loading settings schema...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "⚠️",
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(end = 10.dp),
                                    )
                                    Text(
                                        text = "Experimental: Settings are bridged from Android packages and may not take effect.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }

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
                                        text = "ℹ️ You have unsaved changes. Click 'Apply & Close' below to commit these configurations and reload the plugin.",
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
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (hasNativeSettings) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            Text(
                                                text = "This plugin manages playlists and configuration via its native setup dialog.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                            Button(
                                                onClick = { requestNativeSetup() },
                                                enabled = !nativeLoading,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            ) {
                                                Text(if (nativeLoading) "Loading..." else "Open $pluginName Setup Dialog")
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "No configurable options or sub-providers found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            if (hasNativeSettings) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(enabled = !nativeLoading) { requestNativeSetup() },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Native Plugin Setup",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    text = "Open external dialog (e.g. playlist selector / account manager)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                text = if (nativeLoading) "Loading..." else "Open",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            }

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

                                items(grouped[category].orEmpty(), key = { it.key }) { schema ->
                                    val fullKey = schema.storageKey
                                    val currentValue = currentValues[fullKey]
                                    com.lagradost.cloudstream3.desktop.ui.screens.PluginSettingItem(
                                        schema = schema,
                                        currentValue = currentValue,
                                        pluginName = pluginName,
                                        jarFile = jarFile,
                                        onValueChanged = { newValue ->
                                            viewModel.onEvent(PluginSettingsUiEvent.OnSettingChanged(schema, newValue))
                                        },
                                    )
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
                if (hasChanged) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isApplying,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text("Cancel")
                    }
                }
                Button(
                    onClick = {
                        if (hasChanged) {
                            viewModel.onEvent(PluginSettingsUiEvent.OnApplyChanges)
                        } else {
                            onDismiss()
                        }
                    },
                    enabled = !isApplying,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(if (isApplying) "Applying..." else "Apply & Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    sandboxedDialog?.let { dialog ->
        if (dialog.items.isNotEmpty()) {
            CloudstreamCustomDialog(
                show = true,
                onDismissRequest = {
                    dispatchSandboxedUiAction(dialog.dialogId, whichButton = android.content.DialogInterface.BUTTON_NEGATIVE)
                    sandboxedDialog = null
                },
                modifier = Modifier.fillMaxWidth(0.65f).fillMaxHeight(0.70f),
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        text = dialog.title ?: "$pluginName Setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    dialog.message?.let { m ->
                        Text(
                            text = m,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(dialog.items) { index, item ->
                            val isSelected = dialog.checkedItem == index
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        dispatchSandboxedUiAction(dialog.dialogId, selectedIndex = index)
                                        if (!dialog.isMultiChoice) {
                                            sandboxedDialog = null
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        dialog.negativeText?.let { neg ->
                            TextButton(onClick = {
                                dispatchSandboxedUiAction(dialog.dialogId, whichButton = android.content.DialogInterface.BUTTON_NEGATIVE)
                                sandboxedDialog = null
                            }) {
                                Text(neg)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val pos = dialog.positiveText ?: "Close"
                        Button(onClick = {
                            dispatchSandboxedUiAction(dialog.dialogId, whichButton = android.content.DialogInterface.BUTTON_POSITIVE)
                            sandboxedDialog = null
                        }) {
                            Text(pos)
                        }
                    }
                }
            }
        } else {
            CloudstreamAlertDialog(
                show = true,
                onDismissRequest = {
                    dispatchSandboxedUiAction(dialog.dialogId, whichButton = android.content.DialogInterface.BUTTON_NEGATIVE)
                    sandboxedDialog = null
                },
                title = {
                    Text(dialog.title ?: "$pluginName Setup")
                },
                text = dialog.message?.let { m ->
                    { Text(m) }
                },
                confirmButton = {
                    Button(onClick = {
                        dispatchSandboxedUiAction(dialog.dialogId, whichButton = android.content.DialogInterface.BUTTON_POSITIVE)
                        sandboxedDialog = null
                    }) {
                        Text(dialog.positiveText ?: "OK")
                    }
                },
                dismissButton = dialog.negativeText?.let { neg ->
                    {
                        TextButton(onClick = {
                            dispatchSandboxedUiAction(dialog.dialogId, whichButton = android.content.DialogInterface.BUTTON_NEGATIVE)
                            sandboxedDialog = null
                        }) {
                            Text(neg)
                        }
                    }
                },
            )
        }
    }
/* end custom dialog

                }
            }
        }
    }
}

*/
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
    val lower = key.lowercase()
    val friendly = getFriendlyName(key)
    return when {
        lower.contains("concurrency") || lower.contains("threads") ->
            "Maximum simultaneous connection threads for network operations."
        lower.contains("timeout") ->
            "Connection timeout duration in seconds."
        lower.contains("download") && lower.contains("enable") ->
            "Prioritize direct file download links over streaming playback."
        lower.contains("token") || lower.contains("key") || lower.contains("auth") || lower.contains("password") || lower.contains("secret") ->
            "Configure authentication credentials/API key for $friendly."
        lower.contains("host") || lower.contains("domain") || lower.contains("server") ->
            "Configure the API or media server endpoint address."
        lower.contains("addon") || lower.contains("catalog") || lower.contains("stremio") ->
            "Configure external streaming catalog endpoints."
        lower.contains("sub") || lower.contains("caption") ->
            "Configure subtitle provider and language integration."
        lower.contains("quality") || lower.contains("resolution") ->
            "Preferred media streaming quality."
        lower.contains("disabled") ->
            "Toggle individual sub-scrapers and data sources for this plugin."
        lower.contains("enabled") || key.startsWith("Provider") ->
            "Enable or disable this data source."
        else -> "Adjust configuration setting for $friendly."
    }
}
