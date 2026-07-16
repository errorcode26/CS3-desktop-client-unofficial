package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun SettingsAppearance() {
    val themeAccent by AppearanceConfig.themeAccent.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
    val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
    val ambientGlowPositions by AppearanceConfig.ambientGlowPositions.collectAsState()
    val heroDynamicColorEnabled by AppearanceConfig.heroDynamicColorEnabled.collectAsState()
    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val layoutWidth by AppearanceConfig.layoutWidth.collectAsState()
    val searchBarMode by AppearanceConfig.searchBarMode.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val selectedFont by AppearanceConfig.selectedFont.collectAsState()

    val accentColors = listOf(
        "Purple" to Color(0xFF7C6BFF),
        "Blue" to Color(0xFF3B82F6),
        "Green" to Color(0xFF10B981),
        "Red" to Color(0xFFEF4444),
        "Orange" to Color(0xFFF59E0B),
    )

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- Group 1: Theme & Colors ---
        SettingsGroupCard(title = "Theme & Colors") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Theme Color", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    accentColors.forEach { (name, color) ->
                        val isSelected = themeAccent == name
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { AppearanceConfig.setThemeAccent(name) },
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .align(Alignment.Center),
                                )
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            SettingsToggleItem(
                label = "Light Theme",
                subtitle = "Use a bright white interface",
                checked = isLightMode,
                onCheckedChange = {
                    AppearanceConfig.setLightMode(it)
                    if (it) AppearanceConfig.setAmoledMode(false)
                }
            )
            
            SettingsToggleItem(
                label = "AMOLED Mode",
                subtitle = "Pure black background for OLED screens",
                checked = amoledMode,
                onCheckedChange = {
                    AppearanceConfig.setAmoledMode(it)
                    if (it) AppearanceConfig.setLightMode(false)
                }
            )
        }

        // --- Group 2: Typography ---
        SettingsGroupCard(title = "Typography") {
            SettingsDropdownItem(
                label = "App Font",
                subtitle = "Choose the font used throughout the app",
                options = com.lagradost.cloudstream3.desktop.ui.theme.availableFonts.map { it to it },
                currentValue = selectedFont,
                onSelectionChanged = { AppearanceConfig.setSelectedFont(it) }
            )
        }

        // --- Group 2: Cinematic Aesthetics ---
        SettingsGroupCard(title = "Cinematic Aesthetics") {
            SettingsToggleItem(
                label = "Ambient Glow",
                subtitle = "Adds a subtle, theme-colored gradient background",
                checked = ambientGlowEnabled,
                onCheckedChange = { AppearanceConfig.setAmbientGlowEnabled(it) }
            )
            
            if (ambientGlowEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                
                SettingsSliderItem(
                    label = "Intensity",
                    subtitle = "Adjust how bright the background ambient glow is",
                    value = ambientGlowIntensity,
                    valueRange = 0.0f..0.5f,
                    steps = 100,
                    onValueChange = { AppearanceConfig.setAmbientGlowIntensity(it) }
                )
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Position", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Center", "Top", "Bottom", "Left", "Right", "Top Left", "Top Right", "Bottom Left", "Bottom Right").forEach { pos ->
                            val isSelected = ambientGlowPositions.contains(pos)
                            FilterChip(
                                selected = isSelected,
                                onClick = { AppearanceConfig.toggleAmbientGlowPosition(pos) },
                                label = { Text(pos) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsToggleItem(
                label = "Dynamic Hero Color",
                subtitle = "Tints the home page background with colors sampled from the featured hero item",
                checked = heroDynamicColorEnabled,
                onCheckedChange = { AppearanceConfig.setHeroDynamicColorEnabled(it) }
            )
        }

        // --- Group 3: Display & Layout ---
        SettingsGroupCard(title = "Display & Layout") {
            SettingsDropdownItem(
                label = "Dock Position",
                subtitle = "Choose where the main navigation dock is placed",
                options = listOf("Left" to "Left", "Top" to "Top", "Bottom" to "Bottom", "Right" to "Right"),
                currentValue = dockPosition,
                onSelectionChanged = { AppearanceConfig.setDockPosition(it) }
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            SettingsDropdownItem(
                label = "Poster Size",
                subtitle = "Adjust the size of posters on the home screen",
                options = listOf("Compact" to "Compact", "Normal" to "Normal", "Large" to "Large"),
                currentValue = gridScale,
                onSelectionChanged = { AppearanceConfig.setGridScale(it) }
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            SettingsDropdownItem(
                label = "Layout Width",
                subtitle = "Restrict maximum content width on large monitors",
                options = listOf("Fluid" to "Edge-to-Edge", "Modern" to "Centered", "Compact" to "Narrow"),
                currentValue = layoutWidth,
                onSelectionChanged = { AppearanceConfig.setLayoutWidth(it) }
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            SettingsDropdownItem(
                label = "Search Bar Visibility",
                subtitle = "Control how the search bar appears on the home screen",
                options = listOf("Always Visible" to "Always Visible", "Auto-hide" to "Auto-hide"),
                currentValue = searchBarMode,
                onSelectionChanged = { AppearanceConfig.setSearchBarMode(it) }
            )
        }
    }
}
