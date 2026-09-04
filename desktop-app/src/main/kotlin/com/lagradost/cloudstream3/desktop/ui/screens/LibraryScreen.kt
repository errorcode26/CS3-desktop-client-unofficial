package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.library.LibraryViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.library.components.BookmarkCard
import com.lagradost.cloudstream3.desktop.ui.screens.library.components.LibraryActionBar
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.dialogs.LibraryRecoveryDialog
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
