package com.lagradost.cloudstream3.desktop.ui.screens.profile.contract

import com.lagradost.cloudstream3.desktop.profile.Profile
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState

data class ProfileUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfile: Profile? = null,
    val isManageMode: Boolean = false,
    val editingProfile: Profile? = null,
    val isCreatingNew: Boolean = false,
    val pinPromptProfile: Profile? = null,
    val enteredPin: String = "",
    val pinError: Boolean = false,
) : UiState

sealed interface ProfileUiEvent : UiEvent {
    data class OnToggleManageMode(val isManage: Boolean) : ProfileUiEvent
    data class OnSelectProfile(val profile: Profile) : ProfileUiEvent
    data class OnOpenPinPrompt(val profile: Profile) : ProfileUiEvent
    object OnDismissPinPrompt : ProfileUiEvent
    data class OnVerifyPin(val pin: String) : ProfileUiEvent
    data class OnOpenEdit(val profile: Profile) : ProfileUiEvent
    object OnOpenCreate : ProfileUiEvent
    object OnDismissEdit : ProfileUiEvent
    data class OnSaveProfile(
        val name: String,
        val colorIndex: Int,
        val customAvatar: String?,
        val pin: String?,
        val isKids: Boolean,
    ) : ProfileUiEvent
    data class OnDeleteProfile(val profileId: Int) : ProfileUiEvent
}

sealed interface ProfileUiEffect : UiEffect {
    object NavigateHome : ProfileUiEffect
}
