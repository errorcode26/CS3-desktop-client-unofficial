package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.DesktopUiState
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
// Haze removed for performance
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: androidx.compose.foundation.text.KeyboardActionScope.() -> Unit,
    providers: List<MainAPI>,
    selectedProvider: MainAPI?,
    onProviderSelected: (String) -> Unit,
    mergedPluginIcons: Map<String, String>,
    onProviderSelectClick: (() -> Unit)? = null,
) {
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val searchTrigger by DesktopUiState.searchFocusTrigger.collectAsState()

    LaunchedEffect(searchTrigger) {
        if (searchTrigger > 0) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors if not attached yet
            }
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
                selection = androidx.compose.ui.text.TextRange(searchQuery.length)
            )
        )
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = searchQuery,
                selection = androidx.compose.ui.text.TextRange(searchQuery.length)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, bottom = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = DesktopUi.SurfaceElevated.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.5f)),
                    modifier = Modifier.widthIn(max = 480.dp).height(52.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp),
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                onSearchQueryChange(it.text)
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = DesktopUi.TextPrimary, fontSize = 15.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(DesktopUi.TextPrimary),
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = onSearch),
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text("Search movies or series", color = DesktopUi.TextMuted, fontSize = 15.sp)
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        IconButton(onClick = {
                            if (textFieldValue.text.isNotBlank()) onSearchQueryChange("")
                        }) {
                            Icon(
                                imageVector = if (textFieldValue.text.isNotBlank()) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = DesktopUi.TextMuted,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Provider Selector Button
            Box {
                Surface(
                    onClick = {
                        if (onProviderSelectClick != null) {
                            onProviderSelectClick()
                        } else {
                            isProviderDropdownExpanded = true
                        }
                    },
                    shape = CircleShape,
                    color = DesktopUi.SurfaceElevated.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.5f)),
                    modifier = Modifier.height(52.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        val pluginIcon = selectedProvider?.let { p -> mergedPluginIcons[p.name] ?: fuzzyMatchIcon(p.name) }
                        if (pluginIcon != null) {
                            AsyncImage(
                                model = pluginIcon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = selectedProvider?.name ?: "Select Provider",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }

            }
        }
    }
}

@Composable
fun ProviderSelectionOverlay(
    providers: List<MainAPI>,
    selectedProvider: MainAPI?,
    onProviderSelected: (String) -> Unit,
    mergedPluginIcons: Map<String, String>,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DesktopUi.SurfaceElevated,
            border = BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.5f)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .width(520.dp)
                .heightIn(max = 600.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume clicks inside the dialog
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    "Select Provider",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                if (providers.isEmpty()) {
                    Text("No providers installed", color = MaterialTheme.colorScheme.onSurface)
                } else {
                    var selectedCategory by remember { mutableStateOf("All") }
                    val categories = remember(providers) {
                        val allTypes = providers.flatMap { it.supportedTypes }.toSet()
                        val cats = mutableListOf("All")
                        if (allTypes.contains(com.lagradost.cloudstream3.TvType.Movie) || allTypes.contains(com.lagradost.cloudstream3.TvType.TvSeries)) cats.add("Movies")
                        if (allTypes.contains(com.lagradost.cloudstream3.TvType.Anime) || allTypes.contains(com.lagradost.cloudstream3.TvType.AnimeMovie) || allTypes.contains(com.lagradost.cloudstream3.TvType.OVA)) cats.add("Anime")
                        if (allTypes.contains(com.lagradost.cloudstream3.TvType.Cartoon)) cats.add("Cartoon")
                        if (allTypes.contains(com.lagradost.cloudstream3.TvType.AsianDrama)) cats.add("Asian Drama")
                        if (allTypes.contains(com.lagradost.cloudstream3.TvType.NSFW)) cats.add("NSFW")
                        cats
                    }

                    if (categories.size > 1) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            categories.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier.clickable { selectedCategory = cat },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = cat,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }

                    val filteredProviders = providers.filter { p ->
                        if (selectedCategory == "All") return@filter true
                        val types = p.supportedTypes
                        when (selectedCategory) {
                            "Movies" -> types.contains(com.lagradost.cloudstream3.TvType.Movie) || types.contains(com.lagradost.cloudstream3.TvType.TvSeries)
                            "Anime" -> types.contains(com.lagradost.cloudstream3.TvType.Anime) || types.contains(com.lagradost.cloudstream3.TvType.AnimeMovie) || types.contains(com.lagradost.cloudstream3.TvType.OVA)
                            "Cartoon" -> types.contains(com.lagradost.cloudstream3.TvType.Cartoon)
                            "Asian Drama" -> types.contains(com.lagradost.cloudstream3.TvType.AsianDrama)
                            "NSFW" -> types.contains(com.lagradost.cloudstream3.TvType.NSFW)
                            else -> true
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                    ) {
                        items(filteredProviders.size) { index ->
                            val provider = filteredProviders[index]
                            val iconUrl = mergedPluginIcons[provider.name] // Fix: Use correct fuzzy match if needed, but passing mergedPluginIcons is enough
                            val isProviderSelected = provider.name == selectedProvider?.name

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onProviderSelected(provider.name)
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp),
                            ) {
                                RadioButton(
                                    selected = isProviderSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = DesktopUi.Accent,
                                        unselectedColor = Color.White.copy(alpha = 0.5f),
                                    ),
                                    modifier = Modifier.padding(end = 12.dp).size(20.dp),
                                )

                                if (iconUrl != null) {
                                    AsyncImage(
                                        model = iconUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Text(
                                    provider.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }

                    if (filteredProviders.isEmpty()) {
                        Text(
                            text = "No providers in this category",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
