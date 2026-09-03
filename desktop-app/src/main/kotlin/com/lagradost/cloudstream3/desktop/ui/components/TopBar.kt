package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.profile.Profile
import com.lagradost.cloudstream3.desktop.profile.ProfileAvatar
import com.lagradost.cloudstream3.desktop.profile.ProfileManager
import com.lagradost.cloudstream3.desktop.ui.screens.profile.ProfileEditDialog
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay

@Composable
fun TopBar(
    isHome: Boolean,
    homeUiState: com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState? = null,
    homeActionDispatcher: ((com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent) -> Unit)? = null,
    onBack: () -> Unit = {},
    onOpenProfileManager: (() -> Unit)? = null,
    showDock: Boolean = false,
    currentTitle: String = "",
    onNavigate: (com.lagradost.cloudstream3.desktop.ui.navigation.Config) -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 600.dp
        val effectiveDockPosition = if (isCompact) com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM else dockPosition
        val isTopDock = effectiveDockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.TOP && !isCompact && showDock
        val navPaddingStart = if (isCompact) 12.dp else 16.dp
        val navPaddingEnd = if (isCompact) 12.dp else 16.dp

        val navStyle by AppearanceConfig.navigationStyle.collectAsState()
        val isSeamless = navStyle == com.lagradost.cloudstream3.desktop.ui.theme.NavigationStyle.SEAMLESS_BAR
        val isSeamlessTop = isTopDock && isSeamless

        val hazeState = com.lagradost.cloudstream3.desktop.ui.LocalHazeState.current
        val isScrolled = com.lagradost.cloudstream3.desktop.ui.TopBarScrollState.isScrolled
        val isLightMode by AppearanceConfig.isLightMode.collectAsState()
        val amoledMode by AppearanceConfig.amoledMode.collectAsState()

        val shouldHaveBackground = isSeamlessTop

        val targetBlurRadius by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (shouldHaveBackground) 24.dp else 0.dp,
            animationSpec = androidx.compose.animation.core.tween(250),
        )
        val targetTintAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isSeamlessTop) {
                if (amoledMode) 0.94f else 0.85f
            } else {
                0.0f
            },
            animationSpec = androidx.compose.animation.core.tween(250),
        )

        val tintColor = when {
            isLightMode -> Color(0xFFFAFAFC)
            amoledMode -> Color.Black
            else -> Color(0xFF0F0F14)
        }

        val glassBase = when {
            isLightMode -> Color.White
            amoledMode -> Color.Black
            else -> Color(0xFF14141A)
        }
        val glassGradient = if (amoledMode) {
            androidx.compose.ui.graphics.SolidColor(Color.Black)
        } else {
            androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    glassBase.copy(alpha = 0.75f),
                    glassBase.copy(alpha = 0.60f),
                ),
            )
        }
        val borderGradient = if (amoledMode) {
            androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.12f))
        } else {
            androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    if (isLightMode) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f),
                    if (isLightMode) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f),
                ),
            )
        }

        val hazeModifier = if (hazeState != null && targetBlurRadius > 0.dp && !amoledMode) {
            Modifier.hazeEffect(
                state = hazeState,
                style = dev.chrisbanes.haze.HazeStyle(
                    backgroundColor = tintColor.copy(alpha = 0.65f),
                    tint = dev.chrisbanes.haze.HazeTint(tintColor.copy(alpha = 0.65f)),
                    blurRadius = targetBlurRadius,
                    noiseFactor = 0f,
                ),
            )
        } else {
            Modifier
        }

        val borderModifier = if (targetTintAlpha > 0.05f) {
            Modifier.drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawLine(
                        color = if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
        } else {
            Modifier
        }

        val backgroundModifier = if (targetTintAlpha > 0.01f) {
            Modifier.background(tintColor.copy(alpha = targetTintAlpha))
        } else {
            Modifier
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(backgroundModifier)
                .then(hazeModifier)
                .then(borderModifier)
                .pointerInput(Unit) {
                    detectTapGestures { }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 50.dp else 56.dp)
                    .padding(start = navPaddingStart, end = navPaddingEnd),
            ) {
                // Top-Left: Clock & Date Widget anchored to corner
                if (!isCompact) {
                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
                        ClockWidget(alignment = Alignment.Start)
                    }
                }

                if (isTopDock) {
                    // Center: 100% Window-Centered Navigation Dock (Seamless or Floating Island Pill)
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSeamless) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DockItemsList(
                                    currentTitle = currentTitle,
                                    isHorizontal = true,
                                    indicatorAtTop = true,
                                    onNavigate = onNavigate,
                                    onSearchClick = onSearchClick,
                                )
                            }
                        } else {
                            val topDockHazeModifier = if (hazeState != null && !amoledMode) {
                                Modifier.hazeEffect(
                                    state = hazeState,
                                    style = dev.chrisbanes.haze.HazeStyle(
                                        backgroundColor = glassBase.copy(alpha = 0.65f),
                                        tint = dev.chrisbanes.haze.HazeTint(glassBase.copy(alpha = 0.65f)),
                                        blurRadius = 24.dp,
                                    ),
                                )
                            } else Modifier

                            val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
                            Box(modifier = Modifier.height(52.dp).wrapContentWidth()) {
                                // Drop shadow
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .blur(14.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                                        .background(Color.Black.copy(alpha = 0.45f), pillShape),
                                )

                                // Main Glass Pill Container
                                Box(
                                    modifier = Modifier
                                        .clip(pillShape)
                                        .then(topDockHazeModifier)
                                        .background(glassGradient)
                                        .border(1.2.dp, borderGradient, pillShape)
                                        .pointerInput(Unit) {
                                            detectTapGestures { }
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        DockItemsList(
                                            currentTitle = currentTitle,
                                            isHorizontal = true,
                                            indicatorAtTop = false,
                                            onNavigate = onNavigate,
                                            onSearchClick = onSearchClick,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Top-Right: Home Actions & Profile Avatar Pill
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
                ) {
                    TopBarDownloadPill()

                    WindowControlsPill(
                        isHome = isHome,
                        isCompact = isCompact,
                        homeUiState = homeUiState,
                        homeActionDispatcher = homeActionDispatcher,
                    )

                    TopBarProfilePill(onOpenProfileManager = onOpenProfileManager)
                }
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
    val isExpanded = showProfileName || isHovered || showProfileFlyout

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
private fun ClockWidget(alignment: Alignment.Horizontal = Alignment.Start) {
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

    val textAlign = if (alignment == Alignment.Start) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End
    val textShadow = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.40f),
        offset = androidx.compose.ui.geometry.Offset(0f, 1f),
        blurRadius = 2f,
    )

    Column(
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.Center,
    ) {
        if (mode == ClockDisplayMode.TIME_ONLY || mode == ClockDisplayMode.BOTH) {
            Text(
                text = try {
                    now.format(java.time.format.DateTimeFormatter.ofPattern(timeFormat))
                } catch (_: Exception) {
                    "--:--"
                },
                fontFamily = com.lagradost.cloudstream3.desktop.ui.theme.InterFontFamily,
                fontSize = if (mode == ClockDisplayMode.BOTH) 15.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = Color.White,
                textAlign = textAlign,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow),
            )
        }
        if (mode == ClockDisplayMode.DATE_ONLY || mode == ClockDisplayMode.BOTH) {
            val isDateOnly = mode == ClockDisplayMode.DATE_ONLY
            Text(
                text = try {
                    now.format(java.time.format.DateTimeFormatter.ofPattern(dateFormat))
                } catch (_: Exception) {
                    "---"
                },
                fontFamily = com.lagradost.cloudstream3.desktop.ui.theme.InterFontFamily,
                fontSize = if (isDateOnly) 14.5.sp else 12.sp,
                fontWeight = if (isDateOnly) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.2.sp,
                color = if (isDateOnly) Color.White else Color.White.copy(alpha = 0.70f),
                textAlign = textAlign,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow),
            )
        }
    }
}
