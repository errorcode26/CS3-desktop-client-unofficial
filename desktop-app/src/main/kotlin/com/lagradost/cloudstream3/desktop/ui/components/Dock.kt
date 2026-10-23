package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun DockItemsList(
    currentTitle: String,
    isHorizontal: Boolean = false,
    indicatorAtTop: Boolean = false,
    isRight: Boolean = false,
    isExpanded: Boolean = false,
    onNavigate: (com.lagradost.cloudstream3.desktop.ui.navigation.Config) -> Unit,
    onSearchClick: () -> Unit,
) {
    val dockOrder by AppearanceConfig.dockItemOrder.collectAsState()
    val dockDisabled by AppearanceConfig.dockDisabledItems.collectAsState()

    dockOrder.filter { it !in dockDisabled }.forEach { itemKey ->
        when (itemKey) {
            com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.HOME -> {
                DockItem(
                    icon = PremiumIcons.Home,
                    label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.HOME,
                    selected = currentTitle == "Home",
                    isHorizontal = isHorizontal,
                    indicatorAtTop = indicatorAtTop,
                    isRight = isRight,
                    isExpanded = isExpanded,
                    onClick = {
                        if (currentTitle == "Home") {
                            com.lagradost.cloudstream3.desktop.ui.GlobalScrollToTopHandler.trigger(com.lagradost.cloudstream3.desktop.ui.NavScrollTarget.HOME)
                        } else {
                            onNavigate(com.lagradost.cloudstream3.desktop.ui.navigation.Config.Home)
                        }
                    },
                )
            }
            com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.EXPLORE -> {
                DockItem(
                    icon = PremiumIcons.Explore,
                    label = "Explore",
                    selected = currentTitle == "Explore & Catalogs",
                    isHorizontal = isHorizontal,
                    indicatorAtTop = indicatorAtTop,
                    isRight = isRight,
                    isExpanded = isExpanded,
                    onClick = {
                        if (currentTitle == "Explore & Catalogs") {
                            com.lagradost.cloudstream3.desktop.ui.GlobalScrollToTopHandler.trigger(com.lagradost.cloudstream3.desktop.ui.NavScrollTarget.EXPLORE)
                        } else {
                            onNavigate(com.lagradost.cloudstream3.desktop.ui.navigation.Config.Explore)
                        }
                    },
                )
            }
            com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.SEARCH -> {
                DockItem(
                    icon = PremiumIcons.Search,
                    label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.SEARCH,
                    selected = currentTitle == "Search",
                    isHorizontal = isHorizontal,
                    indicatorAtTop = indicatorAtTop,
                    isRight = isRight,
                    isExpanded = isExpanded,
                    onClick = onSearchClick,
                )
            }
            com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.LIBRARY -> {
                DockItem(
                    icon = PremiumIcons.Library,
                    label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.LIBRARY,
                    selected = currentTitle == "Library",
                    isHorizontal = isHorizontal,
                    indicatorAtTop = indicatorAtTop,
                    isRight = isRight,
                    isExpanded = isExpanded,
                    onClick = { onNavigate(com.lagradost.cloudstream3.desktop.ui.navigation.Config.Library) },
                )
            }
            com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.DOWNLOADS -> {
                val activeTasks by com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager.tasks.collectAsState()
                val downloadingCount = activeTasks.count { it.status == com.lagradost.cloudstream3.desktop.downloader.DownloadStatus.DOWNLOADING }

                DockItem(
                    icon = PremiumIcons.Downloads,
                    label = "Downloads",
                    selected = currentTitle == "Downloads",
                    isHorizontal = isHorizontal,
                    indicatorAtTop = indicatorAtTop,
                    isRight = isRight,
                    isExpanded = isExpanded,
                    badge = if (downloadingCount > 0) downloadingCount.toString() else null,
                    onClick = { onNavigate(com.lagradost.cloudstream3.desktop.ui.navigation.Config.Downloads) },
                )
            }
            com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.HISTORY -> {
                DockItem(
                    icon = PremiumIcons.History,
                    label = "Watch History",
                    selected = currentTitle == "Watch History",
                    isHorizontal = isHorizontal,
                    indicatorAtTop = indicatorAtTop,
                    isRight = isRight,
                    isExpanded = isExpanded,
                    onClick = { onNavigate(com.lagradost.cloudstream3.desktop.ui.navigation.Config.History) },
                )
            }
            com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.EXTENSIONS -> {
                DockItem(
                    icon = PremiumIcons.Extensions,
                    label = "Extensions",
                    selected = currentTitle == "Extensions",
                    isHorizontal = isHorizontal,
                    indicatorAtTop = indicatorAtTop,
                    isRight = isRight,
                    isExpanded = isExpanded,
                    onClick = { onNavigate(com.lagradost.cloudstream3.desktop.ui.navigation.Config.Extensions()) },
                )
            }
            com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.SETTINGS -> {
                DockItem(
                    icon = PremiumIcons.Settings,
                    label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.SETTINGS,
                    selected = currentTitle == "Settings",
                    isHorizontal = isHorizontal,
                    indicatorAtTop = indicatorAtTop,
                    isRight = isRight,
                    isExpanded = isExpanded,
                    onClick = { onNavigate(com.lagradost.cloudstream3.desktop.ui.navigation.Config.Settings) },
                )
            }
        }
    }
}

@Composable
fun DockItem(
    icon: ImageVector,
    label: String,
    showLabel: Boolean = false,
    selected: Boolean,
    badge: String? = null,
    isHorizontal: Boolean = false,
    indicatorAtTop: Boolean = false,
    isRight: Boolean = false,
    isExpanded: Boolean = false,
    onClick: () -> Unit,
) {
    val itemInteraction = remember { MutableInteractionSource() }
    val isHovered by itemInteraction.collectIsHoveredAsState()

    val theme = LocalDesktopTheme.current
    val iconTint = when {
        selected -> MaterialTheme.colorScheme.primary
        isHovered -> theme.TextPrimary
        else -> theme.TextMuted
    }
    val bgAlpha by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        label = "dockItemBgAlpha",
    )
    val targetScale = if (isHovered && isHorizontal) 1.15f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dockItemScale",
    )

    val hoverBgColor = if (isHorizontal) theme.SurfaceElevated else Color.White.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .then(
                if (isHorizontal) {
                    Modifier.size(42.dp)
                } else {
                    Modifier.fillMaxWidth().height(44.dp)
                },
            )
            .clip(RoundedCornerShape(12.dp))
            .hoverable(itemInteraction)
            .clickable(
                interactionSource = itemInteraction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Hover Background layer (using graphicsLayer to avoid Compose Desktop alpha blending bugs)
        if (bgAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = bgAlpha }
                    .clip(RoundedCornerShape(14.dp))
                    .background(hoverBgColor),
            )
        }

        // Active indicator pill
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + when {
                indicatorAtTop -> slideInVertically { -it / 2 }
                isHorizontal -> slideInHorizontally { it / 2 }
                else -> slideInVertically { it / 2 }
            },
            exit = fadeOut() + when {
                indicatorAtTop -> slideOutVertically { -it / 2 }
                isHorizontal -> slideOutHorizontally { it / 2 }
                else -> slideOutVertically { it / 2 }
            },
            modifier = Modifier.align(
                when {
                    indicatorAtTop -> Alignment.TopCenter
                    isHorizontal -> Alignment.BottomCenter
                    isRight -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
            ),
        ) {
            Box(
                modifier = Modifier
                    .run {
                        when {
                            indicatorAtTop -> padding(top = 2.dp).width(18.dp).height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                            isHorizontal -> padding(bottom = 2.dp).width(18.dp).height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                            isRight -> padding(end = 2.dp).width(3.dp).height(18.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                            else -> padding(start = 2.dp).width(3.dp).height(18.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                        }
                    }
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        if (isHorizontal) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                    )

                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }

                if (showLabel) {
                    AnimatedVisibility(
                        visible = isHovered,
                        enter = fadeIn() + expandVertically() + slideInVertically { it / 2 },
                        exit = fadeOut() + shrinkVertically() + slideOutVertically { it / 2 },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                color = if (selected) theme.TextPrimary else theme.TextMuted,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        } else {
            // Side-mounted rail (LEFT or RIGHT): Rock-solid unified icon alignment with smooth staggered text reveal
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start,
            ) {
                if (isRight) {
                    // Right dock: smooth staggered text on the left
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn(tween(140, delayMillis = 40)) + slideInHorizontally(tween(140, delayMillis = 40)) { 20 },
                        exit = fadeOut(tween(70)) + slideOutHorizontally(tween(70)) { 20 },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            if (badge != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else if (isHovered) {
                                    theme.TextPrimary
                                } else {
                                    theme.TextMuted
                                },
                                fontWeight = if (selected) {
                                    FontWeight.Bold
                                } else if (isHovered) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Medium
                                },
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Fixed 64dp icon slot: Centered at x = 32dp from the edge, NEVER moves
                    Box(
                        modifier = Modifier.size(width = 64.dp, height = 44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                        if (badge != null && !isExpanded) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-12).dp, y = 6.dp)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape),
                            )
                        }
                    }
                } else {
                    // Left dock:
                    // Fixed 64dp icon slot: Centered at x = 32dp from the edge, NEVER moves
                    Box(
                        modifier = Modifier.size(width = 64.dp, height = 44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                        if (badge != null && !isExpanded) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-12).dp, y = 6.dp)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape),
                            )
                        }
                    }

                    // Smooth staggered text on the right
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn(tween(140, delayMillis = 40)) + slideInHorizontally(tween(140, delayMillis = 40)) { -20 },
                        exit = fadeOut(tween(70)) + slideOutHorizontally(tween(70)) { -20 },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else if (isHovered) {
                                    theme.TextPrimary
                                } else {
                                    theme.TextMuted
                                },
                                fontWeight = if (selected) {
                                    FontWeight.Bold
                                } else if (isHovered) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Medium
                                },
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (badge != null) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DockProfileAvatar(
    isExpanded: Boolean,
    isRight: Boolean = false,
    isHorizontal: Boolean = false,
    onOpenProfileManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val activeProfile by com.lagradost.cloudstream3.desktop.profile.ProfileManager.activeProfile.collectAsState()
    val profiles by com.lagradost.cloudstream3.desktop.profile.ProfileManager.profiles.collectAsState()
    val theme = LocalDesktopTheme.current
    val isLightMode = theme.isLightMode

    var showProfileFlyout by remember { mutableStateOf(false) }
    var pinPromptProfile by remember { mutableStateOf<com.lagradost.cloudstream3.desktop.profile.Profile?>(null) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // PIN prompt modal
    PinCodeDialog(
        show = pinPromptProfile != null,
        profile = pinPromptProfile,
        onDismiss = { pinPromptProfile = null },
        onVerified = {
            pinPromptProfile?.let { target ->
                com.lagradost.cloudstream3.desktop.profile.ProfileManager.switchProfile(target.id, target.pinCode ?: "")
            }
            pinPromptProfile = null
        },
    )

    if (showCreateProfileDialog) {
        com.lagradost.cloudstream3.desktop.ui.screens.profile.ProfileEditDialog(
            profile = null,
            canDelete = false,
            onDismiss = { showCreateProfileDialog = false },
            onSave = { name, colorIndex, customAvatar, pin, isKids ->
                val newP = com.lagradost.cloudstream3.desktop.profile.ProfileManager.createProfile(name, colorIndex, customAvatar, pin, isKids)
                com.lagradost.cloudstream3.desktop.profile.ProfileManager.switchProfile(newP.id)
            },
        )
    }

    Box(modifier = modifier) {
        Surface(
            modifier = if (isHorizontal) {
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .hoverable(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { showProfileFlyout = true },
                    )
            } else {
                Modifier
                    .height(48.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .hoverable(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { showProfileFlyout = true },
                    )
            },
            color = if (isHovered) Color.White.copy(alpha = 0.08f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isHorizontal) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, if (isHovered) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f)),
                        color = Color.Transparent,
                    ) {
                        com.lagradost.cloudstream3.desktop.profile.ProfileAvatar(
                            profile = activeProfile,
                            size = 30.dp,
                            shape = CircleShape,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start,
                ) {
                    if (isRight) {
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn(tween(140, delayMillis = 40)) + slideInHorizontally(tween(140, delayMillis = 40)) { 20 },
                            exit = fadeOut(tween(70)) + slideOutHorizontally(tween(70)) { 20 },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp),
                            ) {
                                Text(
                                    text = activeProfile.name,
                                    color = if (isHovered) theme.TextPrimary else theme.TextMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.size(width = 64.dp, height = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                border = BorderStroke(1.5.dp, if (isHovered) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f)),
                                color = Color.Transparent,
                            ) {
                                com.lagradost.cloudstream3.desktop.profile.ProfileAvatar(
                                    profile = activeProfile,
                                    size = 34.dp,
                                    shape = CircleShape,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(width = 64.dp, height = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                border = BorderStroke(1.5.dp, if (isHovered) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f)),
                                color = Color.Transparent,
                            ) {
                                com.lagradost.cloudstream3.desktop.profile.ProfileAvatar(
                                    profile = activeProfile,
                                    size = 34.dp,
                                    shape = CircleShape,
                                    fontSize = 14.sp,
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn(tween(140, delayMillis = 40)) + slideInHorizontally(tween(140, delayMillis = 40)) { -20 },
                            exit = fadeOut(tween(70)) + slideOutHorizontally(tween(70)) { -20 },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 12.dp),
                            ) {
                                Column {
                                    Text(
                                        text = activeProfile.name,
                                        color = if (isHovered) theme.TextPrimary else theme.TextMuted,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    if (activeProfile.isKids) {
                                        Text(
                                            text = "Kids",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Profile Switcher Dropdown
        androidx.compose.material3.DropdownMenu(
            expanded = showProfileFlyout,
            onDismissRequest = { showProfileFlyout = false },
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isLightMode) Color(0xFFFAFAFC) else Color(0xFF16161A))
                .border(1.dp, if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(8.dp),
        ) {
            val currProfile = activeProfile
            if (currProfile != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        com.lagradost.cloudstream3.desktop.profile.ProfileAvatar(
                            profile = currProfile,
                            size = 38.dp,
                            shape = CircleShape,
                            fontSize = 15.sp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currProfile.name,
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
                                    text = if (currProfile.isKids) "Kids Profile • Active" else "Active Profile",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
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
                                    com.lagradost.cloudstream3.desktop.profile.ProfileManager.switchProfile(profile.id)
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            com.lagradost.cloudstream3.desktop.profile.ProfileAvatar(
                                profile = profile,
                                size = 32.dp,
                                shape = CircleShape,
                                fontSize = 13.sp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                if (profile.isKids) {
                                    Text(
                                        text = "Kids Profile",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (profile.hasPin) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                    contentDescription = "PIN Protected",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(8.dp))

            // Add Profile & Manage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        showProfileFlyout = false
                        showCreateProfileDialog = true
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = {
                        showProfileFlyout = false
                        onOpenProfileManager?.invoke()
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Manage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
