package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.lagradost.cloudstream3.desktop.network.DohProvider
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu
import com.lagradost.common.storage.DesktopDataStore

@Composable
fun SettingsNetwork() {
    var expanded by remember { mutableStateOf(false) }
    var selectedProvider by remember {
        mutableStateOf(DesktopDataStore.getKey<Int>(NetworkConfig.PREF_DOH_PROVIDER) ?: 0)
    }
    var statusMessage by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("DNS over HTTPS (DoH)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Bypass ISP DNS blocking by encrypting your DNS queries. Changing this will instantly hot-reload the app's networking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Provider: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    FilledTonalButton(onClick = { expanded = true }) {
                        Text(DohProvider.values().getOrNull(selectedProvider)?.title ?: DohProvider.NONE.title)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DohProvider.values().forEachIndexed { index, provider ->
                            DropdownMenuItem(
                                text = { Text(provider.title) },
                                onClick = {
                                    selectedProvider = index
                                    DesktopDataStore.setKey(NetworkConfig.PREF_DOH_PROVIDER, index)
                                    try {
                                        NetworkConfig.updateGlobalNetworkClients()
                                        statusMessage = "Network reloaded successfully with ${provider.title}!"
                                    } catch (e: Exception) {
                                        statusMessage = "Error updating network: ${e.message}"
                                    }
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }

            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(statusMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
