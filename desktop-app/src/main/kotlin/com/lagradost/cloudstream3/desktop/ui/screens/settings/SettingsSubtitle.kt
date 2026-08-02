package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.common.storage.DesktopDataStore

fun String?.toColor(): Color {
    if (this == null) return Color.Transparent
    val hex = this.removePrefix("#")
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> "FF000000"
    }
    return try {
        Color(argb.toLong(16))
    } catch (e: Exception) {
        Color.Transparent
    }
}

/**
 * Full-width dedicated sub-screen for subtitle customization.
 * Preview pinned at the top, controls below in a scrollable column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubtitleEditorScreen() {
    var subSize by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SIZE) ?: "45") }
    var subColor by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_COLOR) ?: "#FFFFFF") }
    var subBg by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BG) ?: "#00000000") }
    var subFont by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_FONT)) }
    var subBorderColor by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BORDER_COLOR) ?: "#000000") }
    var subBorderSize by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BORDER_SIZE) ?: "3") }
    var subShadowColor by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SHADOW_COLOR) ?: "#00000000") }
    var subShadowOffset by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SHADOW_OFFSET) ?: "0") }
    var subBlur by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BLUR) ?: "0") }
    var subBold by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BOLD) ?: "no") }
    var subItalic by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_ITALIC) ?: "no") }
    var subOverrideEnabled by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_ENABLE_SUB_OVERRIDE) ?: false) }

    val parseSize = subSize.toFloatOrNull() ?: 45f
    val parseBorderSize = subBorderSize.toFloatOrNull() ?: 3f
    val parseShadowOffset = subShadowOffset.toFloatOrNull() ?: 0f
    val parseBlur = subBlur.toFloatOrNull() ?: 0f

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Preview Area — pinned at top, not scrollable
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter = painterResource("subtitle_preview_bg.jpg"),
                contentDescription = "Preview Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = 0.6f,
            )

            Box(
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .background(subBg.toColor(), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val textStyle = TextStyle(
                    fontFamily = com.lagradost.cloudstream3.desktop.ui.theme.getFontFamily(subFont.takeIf { !it.isNullOrBlank() } ?: "Inter"),
                    fontSize = (parseSize / 1.5f).sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (subBold == "yes") FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (subItalic == "yes") androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    shadow = if (parseShadowOffset > 0f) Shadow(
                        color = subShadowColor.toColor(),
                        offset = Offset(parseShadowOffset * 2, parseShadowOffset * 2),
                        blurRadius = parseBlur * 2,
                    ) else null,
                )

                if (parseBorderSize > 0f) {
                    Text(
                        text = "To hell with being forgotten,\nand to hell with forgetting!",
                        style = textStyle.copy(
                            drawStyle = Stroke(width = parseBorderSize * 1.5f, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                            color = subBorderColor.toColor(),
                        ),
                    )
                }

                Text(
                    text = "To hell with being forgotten,\nand to hell with forgetting!",
                    style = textStyle.copy(
                        drawStyle = Fill,
                        color = subColor.toColor(),
                    ),
                )
            }
        }

        // Scrollable Controls Below
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroupCard(title = "Global Override") {
                SettingsToggleItem(
                    label = "Override Video Subtitles",
                    subtitle = "When enabled, the player forces these custom styles over the video's default subtitle styles.",
                    checked = subOverrideEnabled,
                    onCheckedChange = { 
                        subOverrideEnabled = it
                        DesktopDataStore.setKey(PlayerConfig.PREF_ENABLE_SUB_OVERRIDE, it) 
                    },
                )
            }

            SettingsGroupCard(title = "Text") {
                SubtitleColorPickerRow("Text Color", subColor) {
                    subColor = it; DesktopDataStore.setKey(PlayerConfig.PREF_SUB_COLOR, it)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                SettingsDropdownItem(
                    label = "Subtitle Font",
                    options = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getAvailableFonts().map { it to it },
                    currentValue = subFont.takeIf { !it.isNullOrBlank() } ?: "Inter",
                    onSelectionChanged = { subFont = it; DesktopDataStore.setKey(PlayerConfig.PREF_SUB_FONT, it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                SubtitleSliderRow("Font Size", parseSize, 20f..100f) {
                    subSize = it.toInt().toString(); DesktopDataStore.setKey(PlayerConfig.PREF_SUB_SIZE, subSize)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                SettingsDropdownItem(
                    label = "Background Style",
                    options = listOf("#00000000" to "Transparent", "#80000000" to "Semi-transparent Black", "#FF000000" to "Solid Black"),
                    currentValue = subBg,
                    onSelectionChanged = { subBg = it; DesktopDataStore.setKey(PlayerConfig.PREF_SUB_BG, it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Font Style", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = subBold == "yes",
                            onClick = {
                                val v = if (subBold == "yes") "no" else "yes"
                                subBold = v; DesktopDataStore.setKey(PlayerConfig.PREF_SUB_BOLD, v)
                            },
                            label = { Text("Bold") },
                        )
                        FilterChip(
                            selected = subItalic == "yes",
                            onClick = {
                                val v = if (subItalic == "yes") "no" else "yes"
                                subItalic = v; DesktopDataStore.setKey(PlayerConfig.PREF_SUB_ITALIC, v)
                            },
                            label = { Text("Italic") },
                        )
                    }
                }
            }

            SettingsGroupCard(title = "Border") {
                SubtitleColorPickerRow("Border Color", subBorderColor) {
                    subBorderColor = it; DesktopDataStore.setKey(PlayerConfig.PREF_SUB_BORDER_COLOR, it)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                SubtitleSliderRow("Border Size", parseBorderSize, 0f..10f) {
                    subBorderSize = it.toInt().toString(); DesktopDataStore.setKey(PlayerConfig.PREF_SUB_BORDER_SIZE, subBorderSize)
                }
            }

            SettingsGroupCard(title = "Shadow") {
                SubtitleColorPickerRow("Shadow Color", subShadowColor) {
                    subShadowColor = it; DesktopDataStore.setKey(PlayerConfig.PREF_SUB_SHADOW_COLOR, it)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                SubtitleSliderRow("Shadow Offset", parseShadowOffset, 0f..10f) {
                    subShadowOffset = it.toInt().toString(); DesktopDataStore.setKey(PlayerConfig.PREF_SUB_SHADOW_OFFSET, subShadowOffset)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                SubtitleSliderRow("Shadow Blur", parseBlur, 0f..10f) {
                    subBlur = it.toInt().toString(); DesktopDataStore.setKey(PlayerConfig.PREF_SUB_BLUR, subBlur)
                }
            }
            Button(
                onClick = {
                    subSize = "45"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_SIZE)
                    subColor = "#FFFFFF"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_COLOR)
                    subBg = "#00000000"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_BG)
                    subFont = null; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_FONT)
                    subBorderColor = "#000000"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_BORDER_COLOR)
                    subBorderSize = "3"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_BORDER_SIZE)
                    subShadowColor = "#00000000"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_SHADOW_COLOR)
                    subShadowOffset = "0"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_SHADOW_OFFSET)
                    subBlur = "0"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_BLUR)
                    subBold = "no"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_BOLD)
                    subItalic = "no"; DesktopDataStore.removeKey(PlayerConfig.PREF_SUB_ITALIC)
                    subOverrideEnabled = false; DesktopDataStore.removeKey(PlayerConfig.PREF_ENABLE_SUB_OVERRIDE)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("Reset to Defaults", color = MaterialTheme.colorScheme.onErrorContainer)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SubtitleColorPickerRow(label: String, selectedHex: String, onColorSelected: (String) -> Unit) {
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
                        .clickable { onColorSelected(hex) }
                        .then(
                            if (isSelected) Modifier.padding(2.dp).background(Color.Transparent, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hex == "#00000000") {
                        Text("❌", fontSize = 10.sp)
                    } else if (isSelected) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.5f)))
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value.toInt().toString(), modifier = Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
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
