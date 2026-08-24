package com.lagradost.cloudstream3.desktop.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.profile.Profile
import com.lagradost.cloudstream3.desktop.profile.ProfileAvatar
import com.lagradost.cloudstream3.desktop.profile.ProfilePalette
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun ProfileEditDialog(
    profile: Profile?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, avatarColorIndex: Int, customAvatarPath: String?, pinCode: String?, isKids: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var selectedColorIndex by remember(profile) { mutableStateOf(profile?.avatarColorIndex ?: 0) }
    var customAvatarPath by remember(profile) { mutableStateOf(profile?.customAvatarPath ?: "") }
    var avatarMode by remember(profile) { mutableStateOf(if (!profile?.customAvatarPath.isNullOrBlank()) 1 else 0) }
    var hasPin by remember(profile) { mutableStateOf(profile?.hasPin == true) }
    var pinCode by remember(profile) { mutableStateOf(profile?.pinCode ?: "") }
    var isKids by remember(profile) { mutableStateOf(profile?.isKids == true) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val previewProfile = remember(name, selectedColorIndex, customAvatarPath, avatarMode) {
        Profile(
            id = profile?.id ?: -1,
            name = name.ifBlank { "Profile" },
            avatarColorIndex = selectedColorIndex,
            customAvatarPath = if (avatarMode == 1 && customAvatarPath.isNotBlank()) customAvatarPath.trim() else null,
            pinCode = pinCode,
            isKids = isKids,
        )
    }

    if (showDeleteConfirm && onDelete != null) {
        com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Profile?") },
            text = { Text("Are you sure you want to delete profile '${profile?.name}'? Its watch history and bookmarks will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 540.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Title
            Text(
                text = if (profile == null) "Create Profile" else "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Profile Avatar Preview + Name Field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileAvatar(
                    profile = previewProfile,
                    size = 68.dp,
                    shape = RoundedCornerShape(16.dp),
                    fontSize = 28.sp,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 18) name = it },
                    label = { Text("Profile Name") },
                    supportingText = {
                        Text("${name.length}/18", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            // Avatar Mode Selector Tabs
            TabRow(
                selectedTabIndex = avatarMode,
                containerColor = Color.Transparent,
                divider = {},
            ) {
                Tab(
                    selected = avatarMode == 0,
                    onClick = { avatarMode = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Preset Gradients")
                        }
                    },
                )
                Tab(
                    selected = avatarMode == 1,
                    onClick = { avatarMode = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Image / GIF")
                        }
                    },
                )
            }

    var showCropperForPath by remember { mutableStateOf<String?>(null) }

    if (showCropperForPath != null) {
        AvatarCropperDialog(
            imagePathOrUrl = showCropperForPath!!,
            onDismiss = { showCropperForPath = null },
            onCropCompleted = { croppedPath ->
                customAvatarPath = croppedPath
                showCropperForPath = null
            },
        )
    }

    if (avatarMode == 0) {
        // Avatar Color Palette Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose Color Gradient", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ProfilePalette.colors.indices.forEach { index ->
                    val isSelected = selectedColorIndex == index
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ProfilePalette.getBrush(index))
                            .clickable { selectedColorIndex = index }
                            .then(
                                if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    } else {
        // Custom Image / GIF (Local File + URL + Cropper)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = customAvatarPath,
                onValueChange = { customAvatarPath = it },
                label = { Text("Image / Animated GIF URL or Local Path") },
                placeholder = { Text("https://example.com/avatar.gif") },
                singleLine = true,
                trailingIcon = {
                    if (customAvatarPath.isNotEmpty()) {
                        IconButton(onClick = { customAvatarPath = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val pickedFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                            title = "Choose Avatar Image / GIF",
                            allowedExtensions = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif"),
                        )
                        if (pickedFile != null && pickedFile.exists()) {
                            val fullPath = pickedFile.absolutePath
                            customAvatarPath = fullPath
                            showCropperForPath = fullPath
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Browse PC", maxLines = 1)
                }

                if (customAvatarPath.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            customAvatarPath = ""
                            avatarMode = 0
                        },
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }

                    Button(
                        onClick = { showCropperForPath = customAvatarPath },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Crop & Adjust")
                    }
                }
            }
        }
    }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Kids Mode Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kids Profile", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Highlights family and animation content", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isKids,
                    onCheckedChange = { isKids = it },
                )
            }

            // PIN Lock Checkbox & Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Profile PIN Lock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Require 4-digit PIN to switch to this profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = hasPin,
                        onCheckedChange = {
                            hasPin = it
                            if (!it) pinCode = ""
                        },
                    )
                }

                if (hasPin) {
                    OutlinedTextField(
                        value = pinCode,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 6) {
                                pinCode = input
                            }
                        },
                        label = { Text("Enter 4 to 6 Digit PIN") },
                        supportingText = {
                            Text("${pinCode.length}/6 digits (min 4)", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Footer Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (profile != null && canDelete) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val trimmedCustom = if (avatarMode == 1 && customAvatarPath.isNotBlank()) customAvatarPath.trim() else null
                                if (trimmedCustom != null && (trimmedCustom.startsWith("http://", ignoreCase = true) || trimmedCustom.startsWith("https://", ignoreCase = true))) {
                                    isSaving = true
                                    scope.launch(Dispatchers.IO) {
                                        var finalPath = trimmedCustom
                                        try {
                                            val avatarDir = File(com.lagradost.common.platform.PlatformPaths.appDataDir, "profiles/avatars").also { it.mkdirs() }
                                            val client = okhttp3.OkHttpClient.Builder()
                                                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                                .build()
                                            val req = okhttp3.Request.Builder().url(trimmedCustom).header("User-Agent", "Mozilla/5.0").build()
                                            val resp = client.newCall(req).execute()
                                            val bytes = resp.body.bytes()
                                            resp.close()
                                            if (bytes.isNotEmpty()) {
                                                val isGif = GifCropper.isGif(bytes) || GifCropper.isGif(trimmedCustom)
                                                val ext = if (isGif) "gif" else "png"
                                                val cachedFile = File(avatarDir, "cached_avatar_${System.currentTimeMillis()}.$ext")
                                                cachedFile.writeBytes(bytes)
                                                finalPath = cachedFile.absolutePath
                                            }
                                        } catch (_: Exception) {}

                                        withContext(Dispatchers.Main) {
                                            onSave(
                                                name.trim(),
                                                selectedColorIndex,
                                                finalPath,
                                                if (hasPin && pinCode.isNotBlank()) pinCode.trim() else null,
                                                isKids,
                                            )
                                            isSaving = false
                                            onDismiss()
                                        }
                                    }
                                } else {
                                    onSave(
                                        name.trim(),
                                        selectedColorIndex,
                                        trimmedCustom,
                                        if (hasPin && pinCode.isNotBlank()) pinCode.trim() else null,
                                        isKids,
                                    )
                                    onDismiss()
                                }
                            }
                        },
                        enabled = name.isNotBlank() && (!hasPin || pinCode.length >= 4) && !isSaving,
                    ) {
                        if (isSaving) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Text("Saving...")
                            }
                        } else {
                            Text("Save Profile")
                        }
                    }
                }
            }
        }
    }
}
