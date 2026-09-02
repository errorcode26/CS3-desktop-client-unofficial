package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.screens.links.LinksViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.VlcPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksSidePanel(
    provider: MainAPI,
    dataUrl: String,
    history: WatchHistory,
    loadResponse: com.lagradost.cloudstream3.LoadResponse?,
    onClose: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { LinksViewModel() }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    val vlcPlayer = remember { VlcPlayer() }
    DisposableEffect(vlcPlayer) {
        onDispose {
            vlcPlayer.destroy()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val links = uiState.links
    val statusText = uiState.statusText
    val isScraping = uiState.isScraping

    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current
    val isVideoPlayerActive = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayerActive.current
    val selectedPlayer = uiState.preferredPlayer
    var isLaunchingPlayer by remember { mutableStateOf(false) }
    var playerLaunchError by remember { mutableStateOf<String?>(null) }
    var embeddedError by remember { mutableStateOf<String?>(null) }
    var currentPlayingUrl by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<Int?>(null) }
    var selectedFormat by remember { mutableStateOf(StreamFormatFilter.ALL) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var linkToDownload by remember { mutableStateOf<ExtractorLink?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is LinksUiEffect.ShowToast -> {
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(effect.message)
                }
                is LinksUiEffect.NotifyLaunching -> {
                    isLaunchingPlayer = effect.isLaunching
                }
                is LinksUiEffect.NotifyCurrentUrl -> {
                    currentPlayingUrl = effect.url
                }
                is LinksUiEffect.LaunchVlc -> {
                    coroutineScope.launch {
                        val result = vlcPlayer.play(effect.link, effect.displayTitle, effect.subtitles, effect.startMs)
                        if (!result.isSuccess) {
                            viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Could not start player."))
                        }
                        isLaunchingPlayer = false
                        currentPlayingUrl = null
                    }
                }
                is LinksUiEffect.LaunchEmbeddedPlayer -> {
                    isLaunchingPlayer = false
                    currentPlayingUrl = null
                    playVideo(effect.launchData)
                }
            }
        }
    }

    val displayTitle = remember(history) {
        buildString {
            append(history.showName)
            if (history.season != null && history.episode != null) {
                append(" - S${history.season}E${history.episode}")
            } else if (history.episode != null) {
                append(" - E${history.episode}")
            }
        }
    }

    val availableQualities = remember(links) {
        links.groupBy { it.quality }
            .map { (qual, list) ->
                QualityOption(
                    qualityValue = qual,
                    label = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(qual),
                    count = list.size,
                )
            }
            .sortedByDescending { it.qualityValue }
    }

    val availableFormats = remember(links) {
        val totalDirect = links.count { link ->
            val isHls = link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8")
            val isDash = link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd")
            val isTorrent = link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.TORRENT ||
                link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET ||
                link.url.startsWith("magnet:")
            !isHls && !isDash && !isTorrent
        }
        val totalHls = links.count { link ->
            link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8")
        }
        val totalDash = links.count { link ->
            link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd")
        }
        val totalTorrent = links.count { link ->
            link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.TORRENT ||
                link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET ||
                link.url.startsWith("magnet:")
        }

        val list = mutableListOf<FormatOption>()
        list.add(FormatOption(StreamFormatFilter.ALL, links.size))
        if (totalDirect > 0) list.add(FormatOption(StreamFormatFilter.DIRECT, totalDirect))
        if (totalHls > 0) list.add(FormatOption(StreamFormatFilter.HLS, totalHls))
        if (totalDash > 0) list.add(FormatOption(StreamFormatFilter.DASH, totalDash))
        if (totalTorrent > 0) list.add(FormatOption(StreamFormatFilter.TORRENT, totalTorrent))
        list
    }

    val filteredLinks = remember(links, selectedQuality, selectedFormat) {
        links.filter { link ->
            val qualityMatches = selectedQuality == null || link.quality == selectedQuality
            val isHls = link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8")
            val isDash = link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd")
            val isTorrent = link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.TORRENT ||
                link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET ||
                link.url.startsWith("magnet:")
            val isDirect = !isHls && !isDash && !isTorrent

            val formatMatches = when (selectedFormat) {
                StreamFormatFilter.ALL -> true
                StreamFormatFilter.DIRECT -> isDirect
                StreamFormatFilter.HLS -> isHls
                StreamFormatFilter.DASH -> isDash
                StreamFormatFilter.TORRENT -> isTorrent
            }
            qualityMatches && formatMatches
        }
    }

    LaunchedEffect(dataUrl) {
        viewModel.onEvent(LinksUiEvent.OnScrape(provider, dataUrl))
    }

    val vlcState = vlcPlayer.state.collectAsState().value
    val isAnyPlaying = vlcState.isPlaying
    var lastVlcSavedPositionSec by remember { mutableStateOf(0L) }

    LaunchedEffect(vlcState.position) {
        val posMs = if (vlcState.isPlaying) vlcState.position else 0L
        val durMs = if (vlcState.isPlaying) vlcState.duration else 0L
        if (posMs > 0 && durMs > 0) {
            val posSec = posMs / 1000L
            if (kotlin.math.abs(posSec - lastVlcSavedPositionSec) >= 5) {
                lastVlcSavedPositionSec = posSec
                viewModel.onEvent(LinksUiEvent.OnSaveWatchPosition(history, posMs, durMs))
            }
        }
    }

    DisposableEffect(isAnyPlaying) {
        onDispose {
            if (!isAnyPlaying && vlcState.position > 0 && vlcState.duration > 0) {
                viewModel.onEvent(LinksUiEvent.OnSaveWatchPosition(history, vlcState.position, vlcState.duration))
            }
        }
    }

    LaunchedEffect(isAnyPlaying, isVideoPlayerActive) {
        if (!isAnyPlaying && !isVideoPlayerActive) {
            if (statusText == "Player started." || statusText.startsWith("Playing:")) {
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available."))
            }
            isLaunchingPlayer = false
            currentPlayingUrl = null
        }
    }

    LaunchedEffect(vlcState.error, embeddedError) {
        val errorMessage = vlcState.error ?: embeddedError
        if (errorMessage != null) {
            val autoPlay = uiState.autoPlayEnabled
            val currentIndex = filteredLinks.indexOfFirst { it.url == currentPlayingUrl }
            val isVlcError = vlcState.error != null
            if (autoPlay && isVlcError && currentIndex != -1 && currentIndex + 1 < filteredLinks.size) {
                val nextLink = filteredLinks[currentIndex + 1]
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Link failed. Auto-trying next: ${nextLink.name}"))
                embeddedError = null
                playerLaunchError = null
                delay(800)
                viewModel.onEvent(
                    LinksUiEvent.OnPlayLink(
                        link = nextLink,
                        displayTitle = displayTitle,
                        history = history,
                        loadResponse = loadResponse,
                        currentPlayingUrl = currentPlayingUrl,
                    ),
                )
            } else {
                playerLaunchError = errorMessage
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Playback failed: $errorMessage"))
                isLaunchingPlayer = false
                currentPlayingUrl = null
                embeddedError = null
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val epSubtitle = if (history.season != null && history.episode != null) {
                            "Season ${history.season} • Episode ${history.episode}"
                        } else if (history.episode != null) {
                            "Episode ${history.episode}"
                        } else {
                            "Movie"
                        }
                        Text(
                            text = history.showName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DesktopUi.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = epSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        ),
                    ) {
                        Text(
                            text = "${links.size} Streams",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                HorizontalDivider(color = DesktopUi.Divider)

                // Status & Search Bar
                StreamStatusCard(
                    statusText = statusText,
                    isLoading = isScraping || isLaunchingPlayer,
                    isScraping = isScraping,
                    onStop = { viewModel.onEvent(LinksUiEvent.OnCancelScrape) },
                )

                // Player Selector
                PlayerSelector(
                    selectedPlayer = selectedPlayer,
                    onSelect = { player -> viewModel.onEvent(LinksUiEvent.OnPreferredPlayerChanged(player)) },
                )

                // Dual Quality & Format Filter Selector
                if (availableQualities.isNotEmpty()) {
                    QualitySelector(
                        totalLinkCount = links.size,
                        availableQualities = availableQualities,
                        selectedQuality = selectedQuality,
                        onSelect = { selectedQuality = it },
                        onOpenPriorityDialog = { showPriorityDialog = true },
                        availableFormats = availableFormats,
                        selectedFormat = selectedFormat,
                        onSelectFormat = { selectedFormat = it },
                    )
                }

                // Stream Link List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!isScraping && filteredLinks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No Streams Found",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No playable links match the selected filter criteria.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(filteredLinks, key = { index, it -> "${it.name}-${it.url}-$index" }) { _, link ->
                        StreamLinkCard(
                            link = link,
                            isBusy = isLaunchingPlayer && currentPlayingUrl != link.url,
                            onPlay = {
                                viewModel.onEvent(
                                    LinksUiEvent.OnPlayLink(
                                        link = link,
                                        displayTitle = displayTitle,
                                        history = history,
                                        loadResponse = loadResponse,
                                        currentPlayingUrl = currentPlayingUrl,
                                    ),
                                )
                            },
                            onDownload = {
                                linkToDownload = link
                            },
                            onCopy = {
                                if (link.url.isNotBlank()) {
                                    val selection = java.awt.datatransfer.StringSelection(link.url)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                    viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("URL copied to clipboard."))
                                }
                            },
                        )
                    }
                }
            }

            // Download Confirmation Dialog
            DownloadConfirmationDialog(
                show = linkToDownload != null,
                onDismiss = { linkToDownload = null },
                link = linkToDownload,
                displayTitle = displayTitle,
                history = history,
                loadResponse = loadResponse,
                providerName = provider.name,
                onConfirmDownload = {
                    val targetLink = linkToDownload ?: return@DownloadConfirmationDialog
                    linkToDownload = null
                    com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager.enqueue(
                        canonicalKey = history.showUrl,
                        showName = history.showName,
                        showUrl = history.showUrl,
                        episodeTitle = history.episodeId,
                        posterUrl = history.posterUrl,
                        backdropUrl = loadResponse?.backgroundPosterUrl,
                        season = history.season,
                        episode = history.episode,
                        link = targetLink,
                        apiName = provider.name,
                    )
                },
            )

            com.lagradost.cloudstream3.desktop.ui.screens.player.SourcePriorityDialog(
                show = showPriorityDialog,
                onDismissRequest = { showPriorityDialog = false },
            )

            com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
                show = playerLaunchError != null,
                onDismissRequest = { playerLaunchError = null },
                title = { Text("Player error") },
                text = { Text(playerLaunchError ?: "") },
                confirmButton = {
                    TextButton(onClick = { playerLaunchError = null }) { Text("OK") }
                },
            )
        }
    }
}

@Composable
private fun StreamStatusCard(
    statusText: String,
    isLoading: Boolean,
    isScraping: Boolean,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, DesktopUi.Divider.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = DesktopUi.Accent,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = DesktopUi.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (isScraping) {
                TextButton(onClick = onStop) {
                    Text("Stop", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSelector(selectedPlayer: String, onSelect: (String) -> Unit) {
    val players = listOf("mpv" to "MPV (Internal)", "vlc" to "VLC (External)")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.SmartDisplay,
            contentDescription = null,
            tint = DesktopUi.TextMuted,
            modifier = Modifier.size(16.dp),
        )
        Text("Player:", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
        players.forEach { (id, label) ->
            FilterChip(
                selected = selectedPlayer == id,
                onClick = { onSelect(id) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DesktopUi.AccentSoft,
                    selectedLabelColor = DesktopUi.Accent,
                ),
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}

@Composable
private fun StreamLinkCard(
    link: ExtractorLink,
    isBusy: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.01f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )

    val formattedQuality = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(link.quality)
    val is4k = link.quality >= 2160
    val is1080 = link.quality in 1080..2159
    val is720 = link.quality in 720..1079

    val qualityContainerColor = when {
        is4k -> Color(0xFFE5A00D).copy(alpha = 0.2f)
        is1080 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        is720 -> Color(0xFF00B4D8).copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val qualityTextColor = when {
        is4k -> Color(0xFFFFC107)
        is1080 -> MaterialTheme.colorScheme.primary
        is720 -> Color(0xFF00D2FF)
        else -> DesktopUi.TextMuted
    }

    val formatTag = when {
        link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8") -> "HLS"
        link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd") -> "DASH"
        link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.TORRENT ||
            link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET ||
            link.url.startsWith("magnet:") -> "TORRENT"
        else -> "MP4"
    }

    val cleanSize = remember(link.name) { extractCleanSize(link.name) }
    val hostSource = remember(link.name, link.source) {
        if (link.source.isNotBlank() && link.source != link.name) link.source else extractCleanServer(link.name, "")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .hoverable(interaction),
        shape = RoundedCornerShape(10.dp),
        color = if (hovered) DesktopUi.SurfaceElevated else DesktopUi.SurfaceCard,
        tonalElevation = if (hovered) 6.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else DesktopUi.Divider.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1: Badges on Left, File Size on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    // Quality Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = qualityContainerColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, qualityTextColor.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = formattedQuality,
                            color = qualityTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }

                    // Format Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DesktopUi.Divider.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = formatTag,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    // Host / Source Tag
                    if (hostSource.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ) {
                            Text(
                                text = hostSource,
                                color = DesktopUi.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Extracted File Size Badge
                if (cleanSize != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            text = cleanSize,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Row 2: Full Raw Release Title (100% visible and unclipped)
            Text(
                text = link.name,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = DesktopUi.TextPrimary,
                softWrap = true,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Row 3: Action Buttons (Copy, Download, Play)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = onCopy,
                    enabled = !isBusy,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Stream URL",
                        tint = DesktopUi.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                OutlinedButton(
                    onClick = onDownload,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 34.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Download",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onPlay,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopUi.Accent,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 34.dp),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Play",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun extractCleanServer(rawName: String, fallback: String): String {
    val bracketMatch = Regex("\\[(.*?)\\]").find(rawName)
    if (bracketMatch != null) {
        val candidate = bracketMatch.groupValues[1].trim()
        if (!candidate.contains("MB", ignoreCase = true) && !candidate.contains("GB", ignoreCase = true)) {
            return candidate
        }
    }
    val parts = rawName.split(" ", "-", ".").filter { it.isNotBlank() }
    return parts.firstOrNull { it.length > 2 && !it.contains("720") && !it.contains("1080") && !it.contains("x264") } ?: fallback
}

private fun extractCleanSize(rawName: String): String? {
    val sizeMatch = Regex("\\[([0-9.]+\\s*(?:MB|GB|KB))\\]", RegexOption.IGNORE_CASE).find(rawName)
        ?: Regex("([0-9.]+\\s*(?:MB|GB|KB))", RegexOption.IGNORE_CASE).find(rawName)
    return sizeMatch?.groupValues?.get(1)
}

@Composable
private fun DownloadConfirmationDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    link: ExtractorLink?,
    displayTitle: String,
    history: com.lagradost.common.storage.WatchHistory,
    loadResponse: com.lagradost.cloudstream3.LoadResponse?,
    providerName: String,
    onConfirmDownload: () -> Unit,
) {
    if (!show || link == null) return

    val cleanServer = remember(link.name) { extractCleanServer(link.name, providerName) }
    val cleanSize = remember(link.name) { extractCleanSize(link.name) }
    val poster = history.posterUrl ?: loadResponse?.posterUrl ?: loadResponse?.backgroundPosterUrl
    val threads = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS)?.toInt() ?: 8

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(520.dp).wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        text = "Confirm Download",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // Media Info Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Poster Thumbnail
                    if (!poster.isNullOrBlank()) {
                        AsyncImage(
                            model = poster,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(56.dp)
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = history.showName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val episodeInfo = if (history.season != null && history.episode != null) {
                            "Season ${history.season} • Episode ${history.episode}${if (!history.episodeId.isNullOrBlank()) " - ${history.episodeId}" else ""}"
                        } else if (history.episode != null) {
                            "Episode ${history.episode}${if (!history.episodeId.isNullOrBlank()) " - ${history.episodeId}" else ""}"
                        } else {
                            "Feature Film / Standalone Video"
                        }

                        Text(
                            text = episodeInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Specs Row (Quality & Size Pills)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "${link.quality}p ${if (link.quality >= 1080) "FHD" else "HD"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }

                            if (cleanSize != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = cleanSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Server & Acceleration Details Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Stream Provider", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(cleanServer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Engine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Turbo Multi-Chunk ($threads workers)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Destination", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Downloads Tab / Offline", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onConfirmDownload,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Download", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
