package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.desktop.player.LanguagePriorityHelper
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsStreamPrioritiesScreen(
    viewModel: SettingsViewModel,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Resolutions, 1 = Audio Languages, 2 = Subtitle Languages
    val qualityPriorities by QualityDataHelper.qualityPriorities.collectAsState()
    val audioStack by LanguagePriorityHelper.audioLanguageStack.collectAsState()
    val subtitleStack by LanguagePriorityHelper.subtitleLanguageStack.collectAsState()

    val qualityList = remember {
        listOf(
            Qualities.P2160.value to "4K (2160p)",
            Qualities.P1440.value to "1440p (QHD)",
            Qualities.P1080.value to "1080p (Full HD)",
            Qualities.P720.value to "720p (HD)",
            0 to "Auto (Adaptive Stream)",
            Qualities.P480.value to "480p (SD)",
            Qualities.P360.value to "360p (Low)",
            Qualities.P240.value to "240p (Very Low)",
            Qualities.P144.value to "144p (Minimum)",
            Qualities.Unknown.value to "Unknown Resolution",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = 24.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Stream Priorities",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Configure resolution hierarchy, audio track priority queue, and subtitle track priority queue for automatic stream selection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Tab Selector & Action Presets Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Category Tabs
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StreamPriorityTabButton(
                    text = "Resolutions",
                    icon = Icons.Default.HighQuality,
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                StreamPriorityTabButton(
                    text = "Audio Languages",
                    icon = Icons.Default.GraphicEq,
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
                StreamPriorityTabButton(
                    text = "Subtitle Languages",
                    icon = Icons.Default.Subtitles,
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                )
            }

            // Quick Preset Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (selectedTab) {
                    0 -> {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    QualityDataHelper.setQualityPriority(Qualities.P2160.value, 10)
                                    QualityDataHelper.setQualityPriority(Qualities.P1080.value, 8)
                                    QualityDataHelper.setQualityPriority(Qualities.P720.value, 5)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Prefer 4K", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    QualityDataHelper.setQualityPriority(Qualities.P1080.value, 10)
                                    QualityDataHelper.setQualityPriority(Qualities.P720.value, 8)
                                    QualityDataHelper.setQualityPriority(Qualities.P2160.value, 4)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Prefer 1080p", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    QualityDataHelper.resetToDefaults()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                    1 -> {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    LanguagePriorityHelper.setAudioPreset(listOf("eng,en", "original"))
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Prefer English", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    LanguagePriorityHelper.setAudioPreset(listOf("jpn,ja", "original", "eng,en"))
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Prefer Japanese", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    LanguagePriorityHelper.resetAudioDefaults()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                    2 -> {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    LanguagePriorityHelper.setSubtitlePreset(listOf("eng,en"))
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Prefer English", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    LanguagePriorityHelper.resetSubtitleDefaults()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // List Area
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        ) {
            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(qualityList, key = { it.first }) { (qualVal, qualLabel) ->
                            val currentPriority = qualityPriorities[qualVal] ?: 4
                            StreamPriorityRow(
                                title = qualLabel,
                                subtitle = "Score bonus weight: +${currentPriority * 10} pts",
                                priority = currentPriority,
                                onPriorityChange = { newPriority ->
                                    scope.launch(Dispatchers.IO) {
                                        QualityDataHelper.setQualityPriority(qualVal, newPriority)
                                    }
                                },
                            )
                        }
                    }
                }
                1 -> {
                    PriorityStackManager(
                        activeStack = audioStack,
                        allOptions = PlayerConfig.GLOBAL_LANGUAGE_OPTIONS,
                        onReorder = { fromIdx, toIdx ->
                            scope.launch(Dispatchers.IO) {
                                LanguagePriorityHelper.moveAudio(fromIdx, toIdx)
                            }
                        },
                        onRemove = { code ->
                            scope.launch(Dispatchers.IO) {
                                LanguagePriorityHelper.removeAudioFromStack(code)
                            }
                        },
                        onAdd = { code ->
                            scope.launch(Dispatchers.IO) {
                                LanguagePriorityHelper.addAudioToStack(code)
                            }
                        },
                        searchPlaceholder = "Search audio languages...",
                    )
                }
                else -> {
                    val subOptions = remember {
                        PlayerConfig.GLOBAL_LANGUAGE_OPTIONS.filter {
                            it.first != "auto" && it.first != "off" && it.first != "original"
                        }
                    }
                    PriorityStackManager(
                        activeStack = subtitleStack,
                        allOptions = subOptions,
                        onReorder = { fromIdx, toIdx ->
                            scope.launch(Dispatchers.IO) {
                                LanguagePriorityHelper.moveSubtitle(fromIdx, toIdx)
                            }
                        },
                        onRemove = { code ->
                            scope.launch(Dispatchers.IO) {
                                LanguagePriorityHelper.removeSubtitleFromStack(code)
                            }
                        },
                        onAdd = { code ->
                            scope.launch(Dispatchers.IO) {
                                LanguagePriorityHelper.addSubtitleToStack(code)
                            }
                        },
                        searchPlaceholder = "Search subtitle languages...",
                    )
                }
            }
        }
    }
}

@Composable
internal fun StreamPriorityTabButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun StreamPriorityRow(
    title: String,
    subtitle: String,
    priority: Int,
    minPriority: Int = 0,
    maxPriority: Int = 15,
    onPriorityChange: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { if (priority > minPriority) onPriorityChange(priority - 1) },
                    enabled = priority > minPriority,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        priority > 6 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        priority < 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.widthIn(min = 44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$priority",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                priority > 6 -> MaterialTheme.colorScheme.primary
                                priority < 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                IconButton(
                    onClick = { if (priority < maxPriority) onPriorityChange(priority + 1) },
                    enabled = priority < maxPriority,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun PriorityStackManager(
    activeStack: List<String>,
    allOptions: List<Pair<String, String>>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    searchPlaceholder: String = "Search languages...",
) {
    var searchQuery by remember { mutableStateOf("") }
    val optionsMap = remember(allOptions) { allOptions.toMap() }

    val unaddedOptions = remember(activeStack, allOptions, searchQuery) {
        val activeSet = activeStack.toSet()
        allOptions.filter { (code, label) ->
            code !in activeSet && (
                searchQuery.isBlank() ||
                label.contains(searchQuery, ignoreCase = true) ||
                code.contains(searchQuery, ignoreCase = true)
            )
        }
    }

    // Drag-to-reorder state machine matching SettingsDetailsSectionsScreen & SettingsNavDockScreen
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    var dragInitialIndex by remember { mutableStateOf(0) }
    var slotHeightPx by remember { mutableStateOf(0f) }
    val fallbackSlotHeight = with(LocalDensity.current) { 56.dp.toPx() }
    val effectiveSlotHeight = if (slotHeightPx > 0f) slotHeightPx else fallbackSlotHeight

    val currentTargetIndex = if (draggingKey != null && effectiveSlotHeight > 0f) {
        (dragInitialIndex + kotlin.math.round(dragAccumulatedY / effectiveSlotHeight).toInt())
            .coerceIn(0, activeStack.lastIndex)
    } else dragInitialIndex

    val currentActiveStack by rememberUpdatedState(activeStack)
    val currentEffectiveSlotHeight by rememberUpdatedState(effectiveSlotHeight)
    val currentDragAccumulatedY by rememberUpdatedState(dragAccumulatedY)
    val currentDragInitialIndex by rememberUpdatedState(dragInitialIndex)

    val onDropItem by rememberUpdatedState {
        val fromIdx = currentDragInitialIndex
        val slotH = currentEffectiveSlotHeight
        val accY = currentDragAccumulatedY
        val toIdx = if (slotH > 0f) {
            (fromIdx + kotlin.math.round(accY / slotH).toInt())
                .coerceIn(0, currentActiveStack.lastIndex)
        } else fromIdx
        draggingKey = null
        dragAccumulatedY = 0f
        if (fromIdx != toIdx && fromIdx in currentActiveStack.indices && toIdx in currentActiveStack.indices) {
            onReorder(fromIdx, toIdx)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Active Priority Queue",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Drag the handles (⠿) to reorder priority. Top rank (#1) is matched first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (activeStack.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No languages prioritized. Streams will fall back to provider defaults.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                activeStack.forEachIndexed { index, code ->
                    val isDraggingThis = draggingKey == code

                    val targetShiftY = when {
                        isDraggingThis -> dragAccumulatedY
                        draggingKey != null && dragInitialIndex < currentTargetIndex && index in (dragInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                        draggingKey != null && dragInitialIndex > currentTargetIndex && index in currentTargetIndex until dragInitialIndex -> effectiveSlotHeight
                        else -> 0f
                    }
                    val animatedShiftY by animateFloatAsState(
                        targetValue = targetShiftY,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )

                    val elevation by animateDpAsState(if (isDraggingThis) 16.dp else 0.dp)
                    val scale by animateFloatAsState(if (isDraggingThis) 1.02f else 1.0f)

                    val label = optionsMap[code] ?: code
                    val prospectiveRank = if (isDraggingThis) currentTargetIndex + 1 else index + 1

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDraggingThis) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            if (isDraggingThis) 1.5.dp else 1.dp,
                            if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Drag grip handle
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDraggingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                        .pointerInput(code) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggingKey = code
                                                    dragInitialIndex = currentActiveStack.indexOf(code)
                                                    dragAccumulatedY = 0f
                                                },
                                                onDragEnd = { onDropItem() },
                                                onDragCancel = {
                                                    draggingKey = null
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

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (prospectiveRank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = "#$prospectiveRank",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (prospectiveRank == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }

                                Column {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (code.isNotBlank()) "Code: $code" else "Default",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onRemove(code) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Available Languages",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Click 'Add' to append to the priority queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(searchPlaceholder, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.width(240.dp).height(50.dp),
                shape = RoundedCornerShape(10.dp),
            )
        }

        if (unaddedOptions.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No languages matching \"$searchQuery\"" else "All available languages are in the priority queue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                unaddedOptions.forEach { (code, label) ->
                    AvailableLanguageItemCard(
                        title = label,
                        code = code,
                        onAdd = { onAdd(code) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AvailableLanguageItemCard(
    title: String,
    code: String,
    onAdd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Code: $code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalButton(
                onClick = onAdd,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", fontSize = 12.sp)
            }
        }
    }
}

