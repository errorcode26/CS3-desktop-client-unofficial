package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.applyShadowMultiplier
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiState
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.delay

val LocalSettingsScrollState = compositionLocalOf<ScrollState?> { null }
val LocalScrollContainerCoordinates = compositionLocalOf<LayoutCoordinates?> { null }

fun Modifier.highlightAndScrollIfRequested(label: String): Modifier = composed {
    val isHighlighted = SettingsSession.highlightedSetting == label
    val scrollState = LocalSettingsScrollState.current
    val containerCoords = LocalScrollContainerCoordinates.current
    var hasScrolled by remember { mutableStateOf(false) }

    var myCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(isHighlighted, myCoords, containerCoords, scrollState) {
        if (isHighlighted && !hasScrolled && myCoords != null && containerCoords != null && scrollState != null) {
            hasScrolled = true

            // Wait a tiny bit just in case animations are still snapping into place
            delay(150)

            try {
                // localBoundingBoxOf gives us coordinates of this item relative to the scroll container's current visible viewport.
                // We add the current scroll value to get the absolute pixel offset from the very top of the scrollable content.
                val bounds = containerCoords.localBoundingBoxOf(myCoords!!)
                val targetY = (bounds.top + scrollState.value).toInt()

                // Add a small padding to the top so it's not flush with the very edge
                val finalY = (targetY - 16).coerceAtLeast(0)

                scrollState.animateScrollTo(finalY)
            } catch (e: Exception) {
                // Ignore layout detached exceptions
            }

            delay(1500)
            if (SettingsSession.highlightedSetting == label) {
                SettingsSession.highlightedSetting = null
            }
        }
    }

    // Reset hasScrolled if the highlight changes away
    LaunchedEffect(isHighlighted) {
        if (!isHighlighted) {
            hasScrolled = false
        }
    }

    this
        .onGloballyPositioned { myCoords = it }
        .then(
            if (isHighlighted) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)) else Modifier,
        )
}

@Composable
fun SettingsGroupCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val isLightMode = theme.isLightMode
    val amoledMode = theme.isAmoled
    val uiCardOpacity = theme.cardOpacity

    Column(modifier = modifier.fillMaxWidth().highlightAndScrollIfRequested(title)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                isLightMode -> theme.SurfaceCard
                amoledMode -> Color.Black
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity)
            },
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when {
                    isLightMode -> theme.Divider
                    amoledMode -> Color.White.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                },
            ),
            shadowElevation = if (isLightMode) 1.dp else 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().highlightAndScrollIfRequested(label).padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
fun <T> SettingsDropdownItem(
    label: String,
    subtitle: String? = null,
    options: List<Pair<T, String>>,
    currentValue: T,
    enabled: Boolean = true,
    fontFamilyForOption: ((T) -> FontFamily?)? = null,
    onSelectionChanged: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().highlightAndScrollIfRequested(label).padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }

        Box {
            FilledTonalButton(
                onClick = { expanded = true },
                enabled = enabled,
            ) {
                val currentTitle = options.find { it.first == currentValue }?.second ?: currentValue.toString()
                Text(
                    text = currentTitle,
                    fontFamily = fontFamilyForOption?.invoke(currentValue),
                )
            }
            AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, title) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = title,
                                fontFamily = fontFamilyForOption?.invoke(value),
                            )
                        },
                        onClick = {
                            onSelectionChanged(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSliderItem(
    label: String,
    subtitle: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().highlightAndScrollIfRequested(label).padding(vertical = 4.dp, horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            ),
        )
    }
}

@Composable
fun SettingsChipGroupItem(
    label: String,
    subtitle: String? = null,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelectionChanged: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().highlightAndScrollIfRequested(label).padding(vertical = 4.dp, horizontal = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { (id, title) ->
                FilterChip(
                    selected = selectedValue == id,
                    onClick = { onSelectionChanged(id) },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
    }
}

@Composable
fun SettingsNavigationItem(
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .highlightAndScrollIfRequested(label)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Smart MVI Wrappers ─────────────────────────────────────────────────────────────

@Composable
fun MviSettingsToggle(
    key: String,
    label: String,
    subtitle: String? = null,
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    defaultValue: Boolean = false,
) {
    val initialValue = remember(key) { DesktopDataStore.getKey<Boolean>(key) ?: defaultValue }
    val checked = uiState.booleanSettings[key] ?: initialValue

    SettingsToggleItem(
        label = label,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = { newValue ->
            onEvent(SettingsUiEvent.OnUpdateBoolean(key, newValue))
        },
    )
}

@Composable
inline fun <reified T> MviSettingsDropdown(
    key: String,
    label: String,
    subtitle: String? = null,
    options: List<Pair<T, String>>,
    uiState: SettingsUiState,
    crossinline onEvent: (SettingsUiEvent) -> Unit,
    defaultValue: T,
    noinline fontFamilyForOption: ((T) -> FontFamily?)? = null,
) {
    val initialValue = remember(key) { DesktopDataStore.getKey<T>(key) ?: defaultValue }

    // We dynamically map the value type from the UiState
    val currentValue = when (defaultValue) {
        is String -> (uiState.stringSettings[key] ?: initialValue) as T
        is Boolean -> (uiState.booleanSettings[key] ?: initialValue) as T
        is Int -> (uiState.intSettings[key] ?: initialValue) as T
        is Float -> (uiState.floatSettings[key] ?: initialValue) as T
        else -> initialValue
    }

    SettingsDropdownItem(
        label = label,
        subtitle = subtitle,
        options = options,
        currentValue = currentValue,
        fontFamilyForOption = fontFamilyForOption,
        onSelectionChanged = { newValue ->
            when (newValue) {
                is String -> onEvent(SettingsUiEvent.OnUpdateString(key, newValue))
                is Boolean -> onEvent(SettingsUiEvent.OnUpdateBoolean(key, newValue))
                is Int -> onEvent(SettingsUiEvent.OnUpdateInt(key, newValue))
                is Float -> onEvent(SettingsUiEvent.OnUpdateFloat(key, newValue))
            }
        },
    )
}

@Composable
fun MviSettingsSlider(
    key: String,
    label: String,
    subtitle: String? = null,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    defaultValue: Float,
) {
    val initialValue = remember(key) { DesktopDataStore.getKey<Float>(key) ?: defaultValue }
    val value = uiState.floatSettings[key] ?: initialValue

    SettingsSliderItem(
        label = label,
        subtitle = subtitle,
        value = value,
        valueRange = valueRange,
        steps = steps,
        onValueChange = { newValue ->
            onEvent(SettingsUiEvent.OnUpdateFloat(key, newValue))
        },
    )
}

@Composable
fun MviSettingsChipGroup(
    key: String,
    label: String,
    subtitle: String? = null,
    options: List<Pair<String, String>>,
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    defaultValue: String,
) {
    val initialValue = remember(key) { DesktopDataStore.getKey<String>(key) ?: defaultValue }
    val selectedValue = uiState.stringSettings[key] ?: initialValue

    SettingsChipGroupItem(
        label = label,
        subtitle = subtitle,
        options = options,
        selectedValue = selectedValue,
        onSelectionChanged = { newValue ->
            onEvent(SettingsUiEvent.OnUpdateString(key, newValue))
        },
    )
}

@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .highlightAndScrollIfRequested(title)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate to $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
