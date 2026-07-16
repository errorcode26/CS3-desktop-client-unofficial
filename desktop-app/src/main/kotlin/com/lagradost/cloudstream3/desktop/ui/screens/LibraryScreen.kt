package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeLibraryScreen(navController: NavController) {
    // We use a mutable state list so the UI updates when we remove a bookmark
    val bookmarksState = remember { mutableStateListOf<DesktopBookmark>() }

    // Load initial bookmarks
    LaunchedEffect(Unit) {
        bookmarksState.clear()
        bookmarksState.addAll(DesktopDataStore.getBookmarks())
    }

    var showError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (bookmarksState.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Your library is empty.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Go bookmark some shows!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { navController.navigate(Screen.Home) }) {
                    Text("Browse Shows")
                }
            }
        } else {
            val gridScale by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.gridScale.collectAsState()
            val minSize = when (gridScale) {
                "Compact" -> 120.dp
                "Large" -> 180.dp
                else -> 150.dp
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minSize),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(bookmarksState) { bookmark ->
                    BookmarkCard(
                        bookmark = bookmark,
                        onClick = {
                            val provider = APIHolder.getApiFromNameNull(bookmark.apiName)
                            if (provider != null) {
                                navController.navigate(Screen.Details(provider, bookmark.url))
                            } else {
                                showError = "The provider '${bookmark.apiName}' is not loaded. Please install or enable it first."
                            }
                        },
                        onDelete = {
                            DesktopDataStore.removeBookmark(bookmark.id)
                            bookmarksState.remove(bookmark)
                        },
                    )
                }
            }
        }
    }

    if (showError != null) {
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("Provider Missing") },
            text = { Text(showError!!) },
            confirmButton = {
                Button(onClick = { showError = null }) { Text("OK") }
            },
        )
    }
}

@Composable
fun BookmarkCard(bookmark: DesktopBookmark, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Poster
                if (bookmark.posterUrl != null) {
                    AsyncImage(
                        model = bookmark.posterUrl,
                        contentDescription = bookmark.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No Poster", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Title & Provider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = bookmark.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = bookmark.apiName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Delete Button (Top Right over Poster)
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Bookmark",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
