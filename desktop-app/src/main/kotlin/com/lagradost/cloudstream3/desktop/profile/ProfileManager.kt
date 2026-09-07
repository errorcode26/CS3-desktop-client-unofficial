package com.lagradost.cloudstream3.desktop.profile

import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ProfileManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private const val PREF_PROFILES = "cs_desktop_profiles_v1"
    private const val PREF_ACTIVE_ID = "cs_desktop_active_profile_id_v1"
    private const val PREF_SHOW_PICKER_STARTUP = "cs_desktop_show_profile_picker_on_startup"

    private val defaultProfile = Profile(
        id = 0,
        name = "Main",
        avatarColorIndex = 0,
        pinCode = null,
        isKids = false,
    )

    private val _profiles = MutableStateFlow<List<Profile>>(listOf(defaultProfile))
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(defaultProfile)
    val activeProfile: StateFlow<Profile> = _activeProfile.asStateFlow()

    val activeProfileId: Int
        get() = _activeProfile.value.id

    private const val PREF_AUTO_SIGN_IN = "cs_desktop_auto_sign_in_startup"

    private val _isPickerOnStartup = MutableStateFlow(true)
    val isPickerOnStartup: StateFlow<Boolean> = _isPickerOnStartup.asStateFlow()

    private val _autoSignIn = MutableStateFlow(false)
    val autoSignIn: StateFlow<Boolean> = _autoSignIn.asStateFlow()

    private val _welcomeToast = MutableStateFlow<Profile?>(null)
    val welcomeToast: StateFlow<Profile?> = _welcomeToast.asStateFlow()

    fun triggerWelcomeToast(profile: Profile) {
        _welcomeToast.value = profile
    }

    fun dismissWelcomeToast() {
        _welcomeToast.value = null
    }

    fun init() {
        try {
            val savedProfilesJson = DesktopDataStore.getKey<String>(PREF_PROFILES)
            val loadedProfiles: List<Profile>? = if (!savedProfilesJson.isNullOrBlank()) {
                mapper.readValue(savedProfilesJson, object : TypeReference<List<Profile>>() {})
            } else {
                null
            }

            val validProfiles = if (loadedProfiles.isNullOrEmpty()) {
                listOf(defaultProfile)
            } else {
                loadedProfiles
            }
            _profiles.value = validProfiles

            val activeId = DesktopDataStore.getKey<Int>(PREF_ACTIVE_ID) ?: 0
            val resolvedActive = validProfiles.find { it.id == activeId } ?: validProfiles.first()
            _activeProfile.value = resolvedActive

            val autoSignInPref = DesktopDataStore.getKey<Boolean>(PREF_AUTO_SIGN_IN)
            val pickerPref = DesktopDataStore.getKey<Boolean>(PREF_SHOW_PICKER_STARTUP)
            val resolvedAutoSignIn = when {
                autoSignInPref != null -> autoSignInPref
                pickerPref != null -> !pickerPref
                else -> false
            }
            _autoSignIn.value = resolvedAutoSignIn
            _isPickerOnStartup.value = !resolvedAutoSignIn

            saveProfilesInternal()
            AppLogger.i("ProfileManager initialized with ${validProfiles.size} profiles. Active: '${resolvedActive.name}' (ID: ${resolvedActive.id}, AutoSignIn: $resolvedAutoSignIn)")
        } catch (e: Exception) {
            AppLogger.e("Failed to initialize ProfileManager", e)
            _profiles.value = listOf(defaultProfile)
            _activeProfile.value = defaultProfile
        }
    }

    private fun saveProfilesInternal() {
        val currentProfiles = _profiles.value
        val currentActiveId = _activeProfile.value.id
        val currentPicker = _isPickerOnStartup.value
        val currentAutoSignIn = _autoSignIn.value
        scope.launch(Dispatchers.IO) {
            try {
                val json = mapper.writeValueAsString(currentProfiles)
                DesktopDataStore.setKey(PREF_PROFILES, json)
                DesktopDataStore.setKey(PREF_ACTIVE_ID, currentActiveId)
                DesktopDataStore.setKey(PREF_SHOW_PICKER_STARTUP, currentPicker)
                DesktopDataStore.setKey(PREF_AUTO_SIGN_IN, currentAutoSignIn)
            } catch (e: Exception) {
                AppLogger.e("Failed to save profiles to storage", e)
            }
        }
    }

    fun setAutoSignIn(enabled: Boolean) {
        _autoSignIn.value = enabled
        _isPickerOnStartup.value = !enabled
        saveProfilesInternal()
    }

    fun setShowPickerOnStartup(enabled: Boolean) {
        setAutoSignIn(!enabled)
    }

    fun createProfile(
        name: String,
        avatarColorIndex: Int = 0,
        customAvatarPath: String? = null,
        pinCode: String? = null,
        isKids: Boolean = false,
    ): Profile {
        val nextId = (_profiles.value.maxOfOrNull { it.id } ?: 0) + 1
        val newProfile = Profile(
            id = nextId,
            name = name.trim().take(16).ifEmpty { "Profile $nextId" },
            avatarColorIndex = avatarColorIndex,
            customAvatarPath = customAvatarPath?.trim()?.takeIf { it.isNotEmpty() },
            pinCode = pinCode?.trim()?.takeIf { it.isNotEmpty() },
            isKids = isKids,
        )
        _profiles.update { it + newProfile }
        saveProfilesInternal()
        AppLogger.i("Created new profile '${newProfile.name}' (ID: ${newProfile.id})")
        return newProfile
    }

    private fun cleanupOldAvatarFile(oldPath: String?, newPath: String?) {
        if (!oldPath.isNullOrBlank() && oldPath != newPath && (oldPath.contains("profiles/avatars") || oldPath.contains("profiles\\avatars"))) {
            scope.launch(Dispatchers.IO) {
                try {
                    val f = java.io.File(oldPath)
                    if (f.exists()) f.delete()
                } catch (_: Exception) {}
            }
        }
    }

    fun updateProfile(updated: Profile) {
        val old = _profiles.value.find { it.id == updated.id }
        if (old != null && old.customAvatarPath != updated.customAvatarPath) {
            cleanupOldAvatarFile(old.customAvatarPath, updated.customAvatarPath)
        }
        _profiles.update { list ->
            list.map { if (it.id == updated.id) updated else it }
        }
        if (_activeProfile.value.id == updated.id) {
            _activeProfile.value = updated
        }
        saveProfilesInternal()
        AppLogger.i("Updated profile '${updated.name}' (ID: ${updated.id})")
    }

    fun deleteProfile(id: Int): Boolean {
        if (_profiles.value.size <= 1) {
            AppLogger.i("Cannot delete the only remaining profile.")
            return false
        }
        val target = _profiles.value.find { it.id == id } ?: return false
        cleanupOldAvatarFile(target.customAvatarPath, null)
        _profiles.update { list -> list.filter { it.id != id } }

        if (_activeProfile.value.id == id) {
            _activeProfile.value = _profiles.value.first()
            DesktopDataStore.notifyHistoryChanged(force = true)
        }
        saveProfilesInternal()
        AppLogger.i("Deleted profile '${target.name}' (ID: $id)")
        return true
    }

    fun switchProfile(id: Int, pin: String? = null): Boolean {
        val target = _profiles.value.find { it.id == id } ?: return false
        if (target.hasPin && target.pinCode != pin) {
            AppLogger.i("Failed PIN verification for profile '${target.name}'")
            return false
        }
        _activeProfile.value = target
        DesktopDataStore.setKey(PREF_ACTIVE_ID, target.id)
        DesktopDataStore.notifyHistoryChanged(force = true)
        com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.reloadFromDataStore()
        com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.reloadFromDataStore()
        triggerWelcomeToast(target)
        AppLogger.i("Switched active profile to '${target.name}' (ID: ${target.id})")
        return true
    }

    fun verifyPin(id: Int, pin: String): Boolean {
        val target = _profiles.value.find { it.id == id } ?: return false
        return !target.hasPin || target.pinCode == pin.trim()
    }
}
