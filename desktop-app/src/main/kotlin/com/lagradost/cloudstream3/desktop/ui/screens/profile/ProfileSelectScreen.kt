package com.lagradost.cloudstream3.desktop.ui.screens.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.profile.Profile
import com.lagradost.cloudstream3.desktop.profile.ProfileAvatar
import com.lagradost.cloudstream3.desktop.profile.ProfileManager
import com.lagradost.cloudstream3.desktop.ui.DesktopAppShell
import com.lagradost.cloudstream3.desktop.ui.components.PinCodeDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog

@Composable
fun ProfileSelectScreen(
    onNavigateHome: () -> Unit,
) {
    val profiles by ProfileManager.profiles.collectAsState()
    val activeProfile by ProfileManager.activeProfile.collectAsState()

    var isManageMode by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    var pinPromptProfile by remember { mutableStateOf<Profile?>(null) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // PIN Verification Modal with Discrete Boxes
    PinCodeDialog(
        show = pinPromptProfile != null,
        profile = pinPromptProfile,
        onDismiss = { pinPromptProfile = null },
        onVerified = {
            pinPromptProfile?.let { target ->
                ProfileManager.switchProfile(target.id, target.pinCode ?: "")
            }
            pinPromptProfile = null
            onNavigateHome()
        },
    )

    // Profile Edit / Create Modal
    if (editingProfile != null || isCreatingNew) {
        ProfileEditDialog(
            profile = editingProfile,
            canDelete = profiles.size > 1,
            onDismiss = {
                editingProfile = null
                isCreatingNew = false
            },
            onSave = { name, colorIndex, customAvatar, pin, isKids ->
                if (isCreatingNew) {
                    ProfileManager.createProfile(name, colorIndex, customAvatar, pin, isKids)
                } else if (editingProfile != null) {
                    ProfileManager.updateProfile(
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
                editingProfile?.let { ProfileManager.deleteProfile(it.id) }
            },
        )
    }

    // Inherit the global app shell wallpaper, ambient glows, and theme backdrop
    DesktopAppShell(
        onNavigate = {},
        showDock = false,
        showTopBar = false,
        applySafePadding = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 900.dp)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (isManageMode) "Manage Profiles" else "Who's Watching?",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 0.5.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isManageMode) "Click any profile to customize its name, colors, picture, or PIN." else "Select a profile to continue watching your movies and series.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Profiles Grid with ample unconstrained space
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileCardItem(
                            profile = profile,
                            isManageMode = isManageMode,
                            isActive = profile.id == activeProfile.id,
                            onClick = {
                                if (isManageMode) {
                                    editingProfile = profile
                                } else {
                                    if (profile.hasPin) {
                                        pinPromptProfile = profile
                                    } else {
                                        ProfileManager.switchProfile(profile.id)
                                        onNavigateHome()
                                    }
                                }
                            },
                        )
                    }

                    // Add Profile Card
                    if (profiles.size < 8) {
                        item {
                            AddProfileCardItem(onClick = { isCreatingNew = true })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(44.dp))

                // Bottom Actions
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (isManageMode) {
                        Button(
                            onClick = { isManageMode = false },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isManageMode = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))),
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manage Profiles", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCardItem(
    profile: Profile,
    isManageMode: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isHovered -> MaterialTheme.colorScheme.primary
            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(if (isHovered || isActive) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(
                profile = profile,
                size = 110.dp,
                shape = RoundedCornerShape(18.dp),
                fontSize = 44.sp,
            )

            // PIN Lock Indicator
            if (profile.hasPin && !isManageMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }

            // Kids Mode Indicator
            if (profile.isKids && !isManageMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text("KIDS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            // Manage Mode Pencil Overlay
            if (isManageMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Profile Name with unconstrained visible height
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isHovered || isActive) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isActive && !isManageMode) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                Text("Active", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AddProfileCardItem(
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                )
                .border(
                    1.dp,
                    if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Profile",
                modifier = Modifier.size(36.dp),
                tint = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Add Profile",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isHovered) FontWeight.Bold else FontWeight.Medium,
            color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
