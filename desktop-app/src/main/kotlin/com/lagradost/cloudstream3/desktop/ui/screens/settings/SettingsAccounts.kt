package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.common.storage.DesktopDataStore

@Composable
fun SettingsAccounts(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedApiForLogin by remember { mutableStateOf<AuthAPI?>(null) }
    val cachedAccounts by AccountManager.accountsFlow.collectAsState()

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
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val profiles by com.lagradost.cloudstream3.desktop.profile.ProfileManager.profiles.collectAsState()
            val activeProfile by com.lagradost.cloudstream3.desktop.profile.ProfileManager.activeProfile.collectAsState()
            val isPickerOnStartup by com.lagradost.cloudstream3.desktop.profile.ProfileManager.isPickerOnStartup.collectAsState()
            var editingProfile by remember { mutableStateOf<com.lagradost.cloudstream3.desktop.profile.Profile?>(null) }
            var isCreatingNew by remember { mutableStateOf(false) }

            if (editingProfile != null || isCreatingNew) {
                com.lagradost.cloudstream3.desktop.ui.screens.profile.ProfileEditDialog(
                    profile = editingProfile,
                    canDelete = profiles.size > 1,
                    onDismiss = {
                        editingProfile = null
                        isCreatingNew = false
                    },
                    onSave = { name, colorIndex, customAvatar, pin, isKids ->
                        if (isCreatingNew) {
                            com.lagradost.cloudstream3.desktop.profile.ProfileManager.createProfile(name, colorIndex, customAvatar, pin, isKids)
                        } else if (editingProfile != null) {
                            com.lagradost.cloudstream3.desktop.profile.ProfileManager.updateProfile(
                                editingProfile!!.copy(
                                    name = name,
                                    avatarColorIndex = colorIndex,
                                    customAvatarPath = customAvatar,
                                    pinCode = pin,
                                    isKids = isKids,
                                ),
                            )
                        }
                    },
                    onDelete = {
                        editingProfile?.let { com.lagradost.cloudstream3.desktop.profile.ProfileManager.deleteProfile(it.id) }
                    },
                )
            }

            SettingsGroupCard(title = "User Profiles") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Profile List Items
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                com.lagradost.cloudstream3.desktop.profile.ProfileAvatar(
                                    profile = profile,
                                    size = 36.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    fontSize = 16.sp,
                                )

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (profile.id == activeProfile.id) FontWeight.Bold else FontWeight.Medium,
                                            color = Color.White,
                                        )
                                        if (profile.id == activeProfile.id) {
                                            Surface(
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            ) {
                                                Text(
                                                    "Active",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    val details = buildList {
                                        if (profile.isKids) add("Kids Profile")
                                        if (profile.hasPin) add("PIN Protected")
                                    }.joinToString(" • ")
                                    if (details.isNotEmpty()) {
                                        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (profile.id != activeProfile.id) {
                                    OutlinedButton(
                                        onClick = { com.lagradost.cloudstream3.desktop.profile.ProfileManager.switchProfile(profile.id) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp),
                                    ) {
                                        Text("Switch", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                IconButton(
                                    onClick = { editingProfile = profile },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Actions & Preferences
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Button(onClick = { isCreatingNew = true }) {
                            Text("+ Create New Profile")
                        }

                        val autoSignIn by com.lagradost.cloudstream3.desktop.profile.ProfileManager.autoSignIn.collectAsState()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Auto sign-in on launch", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = autoSignIn,
                                onCheckedChange = { com.lagradost.cloudstream3.desktop.profile.ProfileManager.setAutoSignIn(it) },
                            )
                        }
                    }
                }
            }

            SettingsGroupCard(title = "Trackers & Integrations") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                        Text(
                            text = "Trackers Are Not Supported Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "External tracker and sync logins (MAL, AniList, Simkl) are currently disabled for the Desktop Client.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }

            SettingsGroupCard(title = "Discord Rich Presence") {
                MviSettingsToggle(
                    key = DesktopDataStore.PREF_DISCORD_RPC_ENABLED,
                    label = "Enable Discord Rich Presence",
                    subtitle = "Display your current playback and browsing status on your Discord profile.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                val discordRpcEnabled = uiState.booleanSettings[DesktopDataStore.PREF_DISCORD_RPC_ENABLED] ?: (DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_DISCORD_RPC_ENABLED) ?: false)

                if (discordRpcEnabled) {
                    MviSettingsToggle(
                        key = DesktopDataStore.PREF_DISCORD_RPC_SHOW_TITLE,
                        label = "Show Media & Episode Titles",
                        subtitle = "Display the specific movie, series name, and episode number.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = true,
                    )

                    MviSettingsToggle(
                        key = DesktopDataStore.PREF_DISCORD_RPC_SHOW_PROGRESS,
                        label = "Show Playback Progress Bar",
                        subtitle = "Display a live countdown progress bar on Discord while playing video.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = true,
                    )

                    MviSettingsToggle(
                        key = DesktopDataStore.PREF_DISCORD_RPC_SHOW_BROWSING,
                        label = "Show Browsing Activity",
                        subtitle = "Display when browsing menus and catalogs when video is not playing.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = true,
                    )
                }
            }
        }
    }

    if (selectedApiForLogin != null) {
        InAppLoginDialog(
            api = selectedApiForLogin!!,
            onDismiss = { selectedApiForLogin = null },
            onSuccess = { authData ->
                AccountManager.updateAccounts(selectedApiForLogin!!.idPrefix, arrayOf(authData))
                selectedApiForLogin = null
            },
        )
    }
}

@Composable
fun InAppLoginDialog(api: AuthAPI, onDismiss: () -> Unit, onSuccess: (AuthData) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var apiKeyStr by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val req = api.inAppLoginRequirement
    val isApiKeyOnly = req != null && req.apiKey && !req.username && !req.password && !req.email && !req.server

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(24.dp).width(400.dp)) {
            Text(if (isApiKeyOnly) "Enter API Key for ${api.name}" else "Login to ${api.name}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            if (req?.username == true) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (req?.email == true) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (req?.password == true) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (req?.server == true) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (req?.apiKey == true) {
                OutlinedTextField(
                    value = apiKeyStr,
                    onValueChange = { apiKeyStr = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (errorMsg != null) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val authData = AuthData(
                        user = com.lagradost.cloudstream3.syncproviders.AuthUser(name = if (username.isNotBlank()) username else "User", id = 0, profilePicture = ""),
                        token = com.lagradost.cloudstream3.syncproviders.AuthToken(accessToken = apiKeyStr.ifBlank { "dummy_token" }),
                    )
                    onSuccess(authData)
                }) { Text(if (isApiKeyOnly) "Save Key" else "Login") }
            }
        }
    }
}
