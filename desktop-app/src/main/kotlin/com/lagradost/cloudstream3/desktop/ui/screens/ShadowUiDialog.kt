package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.shadowui.ShadowDialog
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog

/**
 * Root host for plugin-driven Android shadow dialogs on Desktop.
 *
 * Observes [ShadowUi.dialogs] and renders any dialog opened by plugins via
 * real Android APIs (AlertDialog, DialogFragment, LinearLayout, Switch, etc.)
 * using Cloudstream's native Material3 + Amoled CustomDialog system.
 */
@Composable
fun ShadowUiHost() {
    val dialogs by ShadowUi.dialogs.collectAsState()
    val version by ShadowUi.version.collectAsState()
    val toast by ShadowUi.toast.collectAsState()

    // Render the topmost dialog in the stack
    dialogs.lastOrNull()?.let { dialog ->
        key(version, dialog.id) {
            ShadowDialogFrame(dialog)
        }
    }

    // Transient toast notification overlay
    toast?.let { event ->
        val visible = remember(event.at) { mutableStateOf(true) }
        LaunchedEffect(event.at) {
            kotlinx.coroutines.delay(2200)
            visible.value = false
        }
        if (visible.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            ) {
                Surface(
                    color = Color(0xE61A1A24),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = event.text,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShadowDialogFrame(shadow: ShadowDialog) {
    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = {
            val platform = shadow.platform
            ShadowUi.dispatch("dialog-dismiss") {
                when (platform) {
                    is android.app.Dialog -> platform.cancel()
                    is androidx.fragment.app.DialogFragment -> platform.dismiss()
                    is androidx.fragment.app.Fragment -> (platform as? androidx.fragment.app.DialogFragment)?.dismiss()
                }
            }
        },
        modifier = Modifier
            .widthIn(min = 550.dp, max = 680.dp)
            .heightIn(max = 800.dp),
    ) {
        when (val platform = shadow.platform) {
            is androidx.fragment.app.Fragment -> FragmentBody(shadow, platform)
            is android.app.Dialog -> PlatformDialogBody(platform, platform.contentView ?: shadow.view)
            else -> {
                shadow.view?.let {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ShadowNode(it, 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FragmentBody(shadow: ShadowDialog, fragment: androidx.fragment.app.Fragment) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = shadow.fragmentTag ?: "Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = {
                    ShadowUi.dispatch("fragment-dismiss") {
                        (fragment as? androidx.fragment.app.DialogFragment)?.dismiss()
                    }
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(20.dp),
        ) {
            shadow.view?.let {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ShadowNode(it, 1)
                }
            }
        }
    }
}

@Composable
private fun PlatformDialogBody(dialog: android.app.Dialog, view: android.view.View?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        dialog.title?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        dialog.message?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 580.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                view?.let { ShadowNode(it, 1) }

                dialog.items?.let { items ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        items.forEachIndexed { index, label ->
                            val selected = dialog.checkedItem == index
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        ShadowUi.dispatch("dialog-item") {
                                            if (dialog.isSingleChoice) {
                                                dialog.checkedItem = index
                                                dialog.itemsListener?.onClick(dialog, index)
                                            } else if (dialog.isMultiChoice) {
                                                dialog.multiChoiceListener?.onClick(dialog, index, true)
                                            } else {
                                                dialog.itemsListener?.onClick(dialog, index)
                                                dialog.dismiss()
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                            ) {
                                if (dialog.isSingleChoice) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                } else if (dialog.isMultiChoice) {
                                    Checkbox(
                                        checked = dialog.checkedItems?.getOrNull(index) == true,
                                        onCheckedChange = null,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                }
                                Text(
                                    text = label.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        ShadowDialogButtons(
            positiveText = dialog.positiveText,
            negativeText = dialog.negativeText,
            neutralText = dialog.neutralText,
            onPositive = {
                ShadowUi.dispatch("dialog-positive") {
                    dialog.positiveListener?.onClick(dialog, -1)
                }
            },
            onNegative = {
                ShadowUi.dispatch("dialog-negative") {
                    dialog.negativeListener?.onClick(dialog, -2)
                }
            },
            onNeutral = {
                ShadowUi.dispatch("dialog-neutral") {
                    dialog.neutralListener?.onClick(dialog, -3)
                }
            },
        )
    }
}

@Composable
private fun ShadowDialogButtons(
    positiveText: CharSequence?,
    negativeText: CharSequence?,
    neutralText: CharSequence?,
    onPositive: () -> Unit,
    onNegative: () -> Unit,
    onNeutral: () -> Unit,
) {
    if (positiveText == null && negativeText == null && neutralText == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        neutralText?.let {
            TextButton(onClick = onNeutral) {
                Text(it.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
        }
        negativeText?.let {
            OutlinedButton(
                onClick = onNegative,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(it.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
        }
        positiveText?.let {
            Button(
                onClick = onPositive,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(it.toString(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── View Tree Recursive Renderer ────────────────────────────────────────────

private fun Int.pxToDp() = (this / 2f).dp
private fun Int.toComposeColor() = Color(this)

@Composable
private fun ShadowNode(view: android.view.View, depth: Int) {
    if (view.visibility == android.view.View.GONE) return
    if (depth > 24) return

    val marginParams = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
    val verticalMargin = ((marginParams?.topMargin ?: 0) + (marginParams?.bottomMargin ?: 0)).pxToDp()
    val horizontalMargin = ((marginParams?.leftMargin ?: 0) + (marginParams?.rightMargin ?: 0)).pxToDp()

    Box(modifier = Modifier.padding(horizontal = horizontalMargin / 2, vertical = verticalMargin / 2)) {
        when (view) {
            is android.widget.ListView -> ListNode(view)
            is android.widget.ScrollView -> ScrollNode(view)
            is android.widget.LinearLayout -> LinearLayoutNode(view, depth)
            is android.widget.RadioGroup -> LinearLayoutNode(view, depth)
            is android.widget.RelativeLayout -> ViewGroupColumnNode(view, depth)
            is android.widget.EditText -> EditTextNode(view)
            is android.widget.Switch -> SwitchNode(view)
            is android.widget.CheckBox -> CheckBoxNode(view)
            is android.widget.RadioButton -> RadioButtonNode(view)
            is android.widget.Button -> ButtonNode(view)
            is android.widget.ImageButton -> IconButtonNode(view)
            is android.widget.ImageView -> ImageNode(view)
            is android.widget.ProgressBar -> ProgressNode(view)
            is android.widget.TextView -> TextNode(view)
            is android.widget.Space -> Spacer(Modifier.height(((view.layoutParams?.height ?: 16) / 2f).dp.coerceAtLeast(4.dp)))
            is android.view.ViewGroup -> ViewGroupColumnNode(view, depth)
            else -> PlainViewNode(view)
        }
    }
}

@Composable
private fun LinearLayoutNode(layout: android.widget.LinearLayout, depth: Int) {
    if (layout.orientation == android.widget.LinearLayout.HORIZONTAL) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            layout.children.forEach { child ->
                Box(Modifier.weight(1f, fill = false)) {
                    ShadowNode(child, depth + 1)
                }
            }
        }
    } else {
        ViewGroupColumnNode(layout, depth)
    }
}

@Composable
private fun ViewGroupColumnNode(group: android.view.ViewGroup, depth: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        group.children.forEach { child ->
            ShadowNode(child, depth + 1)
        }
    }
}

@Composable
private fun ScrollNode(scroll: android.widget.ScrollView) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        scroll.children.forEach { ShadowNode(it, 2) }
    }
}

@Composable
private fun ListNode(list: android.widget.ListView) {
    val adapter = list.adapter
    Column(Modifier.fillMaxWidth()) {
        if (adapter != null) {
            repeat(adapter.getCount()) { position ->
                val row = runCatching { adapter.getView(position, null, list) }.getOrNull()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            ShadowUi.dispatch("list-item") { list.performItemClick(position) }
                        }
                        .padding(vertical = 6.dp),
                ) {
                    row?.let { ShadowNode(it, 3) }
                }
            }
        }
    }
}

@Composable
private fun TextNode(view: android.widget.TextView) {
    val text = view.text.toString().let { if (view.allCaps) it.uppercase() else it }
    val textColor = if (view.textColor != 0) view.textColor.toComposeColor() else MaterialTheme.colorScheme.onSurface

    if (view.clickListener != null) {
        Surface(
            onClick = { ShadowUi.dispatch("text-click") { view.performClick() } },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextViewStyle(text, view, textColor)
        }
    } else {
        TextViewStyle(text, view, textColor)
    }
}

@Composable
private fun TextViewStyle(text: String, view: android.widget.TextView, textColor: Color) {
    Text(
        text = text,
        color = textColor,
        fontSize = (view.textSizePx / 2f).coerceIn(10f, 28f).sp,
        fontWeight = if (view.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (view.italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = if ((view.gravity and 0x7) == 0x1) TextAlign.Center else null,
        maxLines = if (view.maxLines > 0) view.maxLines else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(
            start = view.paddingLeft.pxToDp(),
            top = view.paddingTop.pxToDp(),
            end = view.paddingRight.pxToDp(),
            bottom = view.paddingBottom.pxToDp(),
        ),
    )
}

@Composable
private fun ButtonNode(view: android.widget.Button) {
    Button(
        onClick = { ShadowUi.dispatch("button-click") { view.performClick() } },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = view.paddingLeft.pxToDp(),
                top = view.paddingTop.pxToDp(),
                end = view.paddingRight.pxToDp(),
                bottom = view.paddingBottom.pxToDp(),
            ),
    ) {
        Text(
            text = view.text.toString().let { if (view.allCaps) it.uppercase() else it },
            fontSize = (view.textSizePx / 2f).coerceIn(11f, 18f).sp,
            fontWeight = if (view.bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun EditTextNode(view: android.widget.EditText) {
    val isPassword = (view.inputType and 0x81) == 0x81 || (view.inputType and 128) == 128
    val isNumber = (view.inputType and 2) == 2

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (!view.hint.isNullOrEmpty() && view.text.isEmpty()) {
            Text(
                text = view.hint.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        OutlinedTextField(
            value = view.text.toString(),
            onValueChange = { newValue ->
                ShadowUi.dispatch("edit-text") {
                    view.programmaticText(newValue)
                }
            },
            singleLine = view.singleLine,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isNumber) KeyboardType.Number else KeyboardType.Text),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchNode(view: android.widget.Switch) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                ShadowUi.dispatch("switch-toggle") { view.toggle() }
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (view.text.isNotEmpty()) {
                Text(
                    text = view.text.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = view.isChecked,
            onCheckedChange = { checked ->
                ShadowUi.dispatch("switch-change") { view.isChecked = checked }
            },
        )
    }
}

@Composable
private fun CheckBoxNode(view: android.widget.CheckBox) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                ShadowUi.dispatch("checkbox-toggle") { view.toggle() }
            }
            .padding(vertical = 6.dp),
    ) {
        Checkbox(
            checked = view.isChecked,
            onCheckedChange = { checked ->
                ShadowUi.dispatch("checkbox-change") { view.isChecked = checked }
            },
        )
        if (view.text.isNotEmpty()) {
            Text(
                text = view.text.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun RadioButtonNode(view: android.widget.RadioButton) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                ShadowUi.dispatch("radio-toggle") { view.isChecked = true }
            }
            .padding(vertical = 4.dp),
    ) {
        RadioButton(
            selected = view.isChecked,
            onClick = {
                ShadowUi.dispatch("radio-select") { view.isChecked = true }
            },
        )
        if (view.text.isNotEmpty()) {
            Text(
                text = view.text.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun IconButtonNode(view: android.widget.ImageButton) {
    IconButton(
        onClick = { ShadowUi.dispatch("icon-button") { view.performClick() } },
        modifier = Modifier.padding(4.dp),
    ) {
        Text("⚙", fontSize = 18.sp)
    }
}

@Composable
private fun ImageNode(view: android.widget.ImageView) {
    Box(Modifier.size(48.dp))
}

@Composable
private fun ProgressNode(view: android.widget.ProgressBar) {
    if (view.isIndeterminate) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    } else {
        val fraction = if (view.max > 0) (view.progress.toFloat() / view.max).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun PlainViewNode(view: android.view.View) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (view.clickListener != null) {
                    Modifier.clickable { ShadowUi.dispatch("plain-view-click") { view.performClick() } }
                } else Modifier,
            ),
    )
}
