package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSliderItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun SettingsDetailsSectionsScreen() {
    val lockUnreleasedEpisodes by AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
    val antiSpoilerEnabled by AppearanceConfig.antiSpoilerEnabled.collectAsState()
    val detailsShowCurrentTime by AppearanceConfig.detailsShowCurrentTime.collectAsState()
    val detailsShowEndTime by AppearanceConfig.detailsShowEndTime.collectAsState()
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()
    val sectionOrder by AppearanceConfig.detailsSectionOrder.collectAsState()
    val disabledSections by AppearanceConfig.detailsDisabledSections.collectAsState()
    val scrollState = rememberScrollState()

    var draggingSectionKey by remember { mutableStateOf<DetailsSectionKey?>(null) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    var dragInitialIndex by remember { mutableStateOf(0) }
    var slotHeightPx by remember { mutableStateOf(0f) }
    val fallbackSlotHeight = with(LocalDensity.current) { 64.dp.toPx() }
    val effectiveSlotHeight = if (slotHeightPx > 0f) slotHeightPx else fallbackSlotHeight

    val currentTargetIndex = if (draggingSectionKey != null && effectiveSlotHeight > 0f) {
        (dragInitialIndex + kotlin.math.round(dragAccumulatedY / effectiveSlotHeight).toInt())
            .coerceIn(0, sectionOrder.lastIndex)
    } else dragInitialIndex

    val currentSectionOrder by rememberUpdatedState(sectionOrder)
    val currentEffectiveSlotHeight by rememberUpdatedState(effectiveSlotHeight)
    val currentDragAccumulatedY by rememberUpdatedState(dragAccumulatedY)
    val currentDragInitialIndex by rememberUpdatedState(dragInitialIndex)

    val onDropSection by rememberUpdatedState {
        val fromIdx = currentDragInitialIndex
        val slotH = currentEffectiveSlotHeight
        val accY = currentDragAccumulatedY
        val toIdx = if (slotH > 0f) {
            (fromIdx + kotlin.math.round(accY / slotH).toInt())
                .coerceIn(0, currentSectionOrder.lastIndex)
        } else fromIdx
        draggingSectionKey = null
        dragAccumulatedY = 0f
        if (fromIdx != toIdx && fromIdx in currentSectionOrder.indices && toIdx in currentSectionOrder.indices) {
            AppearanceConfig.moveDetailsSection(fromIdx, toIdx)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsGroupCard(title = "Modular Sections & Drag Reorder") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arrange Details Page Sections",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Drag the handles (⠿) to reorder sections, or toggle them on and off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { AppearanceConfig.resetDetailsSectionOrder() },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Order")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sectionOrder.forEachIndexed { index, sectionKey ->
                    val isEnabled = sectionKey !in disabledSections
                    val isDraggingThis = draggingSectionKey == sectionKey

                    val targetShiftY = when {
                        isDraggingThis -> dragAccumulatedY
                        draggingSectionKey != null && dragInitialIndex < currentTargetIndex && index in (dragInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                        draggingSectionKey != null && dragInitialIndex > currentTargetIndex && index in currentTargetIndex until dragInitialIndex -> effectiveSlotHeight
                        else -> 0f
                    }
                    val animatedShiftY by animateFloatAsState(
                        targetValue = targetShiftY,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )

                    val elevation by animateDpAsState(if (isDraggingThis) 16.dp else 0.dp)
                    val scale by animateFloatAsState(if (isDraggingThis) 1.02f else 1.0f)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDraggingThis) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        } else if (isEnabled) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                        },
                        border = BorderStroke(
                            if (isDraggingThis) 1.5.dp else 0.5.dp,
                            if (isDraggingThis) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                        ),
                        shadowElevation = elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                if (coordinates.size.height > 0 && slotHeightPx == 0f) {
                                    slotHeightPx = coordinates.size.height.toFloat() + 8f
                                }
                            }
                            .zIndex(if (isDraggingThis) 100f else 1f)
                            .scale(scale)
                            .graphicsLayer {
                                translationY = if (isDraggingThis) dragAccumulatedY else animatedShiftY
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Drag grip handle
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDraggingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                    .pointerInput(sectionKey) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingSectionKey = sectionKey
                                                dragInitialIndex = currentSectionOrder.indexOf(sectionKey)
                                                dragAccumulatedY = 0f
                                            },
                                            onDragEnd = { onDropSection() },
                                            onDragCancel = {
                                                draggingSectionKey = null
                                                dragAccumulatedY = 0f
                                            },
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragAccumulatedY += dragAmount.y
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Section info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sectionKey.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = sectionKey.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { AppearanceConfig.toggleDetailsSection(sectionKey, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                }
            }
        }

        SettingsGroupCard(title = "Episode Playback & Protection") {
            SettingsToggleItem(
                label = "Lock Unreleased Episodes",
                subtitle = "Prevent clicking and playing future/unreleased episodes and display countdown/air date badges",
                checked = lockUnreleasedEpisodes,
                onCheckedChange = { AppearanceConfig.setLockUnreleasedEpisodes(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsToggleItem(
                label = "Anti-Spoiler Mode",
                subtitle = "Hide episode thumbnails, titles, and descriptions until watched",
                checked = antiSpoilerEnabled,
                onCheckedChange = { AppearanceConfig.setAntiSpoilerEnabled(it) },
            )
        }

        SettingsGroupCard(title = "Time Badges & Metadata") {
            SettingsToggleItem(
                label = "Show Current Time",
                subtitle = "Display the current time on the details page",
                checked = detailsShowCurrentTime,
                onCheckedChange = { AppearanceConfig.setDetailsShowCurrentTime(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsToggleItem(
                label = "Show End Time",
                subtitle = "Display what time the movie/episode will end",
                checked = detailsShowEndTime,
                onCheckedChange = { AppearanceConfig.setDetailsShowEndTime(it) },
            )
        }

        SettingsGroupCard(title = "Backdrop Frosted Blur & Atmosphere") {
            SettingsToggleItem(
                label = "Dynamic Backdrop Blur",
                subtitle = "Apply atmospheric frosted blur to hero backdrops on movie details screens",
                checked = heroBackgroundBlurEnabled,
                onCheckedChange = { AppearanceConfig.setHeroBackgroundBlurEnabled(it) },
            )

            if (heroBackgroundBlurEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Backdrop Softness",
                    subtitle = "${heroBackdropBlurRadius.toInt()} dp blur",
                    value = heroBackdropBlurRadius,
                    onValueChange = { AppearanceConfig.setHeroBackdropBlurRadius(it) },
                    valueRange = 8f..64f,
                    steps = 7,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Backdrop Darkening",
                    subtitle = "${(heroBackdropDarkening * 100).toInt()}% overlay",
                    value = heroBackdropDarkening,
                    onValueChange = { AppearanceConfig.setHeroBackdropDarkening(it) },
                    valueRange = 0.1f..0.8f,
                    steps = 7,
                )
            }
        }
    }
}
