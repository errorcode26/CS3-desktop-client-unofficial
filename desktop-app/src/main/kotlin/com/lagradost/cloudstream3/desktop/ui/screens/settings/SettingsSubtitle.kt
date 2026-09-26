package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.subtitles.SubtitleConfig
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun String?.toColor(): Color {
    if (this.isNullOrBlank() || this == "#00000000" || this == "transparent") return Color.Transparent
    if (this == "#80000000" || this == "semi-transparent" || this == "0.0/0.0/0.0/0.5" || this == "0.0/0.0/0.0/0.55") return Color.Black.copy(alpha = 0.55f)
    if (this == "#FF000000" || this == "#ff000000" || this == "#000000" || this == "solid" || this == "0.0/0.0/0.0/1.0") return Color.Black
    val hex = this.removePrefix("#")
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return Color.Transparent
    }
    return try {
        Color(argb.toLong(16))
    } catch (e: Exception) {
        Color.Transparent
    }
}

@Composable
fun SettingsSubtitleEditorScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val subSize = uiState.stringSettings[PlayerConfig.PREF_SUB_SIZE] ?: "45"
    val subColor = uiState.stringSettings[PlayerConfig.PREF_SUB_COLOR] ?: "#FFFFFF"
    val subBg = uiState.stringSettings[PlayerConfig.PREF_SUB_BG] ?: "#00000000"
    val subFont = uiState.stringSettings[PlayerConfig.PREF_SUB_FONT] ?: "Inter"
    val subBorderColor = uiState.stringSettings[PlayerConfig.PREF_SUB_BORDER_COLOR] ?: "#000000"
    val subBorderSize = uiState.stringSettings[PlayerConfig.PREF_SUB_BORDER_SIZE] ?: "3"
    val subShadowColor = uiState.stringSettings[PlayerConfig.PREF_SUB_SHADOW_COLOR] ?: "#00000000"
    val subShadowOffset = uiState.stringSettings[PlayerConfig.PREF_SUB_SHADOW_OFFSET] ?: "0"
    val subBlur = uiState.stringSettings[PlayerConfig.PREF_SUB_BLUR] ?: "0"
    val subUppercase = uiState.booleanSettings[PlayerConfig.PREF_SUB_UPPERCASE] ?: false

    val parseSize = subSize.toFloatOrNull() ?: 45f
    val parseBorderSize = subBorderSize.toFloatOrNull() ?: 3f
    val parseShadowOffset = subShadowOffset.toFloatOrNull() ?: 0f
    val parseBlur = subBlur.toFloatOrNull() ?: 0f

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Preview Area — compact, sleek 120dp viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0D0D11)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            @Suppress("DEPRECATION")
            Image(
                painter = painterResource("subtitle_preview_bg.jpg"),
                contentDescription = "Preview Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = 0.45f,
            )

            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .background(subBg.toColor(), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val subBold = uiState.stringSettings[PlayerConfig.PREF_SUB_BOLD] ?: "no"
                val subItalic = uiState.stringSettings[PlayerConfig.PREF_SUB_ITALIC] ?: "no"

                val textStyle = TextStyle(
                    fontFamily = com.lagradost.cloudstream3.desktop.ui.theme.getFontFamily(subFont),
                    fontSize = (parseSize / 1.7f).sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (subBold == "yes") FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (subItalic == "yes") androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    shadow = if (parseShadowOffset > 0f) {
                        Shadow(
                            color = subShadowColor.toColor(),
                            offset = Offset(parseShadowOffset * 2, parseShadowOffset * 2),
                            blurRadius = parseBlur * 2,
                        )
                    } else {
                        null
                    },
                )

                val rawPreviewText = "The quick brown fox jumps over the lazy dog"
                val previewText = if (subUppercase) rawPreviewText.uppercase() else rawPreviewText

                if (parseBorderSize > 0f) {
                    Text(
                        text = previewText,
                        style = textStyle.copy(
                            drawStyle = Stroke(width = parseBorderSize * 1.5f, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                            color = subBorderColor.toColor(),
                        ),
                    )
                }

                Text(
                    text = previewText,
                    style = textStyle.copy(
                        drawStyle = Fill,
                        color = subColor.toColor(),
                    ),
                )
            }
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
                    .weight(1f)
                    .onGloballyPositioned { containerCoordinates = it }
                    .verticalScroll(scrollState)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingsGroupCard(title = "Subtitle Playback & Filtering") {
                    MviSettingsToggle(
                        key = PlayerConfig.PREF_SUB_ENABLED,
                        label = "Enable Subtitles by Default",
                        subtitle = "Automatically displays the highest-ranking subtitle track matching your Stream Priorities",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = true,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    MviSettingsToggle(
                        key = PlayerConfig.PREF_ENABLE_SUB_OVERRIDE,
                        label = "Override Embedded Subtitles",
                        subtitle = "Forces your custom typography and colors over embedded styles (e.g. ASS/anime subtitles)",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = true,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    MviSettingsToggle(
                        key = PlayerConfig.PREF_SUB_REMOVE_CAPTIONS,
                        label = "Remove Closed Captions (CC / SDH)",
                        subtitle = "Automatically filters out hearing-impaired sound effects (e.g. [MUSIC], (screaming)) and speaker tags",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = false,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    MviSettingsToggle(
                        key = PlayerConfig.PREF_SUB_REMOVE_BLOAT,
                        label = "Remove Promotional Bloat",
                        subtitle = "Strips website advertisements, encoder credits, and sync watermarks from subtitles",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = true,
                    )
                }

                SettingsGroupCard(title = "Typography & Text Styling") {
                    MviSubtitleColorPickerRow(
                        label = "Text Color",
                        key = PlayerConfig.PREF_SUB_COLOR,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "#FFFFFF",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    val availableFonts by com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.availableFonts.collectAsState()
                    val fontOptions = remember(availableFonts) {
                        availableFonts.map { it to it }
                    }
                    MviSettingsDropdown(
                        key = PlayerConfig.PREF_SUB_FONT,
                        label = "Subtitle Font",
                        options = fontOptions,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "Inter",
                        fontFamilyForOption = { com.lagradost.cloudstream3.desktop.ui.theme.getFontFamily(it) },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val chosenFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                            title = "Select Subtitle Font (.ttf, .otf, .woff)",
                                            allowedExtensions = listOf("ttf", "otf", "woff"),
                                            category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.FONT,
                                        )
                                        if (chosenFile != null) {
                                            val result = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.installFont(chosenFile)
                                            result.onSuccess { familyName ->
                                                viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_FONT, familyName))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        com.lagradost.common.logging.AppLogger.e("SettingsSubtitle: Font import error", e)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Install Font (.ttf / .otf)", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.openFontsDirectory()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Open Fonts Folder", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    MviSubtitleSliderRow(
                        label = "Font Size",
                        key = PlayerConfig.PREF_SUB_SIZE,
                        range = 20f..100f,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "45",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    MviSettingsDropdown(
                        key = PlayerConfig.PREF_SUB_BG,
                        label = "Background Style",
                        options = listOf("#00000000" to "Transparent", "#80000000" to "Semi-transparent Black", "#FF000000" to "Solid Black"),
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "#00000000",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Font Style", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val subBold = uiState.stringSettings[PlayerConfig.PREF_SUB_BOLD] ?: "no"
                            val subItalic = uiState.stringSettings[PlayerConfig.PREF_SUB_ITALIC] ?: "no"

                            FilterChip(
                                selected = subBold == "yes",
                                onClick = {
                                    val v = if (subBold == "yes") "no" else "yes"
                                    viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_BOLD, v))
                                },
                                label = { Text("Bold") },
                            )
                            FilterChip(
                                selected = subItalic == "yes",
                                onClick = {
                                    val v = if (subItalic == "yes") "no" else "yes"
                                    viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_ITALIC, v))
                                },
                                label = { Text("Italic") },
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    MviSettingsToggle(
                        key = PlayerConfig.PREF_SUB_UPPERCASE,
                        label = "Uppercase All Subtitles",
                        subtitle = "Converts dialogue text into all-caps (UPPERCASE) for high visibility",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = false,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    MviSubtitleSliderRow(
                        label = "Vertical Position",
                        key = PlayerConfig.PREF_SUB_POS,
                        range = 70f..100f,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "100",
                    )
                }

                SettingsGroupCard(title = "Border") {
                    MviSubtitleColorPickerRow(
                        label = "Border Color",
                        key = PlayerConfig.PREF_SUB_BORDER_COLOR,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "#000000",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    MviSubtitleSliderRow(
                        label = "Border Size",
                        key = PlayerConfig.PREF_SUB_BORDER_SIZE,
                        range = 0f..10f,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "3",
                    )
                }

                SettingsGroupCard(title = "Shadow") {
                    MviSubtitleColorPickerRow(
                        label = "Shadow Color",
                        key = PlayerConfig.PREF_SUB_SHADOW_COLOR,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "#00000000",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    MviSubtitleSliderRow(
                        label = "Shadow Offset",
                        key = PlayerConfig.PREF_SUB_SHADOW_OFFSET,
                        range = 0f..10f,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "0",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    MviSubtitleSliderRow(
                        label = "Shadow Blur",
                        key = PlayerConfig.PREF_SUB_BLUR,
                        range = 0f..10f,
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "0",
                    )
                }
                Button(
                    onClick = {
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_SIZE, "45"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_COLOR, "#FFFFFF"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_BG, "#00000000"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_FONT, "Inter"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_BORDER_COLOR, "#000000"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_BORDER_SIZE, "3"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_SHADOW_COLOR, "#00000000"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_SHADOW_OFFSET, "0"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_BLUR, "0"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_BOLD, "no"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_ITALIC, "no"))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateBoolean(PlayerConfig.PREF_ENABLE_SUB_OVERRIDE, true))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateBoolean(PlayerConfig.PREF_SUB_REMOVE_CAPTIONS, false))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateBoolean(PlayerConfig.PREF_SUB_REMOVE_BLOAT, true))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateBoolean(PlayerConfig.PREF_SUB_UPPERCASE, false))
                        viewModel.onEvent(SettingsUiEvent.OnUpdateString(PlayerConfig.PREF_SUB_POS, "100"))
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text("Reset to Defaults", color = MaterialTheme.colorScheme.onErrorContainer)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MviSubtitleColorPickerRow(
    label: String,
    key: String,
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    defaultValue: String,
) {
    val selectedHex = uiState.stringSettings[key] ?: defaultValue
    val colors = listOf("#00000000", "#000000", "#FFFFFF", "#FFFF00", "#00FFFF", "#FF9900", "#FF5555", "#55FF55")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { hex ->
                val isSelected = selectedHex == hex
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(hex.toColor())
                        .clickable { onEvent(SettingsUiEvent.OnUpdateString(key, hex)) }
                        .then(
                            if (isSelected) {
                                Modifier.padding(2.dp).background(Color.Transparent, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hex == "#00000000") {
                        Icon(Icons.Filled.Close, contentDescription = "None", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (isSelected) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.5f)))
                    }
                }
            }
        }
    }
}

@Composable
fun MviSubtitleSliderRow(
    label: String,
    key: String,
    range: ClosedFloatingPointRange<Float>,
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    defaultValue: String,
) {
    val valueStr = uiState.stringSettings[key] ?: defaultValue
    val targetValue = valueStr.toFloatOrNull() ?: defaultValue.toFloat()
    var localValue by remember(targetValue) { mutableStateOf(targetValue) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(localValue.toInt().toString(), modifier = Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onEvent(SettingsUiEvent.OnUpdateString(key, localValue.toInt().toString())) },
            valueRange = range,
            modifier = Modifier.width(200.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            ),
        )
    }
}
