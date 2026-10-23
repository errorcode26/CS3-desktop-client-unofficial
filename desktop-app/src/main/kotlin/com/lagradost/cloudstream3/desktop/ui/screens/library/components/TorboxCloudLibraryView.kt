@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class) /*
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

*/ package com.lagradost.cloudstream3.desktop.ui.screens.library.components /*

@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class) /*
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

*/ */ import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxFile
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxLibraryCache
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxStorageType
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxTorrentData
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun copyToClipboard(text: String) {
    try {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    } catch (e: Exception) {
        AppLogger.e("TorboxCloudLibraryView: Failed to copy to clipboard", e)
    }
}

private val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".mov", ".webm", ".ts", ".m4v")

private fun isVideoFile(fileName: String): Boolean {
    val lower = fileName.lowercase()
    return VIDEO_EXTENSIONS.any { lower.endsWith(it) } &&
        !lower.contains("sample") &&
        !lower.contains("trailer")
}

private fun cleanItemTitle(raw: String): String {
    var title = raw.trim()
    if (!title.contains(' ') && (title.contains('.') || title.contains('_'))) {
        title = title.replace('.', ' ').replace('_', ' ')
    }
    return title.trim()
}

private fun compareNatural(s1: String, s2: String): Int {
    var i1 = 0
    var i2 = 0
    while (i1 < s1.length && i2 < s2.length) {
        val c1 = s1[i1]
        val c2 = s2[i2]
        if (c1.isDigit() && c2.isDigit()) {
            var j1 = i1
            var j2 = i2
            while (j1 < s1.length && s1[j1].isDigit()) j1++
            while (j2 < s2.length && s2[j2].isDigit()) j2++
            val num1 = s1.substring(i1, j1).toLongOrNull() ?: 0L
            val num2 = s2.substring(i2, j2).toLongOrNull() ?: 0L
            val diff = num1.compareTo(num2)
            if (diff != 0) return diff
            i1 = j1
            i2 = j2
        } else {
            val diff = c1.lowercaseChar().compareTo(c2.lowercaseChar())
            if (diff != 0) return diff
            i1++
            i2++
        }
    }
    return s1.length.compareTo(s2.length)
}

val naturalNameComparator = Comparator<TorboxFile> { a, b ->
    compareNatural(a.name, b.name)
}

private fun extractEpisodeIndex(fileName: String, fallbackIndex: Int): Pair<Int?, Int?> {
    val sRegex = Regex("""(?i)[sS](\d{1,2})[eE](\d{1,3})""")
    val match = sRegex.find(fileName)
    if (match != null) {
        val s = match.groupValues[1].toIntOrNull()
        val e = match.groupValues[2].toIntOrNull()
        return Pair(s ?: 1, e ?: (fallbackIndex + 1))
    }
    val xRegex = Regex("""(?i)(\d{1,2})x(\d{1,3})""")
    val xMatch = xRegex.find(fileName)
    if (xMatch != null) {
        val s = xMatch.groupValues[1].toIntOrNull()
        val e = xMatch.groupValues[2].toIntOrNull()
        return Pair(s ?: 1, e ?: (fallbackIndex + 1))
    }
    val epRegex = Regex("""(?i)(?:ep|episode|e)[._\s-]*(\d{1,3})""")
    val epMatch = epRegex.find(fileName)
    if (epMatch != null) {
        val e = epMatch.groupValues[1].toIntOrNull()
        return Pair(1, e ?: (fallbackIndex + 1))
    }
    return Pair(null, fallbackIndex + 1)
}

@Suppress("DEPRECATION_ERROR")
private fun createTorboxEpisode(file: TorboxFile, index: Int): Episode {
    val (season, epNum) = extractEpisodeIndex(file.name, index)
    return Episode(
        data = file.id.toString(),
        name = file.name,
        season = season,
        episode = epNum,
    )
}

private val TorboxFile.formattedFileSize: String
    get() {
        if (size <= 0L) return ""
        val gb = size.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f GB", gb)
        } else {
            val mb = size.toDouble() / (1024 * 1024)
            String.format(java.util.Locale.US, "%.0f MB", mb)
        }
    }

private enum class TorrentFilter(val label: String) {
    All("All"),
    Completed("Completed"),
    Downloading("Downloading"),
}

@Composable
fun TorboxCloudLibraryView(
    onNavigate: (Config) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val launchPlayer = LocalVideoPlayer.current

    var apiKey by remember {
        mutableStateOf(DesktopDataStore.getKey<String>(DesktopDataStore.PREF_TORBOX_API_KEY)?.trim().orEmpty())
    }

    var selectedStorageType by remember {
        mutableStateOf(TorboxLibraryCache.lastSelectedStorageType)
    }
    var itemsList by remember {
        mutableStateOf(TorboxLibraryCache.get(TorboxLibraryCache.lastSelectedStorageType).orEmpty())
    }
    var isLoading by remember {
        mutableStateOf(TorboxLibraryCache.get(TorboxLibraryCache.lastSelectedStorageType) == null)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(TorrentFilter.All) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newLinkInput by remember { mutableStateOf("") }
    var isAddingItem by remember { mutableStateOf(false) }
    var addItemError by remember { mutableStateOf<String?>(null) }

    var itemToDelete by remember { mutableStateOf<TorboxTorrentData?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    var selectedItemForFiles by remember { mutableStateOf<TorboxTorrentData?>(null) }
    var resolvingItemId by remember { mutableStateOf<Int?>(null) }
    var resolvingFileId by remember { mutableStateOf<Int?>(null) }

    fun refreshItems(force: Boolean = false) {
        if (apiKey.isBlank()) return
        val cached = TorboxLibraryCache.get(selectedStorageType)
        if (!force && cached != null && TorboxLibraryCache.isFresh(selectedStorageType)) {
            itemsList = cached
            isLoading = false
            return
        }
        if (cached == null) {
            isLoading = true
        }
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                val list = TorboxApiClient.listItems(apiKey, selectedStorageType)
                TorboxLibraryCache.set(selectedStorageType, list)
                withContext(Dispatchers.Main) {
                    itemsList = list
                    isLoading = false
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxCloudLibraryView: Failed to load ${selectedStorageType.label}", e)
                withContext(Dispatchers.Main) {
                    errorMessage = e.message ?: "Failed to load ${selectedStorageType.label}"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(apiKey, selectedStorageType) {
        TorboxLibraryCache.lastSelectedStorageType = selectedStorageType
        val cached = TorboxLibraryCache.get(selectedStorageType)
        if (cached != null) {
            itemsList = cached
            isLoading = false
            if (!TorboxLibraryCache.isFresh(selectedStorageType)) {
                refreshItems(force = true)
            }
        } else if (apiKey.isNotBlank()) {
            refreshItems(force = true)
        }
    }

    val historyTick by DesktopDataStore.historyUpdates.collectAsState()

    val activeRecentHistory = remember(historyTick, itemsList, selectedStorageType) {
        val prefix = "torbox_${selectedStorageType.name.lowercase()}_"
        val allHistory = DesktopDataStore.getAllWatchHistory()
        val matching = allHistory.filter { it.parentId.startsWith(prefix) }
            .filter { hist ->
                val itemId = hist.parentId.substringAfter(prefix).toIntOrNull()
                itemsList.any { it.id == itemId }
            }
        matching.groupBy { it.parentId }.values.mapNotNull { histories ->
            histories.maxByOrNull { it.updateTime }
        }.sortedByDescending { it.updateTime }
    }

    fun playItemFile(item: TorboxTorrentData, file: TorboxFile?) {
        if (apiKey.isBlank()) return
        val videoFiles = item.files.filter { isVideoFile(it.name) }.sortedWith(naturalNameComparator)
        val targetFile = file ?: videoFiles.firstOrNull() ?: TorboxApiClient.selectBestVideoFile(item.files)
        val activeFileId = targetFile?.id ?: item.id
        val currentEpisodeId = activeFileId.toString()
        val parentId = "torbox_${item.storageType.name.lowercase()}_${item.id}"

        resolvingItemId = item.id
        resolvingFileId = targetFile?.id

        scope.launch(Dispatchers.IO) {
            try {
                val cached = LinkCache.get(currentEpisodeId)
                val (streamUrl, existingLink) = if (cached != null && cached.links.isNotEmpty()) {
                    Pair(cached.links.first().url, cached.links.first())
                } else {
                    val fetched = TorboxApiClient.requestDownloadLink(apiKey, item, targetFile?.id)
                    Pair(fetched, null)
                }

                withContext(Dispatchers.Main) {
                    resolvingItemId = null
                    resolvingFileId = null

                    if (!streamUrl.isNullOrBlank()) {
                        val videoTitle = targetFile?.name?.takeIf { it.isNotBlank() } ?: item.name
                        val effectiveBytes = targetFile?.size?.takeIf { it > 0L } ?: item.size
                        val sizeStr = if (effectiveBytes > 0L) {
                            val gb = effectiveBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            if (gb >= 1.0) {
                                String.format(java.util.Locale.US, "%.1f GB", gb)
                            } else {
                                String.format(java.util.Locale.US, "%.0f MB", effectiveBytes.toDouble() / (1024.0 * 1024.0))
                            }
                        } else {
                            null
                        }
                        val sizeTag = if (sizeStr != null) " [$sizeStr]" else ""
                        val extLink = existingLink ?: newExtractorLink(
                            source = "Torbox ${item.storageType.label}",
                            name = videoTitle + sizeTag,
                            url = streamUrl,
                            type = if (streamUrl.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                        ) {
                            this.referer = ""
                        }

                        val siblingEpisodes = if (videoFiles.size > 1) {
                            videoFiles.mapIndexed { idx, f -> createTorboxEpisode(f, idx) }
                        } else {
                            emptyList()
                        }

                        val activeEpisodeIndex = if (siblingEpisodes.isNotEmpty()) {
                            val idx = videoFiles.indexOfFirst { it.id == activeFileId }
                            if (idx >= 0) idx + 1 else 1
                        } else {
                            null
                        }

                        TorboxLibraryCache.updateItem(item)
                        if (existingLink == null) {
                            LinkCache.set(currentEpisodeId, listOf(extLink), emptyList())
                        }

                        val pastHistory = DesktopDataStore.getEpisodeWatched(parentId, currentEpisodeId)
                        val startPositionMs = if (pastHistory != null && pastHistory.duration > 0 && pastHistory.position < pastHistory.duration - 15) {
                            pastHistory.position * 1000L
                        } else {
                            0L
                        }

                        launchPlayer(
                            VideoLaunchData(
                                links = listOf(extLink),
                                initialIndex = 0,
                                title = videoTitle,
                                subtitles = emptyList(),
                                startPositionMs = startPositionMs,
                                history = WatchHistory(
                                    parentId = parentId,
                                    showName = item.name,
                                    showUrl = streamUrl,
                                    apiName = "Torbox",
                                    posterUrl = null,
                                    episodeThumbnailUrl = null,
                                    screenshotUrl = null,
                                    episode = activeEpisodeIndex,
                                    season = if (activeEpisodeIndex != null) 1 else null,
                                    episodeId = currentEpisodeId,
                                    position = startPositionMs / 1000L,
                                    duration = pastHistory?.duration ?: 0L,
                                    updateTime = System.currentTimeMillis(),
                                    episodeName = videoTitle,
                                ),
                                episodes = siblingEpisodes,
                                enrichedBackdropUrl = null,
                            ),
                        )
                    } else {
                        errorMessage = "Failed to resolve stream link from Torbox"
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxCloudLibraryView: Error resolving stream", e)
                withContext(Dispatchers.Main) {
                    resolvingItemId = null
                    resolvingFileId = null
                    errorMessage = e.message ?: "Failed to generate stream link"
                }
            }
        }
    }

    fun resumeFromHistory(history: WatchHistory) {
        val prefix = "torbox_${selectedStorageType.name.lowercase()}_"
        val itemId = history.parentId.substringAfter(prefix).toIntOrNull() ?: return
        val item = itemsList.find { it.id == itemId } ?: return
        val file = item.files.find { it.id.toString() == history.episodeId }
        playItemFile(item, file)
    }

    fun removeTorboxHistory(history: WatchHistory) {
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.removeWatchHistory(history.parentId)
        }
    }

    if (apiKey.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Text(
                    text = "Torbox Not Connected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connect your Torbox account in Settings > Accounts to browse, manage, and play your cloud torrent library directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onNavigate(Config.Settings) },
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Accounts Settings")
                }
            }
        }
        return
    }

    val filteredItems = remember(itemsList, searchQuery, selectedFilter) {
        itemsList.filter { item ->
            val matchesQuery = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                TorrentFilter.All -> true
                TorrentFilter.Completed -> item.isCompleted
                TorrentFilter.Downloading -> !item.isCompleted
            }
            matchesQuery && matchesFilter
        }
    }

    val addLabel = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "Add Magnet"
        TorboxStorageType.WEB_DOWNLOADS -> "Add Web Link"
        TorboxStorageType.USENET -> "Add Usenet"
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Storage Type Sub-Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TorboxStorageType.entries.forEach { type ->
                FilterChip(
                    selected = selectedStorageType == type,
                    onClick = {
                        if (selectedStorageType != type) {
                            selectedStorageType = type
                            searchQuery = ""
                        }
                    },
                    label = {
                        Text(
                            type.label,
                            fontWeight = if (selectedStorageType == type) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }

        // Dedicated Torbox Continue Watching Shelf
        if (searchQuery.isBlank() && activeRecentHistory.isNotEmpty()) {
            TorboxRecentWatchedShelf(
                recentItems = activeRecentHistory,
                itemsList = itemsList,
                onResume = ::resumeFromHistory,
                onRemove = ::removeTorboxHistory,
            )
        }

        // Toolbar: Search Field, Filters, Add, Refresh
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search ${selectedStorageType.label.lowercase()}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(52.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TorrentFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                    )
                }
            }

            Button(
                onClick = {
                    newLinkInput = ""
                    addItemError = null
                    showAddDialog = true
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(addLabel)
            }

            IconButton(
                onClick = { refreshItems(force = true) },
                enabled = !isLoading,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage ?: "Error loading ${selectedStorageType.label}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { refreshItems(force = true) }) {
                        Text("Retry")
                    }
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No items match '$searchQuery'" else "Your Torbox ${selectedStorageType.label.lowercase()} storage is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (searchQuery.isBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Click '$addLabel' to add to your cloud storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(filteredItems, key = { "${it.storageType}_${it.id}" }) { item ->
                    val videoFiles = remember(item.files) {
                        item.files.filter { isVideoFile(it.name) }.sortedWith(naturalNameComparator)
                    }
                    TorboxCloudItemCard(
                        item = item,
                        apiKey = apiKey,
                        isCurrentResolving = resolvingItemId == item.id,
                        onCardClick = {
                            if (videoFiles.size > 1) {
                                selectedItemForFiles = item
                            } else {
                                val targetFile = TorboxApiClient.selectBestVideoFile(item.files)
                                playItemFile(item, targetFile)
                            }
                        },
                        onBrowseFilesClick = {
                            selectedItemForFiles = item
                        },
                        onPlayClick = {
                            if (videoFiles.size > 1) {
                                selectedItemForFiles = item
                            } else {
                                val targetFile = TorboxApiClient.selectBestVideoFile(item.files)
                                playItemFile(item, targetFile)
                            }
                        },
                        onResumeEpisode = { hist ->
                            val target = item.files.find { it.id.toString() == hist.episodeId }
                            playItemFile(item, target)
                        },
                        onDeleteClick = {
                            itemToDelete = item
                        },
                    )
                }
            }
        }
    }

    // Add Item Dialog (Dynamically adapts for Torrents, Web Downloads, and Usenet)
    val dialogTitle = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "Add Torrent to Cloud"
        TorboxStorageType.WEB_DOWNLOADS -> "Add Web Download to Cloud"
        TorboxStorageType.USENET -> "Add Usenet Download to Cloud"
    }
    val dialogPrompt = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "Enter a magnet URL or info hash to add to your cloud storage:"
        TorboxStorageType.WEB_DOWNLOADS -> "Enter a direct download link (e.g. Rapidgator, Mega, etc.):"
        TorboxStorageType.USENET -> "Enter an NZB file link or Usenet URL:"
    }
    val dialogPlaceholder = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "magnet:?xt=urn:btih:..."
        TorboxStorageType.WEB_DOWNLOADS -> "https://..."
        TorboxStorageType.USENET -> "https://..."
    }
    val dialogConfirmText = when (selectedStorageType) {
        TorboxStorageType.TORRENTS -> "Add Torrent"
        TorboxStorageType.WEB_DOWNLOADS -> "Add Web Link"
        TorboxStorageType.USENET -> "Add Usenet"
    }

    CloudstreamAlertDialog(
        show = showAddDialog,
        onDismissRequest = {
            if (!isAddingItem) showAddDialog = false
        },
        title = { Text(dialogTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = dialogPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newLinkInput,
                    onValueChange = {
                        newLinkInput = it
                        addItemError = null
                    },
                    placeholder = {
                        Text(
                            dialogPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAddingItem,
                )
                if (addItemError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = addItemError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val input = newLinkInput.trim()
                    if (input.isBlank()) {
                        addItemError = "Please enter a valid link"
                        return@Button
                    }
                    isAddingItem = true
                    addItemError = null
                    scope.launch(Dispatchers.IO) {
                        val success = when (selectedStorageType) {
                            TorboxStorageType.TORRENTS -> TorboxApiClient.createTorrent(apiKey, input, addOnlyIfCached = false) != null
                            TorboxStorageType.WEB_DOWNLOADS -> TorboxApiClient.createWebDownload(apiKey, input)
                            TorboxStorageType.USENET -> TorboxApiClient.createUsenet(apiKey, input)
                        }
                        withContext(Dispatchers.Main) {
                            isAddingItem = false
                            if (success) {
                                showAddDialog = false
                                newLinkInput = ""
                                refreshItems(force = true)
                            } else {
                                addItemError = "Failed to add to Torbox cloud storage"
                            }
                        }
                    }
                },
                enabled = !isAddingItem && newLinkInput.isNotBlank(),
            ) {
                if (isAddingItem) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Adding...")
                } else {
                    Text(dialogConfirmText)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showAddDialog = false },
                enabled = !isAddingItem,
            ) {
                Text("Cancel")
            }
        },
    )

    // Delete Confirmation Dialog
    itemToDelete?.let { target ->
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = {
                if (!isDeleting) itemToDelete = null
            },
            title = { Text("Delete ${target.storageType.label.dropLast(1)}") },
            text = {
                Text(
                    text = "Are you sure you want to remove '${target.name}' from your Torbox ${target.storageType.label.lowercase()} storage?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        scope.launch(Dispatchers.IO) {
                            val success = TorboxApiClient.deleteItem(apiKey, target)
                            withContext(Dispatchers.Main) {
                                isDeleting = false
                                itemToDelete = null
                                if (success) {
                                    TorboxLibraryCache.removeItem(target.storageType, target.id)
                                    itemsList = itemsList.filterNot { it.id == target.id }
                                    refreshItems(force = true)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Deleting...")
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToDelete = null },
                    enabled = !isDeleting,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Multi-File Browser Modal
    selectedItemForFiles?.let { item ->
        val videoFiles = remember(item.files) {
            item.files.filter { isVideoFile(it.name) }.sortedWith(naturalNameComparator)
        }
        var showAllFiles by remember { mutableStateOf(videoFiles.isEmpty()) }
        val displayFiles = if (!showAllFiles && videoFiles.isNotEmpty()) videoFiles else item.files

        CloudstreamCustomDialog(
            show = true,
            onDismissRequest = { selectedItemForFiles = null },
            modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = cleanItemTitle(item.name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${displayFiles.size} sources available (${item.formattedSize})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = { selectedItemForFiles = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (videoFiles.isNotEmpty() && item.files.size > videoFiles.size) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showAllFiles,
                            onClick = { showAllFiles = false },
                            label = { Text("Video Streams (${videoFiles.size})") },
                        )
                        FilterChip(
                            selected = showAllFiles,
                            onClick = { showAllFiles = true },
                            label = { Text("All Files (${item.files.size})") },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayFiles, key = { it.id }) { file ->
                        val isFileResolving = resolvingItemId == item.id && resolvingFileId == file.id
                        val ext = file.name.substringAfterLast('.', "").uppercase().take(4)
                        val isVideo = isVideoFile(file.name)
                        val parentId = "torbox_${item.storageType.name.lowercase()}_${item.id}"
                        val epWatched = remember(file.id, historyTick) {
                            DesktopDataStore.getEpisodeWatched(parentId, file.id.toString())
                        }
                        val fileRatio = if (epWatched != null && epWatched.duration > 0L) {
                            (epWatched.position.toFloat() / epWatched.duration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val isEpCompleted = epWatched != null && (fileRatio >= 0.85f || (epWatched.duration > 0L && PlayerLinkHandler.isCompleted(epWatched.position, epWatched.duration)))

                        val interactionSource = remember { MutableInteractionSource() }
                        val isRowHovered by interactionSource.collectIsHoveredAsState()
                        var showFileMenu by remember { mutableStateOf(false) }
                        var fileMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
                        val density = LocalDensity.current

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRowHovered) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                                },
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isRowHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .hoverable(interactionSource)
                                .pointerInput(file.id) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Release) {
                                                if (event.button == PointerButton.Secondary) {
                                                    val pos = event.changes.firstOrNull()?.position ?: androidx.compose.ui.geometry.Offset.Zero
                                                    fileMenuOffset = with(density) {
                                                        DpOffset(pos.x.toDp(), pos.y.toDp())
                                                    }
                                                    showFileMenu = true
                                                } else if (event.button == PointerButton.Primary) {
                                                    playItemFile(item, file)
                                                }
                                            }
                                        }
                                     }
                                },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),

                                ) {
                                    DropdownMenu(
                                        expanded = showFileMenu,
                                        onDismissRequest = { showFileMenu = false },
                                        offset = fileMenuOffset,
                                    ) {
   
   
   
   
                                        DropdownMenuItem(
                                            text = { Text("Copy File Name") },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                showFileMenu = false
                                                copyToClipboard(file.name)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy Stream / Download Link") },
                                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                showFileMenu = false
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val link = TorboxApiClient.requestDownloadLink(apiKey, item, file.id)
                                                        if (!link.isNullOrBlank()) {
                                                            copyToClipboard(link)
                                                        }
                                                    } catch (e: Exception) {
                                                        AppLogger.e("TorboxCloudLibraryView: Failed to copy file stream link", e)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    /*
                                                    }
                                                }
                                            },
                                        )
                                     }

                                ) {
                                    */ if (ext.isNotBlank()) {
                                        Surface(
                                            color = if (isVideo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
       
       
       
                                            Text(
                                                text = ext,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isVideo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                     }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
       
       
       
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (isEpCompleted) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp),
                                        ) {
       
       
               
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        
           
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                   
       
                   
                                                        Icon(
                                                            Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(11.dp),
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        )
                                                        Text(
                                                            text = "Watched",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (fileRatio > 0f && !isEpCompleted) {
                                            Spacer(Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { fileRatio },
                                                modifier = Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                                            )
                                        }
                                        if (file.formattedFileSize.isNotBlank() || (fileRatio > 0f && !isEpCompleted)) {
                                            Spacer(Modifier.height(3.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {

           

           
           
                                                if (file.formattedFileSize.isNotBlank()) {
                                                    Text(
                                                        text = file.formattedFileSize,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                if (fileRatio > 0f && !isEpCompleted) {
                                                    Text(
                                                        text = "${(fileRatio * 100).toInt()}% watched",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        }
                                     }
                                }

                                Button(
                                    onClick = { playItemFile(item, file) },
                                    enabled = !isFileResolving,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    if (isFileResolving) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Loading...", style = MaterialTheme.typography.labelMedium)
                                     } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        val btnLabel = when {
                                            isEpCompleted -> "Replay"
                                            fileRatio > 0f -> "Resume"
                                            else -> "Play"
                                        }
                                        Text(btnLabel, style = MaterialTheme.typography.labelMedium)
                                     }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TorboxRecentWatchedShelf(
    recentItems: List<WatchHistory>,
    itemsList: List<TorboxTorrentData>,
    onResume: (WatchHistory) -> Unit,
    onRemove: (WatchHistory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${recentItems.size} ${if (recentItems.size == 1) "item" else "items"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(recentItems, key = { it.parentId }) { history ->
                val matchingItem = itemsList.find {
                    it.id == history.parentId.substringAfterLast('_').toIntOrNull()
                }
                TorboxRecentCard(
                    history = history,
                    item = matchingItem,
                    onResume = { onResume(history) },
                    onRemove = { onRemove(history) },
                )
            }
        }
    }
}

@Composable
private fun TorboxRecentCard(
    history: WatchHistory,
    item: TorboxTorrentData?,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val duration = history.duration.coerceAtLeast(0L)
    val position = history.position.coerceAtLeast(0L)
    val ratio = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val isCompleted = duration > 0L && PlayerLinkHandler.isCompleted(position, duration)

    val cleanTitle = cleanItemTitle(history.showName)
    val epLabel = history.episodeName?.takeIf { it.isNotBlank() && it != history.showName }
        ?: if (history.episode != null) "Episode ${history.episode}" else null

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
        border = BorderStroke(
            1.dp,
            if (isHovered) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            },
        ),
        modifier = modifier
            .width(260.dp)
            .clip(RoundedCornerShape(10.dp))
            .hoverable(interactionSource)
            .clickable(onClick = onResume),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = if (isCompleted) "Completed" else "${(ratio * 100).toInt()}% Watched",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCompleted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove from history",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Text(
                text = cleanTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (epLabel != null) {
                Text(
                    text = epLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val remainingMinutes = if (duration > position) ((duration - position) / 60).coerceAtLeast(1) else 0
                Text(
                    text = if (isCompleted) {
                        "Finished"
                    } else if (remainingMinutes > 0) {
                        "$remainingMinutes min left"
                    } else {
                        "In progress"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (isCompleted) "Replay" else "Resume",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TorboxCloudItemCard(
    item: TorboxTorrentData,
    apiKey: String,
    isCurrentResolving: Boolean,
    onCardClick: () -> Unit,
    onBrowseFilesClick: () -> Unit,
    onPlayClick: () -> Unit,
    onResumeEpisode: (WatchHistory) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val videoFiles = remember(item.files) { item.files.filter { isVideoFile(it.name) }.sortedWith(naturalNameComparator) }
    val displayTitle = remember(item.name) { cleanItemTitle(item.name) }
    val parentId = "torbox_${item.storageType.name.lowercase()}_${item.id}"
    val historyTick by DesktopDataStore.historyUpdates.collectAsState()
    val latestHistory = remember(item.id, historyTick) {
        DesktopDataStore.getLastWatched(parentId)
    }

    val cardScope = rememberCoroutineScope()
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    var isCopyingLink by remember { mutableStateOf(false) }

    val isPending = !item.isCompleted && (item.downloadState == "pending" || item.downloadState == "queued" || (item.progress == null && item.downloadState != "downloading"))
    val isDownloading = !item.isCompleted && item.downloadState == "downloading"
    val isSeeding = !item.isCompleted && (item.downloadState == "uploading" || item.downloadState == "seeding") /*
    }

    */
    val borderColor = if (isHovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    val cardBg = if (isHovered) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 6.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .pointerInput(item.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release) {
                            if (event.button == PointerButton.Secondary) {
                                val pos = event.changes.firstOrNull()?.position ?: androidx.compose.ui.geometry.Offset.Zero
                                contextMenuOffset = with(density) {
                                    DpOffset(pos.x.toDp(), pos.y.toDp())
                                }
                                showContextMenu = true
                            } else if (event.button == PointerButton.Primary) {
                                onCardClick()
                            }
                        }
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = contextMenuOffset,
            ) {
                DropdownMenuItem(
                    text = { Text("Copy Title") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        copyToClipboard(item.name)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (isCopyingLink) "Fetching link..." else "Copy Stream / Download Link") },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    enabled = !isCopyingLink,
                    onClick = {
                        showContextMenu = false
                        cardScope.launch(Dispatchers.IO) {
                            isCopyingLink = true
                            try {
                                val link = TorboxApiClient.requestDownloadLink(apiKey, item, null)
                                if (!link.isNullOrBlank()) {
                                    copyToClipboard(link)
                                }
                            } catch (e: Exception) {
                                AppLogger.e("TorboxCloudLibraryView: Failed to copy stream link", e)
                            } finally {
                                isCopyingLink = false
                            }
                        }
                    },
                )
                if (!item.hash.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy Magnet Link") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showContextMenu = false
                            val encoded = java.net.URLEncoder.encode(item.name, "UTF-8")
                            copyToClipboard("magnet:?xt=urn:btih:${item.hash}&dn=$encoded")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Info Hash") },
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showContextMenu = false
                            copyToClipboard(item.hash)
                        },
                    )
                }
                if (!item.originalUrl.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy Source URL") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showContextMenu = false
                            copyToClipboard(item.originalUrl)
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("Delete from Cloud", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showContextMenu = false
                        onDeleteClick()
                    },
                )
            }

            // Top Row: Status badge on left, Formatted size on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusLabel = when {
                    item.isCompleted -> "Completed"
                    item.downloadState == "cached" -> "Cached"
                    item.downloadState == "downloading" -> "Downloading (${item.progressPercent}%)"
                    item.downloadState == "uploading" || item.downloadState == "seeding" -> "Seeding"
                    !item.downloadState.isNullOrBlank() -> item.downloadState.replaceFirstChar { it.uppercase() }
                    else -> "Queued"
                }

                Surface(
                    color = if (item.isCompleted || item.downloadState == "cached") {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (item.isCompleted || item.downloadState == "cached") {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else if (item.downloadState == "downloading") {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } /*
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Text(
                        */
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.isCompleted || item.downloadState == "cached") {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                    }
                }

                if (item.formattedSize.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Middle Section: 2-line Clean Title with Tooltip showing Full Raw Name
            Column(modifier = Modifier.fillMaxWidth()) {
                TooltipArea(
                    tooltip = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1E28),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier.widthIn(max = 480.dp),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Full Release Name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                        }
                    },
                    delayMillis = 200,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (!item.isCompleted) {
                    Spacer(Modifier.height(6.dp))
                    if (isPending) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Pending • Resolving metadata / queued",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                            if (item.formattedSize.isNotBlank()) {
                                Text(
                                    text = item.formattedSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    } else {
                        val currentProgress = (item.progress?.toFloat() ?: 0f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val progressText = if (item.size > 0L) {
                                "${item.formattedDownloadedSize} / ${item.formattedSize} (${item.progressPercent}%)"
                            } else {
                                "${item.progressPercent}%"
                            }
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            val telemetryList = listOfNotNull(
                                item.formattedSpeed,
                                item.formattedEta,
                                item.seeds?.takeIf { it > 0 }?.let { "$it seeds" },
                            )
                            if (telemetryList.isNotEmpty()) {
                                Text(
                                    text = telemetryList.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                /*
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (item.progress.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                */ } else if (item.isCompleted && latestHistory != null && latestHistory.duration > 0L) {
                    val watchRatio = (latestHistory.position.toFloat() / latestHistory.duration.toFloat()).coerceIn(0f, 1f)
                    if (watchRatio > 0f && !PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { watchRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }

            // Bottom Row: Sources indicator on left, Actions (Delete, Play/Browse/Resume) on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (videoFiles.size > 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable(onClick = onBrowseFilesClick),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${videoFiles.size} Sources",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = if (item.files.isNotEmpty()) "1 Source" else "Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                        )
                    }

                    if (videoFiles.size > 1) {
                        if (latestHistory != null && latestHistory.position > 0) {
                            IconButton(
                                onClick = onBrowseFilesClick,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = "Browse files",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(
                                onClick = { onResumeEpisode(latestHistory) },
                                enabled = !isCurrentResolving,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                if (isCurrentResolving) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Loading...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    val epText = if (latestHistory.episode != null) "Resume Ep ${latestHistory.episode}" else "Resume"
                                    Text(epText, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onBrowseFilesClick,
                                enabled = !isCurrentResolving,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                if (isCurrentResolving) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Loading...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Browse", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        val isResumeSingle = latestHistory != null && latestHistory.position > 0 && !PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)

                        FilledTonalButton(
                            onClick = onPlayClick,

                            enabled = !isCurrentResolving,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            if (isCurrentResolving) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Loading...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        isResumeSingle -> "Resume"
                                        isPending -> "Pending"
                                        isDownloading -> "Stream"
                                        else -> "Play"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
