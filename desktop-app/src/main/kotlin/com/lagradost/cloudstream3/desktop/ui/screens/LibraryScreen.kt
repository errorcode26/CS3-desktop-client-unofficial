package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.library.LibraryViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.SortOption
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeLibraryScreen(
    onNavigate: (Config) -> Unit,
    viewModel: LibraryViewModel,
) {
    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is LibraryUiEffect.Navigate -> {
                    onNavigate(effect.screen)
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val bookmarksList = uiState.bookmarks
    val filteredBookmarks = uiState.filteredBookmarks
    val selectedTab = uiState.selectedTab
    val showError = uiState.showError
    val posterWidthDp = uiState.posterWidthDp

    Box(modifier = Modifier.fillMaxSize()) {
        if (bookmarksList.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Your library is empty",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Button(onClick = { onNavigate(Config.Home) }) {
                    Text("Browse Shows")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DesktopWatchType.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { viewModel.onEvent(LibraryUiEvent.OnSelectTab(tab)) },
                            label = {
                                Text(
                                    text = tab.stringRes,
                                    fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }

                LibraryActionBar(
                    uiState = uiState,
                    onSearch = { query -> viewModel.onEvent(LibraryUiEvent.OnSearchQueryChange(query)) },
                    onSortChange = { sort -> viewModel.onEvent(LibraryUiEvent.OnSortOptionChange(sort)) },
                    onProviderChange = { provider -> viewModel.onEvent(LibraryUiEvent.OnProviderFilterChange(provider)) },
                )

                if (filteredBookmarks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "No bookmarks in ${selectedTab.stringRes}.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    val minSize = posterWidthDp.dp

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minSize),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        items(filteredBookmarks) { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                onClick = {
                                    viewModel.onEvent(LibraryUiEvent.OnBookmarkClick(bookmark.apiName, bookmark.url))
                                },
                                onDelete = {
                                    viewModel.onEvent(LibraryUiEvent.OnDeleteBookmark(bookmark.id))
                                },
                            )
                        }
                    }
                }
            }
        }

        com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
            show = showError != null,
            onDismissRequest = { viewModel.onEvent(LibraryUiEvent.OnDismissError) },
            title = { Text("Provider Missing") },
            text = { Text(showError ?: "") },
            confirmButton = {
                Button(onClick = { viewModel.onEvent(LibraryUiEvent.OnDismissError) }) { Text("OK") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryActionBar(
    uiState: LibraryUiState,
    onSearch: (String) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onProviderChange: (String?) -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearch,
            placeholder = { Text("Search library...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.weight(1f).height(52.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )

        // Provider Filter
        Box {
            OutlinedButton(
                onClick = { providerExpanded = true },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(uiState.selectedProvider ?: "All Providers", maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("All Providers") },
                    onClick = {
                        onProviderChange(null)
                        providerExpanded = false
                    },
                )
                uiState.availableProviders.forEach { prov ->
                    DropdownMenuItem(
                        text = { Text(prov) },
                        onClick = {
                            onProviderChange(prov)
                            providerExpanded = false
                        },
                    )
                }
            }
        }

        // Sort Filter
        Box {
            OutlinedButton(
                onClick = { sortExpanded = true },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(uiState.sortOption.title, maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.title) },
                        onClick = {
                            onSortChange(option)
                            sortExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkCard(bookmark: DesktopBookmark, onClick: () -> Unit, onDelete: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val posterCornerRadius by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.posterRoundingDp.collectAsState()
    val shape = RoundedCornerShape(posterCornerRadius.dp)
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isHovered) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(primary.copy(alpha = 0.65f), shape),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .posterHoverEffect(shape)
                .clip(shape)
                .hoverable(interactionSource)
                .clickable { onClick() },
            shape = shape,
            color = DesktopUi.SurfaceCard,
            tonalElevation = if (isHovered) 8.dp else 2.dp,
            border = if (isHovered) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            ) {
                // Poster
                if (bookmark.posterUrl != null) {
                    AsyncImage(
                        model = bookmark.posterUrl,
                        contentDescription = bookmark.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DesktopUi.SurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            bookmark.name.take(2).uppercase(),
                            color = DesktopUi.Accent,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                }

                // Play button on hover
                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp).padding(start = 2.dp),
                        )
                    }
                }

                // Gradient at the bottom with the title
                AnimatedVisibility(
                    visible = isHovered,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.35f to Color.Black.copy(alpha = 0.7f),
                                        1f to Color.Black.copy(alpha = 0.92f),
                                    ),
                                ),
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Column {
                            // Provider Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = bookmark.apiName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = bookmark.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                // Delete Button (Top Right over Poster)
                AnimatedVisibility(
                    visible = isHovered,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Bookmark",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
