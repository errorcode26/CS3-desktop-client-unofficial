package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.profile.Profile
import com.lagradost.cloudstream3.desktop.profile.ProfileAvatar
import com.lagradost.cloudstream3.desktop.profile.ProfileManager
import com.lagradost.cloudstream3.desktop.ui.screens.profile.ProfileEditDialog
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode
import kotlinx.coroutines.delay

@Composable
fun TopBar(
    isHome: Boolean,
    homeUiState: com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState? = null,
    homeActionDispatcher: ((com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent) -> Unit)? = null,
    onBack: () -> Unit = {},
    onOpenProfileManager: (() -> Unit)? = null,
) {
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 600.dp
        val effectiveDockPosition = if (isCompact) com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM else dockPosition
        val navPaddingStart = when (effectiveDockPosition) {
            com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT -> 84.dp
            else -> if (isCompact) 12.dp else 32.dp
        }
        val navPaddingEnd = when (effectiveDockPosition) {
            com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT -> 84.dp
            else -> if (isCompact) 12.dp else 32.dp
        }

        val bg = Color.Transparent
        Column(modifier = Modifier.fillMaxWidth().background(bg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 50.dp else 56.dp)
                    .padding(start = navPaddingStart, end = navPaddingEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 16.dp),
                ) {
                    if (!isCompact) {
                        ClockWidget()
                    }
                    TopBarProfilePill(onOpenProfileManager = onOpenProfileManager)
                }

                Spacer(Modifier.weight(1f))

                WindowControlsPill(
                    isHome = isHome,
                    isCompact = isCompact,
                    homeUiState = homeUiState,
                    homeActionDispatcher = homeActionDispatcher,
                )
            }
        }
    }
}

@Composable
private fun TopBarProfilePill(
    onOpenProfileManager: (() -> Unit)? = null,
) {
    val showProfile by AppearanceConfig.topBarShowProfile.collectAsState()
    if (!showProfile) return

    val showProfileName by AppearanceConfig.topBarShowProfileName.collectAsState()
    val profiles by ProfileManager.profiles.collectAsState()
    val activeProfile by ProfileManager.activeProfile.collectAsState()

    var showProfileFlyout by remember { mutableStateOf(false) }
    var pinPromptProfile by remember { mutableStateOf<Profile?>(null) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }

    // PIN prompt modal
    PinCodeDialog(
        show = pinPromptProfile != null,
        profile = pinPromptProfile,
        onDismiss = { pinPromptProfile = null },
        onVerified = {
            pinPromptProfile?.let { target ->
                ProfileManager.switchProfile(target.id, target.pinCode ?: "")
            }
            pinPromptProfile = null
        },
    )

    if (showCreateProfileDialog) {
        ProfileEditDialog(
            profile = null,
            canDelete = false,
            onDismiss = { showCreateProfileDialog = false },
            onSave = { name, colorIndex, customAvatar, pin, isKids ->
                val newP = ProfileManager.createProfile(name, colorIndex, customAvatar, pin, isKids)
                ProfileManager.switchProfile(newP.id)
            },
        )
    }

    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val buttonBg = if (isLightMode) Color.White.copy(alpha = 0.85f) else Color(0xFF1E1E24).copy(alpha = 0.50f)
    val buttonBorder = if (isLightMode) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
    val hoverBorder = if (isLightMode) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.25f)

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isExpanded = isHovered || showProfileFlyout

    Box {
        Surface(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { showProfileFlyout = true },
                )
                .border(
                    1.dp,
                    if (isHovered) hoverBorder else buttonBorder,
                    RoundedCornerShape(10.dp),
                ),
            color = buttonBg,
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(
                    profile = activeProfile,
                    size = 36.dp,
                    shape = RoundedCornerShape(8.dp),
                    fontSize = 15.sp,
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = isExpanded,
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)),
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) + androidx.compose.animation.shrinkHorizontally(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    ) {
                        Text(
                            text = activeProfile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch Profile",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showProfileFlyout,
            onDismissRequest = { showProfileFlyout = false },
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isLightMode) Color(0xFFFAFAFC) else Color(0xFF16161A))
                .border(1.dp, if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(8.dp),
        ) {
            // 1. Hero Card (Current Active Profile)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileAvatar(
                        profile = activeProfile,
                        size = 38.dp,
                        shape = RoundedCornerShape(9.dp),
                        fontSize = 15.sp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeProfile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                            Text(
                                text = if (activeProfile.isKids) "Kids Profile • Active" else "Active Profile",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            val otherProfiles = remember(profiles, activeProfile) { profiles.filter { it.id != activeProfile.id } }
            if (otherProfiles.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "SWITCH ACCOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(4.dp))

                otherProfiles.forEach { profile ->
                    val rowInteractionSource = remember { MutableInteractionSource() }
                    val isRowHovered by rowInteractionSource.collectIsHoveredAsState()

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isRowHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = rowInteractionSource, indication = null) {
                                showProfileFlyout = false
                                if (profile.hasPin) {
                                    pinPromptProfile = profile
                                } else {
                                    ProfileManager.switchProfile(profile.id)
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ProfileAvatar(
                                profile = profile,
                                size = 28.dp,
                                shape = RoundedCornerShape(7.dp),
                                fontSize = 12.sp,
                            )
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isRowHovered) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isRowHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (profile.hasPin) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "PIN Protected",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(4.dp))

            // Action: Add Profile
            val addInteraction = remember { MutableInteractionSource() }
            val isAddHovered by addInteraction.collectIsHoveredAsState()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isAddHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = addInteraction, indication = null) {
                        showProfileFlyout = false
                        showCreateProfileDialog = true
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isAddHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Add Profile",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isAddHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Action: Manage Profiles Hub
            val manageInteraction = remember { MutableInteractionSource() }
            val isManageHovered by manageInteraction.collectIsHoveredAsState()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isManageHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = manageInteraction, indication = null) {
                        showProfileFlyout = false
                        onOpenProfileManager?.invoke()
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.ManageAccounts,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isManageHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Manage Profiles",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isManageHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockWidget() {
    val mode by AppearanceConfig.clockMode.collectAsState()
    if (mode == ClockDisplayMode.HIDDEN) return

    val timeFormat by AppearanceConfig.clockTimeFormat.collectAsState()
    val dateFormat by AppearanceConfig.clockDateFormat.collectAsState()

    val now by produceState(initialValue = java.time.LocalDateTime.now()) {
        while (true) {
            delay(1000)
            value = java.time.LocalDateTime.now()
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        if (mode == ClockDisplayMode.TIME_ONLY || mode == ClockDisplayMode.BOTH) {
            Text(
                text = try {
                    now.format(java.time.format.DateTimeFormatter.ofPattern(timeFormat))
                } catch (_: Exception) {
                    "--:--"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (mode == ClockDisplayMode.DATE_ONLY || mode == ClockDisplayMode.BOTH) {
            Text(
                text = try {
                    now.format(java.time.format.DateTimeFormatter.ofPattern(dateFormat))
                } catch (_: Exception) {
                    "---"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
