package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog

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
fun CustomColorStudioDialog(
    show: Boolean,
    title: String,
    initialHex: String,
    defaultHex: String = "#7C6BFF",
    curatedColors: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onColorConfirmed: (String) -> Unit,
) {
    if (!show) return

    var currentHexInput by remember(initialHex, show) { mutableStateOf(initialHex.uppercase()) }
    var parsedColor by remember(initialHex, show) {
        mutableStateOf(com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(initialHex, Color(0xFF7C6BFF)))
    }
    val hsv = remember(parsedColor) { colorToHsv(parsedColor) }
    var hue by remember(show) { mutableStateOf(hsv[0]) }
    var saturation by remember(show) { mutableStateOf(hsv[1]) }
    var value by remember(show) { mutableStateOf(hsv[2]) }

    fun syncFromHsv() {
        val c = hsvToColor(hue, saturation, value)
        val r = (c.red * 255).toInt()
        val g = (c.green * 255).toInt()
        val b = (c.blue * 255).toInt()
        val hex = String.format("#%02X%02X%02X", r, g, b)
        currentHexInput = hex
        parsedColor = c
    }

    fun syncFromHex(hex: String) {
        currentHexInput = hex.uppercase()
        val clean = hex.trim().removePrefix("#")
        if (clean.length == 6 || clean.length == 8) {
            val color = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(hex, parsedColor)
            parsedColor = color
            val newHsv = colorToHsv(color)
            hue = newHsv[0]
            saturation = newHsv[1]
            value = newHsv[2]
        }
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 460.dp, max = 520.dp).padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // Hex Input & Preview Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = currentHexInput,
                    onValueChange = { syncFromHex(it) },
                    label = { Text("Hex Color Code (#RRGGBB)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )

                // Live Preview Block
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(parsedColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                )
            }

            // 2D Saturation / Value Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
            ) {
                val baseHueColor = hsvToColor(hue, 1f, 1f)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.matchParentSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                syncFromHsv()
                            }
                        },
                ) {
                    drawRect(color = baseHueColor, size = size)
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)),
                        size = size,
                    )
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                        size = size,
                    )
                    val thumbX = saturation * size.width
                    val thumbY = (1f - value) * size.height
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(thumbX, thumbY),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }

            // Hue Rainbow Slider
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
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.matchParentSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                                syncFromHsv()
                            }
                        },
                ) {
                    drawRect(brush = Brush.horizontalGradient(rainbowColors), size = size)
                    val thumbX = (hue / 360f) * size.width
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(thumbX, size.height / 2f),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }

            // Quick Select Curated Swatches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Curated Palette",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    curatedColors.forEach { (_, hex) ->
                        val swatchColor = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(hex, Color.Gray)
                        val isPicked = currentHexInput.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(swatchColor)
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { syncFromHex(hex) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPicked) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { syncFromHex(defaultHex) },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset to Default")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val finalHex = if (currentHexInput.startsWith("#")) currentHexInput else "#$currentHexInput"
                            onColorConfirmed(finalHex)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Apply & Save")
                    }
                }
            }
        }
    }
}
