package com.lagradost.cloudstream3.desktop.ui.screens.settings.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.models.CustomSite
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent

@Composable
fun SettingsAdvancedClonedSites(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddCloneDialog by remember { mutableStateOf(false) }
    var clonedSites by remember(uiState.stringSettings[PreferenceKeys.USER_PROVIDER_API]) {
        mutableStateOf<List<CustomSite>>(
            try {
                val json = uiState.stringSettings[PreferenceKeys.USER_PROVIDER_API] ?: com.lagradost.common.storage.DesktopDataStore.getKey<String>(PreferenceKeys.USER_PROVIDER_API)
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

    SettingsGroupCard(title = "Cloned Sites & Custom URLs", modifier = modifier) {
        Text(
            "You can clone an existing provider and override its URL. This is useful if a site changes its domain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        clonedSites.forEach { site ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        site.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        site.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    viewModel.onEvent(SettingsUiEvent.RemoveClonedSite(site))
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        Button(onClick = { showAddCloneDialog = true }) {
            Text("Add Cloned Site")
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
                modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                    ) {
                        Text("Select Base Provider", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))

                        var searchQuery by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search providers...") },
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
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(provider.name, modifier = Modifier.weight(1f), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        if (clonesCount > 0) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondary,
                                                shape = MaterialTheme.shapes.small,
                                            ) {
                                                Text(
                                                    "$clonesCount",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onSecondary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1.5f).fillMaxHeight().padding(24.dp)) {
                        Text("Configure Clone", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))

                        if (selectedProvider == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Select a provider from the left to configure it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Display Name") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Override / Mirror URL") },
                                placeholder = { Text("e.g. https://mirror.example.com") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = langInput,
                                onValueChange = { langInput = it },
                                label = { Text("Language Code (e.g. en)") },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.weight(1f))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showAddCloneDialog = false }) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    val provider = selectedProvider
                                    if (provider != null && nameInput.isNotBlank() && urlInput.isNotBlank()) {
                                        val cleanUrl = urlInput.trim().trimEnd('/')
                                        if (cleanUrl.equals(provider.mainUrl.trimEnd('/'), ignoreCase = true)) {
                                            AppToastManager.showWarning("Clone URL must be an alternate mirror, not identical to base URL")
                                            return@Button
                                        }

                                        val newSite = CustomSite(
                                            parentJavaClass = provider.javaClass.simpleName,
                                            name = nameInput.trim(),
                                            url = cleanUrl,
                                            lang = langInput.trim().ifBlank { provider.lang },
                                        )
                                        clonedSites = clonedSites + newSite
                                        viewModel.onEvent(SettingsUiEvent.AddClonedSite(newSite))
                                        showAddCloneDialog = false
                                    }
                                }) {
                                    Text("Save Clone")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
