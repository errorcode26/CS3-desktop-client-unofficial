package com.lagradost.cloudstream3.desktop.ui.screens.settings

import com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.*
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel : BaseMviViewModel<SettingsUiState, SettingsUiEvent, SettingsUiEffect>(
    initialState = SettingsUiState(),
) {
    override fun handleEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.OnUpdateString -> {
                updateState { copy(stringSettings = stringSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)
                }
            }
            is SettingsUiEvent.OnUpdateBoolean -> {
                updateState { copy(booleanSettings = booleanSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)

                    if (event.key.startsWith("DISCORD_RPC")) {
                        try {
                            DiscordRpcManager.onSettingsChanged()
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("Discord RPC Error", e)
                        }
                    }
                }
            }
            is SettingsUiEvent.OnUpdateInt -> {
                updateState { copy(intSettings = intSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)

                    if (event.key == NetworkConfig.PREF_DOH_PROVIDER) {
                        try {
                            NetworkConfig.updateGlobalNetworkClients()
                            // Status message could be handled via UiEffect if needed
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("Network Reload Error", e)
                        }
                    }
                }
            }
            is SettingsUiEvent.OnUpdateFloat -> {
                updateState { copy(floatSettings = floatSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)
                }
            }
        }
    }
}
