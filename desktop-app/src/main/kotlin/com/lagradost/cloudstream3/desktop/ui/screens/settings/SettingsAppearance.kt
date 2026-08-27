package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.RatingSourcePolicy
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
    val customThemeAccent by AppearanceConfig.customThemeAccent.collectAsState()

    val accentColors = remember(customThemeAccent) {
        listOf(
            "Purple" to Color(0xFF7C6BFF),
            "Blue" to Color(0xFF3B82F6),
            "Green" to Color(0xFF10B981),
            "Red" to Color(0xFFEF4444),
            "Orange" to Color(0xFFF59E0B),
            "Custom" to com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customThemeAccent, Color(0xFF7C6BFF)),
        )
    }

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
                val allPresets = remember(isLightMode, customPresets) {
                    BuiltInPresets.presets.filter { it.isLightMode == isLightMode } + customPresets
                }
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

                val customBg = AppearanceConfig.customAppThemeBackground.value
                val backgroundColors = remember(isLightMode, customBg) {
                    listOf(
                        "Navy" to (if (isLightMode) Color(0xFFF8FAFC) else Color(0xFF0C0C16)),
                        "Midnight Blue" to (if (isLightMode) Color(0xFFE0E7FF) else Color(0xFF0B1120)),
                        "Slate Grey" to (if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF18181B)),
                        "Mocha" to (if (isLightMode) Color(0xFFF5F5F4) else Color(0xFF1E1815)),
                        "Forest" to (if (isLightMode) Color(0xFFF0FDF4) else Color(0xFF0F1714)),
                        "Deep Purple" to (if (isLightMode) Color(0xFFFAF5FF) else Color(0xFF130C1C)),
                        "Pure Black" to (if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF000000)),
                        "Custom" to com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customBg, if (isLightMode) Color(0xFFF8FAFC) else Color(0xFF0C0C16)),
                    )
                }

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
                var customFonts by remember { mutableStateOf<List<String>>(emptyList()) }
                LaunchedEffect(Unit) {
                    customFonts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getUserInstalledFonts()
                    }
                }
                val coroutineScope = rememberCoroutineScope()
                val fontOptions = remember(customFonts) {
                    com.lagradost.cloudstream3.desktop.ui.theme.availableFonts.map { it to it }
                }
                SettingsDropdownItem(
                    label = "App Font",
                    subtitle = "Choose the font used throughout the app",
                    options = fontOptions,
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
                                        com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.refreshCache()
                                        customFonts = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getUserInstalledFonts()
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
                                val selectedFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                    title = "Select Font File",
                                    allowedExtensions = listOf(".ttf", ".otf"),
                                )
                                if (selectedFile != null && selectedFile.exists()) {
                                    val dstFile = java.io.File(com.lagradost.common.platform.PlatformPaths.fontsDir, selectedFile.name)
                                    com.lagradost.common.platform.PlatformPaths.fontsDir.mkdirs()
                                    selectedFile.copyTo(dstFile, overwrite = true)
                                    // Update state
                                    com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.refreshCache()
                                    val refreshed = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getUserInstalledFonts()
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        customFonts = refreshed
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
    val navigationStyle by AppearanceConfig.navigationStyle.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()
    val autoSlideDelay by AppearanceConfig.heroAutoSlideDelaySeconds.collectAsState()
    val heroBannerStyle by AppearanceConfig.heroBannerStyle.collectAsState()
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
                    label = "Hero Banner Style",
                    subtitle = "Choose how the home page hero section is presented",
                    options = listOf(
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.CINEMA_PEEKING to "Cinema (Peeking Rails)",
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.FULLSCREEN_IMMERSIVE to "Fullscreen Immersive",
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.THUMBNAIL_STRIP to "Thumbnail Filmstrip",
                    ),
                    currentValue = heroBannerStyle,
                    onSelectionChanged = { AppearanceConfig.setHeroBannerStyle(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsDropdownItem(
                    label = "Navigation Style",
                    subtitle = "Switch between floating island dock and seamless edge-to-edge navbar",
                    options = listOf(
                        com.lagradost.cloudstream3.desktop.ui.theme.NavigationStyle.FLOATING_DOCK to "Floating Dock",
                        com.lagradost.cloudstream3.desktop.ui.theme.NavigationStyle.SEAMLESS_BAR to "Navigation Bar",
                    ),
                    currentValue = navigationStyle,
                    onSelectionChanged = { AppearanceConfig.setNavigationStyle(it) },
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

                val globalUiScale by AppearanceConfig.globalUiScale.collectAsState()
                val scalePercent = (globalUiScale * 100).toInt()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Global UI Scale / Zoom",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Scale entire interface. Shortcut: Ctrl + / Ctrl - / Ctrl 0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (scalePercent == 100) "100% (Default)" else "$scalePercent%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (scalePercent == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (scalePercent != 100) {
                                TextButton(
                                    onClick = { AppearanceConfig.resetZoom() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = globalUiScale,
                        onValueChange = { AppearanceConfig.setGlobalUiScale(it, notify = false) },
                        valueRange = 0.70f..1.80f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            SettingsGroupCard(title = "Top Bar & Profile") {
                val topBarShowProfile by AppearanceConfig.topBarShowProfile.collectAsState()
                val topBarShowProfileName by AppearanceConfig.topBarShowProfileName.collectAsState()

                SettingsToggleItem(
                    label = "Show Profile in Top Bar",
                    subtitle = "Display active profile avatar and switcher pill in the top-left area",
                    checked = topBarShowProfile,
                    onCheckedChange = { AppearanceConfig.setTopBarShowProfile(it) },
                )

                if (topBarShowProfile) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsToggleItem(
                        label = "Show Profile Name",
                        subtitle = "Display user name next to the avatar pill in the top bar",
                        checked = topBarShowProfileName,
                        onCheckedChange = { AppearanceConfig.setTopBarShowProfileName(it) },
                    )
                }
            }

            SettingsGroupCard(title = "Dock Customization & Order") {
                val dockOrder by AppearanceConfig.dockItemOrder.collectAsState()
                val dockDisabled by AppearanceConfig.dockDisabledItems.collectAsState()

                var draggingDockKey by remember { mutableStateOf<com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey?>(null) }
                var dragAccumulatedY by remember { mutableStateOf(0f) }
                var dragInitialIndex by remember { mutableStateOf(0) }
                var slotHeightPx by remember { mutableStateOf(0f) }
                val fallbackSlotHeight = with(LocalDensity.current) { 58.dp.toPx() }
                val effectiveSlotHeight = if (slotHeightPx > 0f) slotHeightPx else fallbackSlotHeight

                val currentTargetIndex = if (draggingDockKey != null && effectiveSlotHeight > 0f) {
                    (dragInitialIndex + kotlin.math.round(dragAccumulatedY / effectiveSlotHeight).toInt())
                        .coerceIn(0, dockOrder.lastIndex)
                } else dragInitialIndex

                val currentDockOrder by rememberUpdatedState(dockOrder)
                val currentEffectiveSlotHeight by rememberUpdatedState(effectiveSlotHeight)
                val currentDragAccumulatedY by rememberUpdatedState(dragAccumulatedY)
                val currentDragInitialIndex by rememberUpdatedState(dragInitialIndex)

                val onDropDockItem by rememberUpdatedState {
                    val fromIdx = currentDragInitialIndex
                    val slotH = currentEffectiveSlotHeight
                    val accY = currentDragAccumulatedY
                    val toIdx = if (slotH > 0f) {
                        (fromIdx + kotlin.math.round(accY / slotH).toInt())
                            .coerceIn(0, currentDockOrder.lastIndex)
                    } else fromIdx
                    draggingDockKey = null
                    dragAccumulatedY = 0f
                    if (fromIdx != toIdx && fromIdx in currentDockOrder.indices && toIdx in currentDockOrder.indices) {
                        AppearanceConfig.moveDockItem(fromIdx, toIdx)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Customize the order and visibility of dock buttons.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Drag the handles (⠿) to reorder. Essential buttons are always active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        TextButton(onClick = { AppearanceConfig.resetDockItemOrder() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dockOrder.forEachIndexed { index, itemKey ->
                            val isEnabled = itemKey !in dockDisabled
                            val isDraggingThis = draggingDockKey == itemKey

                            val targetShiftY = when {
                                isDraggingThis -> dragAccumulatedY
                                draggingDockKey != null && dragInitialIndex < currentTargetIndex && index in (dragInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                                draggingDockKey != null && dragInitialIndex > currentTargetIndex && index in currentTargetIndex until dragInitialIndex -> effectiveSlotHeight
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
                                            .pointerInput(itemKey) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingDockKey = itemKey
                                                        dragInitialIndex = currentDockOrder.indexOf(itemKey)
                                                        dragAccumulatedY = 0f
                                                    },
                                                    onDragEnd = { onDropDockItem() },
                                                    onDragCancel = {
                                                        draggingDockKey = null
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

                                    // Item Icon
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            when (itemKey) {
                                                com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.HOME -> com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Home
                                                com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.EXPLORE -> com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Explore
                                                com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.SEARCH -> com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Search
                                                com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.LIBRARY -> com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Library
                                                com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.HISTORY -> com.lagradost.cloudstream3.desktop.ui.PremiumIcons.History
                                                com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.EXTENSIONS -> com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Extensions
                                                com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey.SETTINGS -> com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Settings
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Item Details
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = itemKey.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        )
                                        Text(
                                            text = itemKey.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (itemKey.isRequired) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                        ) {
                                            Text(
                                                text = "Required",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    } else {
                                        Switch(
                                            checked = isEnabled,
                                            onCheckedChange = { checked ->
                                                AppearanceConfig.toggleDockItem(itemKey, checked)
                                            },
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
                }
            }

            SettingsGroupCard(title = "Navigation & Page Layouts") {

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

            SettingsGroupCard(title = "Poster Badges & Title Cleanup") {
                val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
                val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
                val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
                val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()
                val ratingPolicy by CardMetadataConfig.ratingPolicy.collectAsState()

                SettingsToggleItem(
                    label = "Auto-Clean Messy Poster Titles",
                    subtitle = "Slices away codecs, release groups, and site tags from movie titles across Home and Search",
                    checked = autoCleanTitles,
                    onCheckedChange = { CardMetadataConfig.setAutoCleanTitles(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsToggleItem(
                    label = "Auto-Detect SUB / DUB Badges",
                    subtitle = "Extracts [SUB], [DUB], or dual-audio split capsules directly from title tokens",
                    checked = autoDetectSubDub,
                    onCheckedChange = { CardMetadataConfig.setAutoDetectSubDub(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsToggleItem(
                    label = "Auto-Detect Quality Badges (4K / 1080p)",
                    subtitle = "Displays metallic 4K UHD and 1080p badges from stream titles",
                    checked = autoDetectQuality,
                    onCheckedChange = { CardMetadataConfig.setAutoDetectQuality(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsToggleItem(
                    label = "Show Rating Badges (★ Gold Pill)",
                    subtitle = "Display a gold rating badge on poster thumbnails when available from the provider",
                    checked = showRatingBadges,
                    onCheckedChange = { CardMetadataConfig.setShowRatingBadges(it) },
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
                                        val selectedFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                            title = "Select Wallpaper",
                                            allowedExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp"),
                                        )
                                        if (selectedFile != null && selectedFile.exists()) {
                                            val path = selectedFile.absolutePath
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
fun SettingsPosterEditorScreen(onBack: () -> Unit = {}) {
    val theme = com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val posterRoundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val posterTitlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val continueWatchingStyle by AppearanceConfig.continueWatchingStyle.collectAsState()
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()

    var isControlsExpanded by remember { mutableStateOf(true) }
    var activeControlTab by remember { mutableStateOf(0) } // 0: Dimensions & Spacing, 1: Style & Glow

    // Mock API for generating SearchResponses
    val mockApi = remember {
        object : com.lagradost.cloudstream3.MainAPI() {
            override var mainUrl = "mock"
            override var name = "Cinemeta"
            override val hasMainPage = true
        }
    }

    // Mock History
    val mockHistory1 = remember {
        com.lagradost.common.storage.WatchHistory(
            parentId = "mock_history_1",
            showName = "House of the Dragon",
            showUrl = "dummy",
            apiName = "Cinemeta",
            posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg",
            episodeThumbnailUrl = null,
            screenshotUrl = null,
            episode = 1,
            season = 2,
            episodeId = "dummy_ep_1",
            position = 1800,
            duration = 3600
        )
    }
    val mockHistory2 = remember {
        com.lagradost.common.storage.WatchHistory(
            parentId = "mock_history_2",
            showName = "Shōgun",
            showUrl = "dummy",
            apiName = "Cinemeta",
            posterUrl = "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg",
            episodeThumbnailUrl = null,
            screenshotUrl = null,
            episode = 4,
            season = 1,
            episodeId = "dummy_ep_2",
            position = 2400,
            duration = 3600
        )
    }

    val fallbackPosters = remember {
        listOf(
            mockApi.newMovieSearchResponse("Obsession", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/uU0wX6kCj9mD3wT6j1uQhM0qE8g.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
            },
            mockApi.newMovieSearchResponse("The Invite", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
            },
            mockApi.newMovieSearchResponse("Don't Say Good Luck", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
            },
            mockApi.newMovieSearchResponse("Project Hail Mary", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nx11H.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
            },
            mockApi.newMovieSearchResponse("Masters of the Universe", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/9PFonQ9Zq0RdRLEBun50Y9Y3eq5.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
            },
            mockApi.newMovieSearchResponse("Arcane", "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/fqldf2t8ztc9aiwn396nlv8g9qc.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
            },
            mockApi.newMovieSearchResponse("Dune: Part Two", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
            },
            mockApi.newMovieSearchResponse("Severance", "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8t4fF2k9YvW1F7yW71c5Mv2M8k7.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
            }
        )
    }

    var mockPosters by remember { mutableStateOf<List<com.lagradost.cloudstream3.SearchResponse>>(emptyList()) }

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
                        if (posters.size >= 16) break
                    }
                    if (posters.isNotEmpty()) {
                        mockPosters = posters
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val displayPosters = if (mockPosters.isNotEmpty()) mockPosters else fallbackPosters

    val animatedSpacing by animateDpAsState(
        targetValue = homeSpacingDp.dp,
        animationSpec = androidx.compose.animation.core.tween(250),
    )
    val animatedWidth by animateDpAsState(
        targetValue = posterWidthDp.dp,
        animationSpec = androidx.compose.animation.core.tween(250),
    )
    val animatedVerticalSpacing by animateDpAsState(
        targetValue = homeVerticalSpacingDp.dp,
        animationSpec = androidx.compose.animation.core.tween(250),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. Full-Width Scrollable Canvas ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 80.dp, bottom = 180.dp, start = 32.dp, end = 32.dp),
            verticalArrangement = Arrangement.spacedBy(animatedVerticalSpacing),
        ) {
            // Row 1: Continue Watching
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(animatedSpacing),
                ) {
                    item {
                        if (continueWatchingStyle == com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM) {
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardWide(
                                modifier = Modifier.width(animatedWidth * 2.2f).height(animatedWidth * 1.5f),
                                history = mockHistory1,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        } else {
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard(
                                modifier = Modifier.width(animatedWidth * 2.0f).height((animatedWidth * 2.0f) * 9f / 16f),
                                history = mockHistory1,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                    }
                    item {
                        if (continueWatchingStyle == com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM) {
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardWide(
                                modifier = Modifier.width(animatedWidth * 2.2f).height(animatedWidth * 1.5f),
                                history = mockHistory2,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        } else {
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard(
                                modifier = Modifier.width(animatedWidth * 2.0f).height((animatedWidth * 2.0f) * 9f / 16f),
                                history = mockHistory2,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                    }
                }
            }

            // Row 2: Trending Movies
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Trending Movies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(animatedSpacing),
                ) {
                    items(displayPosters.size) { index ->
                        com.lagradost.cloudstream3.desktop.ui.components.PosterCard(
                            item = displayPosters[index],
                            provider = mockApi,
                            gridScale = "Normal",
                            itemWidth = animatedWidth,
                            onClick = {},
                            onPlayClick = {}
                        )
                    }
                }
            }

            // Row 3: Popular Series
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Popular Series",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(animatedSpacing),
                ) {
                    items(displayPosters.reversed().size) { index ->
                        com.lagradost.cloudstream3.desktop.ui.components.PosterCard(
                            item = displayPosters.reversed()[index],
                            provider = mockApi,
                            gridScale = "Normal",
                            itemWidth = animatedWidth,
                            onClick = {},
                            onPlayClick = {}
                        )
                    }
                }
            }
        }

        // ── 2. Top Floating Glass Header ─────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(64.dp),
            color = theme.SurfaceElevated.copy(alpha = 0.88f),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.SurfaceCard.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onBack() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Back to Settings",
                            style = MaterialTheme.typography.labelLarge,
                            color = theme.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Title & Subtitle
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            "Poster Workshop Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.TextPrimary,
                        )
                    }
                    Text(
                        "Live full-screen canvas preview across your actual display width",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.TextMuted,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Reset Defaults Button
                TextButton(
                    onClick = {
                        AppearanceConfig.setPosterWidthDp(160)
                        AppearanceConfig.setHomeSpacingDp(12)
                        AppearanceConfig.setHomeVerticalSpacingDp(16)
                        AppearanceConfig.setPosterRoundingDp(12)
                        AppearanceConfig.setPosterTitlePosition(com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW)
                        AppearanceConfig.setContinueWatchingStyle(com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM)
                        AppearanceConfig.setPosterHoverGlowEnabled(true)
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Defaults")
                }
            }
        }

        // ── 3. Bottom Floating Control Dock ─────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 18.dp)
                .widthIn(max = 980.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = theme.SurfaceElevated.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.6f)),
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Dock Header with Category Tabs & Collapse Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = activeControlTab == 0,
                            onClick = { activeControlTab = 0; isControlsExpanded = true },
                            label = { Text("📐 Dimensions & Spacing", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp),
                        )
                        FilterChip(
                            selected = activeControlTab == 1,
                            onClick = { activeControlTab = 1; isControlsExpanded = true },
                            label = { Text("✨ Style & Glow", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp),
                        )
                    }

                    TextButton(
                        onClick = { isControlsExpanded = !isControlsExpanded }
                    ) {
                        Icon(
                            if (isControlsExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isControlsExpanded) "Hide Studio Bar" else "Show Controls")
                    }
                }

                if (isControlsExpanded) {
                    Spacer(modifier = Modifier.height(14.dp))

                    if (activeControlTab == 0) {
                        // ── Tab 0: Dimensions & Spacing Sliders ───────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            // Poster Width
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Poster Width", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                    Text("${posterWidthDp} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = posterWidthDp.toFloat(),
                                    onValueChange = { AppearanceConfig.setPosterWidthDp(it.toInt()) },
                                    valueRange = 100f..250f,
                                    steps = 29,
                                )
                            }

                            // Card Spacing
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Card Spacing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                    Text("${homeSpacingDp} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = homeSpacingDp.toFloat(),
                                    onValueChange = { AppearanceConfig.setHomeSpacingDp(it.toInt()) },
                                    valueRange = 0f..32f,
                                    steps = 15,
                                )
                            }

                            // Row Spacing
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Row Spacing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                    Text("${homeVerticalSpacingDp} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = homeVerticalSpacingDp.toFloat(),
                                    onValueChange = { AppearanceConfig.setHomeVerticalSpacingDp(it.toInt()) },
                                    valueRange = 0f..64f,
                                    steps = 31,
                                )
                            }

                            // Corner Radius
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Corner Radius", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                    Text("${posterRoundingDp} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = posterRoundingDp.toFloat(),
                                    onValueChange = { AppearanceConfig.setPosterRoundingDp(it.toInt()) },
                                    valueRange = 0f..24f,
                                    steps = 23,
                                )
                            }
                        }
                    } else {
                        // ── Tab 1: Style & Glow Controls ──────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            // Title Position Selector
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Poster Title Position", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW to "Below",
                                        com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.INSIDE to "Hover",
                                        com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.HIDDEN to "Hidden",
                                    ).forEach { (pos, label) ->
                                        FilterChip(
                                            selected = posterTitlePosition == pos,
                                            onClick = { AppearanceConfig.setPosterTitlePosition(pos) },
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                    }
                                }
                            }

                            // Continue Watching Style
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Continue Watching Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM to "Wide Card",
                                        com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.THUMBNAIL to "Classic",
                                    ).forEach { (style, label) ->
                                        FilterChip(
                                            selected = continueWatchingStyle == style,
                                            onClick = { AppearanceConfig.setContinueWatchingStyle(style) },
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                    }
                                }
                            }

                            // Hover Ambient Glow Switch
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = theme.SurfaceCard.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column {
                                        Text("Hover Ambient Glow", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                        Text("Dynamic backdrop illumination", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                    }
                                    Switch(
                                        checked = posterHoverGlowEnabled,
                                        onCheckedChange = { AppearanceConfig.setPosterHoverGlowEnabled(it) }
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
