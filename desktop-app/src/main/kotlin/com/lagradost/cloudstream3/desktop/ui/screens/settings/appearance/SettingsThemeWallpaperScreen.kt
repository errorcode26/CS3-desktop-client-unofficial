package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsDropdownItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSliderItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager
import com.lagradost.cloudstream3.desktop.ui.theme.ThemeMode

@Composable
fun SettingsThemeWallpaperScreen() {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val themeAccent by AppearanceConfig.themeAccent.collectAsState()
    val customThemeAccent by AppearanceConfig.customThemeAccent.collectAsState()
    val appThemeBackground by AppearanceConfig.appThemeBackground.collectAsState()
    val customAppThemeBackground by AppearanceConfig.customAppThemeBackground.collectAsState()
    val selectedFont by AppearanceConfig.selectedFont.collectAsState()
    val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
    val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
    val backgroundGradientEnabled by AppearanceConfig.backgroundGradientEnabled.collectAsState()
    val backgroundGradientType by AppearanceConfig.backgroundGradientType.collectAsState()
    val backgroundGradientIntensity by AppearanceConfig.backgroundGradientIntensity.collectAsState()
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

    var showCustomAccentDialog by remember { mutableStateOf(false) }
    var showCustomBgDialog by remember { mutableStateOf(false) }

    val presetAccents = remember {
        listOf(
            "Purple" to Color(0xFF7C6BFF),
            "Blue" to Color(0xFF3B82F6),
            "Green" to Color(0xFF10B981),
            "Red" to Color(0xFFEF4444),
            "Orange" to Color(0xFFF59E0B),
        )
    }

    val curatedAccentColors = remember {
        listOf(
            "Neon Pink" to "#FF007F",
            "Cyber Cyan" to "#00F0FF",
            "Emerald" to "#10B981",
            "Electric Violet" to "#8B5CF6",
            "Sunset Gold" to "#F59E0B",
            "Crimson" to "#EF4444",
            "Mint" to "#6EE7B7",
            "Lavender" to "#C084FC",
        )
    }

    val curatedBackgroundColors = remember {
        listOf(
            "Deep Carbon" to "#0E0E10",
            "Dark Slate" to "#18181B",
            "Midnight Navy" to "#0B1120",
            "Dark Mocha" to "#1E1815",
            "Deep Forest" to "#0F1714",
            "Deep Amethyst" to "#130C1C",
        )
    }

    val scrollState = rememberScrollState()

    CustomColorStudioDialog(
        show = showCustomAccentDialog,
        title = "Custom Accent Color Studio",
        initialHex = customThemeAccent,
        defaultHex = "#7C6BFF",
        curatedColors = curatedAccentColors,
        onDismiss = { showCustomAccentDialog = false },
        onColorConfirmed = { hex ->
            AppearanceConfig.setCustomThemeAccent(hex)
            AppearanceConfig.setThemeAccent("Custom")
        },
    )

    CustomColorStudioDialog(
        show = showCustomBgDialog,
        title = "Custom Background Tone Studio",
        initialHex = customAppThemeBackground,
        defaultHex = "#0C0C16",
        curatedColors = curatedBackgroundColors,
        onDismiss = { showCustomBgDialog = false },
        onColorConfirmed = { hex ->
            AppearanceConfig.setCustomAppThemeBackground(hex)
            AppearanceConfig.setAppThemeBackground("Custom")
        },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "App Mode & Palette") {
            val currentThemeMode = when {
                isLightMode -> ThemeMode.LIGHT
                amoledMode -> ThemeMode.AMOLED
                else -> ThemeMode.DARK
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Select base visual style and contrast profile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = currentThemeMode == mode
                        Surface(
                            onClick = { AppearanceConfig.setThemeMode(mode) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                        ThemeMode.AMOLED -> Icons.Default.Contrast
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Accent Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Primary tint used across buttons, indicators, and focus highlights", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    presetAccents.forEach { (name, color) ->
                        val isSelected = themeAccent == name
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                )
                                .clickable { AppearanceConfig.setThemeAccent(name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    val isCustomAccent = themeAccent == "Custom"
                    val customColor = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customThemeAccent, Color(0xFF7C6BFF))

                    Surface(
                        onClick = {
                            showCustomAccentDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCustomAccent) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (isCustomAccent) 1.5.dp else 1.dp,
                            color = if (isCustomAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.height(38.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(customColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            )
                            Text(
                                text = if (isCustomAccent) "Custom ($customThemeAccent)" else "Custom Color Picker...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCustomAccent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCustomAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Custom Color",
                                tint = if (isCustomAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (currentThemeMode == ThemeMode.AMOLED) {
                SettingsDropdownItem(
                    label = "App Background Palette",
                    subtitle = "Locked to pure #000000 black in AMOLED mode",
                    options = listOf("Pure Black" to "Pure Black (#000000)"),
                    currentValue = "Pure Black",
                    enabled = false,
                    onSelectionChanged = { },
                )
            } else {
                SettingsDropdownItem(
                    label = "App Background Palette",
                    subtitle = "Base canvas color tone across all screens",
                    options = listOf(
                        "Navy" to "Deep Navy",
                        "Midnight" to "Midnight Blue",
                        "Slate" to "Dark Slate",
                        "Mocha" to "Warm Mocha",
                        "Pure Black" to "Pure Black (#000000)",
                        "Custom" to "Custom Hex Tint",
                    ),
                    currentValue = appThemeBackground,
                    onSelectionChanged = {
                        AppearanceConfig.setAppThemeBackground(it)
                        if (it == "Custom") {
                            showCustomBgDialog = true
                        }
                    },
                )

                if (appThemeBackground == "Custom") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { showCustomBgDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customAppThemeBackground, Color(0xFF0C0C16)))
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                )
                                Text(
                                    text = "Custom Background ($customAppThemeBackground)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Background Color",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
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

        SettingsGroupCard(title = "Atmospheric Canvas & Background Gradient") {
            SettingsToggleItem(
                label = "Canvas Background Gradient",
                subtitle = "Renders dynamic depth gradient across application backgrounds",
                checked = backgroundGradientEnabled,
                onCheckedChange = { AppearanceConfig.setBackgroundGradientEnabled(it) },
            )

            if (backgroundGradientEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsDropdownItem(
                    label = "Gradient Geometry",
                    subtitle = "Style and projection of the background depth gradient",
                    options = listOf(
                        "Radial" to "Radial Ambient Glow (Cinematic Center)",
                        "Linear" to "Linear Horizon Flow (Top to Bottom)",
                    ),
                    currentValue = backgroundGradientType,
                    onSelectionChanged = { AppearanceConfig.setBackgroundGradientType(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSliderItem(
                    label = "Gradient Depth Intensity",
                    subtitle = "${(backgroundGradientIntensity * 100).toInt()}% depth intensity",
                    value = backgroundGradientIntensity,
                    onValueChange = { AppearanceConfig.setBackgroundGradientIntensity(it) },
                    valueRange = 0.10f..1.00f,
                    steps = 17,
                )
            }
        }

        SettingsGroupCard(title = "Atmospheric Ambient Glow Lighting") {
            SettingsToggleItem(
                label = "Ambient Glow (Cinematic Backlight)",
                subtitle = "Renders soft adaptive atmospheric lighting behind active hero content",
                checked = ambientGlowEnabled,
                onCheckedChange = { AppearanceConfig.setAmbientGlowEnabled(it) },
            )

            if (ambientGlowEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Ambient Glow Intensity",
                    subtitle = "${(ambientGlowIntensity * 100).toInt()}% intensity",
                    value = ambientGlowIntensity,
                    onValueChange = { AppearanceConfig.setAmbientGlowIntensity(it) },
                    valueRange = 0.05f..0.60f,
                    steps = 11,
                )
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
                                        )
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
}
