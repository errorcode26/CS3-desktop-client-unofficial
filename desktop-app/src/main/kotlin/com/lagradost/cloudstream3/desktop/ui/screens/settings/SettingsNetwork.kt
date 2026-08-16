package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.network.DohProvider
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.common.storage.DesktopDataStore

@Composable
fun SettingsNetwork(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroupCard(title = "DNS over HTTPS (DoH)") {
                Text(
                    "Bypass ISP DNS blocking by encrypting your DNS queries. Changing this will instantly hot-reload the app's networking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                MviSettingsDropdown(
                    key = NetworkConfig.PREF_DOH_PROVIDER,
                    label = "Provider",
                    options = DohProvider.values().mapIndexed { index, provider -> index to provider.title },
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = 0,
                )
            }

            SettingsGroupCard(title = "Security & Browser Isolation") {
                Text(
                    "Control how CloudStream interacts with external browsers for CAPTCHA bypasses and trailers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                MviSettingsToggle(
                    key = DesktopDataStore.PREF_ALLOW_CF_BYPASS,
                    label = "Allow Experimental Cloudflare Bypass",
                    subtitle = "EXPERIMENTAL AND CURRENTLY BROKEN. Advised to leave OFF until further updates. Uses background browser to solve captchas.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                Spacer(modifier = Modifier.height(8.dp))

                MviSettingsToggle(
                    key = DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER,
                    label = "Allow Opening Trailers & External Links",
                    subtitle = "Allow CloudStream to open official trailers and external web links.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                // Dynamic visibility for dependent settings
                val allowExternalBrowser = uiState.booleanSettings[DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER] ?: (DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER) ?: false)

                if (allowExternalBrowser) {
                    Spacer(modifier = Modifier.height(8.dp))

                    MviSettingsToggle(
                        key = DesktopDataStore.PREF_ISOLATED_EXTERNAL_BROWSER,
                        label = "Use Isolated Sandbox for Trailers",
                        subtitle = "Instead of opening your personal browser, click-to-play trailers will open in a strict, disposable, popup-blocked sandboxed window.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = true,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    MviSettingsToggle(
                        key = DesktopDataStore.PREF_DONT_ASK_EXTERNAL_LINKS,
                        label = "Don't Ask Again Before Opening Trailers",
                        subtitle = "Automatically open external links in your preferred browser without showing the confirmation dialog.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = false,
                    )
                }
            }
        }
    }
}
