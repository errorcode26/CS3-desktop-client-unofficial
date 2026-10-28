package com.lagradost.cloudstream3.desktop.ui.screens.profile

import com.lagradost.cloudstream3.desktop.profile.ProfileManager
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.profile.contract.ProfileUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.profile.contract.ProfileUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.profile.contract.ProfileUiState
import kotlinx.coroutines.launch

class ProfileViewModel : BaseMviViewModel<ProfileUiState, ProfileUiEvent, ProfileUiEffect>(ProfileUiState()) {

    init {
        viewModelScope.launch {
            ProfileManager.profiles.collect { list ->
                updateState { copy(profiles = list) }
            }
        }
        viewModelScope.launch {
            ProfileManager.activeProfile.collect { active ->
                updateState { copy(activeProfile = active) }
            }
        }
        viewModelScope.launch {
            ProfileManager.autoSignIn.collect { auto ->
                updateState { copy(autoSignIn = auto) }
            }
        }
    }

    override fun handleEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.OnToggleManageMode -> {
                updateState { copy(isManageMode = event.isManage) }
            }
            is ProfileUiEvent.OnToggleAutoSignIn -> {
                ProfileManager.setAutoSignIn(event.enabled)
            }
            is ProfileUiEvent.OnSelectProfile -> {
                if (event.profile.hasPin) {
                    updateState { copy(pinPromptProfile = event.profile, enteredPin = "", pinError = false) }
                } else {
                    ProfileManager.switchProfile(event.profile.id)
                    sendEffect(ProfileUiEffect.NavigateHome)
                }
            }
            is ProfileUiEvent.OnOpenPinPrompt -> {
                updateState { copy(pinPromptProfile = event.profile, enteredPin = "", pinError = false) }
            }
            is ProfileUiEvent.OnDismissPinPrompt -> {
                updateState { copy(pinPromptProfile = null, enteredPin = "", pinError = false) }
            }
            is ProfileUiEvent.OnVerifyPin -> {
                val target = uiState.value.pinPromptProfile
                if (target != null) {
                    val success = ProfileManager.switchProfile(target.id, event.pin)
                    if (success) {
                        updateState { copy(pinPromptProfile = null, enteredPin = "", pinError = false) }
                        sendEffect(ProfileUiEffect.NavigateHome)
                    } else {
                        updateState { copy(pinError = true) }
                    }
                }
            }
            is ProfileUiEvent.OnOpenEdit -> {
                updateState { copy(editingProfile = event.profile, isCreatingNew = false) }
            }
            is ProfileUiEvent.OnOpenCreate -> {
                updateState { copy(editingProfile = null, isCreatingNew = true) }
            }
            is ProfileUiEvent.OnDismissEdit -> {
                updateState { copy(editingProfile = null, isCreatingNew = false) }
            }
            is ProfileUiEvent.OnSaveProfile -> {
                if (uiState.value.isCreatingNew) {
                    val newProfile = ProfileManager.createProfile(
                        name = event.name,
                        avatarColorIndex = event.colorIndex,
                        customAvatarPath = event.customAvatar,
                        pinCode = event.pin,
                        isKids = event.isKids,
                    )
                    if (event.autoSignIn) {
                        ProfileManager.switchProfile(newProfile.id)
                        ProfileManager.setAutoSignIn(true)
                    }
                } else {
                    val editing = uiState.value.editingProfile
                    if (editing != null) {
                        ProfileManager.updateProfile(
                            editing.copy(
                                name = event.name,
                                avatarColorIndex = event.colorIndex,
                                customAvatarPath = event.customAvatar,
                                pinCode = event.pin,
                                isKids = event.isKids,
                            ),
                        )
                        if (event.autoSignIn) {
                            ProfileManager.switchProfile(editing.id)
                            ProfileManager.setAutoSignIn(true)
                        } else if (ProfileManager.activeProfileId == editing.id && ProfileManager.autoSignIn.value) {
                            ProfileManager.setAutoSignIn(false)
                        }
                    }
                }
                updateState { copy(editingProfile = null, isCreatingNew = false) }
            }
            is ProfileUiEvent.OnDeleteProfile -> {
                ProfileManager.deleteProfile(event.profileId)
                updateState { copy(editingProfile = null, isCreatingNew = false) }
            }
        }
    }
}
