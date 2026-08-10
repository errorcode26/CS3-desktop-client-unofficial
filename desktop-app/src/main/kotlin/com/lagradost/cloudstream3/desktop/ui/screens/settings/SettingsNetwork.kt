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
import com.lagradost.cloudstream3.desktop.network.DohProvider
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.launch

@Composable
fun SettingsNetwork() {
    var expanded by remember { mutableStateOf(false) }
    var selectedProvider by remember {
        mutableStateOf(DesktopDataStore.getKey<Int>(NetworkConfig.PREF_DOH_PROVIDER) ?: 0)
    }

    var statusMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    var allowCfBypass by remember {
        mutableStateOf(DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_ALLOW_CF_BYPASS) ?: false)
    }

    var allowExternalBrowser by remember {
        mutableStateOf(DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER) ?: true)
    }

    var useIsolatedBrowser by remember {
        mutableStateOf(DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_ISOLATED_EXTERNAL_BROWSER) ?: true)
    }

    var dontAskExternal by remember {
        mutableStateOf(DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_DONT_ASK_EXTERNAL_LINKS) ?: false)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                                        expanded = false
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            DesktopDataStore.setKey(NetworkConfig.PREF_DOH_PROVIDER, index)
                                            try {
                                                NetworkConfig.updateGlobalNetworkClients()
                                                statusMessage = "Network reloaded successfully with ${provider.title}!"
                                            } catch (e: Exception) {
                                                statusMessage = "Error updating network: ${e.message}"
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                if (statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(statusMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Security & Browser Isolation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Control how CloudStream interacts with external browsers for CAPTCHA bypasses and trailers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem(
                    label = "Allow Experimental Cloudflare Bypass",
                    subtitle = "EXPERIMENTAL AND CURRENTLY BROKEN. Advised to leave OFF until further updates. Uses background browser to solve captchas.",
                    checked = allowCfBypass,
                    onCheckedChange = { enabled ->
                        allowCfBypass = enabled
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            DesktopDataStore.setKey(DesktopDataStore.PREF_ALLOW_CF_BYPASS, enabled)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem(
                    label = "Allow Opening Trailers & External Links",
                    subtitle = "Allow CloudStream to open official trailers and external web links.",
                    checked = allowExternalBrowser,
                    onCheckedChange = { enabled ->
                        allowExternalBrowser = enabled
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            DesktopDataStore.setKey(DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER, enabled)
                        }
                    }
                )

                if (allowExternalBrowser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem(
                        label = "Use Isolated Sandbox for Trailers",
                        subtitle = "Instead of opening your personal browser, click-to-play trailers will open in a strict, disposable, popup-blocked sandboxed window.",
                        checked = useIsolatedBrowser,
                        onCheckedChange = { enabled ->
                            useIsolatedBrowser = enabled
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                DesktopDataStore.setKey(DesktopDataStore.PREF_ISOLATED_EXTERNAL_BROWSER, enabled)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem(
                        label = "Don't Ask Again Before Opening Trailers",
                        subtitle = "Automatically open external links in your preferred browser without showing the confirmation dialog.",
                        checked = dontAskExternal,
                        onCheckedChange = { enabled ->
                            dontAskExternal = enabled
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                DesktopDataStore.setKey(DesktopDataStore.PREF_DONT_ASK_EXTERNAL_LINKS, enabled)
                            }
                        }
                    )
                }
            }
        }
    }
}
