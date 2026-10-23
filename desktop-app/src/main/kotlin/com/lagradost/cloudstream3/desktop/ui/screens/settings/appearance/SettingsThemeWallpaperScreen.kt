package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsDropdownItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSliderItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets
import com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager
import com.lagradost.cloudstream3.desktop.ui.theme.ThemeMode
import com.lagradost.cloudstream3.desktop.ui.theme.ThemePreset
import com.lagradost.cloudstream3.desktop.ui.theme.accentColorFromName
import com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor

@Composable
fun SettingsThemeWallpaperScreen() {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val themeAccent by AppearanceConfig.themeAccent.collectAsState()
    val appThemeBackground by AppearanceConfig.appThemeBackground.collectAsState()
    val customThemeAccent by AppearanceConfig.customThemeAccent.collectAsState()
    val customAppThemeBackground by AppearanceConfig.customAppThemeBackground.collectAsState()
    val activePresetId by AppearanceConfig.appPresetTheme.collectAsState()
    val selectedFont by AppearanceConfig.selectedFont.collectAsState()
    val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
    val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
    val ambientGlowPositions by AppearanceConfig.ambientGlowPositions.collectAsState()
    val bgImagePath by AppearanceConfig.backgroundImagePath.collectAsState()
    val bgImageBlur by AppearanceConfig.backgroundImageBlur.collectAsState()
    val bgImageBrightness by AppearanceConfig.backgroundImageBrightness.collectAsState()
    val bgImageOpacity by AppearanceConfig.backgroundImageOpacity.collectAsState()
    val bgImageVignetteEnabled by AppearanceConfig.backgroundImageVignetteEnabled.collectAsState()
    val bgImageVignetteIntensity by AppearanceConfig.backgroundImageVignetteIntensity.collectAsState()
    val bgImageTintEnabled by AppearanceConfig.backgroundImageTintEnabled.collectAsState()
    val bgImageTintAlpha by AppearanceConfig.backgroundImageTintAlpha.collectAsState()
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

    val availableFonts by CustomFontManager.availableFonts.collectAsState()
    val userInstalledFonts by CustomFontManager.userInstalledFonts.collectAsState()
    var fontInstallFeedback by remember { mutableStateOf<String?>(null) }
    var showAccentPickerDialog by remember { mutableStateOf(false) }
    var showBackgroundPickerDialog by remember { mutableStateOf(false) }

    val curatedAccents = remember {
        listOf(
            "Blue" to Color(0xFF3B82F6),
            "Cyan" to Color(0xFF06B6D4),
            "Purple" to Color(0xFF7C6BFF),
            "Green" to Color(0xFF10B981),
            "Amber" to Color(0xFFF59E0B),
            "Orange" to Color(0xFFF97316),
            "Red" to Color(0xFFEF4444),
            "Rose" to Color(0xFFEC4899),
            "Ice" to Color(0xFF94A3B8),
        )
    }

    val curatedBackgrounds = remember {
        listOf(
            "Navy" to ("Deep Navy" to Color(0xFF0C0C16)),
            "Midnight" to ("Midnight" to Color(0xFF0B1120)),
            "Slate" to ("Dark Slate" to Color(0xFF18181B)),
            "Mocha" to ("Warm Mocha" to Color(0xFF1E1815)),
            "Forest" to ("Deep Forest" to Color(0xFF0F1714)),
        )
    }

    val curatedLightBackgrounds = remember {
        listOf(
            "Frost" to ("Clean Frost" to Color(0xFFF1F5F9)),
            "Alabaster" to ("Warm Alabaster" to Color(0xFFF7F4EE)),
            "Nordic" to ("Nordic Snow" to Color(0xFFECEFF4)),
            "Latte" to ("Warm Latte" to Color(0xFFF3EDE5)),
            "Matcha" to ("Matcha Tea" to Color(0xFFEDF4EF)),
            "Sakura" to ("Sakura Blush" to Color(0xFFFAF0F4)),
            "Solar" to ("Solarized" to Color(0xFFFDF6E3)),
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val currentThemeMode = when {
            isLightMode -> ThemeMode.LIGHT
            amoledMode -> ThemeMode.AMOLED
            else -> ThemeMode.DARK
        }

        // Card 1: Appearance Mode
        SettingsGroupCard(title = "Appearance Mode") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val modes = listOf(
                    Triple(ThemeMode.DARK, "Dark Mode", "Comfortable dark canvas for everyday viewing"),
                    Triple(ThemeMode.AMOLED, "Pure AMOLED", "100% pitch black canvas for true OLED contrast"),
                    Triple(ThemeMode.LIGHT, "Light Mode", "Crisp, bright daytime contrast and palette"),
                )
                modes.forEach { (mode, title, desc) ->
                    val isSelected = currentThemeMode == mode
                    Surface(
                        onClick = { AppearanceConfig.setThemeMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.weight(1f).height(104.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                        ThemeMode.AMOLED -> Icons.Default.Contrast
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Card 2: Accent Color & Highlights
        SettingsGroupCard(title = "Accent Color & Highlights") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Primary color used for focus rings, active buttons, slider tracks, and indicator badges.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    curatedAccents.forEach { (name, color) ->
                        val isSelected = themeAccent == name
                        Surface(
                            onClick = { AppearanceConfig.setThemeAccent(name) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier.height(42.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Custom Color Chip
                    val isCustom = themeAccent == "Custom"
                    val customColor = remember(customThemeAccent) { parseHexColor(customThemeAccent, Color(0xFF7C6BFF)) }
                    Surface(
                        onClick = { showAccentPickerDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCustom) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isCustom) 2.dp else 1.dp,
                            color = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.height(42.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(customColor)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                            )
                            Text(
                                text = if (isCustom) "Custom (${customThemeAccent.uppercase()})" else "Custom Color...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCustom) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.Default.ColorLens,
                                contentDescription = "Pick custom accent",
                                tint = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // Card 3: Canvas Background Tone
        SettingsGroupCard(title = "Canvas Background Tone") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (currentThemeMode == ThemeMode.AMOLED) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF101010),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Column {
                                Text(
                                    text = "Pure Black Canvas Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    text = "In AMOLED mode, the background canvas is locked to true 100% black (#000000) for pixel turn-off on OLED displays.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Select canvas tone for main screens, sidebars, and dialog backdrops.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val activeBackgrounds = if (currentThemeMode == ThemeMode.LIGHT) curatedLightBackgrounds else curatedBackgrounds

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        activeBackgrounds.forEach { (key, pair) ->
                            val (label, color) = pair
                            val isSelected = appThemeBackground == key
                            Surface(
                                onClick = { AppearanceConfig.setAppThemeBackground(key) },
                                shape = RoundedCornerShape(10.dp),
                                color = color,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                                ),
                                modifier = Modifier.height(42.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (currentThemeMode == ThemeMode.LIGHT) Color(0xFF1E293B) else Color.White,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Custom Canvas Chip
                        val isCustomBg = appThemeBackground == "Custom"
                        val customBgColor = remember(customAppThemeBackground) { parseHexColor(customAppThemeBackground, Color(0xFF0C0C16)) }
                        Surface(
                            onClick = { showBackgroundPickerDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            color = customBgColor,
                            border = BorderStroke(
                                width = if (isCustomBg) 2.dp else 1.dp,
                                color = if (isCustomBg) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier.height(42.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (isCustomBg) "Custom Canvas (${customAppThemeBackground.uppercase()})" else "Custom Canvas...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCustomBg) FontWeight.Bold else FontWeight.Medium,
                                    color = if (currentThemeMode == ThemeMode.LIGHT) Color(0xFF1E293B) else Color.White,
                                )
                                Icon(
                                    Icons.Default.ColorLens,
                                    contentDescription = "Pick custom canvas",
                                    tint = if (isCustomBg) MaterialTheme.colorScheme.primary else (if (currentThemeMode == ThemeMode.LIGHT) Color(0xFF1E293B) else Color.White.copy(alpha = 0.7f)),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Card 4: Curated 1-Click Theme Bundles
        SettingsGroupCard(title = "Curated 1-Click Theme Bundles") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Instantly configure mode, accent highlight, and canvas background in one click.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BuiltInPresets.presets.forEach { preset ->
                        val isMatchingPreset = remember(preset, isLightMode, amoledMode, themeAccent, appThemeBackground) {
                            val modeMatch = if (preset.isLightMode) isLightMode else (!isLightMode && !amoledMode)
                            val accentMatch = themeAccent == preset.themeAccent
                            val bgMatch = appThemeBackground == preset.appThemeBackground
                            modeMatch && accentMatch && bgMatch
                        }
                        ThemePresetCard(
                            preset = preset,
                            isSelected = isMatchingPreset,
                            onClick = { AppearanceConfig.applyPreset(preset) },
                            modifier = Modifier.width(172.dp),
                        )
                    }
                }
            }
        }

        SettingsGroupCard(title = "App Typography & Custom Font Studio") {
            SettingsDropdownItem(
                label = "App Typography & Font",
                subtitle = "Font family applied globally across all titles, cards, and UI components",
                options = availableFonts.map { it to it },
                currentValue = selectedFont,
                fontFamilyForOption = { com.lagradost.cloudstream3.desktop.ui.theme.getFontFamily(it) },
                onSelectionChanged = { AppearanceConfig.setSelectedFont(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        try {
                            val chosenFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                title = "Select Font File (.ttf, .otf, .woff)",
                                allowedExtensions = listOf("ttf", "otf", "woff"),
                                category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.FONT,
                            )
                            if (chosenFile != null) {
                                val result = CustomFontManager.installFont(chosenFile)
                                result.onSuccess { familyName ->
                                    AppearanceConfig.setSelectedFont(familyName)
                                    fontInstallFeedback = "Successfully installed & applied font: $familyName"
                                }.onFailure { err ->
                                    fontInstallFeedback = "Font installation failed: ${err.message}"
                                }
                            }
                        } catch (e: Exception) {
                            fontInstallFeedback = "Error opening file picker: ${e.message}"
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Install Custom Font (.ttf / .otf)")
                }

                OutlinedButton(
                    onClick = { CustomFontManager.openFontsDirectory() },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Fonts Folder")
                }
            }

            if (fontInstallFeedback != null) {
                Text(
                    text = fontInstallFeedback!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fontInstallFeedback!!.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            if (userInstalledFonts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "User-Installed Fonts (${userInstalledFonts.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    userInstalledFonts.forEach { fontName ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fontName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "The quick brown fox jumps over the lazy dog 1234567890",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        CustomFontManager.deleteFont(fontName)
                                        if (selectedFont == fontName) {
                                            AppearanceConfig.setSelectedFont("Inter")
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Font",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SettingsGroupCard(title = "Ambient Glow") {
            SettingsToggleItem(
                label = "Ambient Glow",
                subtitle = "Soft adaptive backlight behind active hero content",
                checked = ambientGlowEnabled,
                onCheckedChange = { AppearanceConfig.setAmbientGlowEnabled(it) },
            )

            if (isLightMode || amoledMode) {
                Text(
                    text = if (isLightMode) "Not available in Light mode." else "Not available in AMOLED mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            if (ambientGlowEnabled && !isLightMode && !amoledMode) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Ambient Glow Intensity",
                    subtitle = "${(ambientGlowIntensity * 100).toInt()}% intensity",
                    value = ambientGlowIntensity,
                    onValueChange = { AppearanceConfig.setAmbientGlowIntensity(it) },
                    valueRange = 0.05f..0.60f,
                    steps = 11,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Glow Projection Corners & Positions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Select one or more screen positions and corners to project ambient glow",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val positions = listOf(
                        "Top Left" to "Top Left Corner",
                        "Top Right" to "Top Right Corner",
                        "Center" to "Center",
                        "Bottom Left" to "Bottom Left Corner",
                        "Bottom Right" to "Bottom Right Corner",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        positions.forEach { (posKey, _) ->
                            val isSelected = ambientGlowPositions.contains(posKey)
                            FilterChip(
                                selected = isSelected,
                                onClick = { AppearanceConfig.toggleAmbientGlowPosition(posKey) },
                                label = { Text(posKey, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else {
                                    null
                                },
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
            }
        }

        SettingsGroupCard(title = "Custom Background Wallpaper") {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Wallpaper Image", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Set a custom local image as app background with blur and tint controls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (bgImagePath.isNotBlank()) {
                        OutlinedButton(
                            onClick = { AppearanceConfig.clearBackgroundImage() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Wallpaper", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (bgImagePath.isNotBlank() && java.io.File(bgImagePath).exists()) {
                    val wallpaperFile = remember(bgImagePath) { java.io.File(bgImagePath) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            coil3.compose.AsyncImage(
                                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                    .data(wallpaperFile)
                                    .size(coil3.size.Size(1280, 720))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Active Wallpaper",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                            startY = 50f,
                                        ),
                                    ),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = wallpaperFile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = wallpaperFile.parent ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Button(
                                    onClick = {
                                        val chosen = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                            title = "Choose Wallpaper Image",
                                            allowedExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp"),
                                            category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.WALLPAPER,
                                        )
                                        if (chosen != null) {
                                            AppearanceConfig.setBackgroundImagePath(chosen.absolutePath)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Change Image")
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.Wallpaper,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "No Custom Wallpaper Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Select a high-resolution image to use as your desktop app canvas backdrop",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    val chosen = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                        title = "Choose Wallpaper Image",
                                        allowedExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp"),
                                        category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.WALLPAPER,
                                    )
                                    if (chosen != null) {
                                        AppearanceConfig.setBackgroundImagePath(chosen.absolutePath)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose Wallpaper Image (.png, .jpg, .webp)")
                            }
                        }
                    }
                }

                if (bgImagePath.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsSliderItem(
                        label = "Wallpaper Blur",
                        subtitle = "${bgImageBlur.toInt()} dp blur",
                        value = bgImageBlur,
                        onValueChange = { AppearanceConfig.setBackgroundImageBlur(it) },
                        valueRange = 0f..50f,
                        steps = 10,
                    )

                    SettingsSliderItem(
                        label = "Wallpaper Brightness",
                        subtitle = "${(bgImageBrightness * 100).toInt()}% brightness",
                        value = bgImageBrightness,
                        onValueChange = { AppearanceConfig.setBackgroundImageBrightness(it) },
                        valueRange = 0.05f..1.0f,
                        steps = 19,
                    )

                    SettingsSliderItem(
                        label = "Wallpaper Opacity",
                        subtitle = "${(bgImageOpacity * 100).toInt()}% opacity",
                        value = bgImageOpacity,
                        onValueChange = { AppearanceConfig.setBackgroundImageOpacity(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 9,
                    )

                    SettingsToggleItem(
                        label = "Wallpaper Vignette",
                        subtitle = "Darkens image edges for a focused cinema look",
                        checked = bgImageVignetteEnabled,
                        onCheckedChange = { AppearanceConfig.setBackgroundImageVignetteEnabled(it) },
                    )

                    if (bgImageVignetteEnabled) {
                        SettingsSliderItem(
                            label = "Vignette Intensity",
                            subtitle = "${(bgImageVignetteIntensity * 100).toInt()}% strength",
                            value = bgImageVignetteIntensity,
                            onValueChange = { AppearanceConfig.setBackgroundImageVignetteIntensity(it) },
                            valueRange = 0.1f..1.0f,
                            steps = 9,
                        )
                    }

                    SettingsToggleItem(
                        label = "Wallpaper Color Tint",
                        subtitle = "Blends accent color overlay onto wallpaper",
                        checked = bgImageTintEnabled,
                        onCheckedChange = { AppearanceConfig.setBackgroundImageTintEnabled(it) },
                    )

                    if (bgImageTintEnabled) {
                        SettingsSliderItem(
                            label = "Tint Opacity",
                            subtitle = "${(bgImageTintAlpha * 100).toInt()}% overlay",
                            value = bgImageTintAlpha,
                            onValueChange = { AppearanceConfig.setBackgroundImageTintAlpha(it) },
                            valueRange = 0.05f..0.80f,
                            steps = 15,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSliderItem(
                    label = "UI Container & Card Glass Opacity",
                    subtitle = "${(uiCardOpacity * 100).toInt()}% opacity",
                    value = uiCardOpacity,
                    onValueChange = { AppearanceConfig.setUiCardOpacity(it) },
                    valueRange = 0.15f..1.0f,
                    steps = 17,
                )
            }
        }
    }

    if (showAccentPickerDialog) {
        CustomColorPickerDialog(
            show = showAccentPickerDialog,
            title = "Custom Accent Color",
            initialHex = customThemeAccent,
            onDismissRequest = { showAccentPickerDialog = false },
            onColorConfirmed = { hex ->
                AppearanceConfig.setCustomThemeAccent(hex)
                AppearanceConfig.setThemeAccent("Custom")
                AppearanceConfig.setAppPresetTheme("custom")
                showAccentPickerDialog = false
            },
        )
    }

    if (showBackgroundPickerDialog) {
        CustomColorPickerDialog(
            show = showBackgroundPickerDialog,
            title = "Custom Canvas Background",
            initialHex = customAppThemeBackground,
            onDismissRequest = { showBackgroundPickerDialog = false },
            onColorConfirmed = { hex ->
                AppearanceConfig.setCustomAppThemeBackground(hex)
                AppearanceConfig.setAppThemeBackground("Custom")
                AppearanceConfig.setAppPresetTheme("custom")
                showBackgroundPickerDialog = false
            },
        )
    }
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = remember(preset) { accentColorFromName(preset.themeAccent, preset.customThemeAccent) }
    val bgColor = remember(preset) {
        if (preset.isLightMode) {
            parseHexColor(preset.customAppThemeBackground, Color(0xFFF1F5F9))
        } else {
            parseHexColor(preset.customAppThemeBackground, Color(0xFF0C0C16))
        }
    }
    val cardColor = remember(bgColor, preset.isLightMode) {
        if (preset.isLightMode) {
            Color(0xFFFFFFFF)
        } else {
            Color(
                (bgColor.red + 0.07f).coerceIn(0f, 1f),
                (bgColor.green + 0.07f).coerceIn(0f, 1f),
                (bgColor.blue + 0.08f).coerceIn(0f, 1f),
            )
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        ),
        modifier = modifier.height(112.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(cardColor)
                            .padding(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (preset.isLightMode) "Light Mode" else "Dark Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorPickerDialog(
    show: Boolean,
    title: String,
    initialHex: String,
    onDismissRequest: () -> Unit,
    onColorConfirmed: (String) -> Unit,
) {
    val cleanInitial = remember(initialHex) {
        val s = initialHex.trim().removePrefix("#")
        if (s.length == 6 && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) "#${s.uppercase()}" else "#3B82F6"
    }
    var hexText by remember { mutableStateOf(cleanInitial) }

    val parsedColor = remember(hexText) {
        val stripped = hexText.trim().removePrefix("#")
        if (stripped.length == 6 && stripped.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            try {
                Color(stripped.toLong(16) or 0xFF000000L)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val currentColor = parsedColor ?: Color(0xFF3B82F6)
    val red = (currentColor.red * 255f).toInt()
    val green = (currentColor.green * 255f).toInt()
    val blue = (currentColor.blue * 255f).toInt()

    fun updateRgb(newR: Int, newG: Int, newB: Int) {
        val hex = String.format("#%02X%02X%02X", newR.coerceIn(0, 255), newG.coerceIn(0, 255), newB.coerceIn(0, 255))
        hexText = hex
    }

    val quickSwatches = remember {
        listOf(
            "#7C6BFF", "#3B82F6", "#06B6D4", "#10B981",
            "#F59E0B", "#F97316", "#EF4444", "#EC4899",
            "#8B5CF6", "#6366F1", "#14B8A6", "#E11D48",
            "#0C0C16", "#0B1120", "#18181B", "#1E1815",
            "#0F1714", "#F1F5F9", "#F7F4EE", "#ECEFF4",
        )
    }

    CloudstreamAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    parsedColor?.let {
                        val formatted = hexText.trim().let { if (!it.startsWith("#")) "#$it" else it }.uppercase()
                        onColorConfirmed(formatted)
                    }
                },
                enabled = parsedColor != null,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Apply Color")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = currentColor,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.45f),
                        ) {
                            Text(
                                text = hexText.uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val filtered = input.take(7).filter { it == '#' || it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                        hexText = if (!filtered.startsWith("#") && filtered.isNotEmpty()) "#$filtered" else filtered
                    },
                    label = { Text("Hex Code") },
                    singleLine = true,
                    isError = parsedColor == null && hexText.isNotEmpty(),
                    supportingText = {
                        if (parsedColor == null && hexText.isNotEmpty()) {
                            Text("Enter a valid 6-character hex (e.g. #7C6BFF)", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RGB Sliders", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("R", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), modifier = Modifier.width(16.dp))
                        Slider(
                            value = red.toFloat(),
                            onValueChange = { updateRgb(it.toInt(), green, blue) },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("$red", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("G", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), modifier = Modifier.width(16.dp))
                        Slider(
                            value = green.toFloat(),
                            onValueChange = { updateRgb(red, it.toInt(), blue) },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("$green", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("B", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6), modifier = Modifier.width(16.dp))
                        Slider(
                            value = blue.toFloat(),
                            onValueChange = { updateRgb(red, green, it.toInt()) },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("$blue", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Palette Swatches", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickSwatches.forEach { swatchHex ->
                            val swatchColor = parseHexColor(swatchHex, Color.Gray)
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (hexText.equals(swatchHex, ignoreCase = true)) 2.dp else 1.dp,
                                        color = if (hexText.equals(swatchHex, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                                        shape = CircleShape,
                                    )
                                    .clickable { hexText = swatchHex },
                            )
                        }
                    }
                }
            }
        },
    )
}
