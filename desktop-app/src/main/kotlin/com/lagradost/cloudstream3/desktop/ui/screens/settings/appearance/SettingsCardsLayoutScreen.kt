package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsDropdownItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSliderItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem
import com.lagradost.cloudstream3.desktop.ui.theme.*
import com.lagradost.cloudstream3.newMovieSearchResponse

enum class CardStylePreset(val label: String, val desc: String) {
    MINIMALIST("Minimalist", "Clean art, hover badges, clean titles"),
    STANDARD("Standard", "Gold star pill, 4K tag, SUB/DUB badges"),
    COMPACT("Compact", "1-line title, high-density compact cards"),
    CUSTOM("Custom", "Customized in fine-tuning drawer"),
}

@Composable
fun SettingsCardsLayoutScreen() {
    val scrollState = rememberScrollState()

    // Poster Dimensions & Spacing
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val posterRoundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()

    // Continue Watching
    val showContinueWatching by AppearanceConfig.showContinueWatching.collectAsState()
    val continueWatchingStyle by AppearanceConfig.continueWatchingStyle.collectAsState()

    // Home Hero Spotlight
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()
    val heroBannerStyle by AppearanceConfig.heroBannerStyle.collectAsState()
    val heroAutoSlideDelaySeconds by AppearanceConfig.heroAutoSlideDelaySeconds.collectAsState()
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()

    // Details Page Modular Sections
    val sectionOrder by AppearanceConfig.detailsSectionOrder.collectAsState()
    val disabledSections by AppearanceConfig.detailsDisabledSections.collectAsState()

    // Advanced Fine-Tuning State
    val cleanModeEnabled by AppearanceConfig.cleanModeEnabled.collectAsState()
    val hideProviderNames by AppearanceConfig.hideProviderNames.collectAsState()
    val hideDetailsSource by AppearanceConfig.hideDetailsSource.collectAsState()
    val posterMetadataPosition by AppearanceConfig.posterMetadataPosition.collectAsState()
    val posterTextAlignment by AppearanceConfig.posterTextAlignment.collectAsState()
    val posterTitleMaxLines by AppearanceConfig.posterTitleMaxLines.collectAsState()
    val elementShadowsEnabled by AppearanceConfig.elementShadowsEnabled.collectAsState()
    val elementShadowMultiplier by AppearanceConfig.elementShadowMultiplier.collectAsState()
    val textDropShadowEnabled by AppearanceConfig.textDropShadowEnabled.collectAsState()
    val textDropShadowBlur by AppearanceConfig.textDropShadowBlur.collectAsState()
    val posterDepthEffectEnabled by AppearanceConfig.posterDepthEffectEnabled.collectAsState()

    // Card Metadata
    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()

    var isAdvancedOpen by remember { mutableStateOf(false) }

    // Sample Movie Card for Live Interactive Preview
    val sampleMovie = remember {
        val dummyApi = object : MainAPI() {
            override var mainUrl = ""
            override var name = "Preview"
            override val hasMainPage = true
        }
        dummyApi.newMovieSearchResponse("Dune: Part Two [4K] [HDR]", "preview_dune", TvType.Movie, false) {
            posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg"
            quality = SearchQuality.UHD
            score = Score.from10(8.6)
        }
    }

    // Determine current preset
    val currentPreset = remember(
        posterMetadataPosition,
        posterTitleMaxLines,
        autoCleanTitles,
        hideProviderNames,
        showRatingBadges,
    ) {
        when {
            posterMetadataPosition == PosterMetadataPosition.STEALTH_HOVER && hideProviderNames && autoCleanTitles -> CardStylePreset.MINIMALIST
            posterMetadataPosition == PosterMetadataPosition.ON_POSTER && posterTitleMaxLines == 1 -> CardStylePreset.COMPACT
            posterMetadataPosition == PosterMetadataPosition.BELOW_POSTER && showRatingBadges && autoDetectQuality -> CardStylePreset.STANDARD
            else -> CardStylePreset.CUSTOM
        }
    }

    // Drag-and-drop state for Details sections
    var draggingSectionKey by remember { mutableStateOf<DetailsSectionKey?>(null) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    var dragInitialIndex by remember { mutableStateOf(0) }
    var slotHeightPx by remember { mutableStateOf(0f) }
    val fallbackSlotHeight = with(LocalDensity.current) { 64.dp.toPx() }
    val effectiveSlotHeight = if (slotHeightPx > 0f) slotHeightPx else fallbackSlotHeight

    val currentTargetIndex = if (draggingSectionKey != null && effectiveSlotHeight > 0f) {
        (dragInitialIndex + kotlin.math.round(dragAccumulatedY / effectiveSlotHeight).toInt())
            .coerceIn(0, sectionOrder.lastIndex)
    } else {
        dragInitialIndex
    }

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
        } else {
            fromIdx
        }
        draggingSectionKey = null
        dragAccumulatedY = 0f
        if (fromIdx != toIdx && fromIdx in currentSectionOrder.indices && toIdx in currentSectionOrder.indices) {
            AppearanceConfig.moveDetailsSection(fromIdx, toIdx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── 1. Live Interactive Sample Card Banner ────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.RemoveRedEye,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Live Interactive Card Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Updates in real time as you adjust presets, poster width (${posterWidthDp}dp), and corner rounding (${posterRoundingDp}dp) below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }

                Box(
                    modifier = Modifier.padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PosterCard(
                        item = sampleMovie,
                        provider = null,
                        itemWidth = posterWidthDp.dp,
                        onClick = {},
                        onPlayClick = {},
                    )
                }
            }
        }

        // ── 2. Card Style Presets ─────────────────────────────────────────────
        SettingsGroupCard(title = "Card Style Presets") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(
                    CardStylePreset.MINIMALIST,
                    CardStylePreset.STANDARD,
                    CardStylePreset.COMPACT,
                ).forEach { preset ->
                    val isSelected = currentPreset == preset
                    Surface(
                        onClick = {
                            when (preset) {
                                CardStylePreset.MINIMALIST -> {
                                    AppearanceConfig.setPosterMetadataPosition(PosterMetadataPosition.STEALTH_HOVER)
                                    AppearanceConfig.setPosterTitleMaxLines(2)
                                    AppearanceConfig.setPosterTextAlignment(PosterTextAlignment.LEFT)
                                    CardMetadataConfig.setAutoCleanTitles(true)
                                    AppearanceConfig.setHideProviderNames(true)
                                }
                                CardStylePreset.STANDARD -> {
                                    AppearanceConfig.setPosterMetadataPosition(PosterMetadataPosition.BELOW_POSTER)
                                    AppearanceConfig.setPosterTitleMaxLines(2)
                                    AppearanceConfig.setPosterTextAlignment(PosterTextAlignment.LEFT)
                                    CardMetadataConfig.setShowRatingBadges(true)
                                    CardMetadataConfig.setAutoDetectQuality(true)
                                    CardMetadataConfig.setAutoDetectSubDub(true)
                                    CardMetadataConfig.setAutoCleanTitles(true)
                                }
                                CardStylePreset.COMPACT -> {
                                    AppearanceConfig.setPosterMetadataPosition(PosterMetadataPosition.ON_POSTER)
                                    AppearanceConfig.setPosterTitleMaxLines(1)
                                    AppearanceConfig.setPosterWidthDp(145)
                                    CardMetadataConfig.setShowRatingBadges(true)
                                    CardMetadataConfig.setAutoDetectQuality(true)
                                }
                                CardStylePreset.CUSTOM -> {}
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f).height(88.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Text(
                                text = preset.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }
        }

        // ── 3. Direct Poster Geometry & Sizing Sliders ────────────────────────
        SettingsGroupCard(title = "Poster Geometry & Dimensions") {
            SettingsSliderItem(
                label = "Poster Card Width",
                subtitle = "${posterWidthDp} dp — Controls poster card size across catalog grids",
                value = posterWidthDp.toFloat(),
                onValueChange = { AppearanceConfig.setPosterWidthDp(it.toInt()) },
                valueRange = 130f..220f,
                steps = 17,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsSliderItem(
                label = "Card Corner Rounding",
                subtitle = "${posterRoundingDp} dp — Corner curve radius for poster artwork and cards",
                value = posterRoundingDp.toFloat(),
                onValueChange = { AppearanceConfig.setPosterRoundingDp(it.toInt()) },
                valueRange = 4f..24f,
                steps = 9,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsSliderItem(
                label = "Grid Horizontal Spacing",
                subtitle = "${homeSpacingDp} dp — Gap spacing between adjacent cards on shelves and grids",
                value = homeSpacingDp.toFloat(),
                onValueChange = { AppearanceConfig.setHomeSpacingDp(it.toInt()) },
                valueRange = 8f..24f,
                steps = 7,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Hover Ambient Glow",
                subtitle = "Subtle accent-tinted glow backlight behind cards when hovered",
                checked = posterHoverGlowEnabled,
                onCheckedChange = { AppearanceConfig.setPosterHoverGlowEnabled(it) },
            )
        }

        // ── 4. Continue Watching Feed ─────────────────────────────────────────
        SettingsGroupCard(title = "Continue Watching Row") {
            SettingsToggleItem(
                label = "Show Continue Watching",
                subtitle = "Display in-progress movies and series at the top of the Home feed",
                checked = showContinueWatching,
                onCheckedChange = { AppearanceConfig.setShowContinueWatching(it) },
            )

            if (showContinueWatching) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Card Layout Style",
                    subtitle = when (continueWatchingStyle) {
                        ContinueWatchingStyle.THUMBNAIL -> "Classic: 16:9 full thumbnail with bottom scrim & progress bar"
                        ContinueWatchingStyle.PREMIUM -> "Wide: Compact 2:3 poster on left with clean metadata rail"
                        ContinueWatchingStyle.DETAILED -> "Detailed: 16:9 episode still with floating bar & episode synopsis"
                    },
                    options = listOf(
                        ContinueWatchingStyle.THUMBNAIL to "Classic (Full Landscape Artwork)",
                        ContinueWatchingStyle.PREMIUM to "Wide (Vertical Poster & Details)",
                        ContinueWatchingStyle.DETAILED to "Detailed (Thumbnail & Episode Rail)",
                    ),
                    currentValue = continueWatchingStyle,
                    onSelectionChanged = { AppearanceConfig.setContinueWatchingStyle(it) },
                )
            }
        }

        // ── 5. Home Spotlight Hero Carousel ──────────────────────────────────
        SettingsGroupCard(title = "Home Spotlight Hero") {
            SettingsToggleItem(
                label = "Enable Hero Slider",
                subtitle = "Display featured trending media spotlight banner at the top of Home",
                checked = heroEnabled,
                onCheckedChange = { AppearanceConfig.setHeroEnabled(it) },
            )

            if (heroEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Hero Banner Layout Style",
                    subtitle = "Choose between cinema peeking edges, fullscreen banner, and filmstrip",
                    options = listOf(
                        HeroBannerStyle.CINEMA_PEEKING to "Cinema (Peeking Rails)",
                        HeroBannerStyle.FULLSCREEN_IMMERSIVE to "Fullscreen Immersive",
                        HeroBannerStyle.THUMBNAIL_STRIP to "Thumbnail Filmstrip",
                    ),
                    currentValue = heroBannerStyle,
                    onSelectionChanged = { AppearanceConfig.setHeroBannerStyle(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Hero Auto-Slide Delay",
                    subtitle = "How long each spotlight item stays visible before transitioning",
                    options = listOf(
                        0 to "Off (Manual Only)",
                        4 to "4 seconds",
                        6 to "6 seconds",
                        8 to "8 seconds",
                        12 to "12 seconds",
                    ),
                    currentValue = heroAutoSlideDelaySeconds,
                    onSelectionChanged = { AppearanceConfig.setHeroAutoSlideDelaySeconds(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsToggleItem(
                    label = "Dynamic Backdrop Blur",
                    subtitle = "Applies frosted glass backdrop blur behind the spotlight banner",
                    checked = heroBackgroundBlurEnabled,
                    onCheckedChange = { AppearanceConfig.setHeroBackgroundBlurEnabled(it) },
                )

                if (heroBackgroundBlurEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsSliderItem(
                        label = "Backdrop Blur Radius",
                        subtitle = "${heroBackdropBlurRadius.toInt()} px blur",
                        value = heroBackdropBlurRadius,
                        onValueChange = { AppearanceConfig.setHeroBackdropBlurRadius(it) },
                        valueRange = 8f..64f,
                        steps = 13,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsSliderItem(
                        label = "Backdrop Darkening",
                        subtitle = "${(heroBackdropDarkening * 100).toInt()}% scrim opacity",
                        value = heroBackdropDarkening,
                        onValueChange = { AppearanceConfig.setHeroBackdropDarkening(it) },
                        valueRange = 0.1f..0.9f,
                        steps = 7,
                    )
                }
            }
        }

        // ── 6. Details Page Modular Sections ─────────────────────────────────
        SettingsGroupCard(title = "Details Page Modular Sections") {
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
                        text = "Drag handles to reorder sections, or toggle them on and off",
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

        // ── 7. Advanced Fine-Tuning Drawer (Collapsed by Default) ─────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isAdvancedOpen = !isAdvancedOpen }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Advanced Card Tweaks & Clean Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isAdvancedOpen) "Click to collapse granular options" else "Granular toggles: badges, drop shadows, title clamping, clean mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isAdvancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isAdvancedOpen) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }

                AnimatedVisibility(
                    visible = isAdvancedOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        // Clean Mode Master
                        SettingsToggleItem(
                            label = "Cinematic Clean Mode (Master)",
                            subtitle = "One-click switch to strip all scraper branding and release noise (Shortcut: Ctrl+Shift+C)",
                            checked = cleanModeEnabled,
                            onCheckedChange = { AppearanceConfig.setCleanModeEnabled(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "Hide Provider & Scraper Names Everywhere",
                            subtitle = "Suppresses provider names in context menus, cards, and list subtitles",
                            checked = hideProviderNames,
                            onCheckedChange = { AppearanceConfig.setHideProviderNames(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "Auto-Clean Messy Release Titles",
                            subtitle = "Strips raw release tags (WEB-DL, Dual Audio, codecs) to show pure titles",
                            checked = autoCleanTitles,
                            onCheckedChange = { CardMetadataConfig.setAutoCleanTitles(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "Hide Source on Details Page",
                            subtitle = "Omits the 'Source' provider name from the technical specs sidebar",
                            checked = hideDetailsSource,
                            onCheckedChange = { AppearanceConfig.setHideDetailsSource(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsDropdownItem(
                            label = "Metadata Placement",
                            subtitle = "Position of specs and tags on the card",
                            options = listOf(
                                PosterMetadataPosition.BELOW_POSTER to "Below Poster (Clean Artwork)",
                                PosterMetadataPosition.ON_POSTER to "On Poster (Floating Pills)",
                                PosterMetadataPosition.HYBRID to "Hybrid (Rating on Poster, Specs Below)",
                                PosterMetadataPosition.STEALTH_HOVER to "Stealth Hover (Fade in on Hover)",
                            ),
                            currentValue = posterMetadataPosition,
                            onSelectionChanged = { AppearanceConfig.setPosterMetadataPosition(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsDropdownItem(
                            label = "Poster Text Alignment",
                            subtitle = "Alignment of title and metadata under poster",
                            options = listOf(
                                PosterTextAlignment.LEFT to "Left (Apple TV+ / Infuse)",
                                PosterTextAlignment.CENTER to "Center (Classic)",
                            ),
                            currentValue = posterTextAlignment,
                            onSelectionChanged = { AppearanceConfig.setPosterTextAlignment(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsDropdownItem(
                            label = "Title Line Clamping",
                            subtitle = "Maximum lines displayed for long titles before truncating",
                            options = listOf(
                                1 to "1 Line (Ultra Compact)",
                                2 to "2 Lines (Standard)",
                            ),
                            currentValue = posterTitleMaxLines,
                            onSelectionChanged = { AppearanceConfig.setPosterTitleMaxLines(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "Show Rating Badges (★)",
                            subtitle = "Displays star score indicator on media cards",
                            checked = showRatingBadges,
                            onCheckedChange = { CardMetadataConfig.setShowRatingBadges(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "Show Resolution / Quality Badges",
                            subtitle = "Detects and displays 4K, 1080p, HD resolution tags",
                            checked = autoDetectQuality,
                            onCheckedChange = { CardMetadataConfig.setAutoDetectQuality(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "Show SUB / DUB Audio Badges",
                            subtitle = "Displays subtitle and dubbed audio availability tags",
                            checked = autoDetectSubDub,
                            onCheckedChange = { CardMetadataConfig.setAutoDetectSubDub(it) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "UI Element Drop Shadows",
                            subtitle = "Adds visual depth and elevation shadows under cards and floating panels",
                            checked = elementShadowsEnabled,
                            onCheckedChange = { AppearanceConfig.setElementShadowsEnabled(it) },
                        )

                        if (elementShadowsEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            SettingsSliderItem(
                                label = "Shadow Intensity Multiplier",
                                subtitle = "%.1fx strength".format(elementShadowMultiplier),
                                value = elementShadowMultiplier,
                                onValueChange = { AppearanceConfig.setElementShadowMultiplier(it) },
                                valueRange = 0.5f..2.5f,
                                steps = 8,
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "Text Legibility Shadows",
                            subtitle = "Soft subtle drop shadows behind titles and badges for enhanced readability",
                            checked = textDropShadowEnabled,
                            onCheckedChange = { AppearanceConfig.setTextDropShadowEnabled(it) },
                        )

                        if (textDropShadowEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            SettingsSliderItem(
                                label = "Text Shadow Blur Radius",
                                subtitle = "${textDropShadowBlur.toInt()} dp blur",
                                value = textDropShadowBlur,
                                onValueChange = { AppearanceConfig.setTextDropShadowBlur(it) },
                                valueRange = 2f..24f,
                                steps = 11,
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            label = "Poster Depth Effect",
                            subtitle = "Directional overhead rim light bevel and laminated glass sheen across cards",
                            checked = posterDepthEffectEnabled,
                            onCheckedChange = { AppearanceConfig.setPosterDepthEffectEnabled(it) },
                        )
                    }
                }
            }
        }
    }
}
