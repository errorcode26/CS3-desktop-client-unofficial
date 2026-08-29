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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.library.LibraryViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.SortOption
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType

private val CARD_TITLE_SCRIM_BRUSH = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color.Transparent,
        0.35f to Color.Black.copy(alpha = 0.7f),
        1f to Color.Black.copy(alpha = 0.92f),
    ),
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    val posterWidthDp = uiState.posterWidthDp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp

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
                        .padding(horizontal = if (isCompact) 8.dp else 16.dp, vertical = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DesktopWatchType.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { viewModel.onEvent(LibraryUiEvent.OnSelectTab(tab)) },
                            label = {
                                Text(
                                    tab.stringRes,
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
                    isCompact = isCompact,
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
                    val minSize = if (isCompact) 105.dp else posterWidthDp.dp

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minSize),
                        contentPadding = PaddingValues(horizontal = if (isCompact) 6.dp else 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 16.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        items(filteredBookmarks, key = { it.id }) { bookmark ->
                            val provider = APIHolder.allProviders.firstOrNull {
                                it.name == bookmark.apiName && it.mainUrl.isNotBlank() && bookmark.url.startsWith(it.mainUrl)
                            } ?: APIHolder.getApiFromNameNull(bookmark.apiName)

                            BookmarkCard(
                                bookmark = bookmark,
                                isProviderMissing = bookmark.apiName !in uiState.installedProviderNames,
                                onClick = {
                                    viewModel.onEvent(LibraryUiEvent.OnBookmarkClick(bookmark))
                                },
                                onSecondaryClick = { bounds ->
                                    GlobalContextMenuState.showForBookmark(
                                        bounds = bounds,
                                        bookmark = bookmark,
                                        provider = provider,
                                        onClick = {
                                            viewModel.onEvent(LibraryUiEvent.OnBookmarkClick(bookmark))
                                        },
                                        onPlayClick = {
                                            viewModel.onEvent(LibraryUiEvent.OnBookmarkClick(bookmark))
                                        },
                                        onRemove = {
                                            viewModel.onEvent(LibraryUiEvent.OnDeleteBookmark(bookmark.id))
                                        },
                                        onChangeCategory = { newType ->
                                            viewModel.onEvent(LibraryUiEvent.OnChangeWatchType(bookmark.id, newType))
                                        },
                                        onReLink = {
                                            viewModel.onEvent(LibraryUiEvent.OnStartReLink(bookmark))
                                        },
                                        onSearchOtherProviders = {
                                            viewModel.onEvent(LibraryUiEvent.OnSearchGlobal(bookmark.name))
                                        },
                                    )
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

        uiState.orphanRecoveryBookmark?.let { orphan ->
            LibraryRecoveryDialog(
                bookmark = orphan,
                isSearching = uiState.isSearchingMatches,
                matchedResults = uiState.matchedResults,
                onDismiss = { viewModel.onEvent(LibraryUiEvent.OnDismissRecoveryModal) },
                onSelectMatch = { prov, match ->
                    viewModel.onEvent(LibraryUiEvent.OnSelectReLinkMatch(orphan, prov, match))
                },
                onSearchGlobal = {
                    viewModel.onEvent(LibraryUiEvent.OnSearchGlobal(orphan.name))
                },
                onDelete = {
                    viewModel.onEvent(LibraryUiEvent.OnDeleteBookmark(orphan.id))
                    viewModel.onEvent(LibraryUiEvent.OnDismissRecoveryModal)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryActionBar(
    uiState: LibraryUiState,
    isCompact: Boolean = false,
    onSearch: (String) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onProviderChange: (String?) -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    if (isCompact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearch,
                placeholder = { Text("Search library...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val selIcon = remember(uiState.selectedProvider) {
                        DesktopRepositoryManager.getPluginIcon(uiState.selectedProvider)
                    }
                    OutlinedButton(
                        onClick = { providerExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        if (selIcon != null) {
                            AsyncImage(
                                model = selIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).clip(CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(uiState.selectedProvider ?: "All Providers", maxLines = 1, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false), overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("All Providers", fontWeight = if (uiState.selectedProvider == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onProviderChange(null)
                                providerExpanded = false
                            },
                        )
                        uiState.availableProviders.forEach { prov ->
                            val provIcon = DesktopRepositoryManager.getPluginIcon(prov)
                            DropdownMenuItem(
                                leadingIcon = {
                                    if (provIcon != null) {
                                        AsyncImage(
                                            model = provIcon,
                                            contentDescription = prov,
                                            modifier = Modifier.size(20.dp).clip(CircleShape),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = prov.take(1).uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                },
                                text = { Text(prov, fontWeight = if (uiState.selectedProvider == prov) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onProviderChange(prov)
                                    providerExpanded = false
                                },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(uiState.sortOption.title, maxLines = 1, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false), overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
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
    } else {
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

            Box {
                OutlinedButton(
                    onClick = { sortExpanded = true },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
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
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BookmarkCard(
    bookmark: DesktopBookmark,
    isProviderMissing: Boolean,
    onClick: () -> Unit,
    onSecondaryClick: (Rect) -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val posterCornerRadius by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.posterRoundingDp.collectAsState()
    val shape = remember(posterCornerRadius) { RoundedCornerShape(posterCornerRadius.dp) }
    val primary = MaterialTheme.colorScheme.primary
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isHovered) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(if (isProviderMissing) Color(0xFFE65100).copy(alpha = 0.65f) else primary.copy(alpha = 0.65f), shape),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .posterHoverEffect(shape)
                .clip(shape)
                .hoverable(interactionSource)
                .onGloballyPositioned { coords ->
                    bounds = coords.boundsInWindow()
                }
                .pointerInput(bookmark) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                if (event.button == PointerButton.Secondary) {
                                    onSecondaryClick(bounds)
                                } else if (event.button == PointerButton.Primary) {
                                    onClick()
                                }
                            }
                        }
                    }
                },
            shape = shape,
            color = DesktopUi.SurfaceCard,
            tonalElevation = if (isHovered) 8.dp else 2.dp,
            border = if (isHovered) androidx.compose.foundation.BorderStroke(2.dp, if (isProviderMissing) Color(0xFFFFA726) else MaterialTheme.colorScheme.primary) else null,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            ) {
                if (bookmark.posterUrl != null) {
                    val enhancedPoster = remember(bookmark.posterUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(bookmark.posterUrl) }
                    AsyncImage(
                        model = enhancedPoster,
                        contentDescription = bookmark.name,
                        contentScale = ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
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
                            .background(if (isProviderMissing) Color(0xFFE65100).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f))
                            .border(1.dp, if (isProviderMissing) Color(0xFFFFA726) else Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isProviderMissing) Icons.Default.Sync else Icons.Default.PlayArrow,
                            contentDescription = if (isProviderMissing) "Re-link" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isHovered || isProviderMissing,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CARD_TITLE_SCRIM_BRUSH)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Column {
                            if (isProviderMissing) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE65100).copy(alpha = 0.90f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                        Text(
                                            text = "${bookmark.apiName} (Missing)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                }
                            } else {
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
            }
        }
    }
}

@Composable
fun LibraryRecoveryDialog(
    bookmark: DesktopBookmark,
    isSearching: Boolean,
    matchedResults: List<Pair<MainAPI, SearchResponse>>,
    onDismiss: () -> Unit,
    onSelectMatch: (MainAPI, SearchResponse) -> Unit,
    onSearchGlobal: () -> Unit,
    onDelete: () -> Unit,
) {
    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.82f).fillMaxHeight(0.80f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE65100).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFA726).copy(alpha = 0.5f)),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFFA726),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Provider Not Available",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "\"${bookmark.name}\" was saved via \"${bookmark.apiName}\", which is currently not installed or active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (isSearching) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Searching active providers for \"${bookmark.name}\"...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (matchedResults.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Found ${matchedResults.size} matches on your active providers. Click any match to re-link this bookmark:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(matchedResults) { (prov, match) ->
                                Surface(
                                    onClick = { onSelectMatch(prov, match) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(2f / 3f)
                                                .clip(RoundedCornerShape(8.dp)),
                                        ) {
                                            if (match.posterUrl != null) {
                                                val enhancedMatchPoster = remember(match.posterUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(match.posterUrl) }
                                                AsyncImage(
                                                    model = enhancedMatchPoster,
                                                    contentDescription = match.name,
                                                    contentScale = ContentScale.Crop,
                                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().background(DesktopUi.SurfaceElevated),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(match.name.take(2).uppercase(), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = match.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        ) {
                                            Text(
                                                text = prov.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "No automatic matches found on your active providers.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = "You can search manually across providers or remove this orphaned item.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSearchGlobal,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Search Providers")
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove Bookmark")
                    }
                }

                OutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
