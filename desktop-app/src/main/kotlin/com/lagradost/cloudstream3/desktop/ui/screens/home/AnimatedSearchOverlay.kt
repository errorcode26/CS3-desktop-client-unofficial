package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.DesktopUiState
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.delay

@Composable
fun AnimatedSearchOverlay(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: androidx.compose.foundation.text.KeyboardActionScope.() -> Unit,
    onClose: () -> Unit,
    isSearchActive: Boolean = false,
    providers: List<MainAPI>,
    selectedProvider: MainAPI?,
    onProviderSelected: (String) -> Unit,
    mergedPluginIcons: Map<String, String>,
) {
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val searchTrigger by DesktopUiState.searchFocusTrigger.collectAsState()

    val searchBarMode by AppearanceConfig.searchBarMode.collectAsState()
    val isForced by DesktopUiState.forceShowSearchBar.collectAsState()

    val isVisible = searchBarMode == "Always Visible" || isForced || isSearchActive

    LaunchedEffect(searchTrigger) {
        if (searchTrigger > 0) {
            delay(100) // allow animation to start
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {}
            DesktopUiState.searchFocusTrigger.value = 0
        }
    }

    fun fuzzyMatchIcon(providerName: String): String? {
        val pName = providerName.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
        return mergedPluginIcons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
            if (kName.length < 3) return@firstOrNull false
            pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
    }

    var textFieldValue by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = searchQuery,
                selection = androidx.compose.ui.text.TextRange(searchQuery.length),
            ),
        )
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = searchQuery,
                selection = androidx.compose.ui.text.TextRange(searchQuery.length),
            )
        }
    }

    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val topPadding = if (dockPosition == "Top") 84.dp else 16.dp

    Box(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(200)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)),
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .wrapContentHeight(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                "Search titles, shows, anime...",
                                color = DesktopUi.TextMuted,
                                fontSize = 15.sp,
                            )
                        }
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                onSearchQueryChange(it.text)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = onSearch),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }

                    if (searchBarMode != "Always Visible") {
                        if (textFieldValue.text.isNotEmpty()) {
                            // Text is present — clear it
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = DesktopUi.TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            // Field is empty — close the search entirely
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
