package com.lagradost.cloudstream3.desktop.ui.screens.settings.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState

sealed class SettingsUiEvent : UiEvent {
    data class OnUpdateString(val key: String, val value: String) : SettingsUiEvent()
    data class OnUpdateBoolean(val key: String, val value: Boolean) : SettingsUiEvent()
    data class OnUpdateInt(val key: String, val value: Int) : SettingsUiEvent()
    data class OnUpdateFloat(val key: String, val value: Float) : SettingsUiEvent()
}

data class SettingsUiState(
    val stringSettings: Map<String, String> = emptyMap(),
    val booleanSettings: Map<String, Boolean> = emptyMap(),
    val intSettings: Map<String, Int> = emptyMap(),
    val floatSettings: Map<String, Float> = emptyMap(),
) : UiState

sealed class SettingsUiEffect : UiEffect {
    data class ShowToast(val message: String) : SettingsUiEffect()
}
