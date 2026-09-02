package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.models.CustomSite
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ComposeExtensionScreen
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ExtensionsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsExtensions(
    onNavigate: (Config) -> Unit = {},
    initialTab: Int = 0,
    viewModel: ExtensionsViewModel = remember { ExtensionsViewModel() },
) {
    ComposeExtensionScreen(
        onNavigate = onNavigate,
        initialTab = initialTab,
        viewModel = viewModel,
        isInsideSettings = true,
    )
}

@Composable
fun SettingsPluginsAndAddonsScreen(
    onNavigate: (Config) -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("Plugins & Repositories") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("Stremio Addons") },
                leadingIcon = {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            FilterChip(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                label = { Text("Custom Site Mirrors") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> SettingsExtensions(onNavigate = onNavigate)
                1 -> SettingsAddons()
                2 -> SettingsCustomMirrorsScreen()
            }
        }
    }
}

@Composable
fun SettingsCustomMirrorsScreen(
    viewModel: SettingsViewModel = remember { SettingsViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showAddCloneDialog by remember { mutableStateOf(false) }
    var clonedSites by remember(uiState.stringSettings["USER_PROVIDER_API"]) {
        mutableStateOf(
            try {
                val json = uiState.stringSettings["USER_PROVIDER_API"] ?: DesktopDataStore.getKey<String>("USER_PROVIDER_API")
                if (json != null) {
                    val mapper = jacksonObjectMapper()
                    mapper.readValue<List<CustomSite>>(
                        json,
                        object : TypeReference<List<CustomSite>>() {},
                    )
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            },
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Custom Site Mirrors & URL Overrides") {
            Text(
                "You can clone an existing provider and override its base domain URL. This is useful when a streaming provider changes its domain or when accessing alternate regional mirrors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (clonedSites.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "No Custom Mirrors Configured",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Add a mirror to override provider endpoints with alternate domains.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                clonedSites.forEach { site ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(site.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(site.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                        IconButton(onClick = {
                            val newList = clonedSites.filter { it != site }
                            clonedSites = newList
                            scope.launch(Dispatchers.IO) {
                                val mapper = jacksonObjectMapper()
                                viewModel.onEvent(SettingsUiEvent.OnUpdateString("USER_PROVIDER_API", mapper.writeValueAsString(newList)))
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }

            Button(
                onClick = { showAddCloneDialog = true },
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Custom Site Mirror")
            }
        }
    }

    if (showAddCloneDialog) {
        var selectedProvider by remember { mutableStateOf<MainAPI?>(null) }
        var nameInput by remember { mutableStateOf("") }
        var urlInput by remember { mutableStateOf("") }
        var langInput by remember { mutableStateOf("") }

        val availableProviders = remember {
            APIHolder.allProviders.distinctBy { it::class.java.simpleName }.sortedBy { it.name }
        }

        CloudstreamCustomDialog(
            show = true,
            onDismissRequest = { showAddCloneDialog = false },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)).padding(16.dp)) {
                        Text("Select Base Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        var searchQuery by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search providers...", fontSize = 13.sp) },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))

                        val filtered = availableProviders.filter { it.name.contains(searchQuery, true) }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered) { provider ->
                                val isSelected = selectedProvider == provider
                                val clonesCount = clonedSites.count { it.parentJavaClass == provider.javaClass.simpleName }

                                Surface(
                                    onClick = {
                                        selectedProvider = provider
                                        nameInput = provider.name + " Mirror"
                                        urlInput = ""
                                        langInput = provider.lang
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(provider.name, modifier = Modifier.weight(1f), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        if (clonesCount > 0) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondary,
                                                shape = MaterialTheme.shapes.small,
                                            ) {
                                                Text("$clonesCount", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1.5f).fillMaxHeight().padding(24.dp)) {
                        Text("Configure Mirror Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))

                        if (selectedProvider == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Select a provider from the left list to configure an alternate mirror.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Display Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Override / Mirror Domain URL") },
                                placeholder = { Text("e.g. https://mirror.example.com") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = langInput,
                                onValueChange = { langInput = it },
                                label = { Text("Language Code (e.g. en)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )

                            Spacer(Modifier.weight(1f))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { showAddCloneDialog = false }) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(10.dp))
                                Button(onClick = {
                                    val provider = selectedProvider
                                    if (provider != null && nameInput.isNotBlank() && urlInput.isNotBlank()) {
                                        val cleanUrl = urlInput.trim().trimEnd('/')
                                        if (cleanUrl.equals(provider.mainUrl.trimEnd('/'), ignoreCase = true)) {
                                            AppToastManager.showWarning("Mirror URL must be an alternate domain, not identical to base URL")
                                            return@Button
                                        }

                                        val newSite = CustomSite(
                                            parentJavaClass = provider.javaClass.simpleName,
                                            name = nameInput.trim(),
                                            url = cleanUrl,
                                            lang = langInput.trim().ifBlank { provider.lang },
                                        )
                                        val newList = clonedSites + newSite
                                        clonedSites = newList

                                        scope.launch(Dispatchers.IO) {
                                            val mapper = jacksonObjectMapper()
                                            viewModel.onEvent(SettingsUiEvent.OnUpdateString("USER_PROVIDER_API", mapper.writeValueAsString(newList)))

                                            try {
                                                val clone = provider.javaClass.getDeclaredConstructor().newInstance()
                                                clone.name = newSite.name
                                                clone.lang = newSite.lang
                                                clone.mainUrl = cleanUrl
                                                clone.canBeOverridden = false
                                                APIHolder.allProviders.add(clone)
                                                APIHolder.addPluginMapping(clone)
                                            } catch (_: Exception) {}
                                        }
                                        showAddCloneDialog = false
                                    }
                                }) {
                                    Text("Save Mirror")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
