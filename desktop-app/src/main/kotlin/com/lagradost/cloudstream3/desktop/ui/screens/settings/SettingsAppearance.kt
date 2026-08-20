package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets
import com.lagradost.cloudstream3.desktop.ui.theme.ThemePreset
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.common.storage.WatchHistory

// Dead routing code removed during 10-tab restructure

@Composable
fun SettingsAppearanceThemeScreen() {
    val themeAccent by AppearanceConfig.themeAccent.collectAsState()
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val appThemeBackground by AppearanceConfig.appThemeBackground.collectAsState()
    val selectedFont by AppearanceConfig.selectedFont.collectAsState()
    val appPresetTheme by AppearanceConfig.appPresetTheme.collectAsState()
    val customPresets by AppearanceConfig.customPresets.collectAsState()
    val backgroundGradientEnabled by AppearanceConfig.backgroundGradientEnabled.collectAsState()
    val backgroundGradientType by AppearanceConfig.backgroundGradientType.collectAsState()
    val backgroundGradientIntensity by AppearanceConfig.backgroundGradientIntensity.collectAsState()
    val scope = rememberCoroutineScope()

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

    val accentColors = listOf(
        "Purple" to Color(0xFF7C6BFF),
        "Blue" to Color(0xFF3B82F6),
        "Green" to Color(0xFF10B981),
        "Red" to Color(0xFFEF4444),
        "Orange" to Color(0xFFF59E0B),
        "Custom" to com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(AppearanceConfig.customThemeAccent.value, Color(0xFF7C6BFF)),
    )

    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsGroupCard(title = "Theme Presets") {
                val allPresets = BuiltInPresets.presets.filter { it.isLightMode == isLightMode } + customPresets
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    allPresets.forEach { preset ->
                        val isSelected = appPresetTheme == preset.id
                        Column(
                            modifier = Modifier.width(120.dp).clickable { AppearanceConfig.applyPreset(preset) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp, 60.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    )
                                    .background(com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(if (preset.appThemeBackground == "Custom") preset.customAppThemeBackground else "#0C0C16", Color.Black)),
                            ) {
                                if (!preset.isBuiltIn) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                            .clickable { AppearanceConfig.deleteCustomPreset(preset.id) },
                                    )
                                }
                            }
                            Text(preset.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

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
                                } else if (name == "Custom") {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp).align(Alignment.Center),
                                    )
                                }
                            }
                        }
                    }

                    if (themeAccent == "Custom") {
                        val customThemeAccent by AppearanceConfig.customThemeAccent.collectAsState()
                        CustomColorPickerUI(
                            colorHex = customThemeAccent,
                            onColorChanged = { AppearanceConfig.setCustomThemeAccent(it) },
                        )
                    }
                }

                val backgroundColors = listOf(
                    "Navy" to (if (isLightMode) Color(0xFFF8FAFC) else Color(0xFF0C0C16)),
                    "Midnight Blue" to (if (isLightMode) Color(0xFFE0E7FF) else Color(0xFF0B1120)),
                    "Slate Grey" to (if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF18181B)),
                    "Mocha" to (if (isLightMode) Color(0xFFF5F5F4) else Color(0xFF1E1815)),
                    "Forest" to (if (isLightMode) Color(0xFFF0FDF4) else Color(0xFF0F1714)),
                    "Deep Purple" to (if (isLightMode) Color(0xFFFAF5FF) else Color(0xFF130C1C)),
                    "Pure Black" to (if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF000000)),
                    "Custom" to com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(AppearanceConfig.customAppThemeBackground.value, if (isLightMode) Color(0xFFF8FAFC) else Color(0xFF0C0C16)),
                )

                Text(
                    text = "Background Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    backgroundColors.forEach { (name, color) ->
                        val isSelected = appThemeBackground == name
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                )
                                .clickable { AppearanceConfig.setAppThemeBackground(name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else if (name == "Custom") {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp).align(Alignment.Center),
                                )
                            }
                        }
                    }
                }

                if (appThemeBackground == "Custom") {
                    val customAppThemeBackground by AppearanceConfig.customAppThemeBackground.collectAsState()
                    CustomColorPickerUI(
                        colorHex = customAppThemeBackground,
                        onColorChanged = { AppearanceConfig.setCustomAppThemeBackground(it) },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsToggleItem(
                    label = "Light Theme",
                    subtitle = "Use a bright clean interface",
                    checked = isLightMode,
                    onCheckedChange = { AppearanceConfig.setLightMode(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val amoledMode by AppearanceConfig.amoledMode.collectAsState()
                SettingsToggleItem(
                    label = "AMOLED Pure Black Mode",
                    subtitle = "Force deep pitch black (#000000) for OLED/AMOLED displays",
                    checked = amoledMode,
                    onCheckedChange = { AppearanceConfig.setAmoledMode(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsToggleItem(
                    label = "Background Gradients",
                    subtitle = "Enable rich depth and color grading on the background",
                    checked = backgroundGradientEnabled,
                    onCheckedChange = { AppearanceConfig.setBackgroundGradientEnabled(it) },
                )

                if (backgroundGradientEnabled) {
                    SettingsDropdownItem(
                        label = "Gradient Type",
                        subtitle = "Choose between radial glows or smooth linear sweeps",
                        options = listOf("Radial" to "Radial", "Linear" to "Linear"),
                        currentValue = backgroundGradientType,
                        onSelectionChanged = { AppearanceConfig.setBackgroundGradientType(it) },
                    )

                    SettingsSliderItem(
                        label = "Gradient Intensity",
                        subtitle = "Adjust how strong the gradient blends",
                        value = backgroundGradientIntensity,
                        onValueChange = { AppearanceConfig.setBackgroundGradientIntensity(it) },
                        valueRange = 0.0f..1.0f,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Button(
                    onClick = { showSavePresetDialog = true },
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Current as Preset")
                }
            }

            SettingsGroupCard(title = "Typography") {
                var customFonts by remember { mutableStateOf(com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getAvailableFonts()) }
                val coroutineScope = rememberCoroutineScope()
                SettingsDropdownItem(
                    label = "App Font",
                    subtitle = "Choose the font used throughout the app",
                    options = com.lagradost.cloudstream3.desktop.ui.theme.availableFonts.map { it to it },
                    currentValue = selectedFont,
                    onSelectionChanged = { AppearanceConfig.setSelectedFont(it) },
                )
                if (customFonts.isNotEmpty()) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                        Text("Installed Custom Fonts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 4.dp))
                        customFonts.forEach { fontName ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(fontName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = {
                                    val f = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getFontFile(fontName)
                                    if (f != null && f.delete()) {
                                        customFonts = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getAvailableFonts()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Font", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Font File", java.awt.FileDialog.LOAD)
                                dialog.file = "*.ttf;*.otf"
                                dialog.isVisible = true
                                if (dialog.directory != null && dialog.file != null) {
                                    val srcFile = java.io.File(dialog.directory, dialog.file)
                                    val dstFile = java.io.File(com.lagradost.common.platform.PlatformPaths.fontsDir, srcFile.name)
                                    com.lagradost.common.platform.PlatformPaths.fontsDir.mkdirs()
                                    srcFile.copyTo(dstFile, overwrite = true)
                                    // Update state
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        customFonts = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getAvailableFonts()
                                    }
                                }
                            } catch (e: Exception) {
                                com.lagradost.common.logging.AppLogger.e("Font install error", e)
                            }
                        }
                    },
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                ) {
                    Text("Install Custom Font")
                }
            }
        }
    }

    if (showSavePresetDialog) {
        com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Custom Preset") },
            text = {
                Column {
                    Text("Enter a name for your custom preset:")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. My Dark Theme") },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            val preset = ThemePreset(
                                name = newPresetName,
                                isLightMode = isLightMode,
                                themeAccent = themeAccent,
                                customThemeAccent = AppearanceConfig.customThemeAccent.value,
                                appThemeBackground = appThemeBackground,
                                customAppThemeBackground = AppearanceConfig.customAppThemeBackground.value,
                                backgroundGradientEnabled = backgroundGradientEnabled,
                                backgroundGradientType = backgroundGradientType,
                                backgroundGradientIntensity = backgroundGradientIntensity,
                            )
                            AppearanceConfig.saveCustomPreset(preset)
                            showSavePresetDialog = false
                            newPresetName = ""
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun SettingsAppearanceLayoutScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit) {
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()
    val autoSlideDelay by AppearanceConfig.heroAutoSlideDelaySeconds.collectAsState()
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val showPosterRating by AppearanceConfig.showPosterRating.collectAsState()
    val showPosterQuality by AppearanceConfig.showPosterQuality.collectAsState()
    val showPosterLanguage by AppearanceConfig.showPosterLanguage.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val posterRoundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val posterTitlePosition by AppearanceConfig.posterTitlePosition.collectAsState()

    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsGroupCard(title = "Display & Layout") {
                SettingsToggleItem(
                    label = "Enable Hero Slider",
                    subtitle = "Show the large featured hero slider on the home page",
                    checked = heroEnabled,
                    onCheckedChange = { AppearanceConfig.setHeroEnabled(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsDropdownItem(
                    label = "Hero Auto-Slide Delay",
                    subtitle = "Time before the home page hero section automatically switches to the next item",
                    options = listOf(
                        5 to "5 Seconds",
                        10 to "10 Seconds",
                        15 to "15 Seconds",
                        30 to "30 Seconds",
                        60 to "60 Seconds",
                    ),
                    currentValue = autoSlideDelay,
                    onSelectionChanged = { AppearanceConfig.setHeroAutoSlideDelaySeconds(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsDropdownItem(
                    label = "Dock Position",
                    subtitle = "Choose where the main navigation dock is placed",
                    options = listOf(
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT to "Left",
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.TOP to "Top",
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM to "Bottom",
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT to "Right",
                    ),
                    currentValue = dockPosition,
                    onSelectionChanged = { AppearanceConfig.setDockPosition(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsNavigationItem(
                    label = "Details Page Sections & Layout",
                    subtitle = "Customize time badges, info tags, and order of details page sections",
                    onClick = { onNavigateToSubScreen(SettingsSubScreen.DETAILS_LAYOUT) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsNavigationItem(
                    label = "Poster Layout Editor",
                    subtitle = "Customize poster sizes, spacing, corner radius, and titles",
                    onClick = { onNavigateToSubScreen(SettingsSubScreen.POSTER_EDITOR) },
                )
            }

            SettingsGroupCard(title = "Poster Badges") {
                SettingsToggleItem(
                    label = "Show Rating / Score",
                    subtitle = "Display a small star rating on posters if available",
                    checked = showPosterRating,
                    onCheckedChange = { AppearanceConfig.setShowPosterRating(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsToggleItem(
                    label = "Show Quality (HD / 4K)",
                    subtitle = "Display the video quality tag on the bottom right of posters",
                    checked = showPosterQuality,
                    onCheckedChange = { AppearanceConfig.setShowPosterQuality(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsToggleItem(
                    label = "Show Language (Sub / Dub)",
                    subtitle = "Display the subtitle and dub episode counts on posters",
                    checked = showPosterLanguage,
                    onCheckedChange = { AppearanceConfig.setShowPosterLanguage(it) },
                )
            }

            SettingsGroupCard(title = "Depth & Shadows") {
                val elementShadowsEnabled by AppearanceConfig.elementShadowsEnabled.collectAsState()
                val elementShadowMultiplier by AppearanceConfig.elementShadowMultiplier.collectAsState()
                val textDropShadowEnabled by AppearanceConfig.textDropShadowEnabled.collectAsState()
                val textDropShadowBlur by AppearanceConfig.textDropShadowBlur.collectAsState()

                SettingsToggleItem(
                    label = "Enable UI Element Shadows",
                    subtitle = "Applies a physical drop shadow behind posters, episodes, and cards",
                    checked = elementShadowsEnabled,
                    onCheckedChange = { AppearanceConfig.setElementShadowsEnabled(it) },
                )

                if (elementShadowsEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "UI Shadow Intensity",
                        subtitle = "Adjust how deep or heavy the element shadows are",
                        value = elementShadowMultiplier,
                        valueRange = 0.0f..3.0f,
                        steps = 29,
                        onValueChange = { AppearanceConfig.setElementShadowMultiplier(it) },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsToggleItem(
                    label = "Enable Text Shadows",
                    subtitle = "Applies a subtle drop shadow to text over images to improve readability",
                    checked = textDropShadowEnabled,
                    onCheckedChange = { AppearanceConfig.setTextDropShadowEnabled(it) },
                )

                if (textDropShadowEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "Shadow Blur Radius",
                        subtitle = "Adjust how soft and spread out the text shadow is",
                        value = textDropShadowBlur,
                        valueRange = 0.5f..20f,
                        steps = 39,
                        onValueChange = { AppearanceConfig.setTextDropShadowBlur(it) },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Real-time Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color(0xFFE0E0E0), Color(0xFFA0A0A0)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow()
                        Text(
                            text = "Real-time Shadow Preview",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.displaySmall.copy(
                                shadow = shadow,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAppearanceEffectsScreen() {
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()
    val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
    val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
    val ambientGlowPositions by AppearanceConfig.ambientGlowPositions.collectAsState()
    val screensaverEnabled by AppearanceConfig.screensaverEnabled.collectAsState()
    val backgroundGradientEnabled by AppearanceConfig.backgroundGradientEnabled.collectAsState()
    val backgroundGradientType by AppearanceConfig.backgroundGradientType.collectAsState()
    val backgroundGradientIntensity by AppearanceConfig.backgroundGradientIntensity.collectAsState()
    val clockMode by AppearanceConfig.clockMode.collectAsState()
    val clockTimeFormat by AppearanceConfig.clockTimeFormat.collectAsState()
    val clockDateFormat by AppearanceConfig.clockDateFormat.collectAsState()
    val bgImagePath by AppearanceConfig.backgroundImagePath.collectAsState()
    val bgImageBlur by AppearanceConfig.backgroundImageBlur.collectAsState()
    val bgImageBrightness by AppearanceConfig.backgroundImageBrightness.collectAsState()
    val bgImageOpacity by AppearanceConfig.backgroundImageOpacity.collectAsState()
    val bgImageSaturation by AppearanceConfig.backgroundImageSaturation.collectAsState()
    val bgImageVignetteEnabled by AppearanceConfig.backgroundImageVignetteEnabled.collectAsState()
    val bgImageVignetteIntensity by AppearanceConfig.backgroundImageVignetteIntensity.collectAsState()
    val bgImageTintEnabled by AppearanceConfig.backgroundImageTintEnabled.collectAsState()
    val bgImageTintColor by AppearanceConfig.backgroundImageTintColor.collectAsState()
    val bgImageTintAlpha by AppearanceConfig.backgroundImageTintAlpha.collectAsState()
    val scope = rememberCoroutineScope()

    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsGroupCard(title = "Header & Details Backdrop Blur") {
                SettingsToggleItem(
                    label = "Dynamic Backdrop Blur",
                    subtitle = "Apply a real-time frosted glass blur on hero banners and details pages",
                    checked = heroBackgroundBlurEnabled,
                    onCheckedChange = { AppearanceConfig.setHeroBackgroundBlurEnabled(it) },
                )

                if (heroBackgroundBlurEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "Backdrop Blur Softness",
                        subtitle = "Gaussian blur radius applied to the backdrop image",
                        value = heroBackdropBlurRadius,
                        valueRange = 10f..200f,
                        steps = 37,
                        onValueChange = { AppearanceConfig.setHeroBackdropBlurRadius(it) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "Backdrop Darkening",
                        subtitle = "Darkness overlay intensity for text contrast (0% = bright, 100% = maximum contrast)",
                        value = heroBackdropDarkening,
                        valueRange = 0.0f..1.0f,
                        steps = 100,
                        onValueChange = { AppearanceConfig.setHeroBackdropDarkening(it) },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsSliderItem(
                    label = "UI Container & Card Opacity",
                    subtitle = "Translucency level of glassmorphic menus and cards",
                    value = uiCardOpacity,
                    valueRange = 0.0f..1.0f,
                    steps = 100,
                    onValueChange = { AppearanceConfig.setUiCardOpacity(it) },
                )
            }

            SettingsGroupCard(title = "Cinematic Aesthetics") {
                SettingsToggleItem(
                    label = "Ambient Glow",
                    subtitle = "Adds a subtle, theme-colored gradient background",
                    checked = ambientGlowEnabled,
                    onCheckedChange = { AppearanceConfig.setAmbientGlowEnabled(it) },
                )

                if (ambientGlowEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "Intensity",
                        subtitle = "Adjust how bright the background ambient glow is",
                        value = ambientGlowIntensity,
                        valueRange = 0.0f..0.5f,
                        steps = 100,
                        onValueChange = { AppearanceConfig.setAmbientGlowIntensity(it) },
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Position", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(12.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                        selected = isSelected,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            SettingsGroupCard("Background Wallpaper") {
                // Filename preview
                val fileName = if (bgImagePath.isNotEmpty()) java.io.File(bgImagePath).name else "No image selected"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wallpaper Image",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Wallpaper", java.awt.FileDialog.LOAD)
                                        dialog.file = "*.jpg;*.jpeg;*.png;*.webp;*.bmp"
                                        dialog.isVisible = true
                                        if (dialog.directory != null && dialog.file != null) {
                                            val path = java.io.File(dialog.directory, dialog.file).absolutePath
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                AppearanceConfig.setBackgroundImagePath(path)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        com.lagradost.common.logging.AppLogger.e("Wallpaper picker error", e)
                                    }
                                }
                            },
                        ) {
                            Text("Choose Image")
                        }
                        if (bgImagePath.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        AppearanceConfig.clearBackgroundImage()
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }

                if (bgImagePath.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "Blur Radius",
                        subtitle = "How blurred the background image is (0 = sharp, 50 = heavy blur)",
                        value = bgImageBlur,
                        valueRange = 0f..50f,
                        steps = 49,
                        onValueChange = { AppearanceConfig.setBackgroundImageBlur(it) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "Brightness",
                        subtitle = "How bright the wallpaper shows through (0% = black, 100% = full image)",
                        value = bgImageBrightness,
                        valueRange = 0f..1f,
                        steps = 99,
                        onValueChange = { AppearanceConfig.setBackgroundImageBrightness(it) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "Opacity",
                        subtitle = "Overall image transparency — blends wallpaper against your theme background color",
                        value = bgImageOpacity,
                        valueRange = 0f..1f,
                        steps = 99,
                        onValueChange = { AppearanceConfig.setBackgroundImageOpacity(it) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSliderItem(
                        label = "Saturation",
                        subtitle = "Color intensity of the image (0% = full grayscale, 100% = original colors)",
                        value = bgImageSaturation,
                        valueRange = 0f..1f,
                        steps = 99,
                        onValueChange = { AppearanceConfig.setBackgroundImageSaturation(it) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsToggleItem(
                        label = "Vignette",
                        subtitle = "Dark fade from the edges inward for a cinematic look",
                        checked = bgImageVignetteEnabled,
                        onCheckedChange = { AppearanceConfig.setBackgroundImageVignetteEnabled(it) },
                    )

                    if (bgImageVignetteEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsSliderItem(
                            label = "Vignette Intensity",
                            subtitle = "How dark and strong the edge vignette is",
                            value = bgImageVignetteIntensity,
                            valueRange = 0f..1f,
                            steps = 99,
                            onValueChange = { AppearanceConfig.setBackgroundImageVignetteIntensity(it) },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsToggleItem(
                        label = "Color Tint",
                        subtitle = "Overlay a custom color on top of the wallpaper (great for matching your accent)",
                        checked = bgImageTintEnabled,
                        onCheckedChange = { AppearanceConfig.setBackgroundImageTintEnabled(it) },
                    )

                    if (bgImageTintEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        CustomColorPickerUI(
                            colorHex = bgImageTintColor,
                            onColorChanged = { AppearanceConfig.setBackgroundImageTintColor(it) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsSliderItem(
                            label = "Tint Strength",
                            subtitle = "How strongly the tint color is applied over the image",
                            value = bgImageTintAlpha,
                            valueRange = 0f..0.95f,
                            steps = 93,
                            onValueChange = { AppearanceConfig.setBackgroundImageTintAlpha(it) },
                        )
                    }
                }
            }

            SettingsGroupCard("Clock & Date") {
                SettingsDropdownItem(
                    label = "Display Mode",
                    subtitle = "What to show in the top-left of the main menu",
                    options = listOf(
                        com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.HIDDEN to "Hidden",
                        com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.TIME_ONLY to "Time Only",
                        com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.DATE_ONLY to "Date Only",
                        com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.BOTH to "Time & Date",
                    ),
                    currentValue = clockMode,
                    onSelectionChanged = { AppearanceConfig.setClockMode(it) },
                )

                if (clockMode == com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.TIME_ONLY ||
                    clockMode == com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.BOTH
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    SettingsDropdownItem(
                        label = "Time Format",
                        subtitle = "Pattern used to format the clock",
                        options = listOf(
                            "HH:mm" to "24h  (14:30)",
                            "HH:mm:ss" to "24h + seconds  (14:30:00)",
                            "hh:mm a" to "12h  (02:30 PM)",
                            "h:mm a" to "12h short  (2:30 PM)",
                        ),
                        currentValue = clockTimeFormat,
                        onSelectionChanged = { AppearanceConfig.setClockTimeFormat(it) },
                    )
                }

                if (clockMode == com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.DATE_ONLY ||
                    clockMode == com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.BOTH
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    SettingsDropdownItem(
                        label = "Date Format",
                        subtitle = "Pattern used to format the date",
                        options = listOf(
                            "EEE, dd MMM" to "Fri, 01 Aug",
                            "EEEE, MMMM d" to "Friday, August 1",
                            "dd/MM/yyyy" to "01/08/2026",
                            "MM/dd/yyyy" to "08/01/2026",
                            "MMM d, yyyy" to "Aug 1, 2026",
                            "dd-MM-yyyy" to "01-08-2026",
                        ),
                        currentValue = clockDateFormat,
                        onSelectionChanged = { AppearanceConfig.setClockDateFormat(it) },
                    )
                }
            }
        }
    }
}
fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    var h = 0f
    if (delta > 0f) {
        if (max == r) {
            h = 60f * (((g - b) / delta) % 6f)
        } else if (max == g) {
            h = 60f * (((b - r) / delta) + 2f)
        } else if (max == b) {
            h = 60f * (((r - g) / delta) + 4f)
        }
    }
    if (h < 0f) h += 360f

    val s = if (max == 0f) 0f else delta / max
    val v = max

    return floatArrayOf(h, s, v)
}

fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hNorm = h % 360f
    val c = v * s
    val x = c * (1f - kotlin.math.abs((hNorm / 60f) % 2f - 1f))
    val m = v - c

    var r = 0f
    var g = 0f
    var b = 0f

    when ((hNorm / 60f).toInt() % 6) {
        0 -> {
            r = c
            g = x
            b = 0f
        }
        1 -> {
            r = x
            g = c
            b = 0f
        }
        2 -> {
            r = 0f
            g = c
            b = x
        }
        3 -> {
            r = 0f
            g = x
            b = c
        }
        4 -> {
            r = x
            g = 0f
            b = c
        }
        5 -> {
            r = c
            g = 0f
            b = x
        }
    }

    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

@Composable
fun CustomColorPickerUI(colorHex: String, onColorChanged: (String) -> Unit) {
    val initialColor = remember { com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(colorHex, Color.Red) }
    val initialHsv = remember { colorToHsv(initialColor) }

    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }

    LaunchedEffect(colorHex) {
        val currentC = hsvToColor(hue, saturation, value)
        val currentHex = String.format("#%02X%02X%02X", (currentC.red * 255).toInt(), (currentC.green * 255).toInt(), (currentC.blue * 255).toInt())
        if (currentHex != colorHex) {
            val parsedColor = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(colorHex, Color.Red)
            val hsv = colorToHsv(parsedColor)
            hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]
        }
    }

    fun updateColor() {
        val c = hsvToColor(hue, saturation, value)
        val r = (c.red * 255).toInt()
        val g = (c.green * 255).toInt()
        val b = (c.blue * 255).toInt()
        val hex = String.format("#%02X%02X%02X", r, g, b)
        onColorChanged(hex)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .padding(top = 12.dp, start = 24.dp, bottom = 8.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(com.lagradost.cloudstream3.desktop.ui.components.DesktopUi.SurfaceElevated)
            .padding(12.dp),
    ) {
        Text("Custom Color Configuration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        // 2D Saturation/Value Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(Color.Black),
        ) {
            // Background is pure hue
            val baseHueColor = hsvToColor(hue, 1f, 1f)
            androidx.compose.foundation.Canvas(
                modifier = Modifier.matchParentSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            saturation = (change.position.x / width).coerceIn(0f, 1f)
                            value = 1f - (change.position.y / height).coerceIn(0f, 1f)
                            updateColor()
                        }
                    }
                    .clickable { },
            ) {
                // Draw base hue
                drawRect(color = baseHueColor, size = size)
                // Draw white gradient (left to right)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.White, Color.Transparent),
                    ),
                    size = size,
                )
                // Draw black gradient (bottom to top)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                    ),
                    size = size,
                )

                // Draw selector thumb
                val thumbX = saturation * size.width
                val thumbY = (1f - value) * size.height
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(thumbX, thumbY),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hue Slider
        val rainbowColors = listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.matchParentSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            hue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                            updateColor()
                        }
                    },
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(colors = rainbowColors),
                    size = size,
                )

                // Draw thumb
                val thumbX = (hue / 360f) * size.width
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun SettingsPosterEditorScreen() {
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val posterRoundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val posterTitlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val continueWatchingStyle by AppearanceConfig.continueWatchingStyle.collectAsState()
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()

    // Mock API for generating SearchResponses
    val mockApi = remember {
        object : com.lagradost.cloudstream3.MainAPI() {
            override var mainUrl = "mock"
            override var name = "Cinemeta"
            override val hasMainPage = true
        }
    }

    // Mock Data using real TMDB posters for a realistic preview
    val mockHistory = remember {
        com.lagradost.common.storage.WatchHistory(
            parentId = "mock_history",
            showName = "House of the Dragon",
            showUrl = "dummy",
            apiName = "Cinemeta",
            posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg",
            episodeThumbnailUrl = null,
            screenshotUrl = null,
            episode = 1,
            season = 2,
            episodeId = "dummy_ep",
            position = 1800,
            duration = 3600
        )
    }

    var mockPosters by remember { mutableStateOf<List<com.lagradost.cloudstream3.SearchResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val data = com.lagradost.cloudstream3.app.get("https://v3-cinemeta.strem.io/catalog/movie/top.json")
                    .parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                
                val metas = data?.get("metas")
                if (metas != null && metas.isArray) {
                    val posters = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                    for (node in metas) {
                        val name = node.get("name")?.asText() ?: continue
                        val posterUrl = node.get("poster")?.asText()
                        posters.add(
                            mockApi.newMovieSearchResponse(name, "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                                this.posterUrl = posterUrl
                                this.quality = com.lagradost.cloudstream3.SearchQuality.HD
                            }
                        )
                        if (posters.size >= 15) break
                    }
                    mockPosters = posters
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Sticky Preview Area (Top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(16.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(homeVerticalSpacingDp.dp)
            ) {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val animatedSpacing by androidx.compose.animation.core.animateDpAsState(
                    targetValue = homeSpacingDp.dp,
                    animationSpec = androidx.compose.animation.core.tween(300),
                )
                val animatedWidth by androidx.compose.animation.core.animateDpAsState(
                    targetValue = posterWidthDp.dp,
                    animationSpec = androidx.compose.animation.core.tween(300),
                )
                
                // Row 1: Continue Watching
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(animatedSpacing),
                ) {
                    item {
                        if (continueWatchingStyle == com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM) {
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardWide(
                                modifier = Modifier.width(animatedWidth * 2.2f).height(animatedWidth * 1.5f),
                                history = mockHistory,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        } else {
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard(
                                modifier = Modifier.width(animatedWidth * 2.0f).height((animatedWidth * 2.0f) * 9f / 16f),
                                history = mockHistory,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Trending Movies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = com.lagradost.cloudstream3.desktop.ui.components.DesktopUi.Accent)
                    }
                } else {
                    // Row 2: Standard Posters
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(animatedSpacing),
                    ) {
                        items(mockPosters.size) { index ->
                            com.lagradost.cloudstream3.desktop.ui.components.PosterCard(
                                item = mockPosters[index],
                                provider = mockApi,
                                gridScale = "Normal",
                                itemWidth = animatedWidth, // IMPORTANT: Apply width scaling!
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                    }
                }
            }
        }

        // Scrollable Controls Below (Bottom)
        val scrollState = rememberScrollState()
        var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

        CompositionLocalProvider(
            LocalSettingsScrollState provides scrollState,
            LocalScrollContainerCoordinates provides containerCoordinates,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { containerCoordinates = it }
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingsGroupCard(title = "Poster Properties") {
                    SettingsDropdownItem(
                        label = "Continue Watching Style",
                        subtitle = "Choose the layout for items in the Continue Watching row",
                        options = listOf(
                            com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.THUMBNAIL to "Classic Thumbnail",
                            com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM to "Premium Wide Card",
                        ),
                        currentValue = continueWatchingStyle,
                        onSelectionChanged = { AppearanceConfig.setContinueWatchingStyle(it) },
                    )
                    SettingsDropdownItem(
                        label = "Poster Title Position",
                        subtitle = "Choose where the title is displayed on posters",
                        options = listOf(
                            com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.INSIDE to "Inside on Hover",
                            com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW to "Below Poster",
                            com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.HIDDEN to "Hidden",
                        ),
                        currentValue = posterTitlePosition,
                        onSelectionChanged = { AppearanceConfig.setPosterTitlePosition(it) },
                    )
                    SettingsToggleItem(
                        label = "Hover Ambient Glow",
                        subtitle = "Displays a soft colorful glow behind posters when hovering",
                        checked = posterHoverGlowEnabled,
                        onCheckedChange = { AppearanceConfig.setPosterHoverGlowEnabled(it) },
                    )
                }

                SettingsGroupCard(title = "Size & Spacing") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSliderItem(
                        label = "Poster Width",
                        subtitle = "Adjust the size of posters on the home screen",
                        value = posterWidthDp.toFloat(),
                        valueRange = 100f..250f,
                        steps = 29, // 5dp steps: (250-100)/5 - 1 = 29
                        onValueChange = { AppearanceConfig.setPosterWidthDp(it.toInt()) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSliderItem(
                        label = "Home Page Spacing",
                        subtitle = "Adjust the horizontal spacing between items on the home page",
                        value = homeSpacingDp.toFloat(),
                        valueRange = 0f..32f,
                        steps = 15, // 2dp steps
                        onValueChange = { AppearanceConfig.setHomeSpacingDp(it.toInt()) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSliderItem(
                        label = "Home Page Vertical Spacing",
                        subtitle = "Adjust the vertical spacing between categories on the home page",
                        value = homeVerticalSpacingDp.toFloat(),
                        valueRange = 0f..64f,
                        steps = 31, // 2dp steps
                        onValueChange = { AppearanceConfig.setHomeVerticalSpacingDp(it.toInt()) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSliderItem(
                        label = "Poster Corner Radius",
                        subtitle = "Adjust how rounded the posters are",
                        value = posterRoundingDp.toFloat(),
                        valueRange = 0f..24f,
                        steps = 23,
                        onValueChange = { AppearanceConfig.setPosterRoundingDp(it.toInt()) },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SettingsDetailsSectionsScreen() {
    val lockUnreleasedEpisodes by AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
    val antiSpoilerEnabled by AppearanceConfig.antiSpoilerEnabled.collectAsState()
    val detailsShowCurrentTime by AppearanceConfig.detailsShowCurrentTime.collectAsState()
    val detailsShowEndTime by AppearanceConfig.detailsShowEndTime.collectAsState()
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
        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsGroupCard(title = "Modular Sections & Order") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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
                        border = androidx.compose.foundation.BorderStroke(
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

                            // Standard toggle switch with clean theme colors
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

        SettingsGroupCard(title = "Time Badges & Meta Data") {
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
    }
}
