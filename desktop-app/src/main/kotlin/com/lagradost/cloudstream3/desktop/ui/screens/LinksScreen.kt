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
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.screens.links.StreamLinkCard
import com.lagradost.cloudstream3.desktop.ui.screens.links.dialogs.DownloadConfirmationDialog
import com.lagradost.cloudstream3.desktop.ui.components.P2pTorrentDisclaimerDialog
import com.lagradost.cloudstream3.desktop.ui.screens.links.LinksViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.VlcPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksSidePanel(
    provider: MainAPI,
    dataUrl: String,
    history: WatchHistory,
    loadResponse: com.lagradost.cloudstream3.LoadResponse?,
    enrichedActors: List<com.lagradost.cloudstream3.ActorData>? = null,
    enrichedLogoUrl: String? = null,
    enrichedBackdropUrl: String? = null,
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
    val isLaunchingPlayer = uiState.isLaunchingPlayer
    val playerLaunchError = uiState.playerLaunchError
    val currentPlayingUrl = uiState.currentPlayingUrl
    val selectedQuality = uiState.selectedQuality
    val selectedFormat = uiState.selectedFormat
    val isP2pEnabled = uiState.isP2pEnabled
    var showPriorityDialog by remember { mutableStateOf(false) }
    var p2pDisclaimerTargetAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is LinksUiEffect.ShowToast -> {
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(effect.message)
                }
                is LinksUiEffect.LaunchVlc -> {
                    coroutineScope.launch {
                        val result = vlcPlayer.play(effect.link, effect.displayTitle, effect.subtitles, effect.startMs)
                        if (!result.isSuccess) {
                            viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Could not start player."))
                            viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished("Could not start player."))
                        } else {
                            viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null))
                        }
                    }
                }
                is LinksUiEffect.LaunchEmbeddedPlayer -> {
                    viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null))
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
        links.groupBy { com.lagradost.cloudstream3.desktop.player.QualityDataHelper.extractEffectiveQuality(it) }
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
            val effQual = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.extractEffectiveQuality(link)
            val qualityMatches = selectedQuality == null || effQual == selectedQuality
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
    LaunchedEffect(vlcState.position) {
        val posMs = if (vlcState.isPlaying) vlcState.position else 0L
        val durMs = if (vlcState.isPlaying) vlcState.duration else 0L
        if (posMs > 0 && durMs > 0) {
            val posSec = posMs / 1000L
            if (kotlin.math.abs(posSec - uiState.lastVlcSavedPositionSec) >= 5) {
                viewModel.onEvent(LinksUiEvent.OnUpdateVlcSavedPosition(posSec))
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
            viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null))
        }
    }

    LaunchedEffect(vlcState.error, uiState.embeddedError) {
        val errorMessage = vlcState.error ?: uiState.embeddedError
        if (errorMessage != null) {
            val autoPlay = uiState.autoPlayEnabled
            val currentIndex = filteredLinks.indexOfFirst { it.url == currentPlayingUrl }
            val isVlcError = vlcState.error != null
            if (autoPlay && isVlcError && currentIndex != -1 && currentIndex + 1 < filteredLinks.size) {
                val nextLink = filteredLinks[currentIndex + 1]
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Link failed. Auto-trying next: ${nextLink.name}"))
                viewModel.onEvent(LinksUiEvent.OnSetEmbeddedError(null))
                viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null))
                delay(800)
                viewModel.onEvent(
                    LinksUiEvent.OnPlayLink(
                        link = nextLink,
                        displayTitle = displayTitle,
                        history = history,
                        loadResponse = loadResponse,
                        currentPlayingUrl = currentPlayingUrl,
                        enrichedActors = enrichedActors,
                        enrichedLogoUrl = enrichedLogoUrl,
                        enrichedBackdropUrl = enrichedBackdropUrl,
                    ),
                )
            } else {
                viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(errorMessage))
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Playback failed: $errorMessage"))
                viewModel.onEvent(LinksUiEvent.OnSetEmbeddedError(null))
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
                        onSelect = { viewModel.onEvent(LinksUiEvent.OnFilterQuality(it)) },
                        onOpenPriorityDialog = { showPriorityDialog = true },
                        availableFormats = availableFormats,
                        selectedFormat = selectedFormat,
                        onSelectFormat = { viewModel.onEvent(LinksUiEvent.OnFilterFormat(it)) },
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
                        val isTorrent = link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.TORRENT ||
                            link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET ||
                            link.url.startsWith("magnet:")

                        StreamLinkCard(
                            link = link,
                            isP2pEnabled = isP2pEnabled,
                            isBusy = isLaunchingPlayer && currentPlayingUrl != link.url,
                            onPlay = {
                                if (isTorrent && !isP2pEnabled) {
                                    p2pDisclaimerTargetAction = {
                                        viewModel.onEvent(
                                            LinksUiEvent.OnPlayLink(
                                                link = link,
                                                displayTitle = displayTitle,
                                                history = history,
                                                loadResponse = loadResponse,
                                                currentPlayingUrl = currentPlayingUrl,
                                                enrichedActors = enrichedActors,
                                                enrichedLogoUrl = enrichedLogoUrl,
                                                enrichedBackdropUrl = enrichedBackdropUrl,
                                            ),
                                        )
                                    }
                                } else {
                                    viewModel.onEvent(
                                        LinksUiEvent.OnPlayLink(
                                            link = link,
                                            displayTitle = displayTitle,
                                            history = history,
                                            loadResponse = loadResponse,
                                            currentPlayingUrl = currentPlayingUrl,
                                            enrichedActors = enrichedActors,
                                            enrichedLogoUrl = enrichedLogoUrl,
                                            enrichedBackdropUrl = enrichedBackdropUrl,
                                        ),
                                    )
                                }
                            },
                            onDownload = {
                                if (isTorrent && !isP2pEnabled) {
                                    p2pDisclaimerTargetAction = {
                                        viewModel.onEvent(LinksUiEvent.OnSetLinkToDownload(link))
                                    }
                                } else {
                                    viewModel.onEvent(LinksUiEvent.OnSetLinkToDownload(link))
                                }
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
                show = uiState.linkToDownload != null,
                onDismiss = { viewModel.onEvent(LinksUiEvent.OnSetLinkToDownload(null)) },
                link = uiState.linkToDownload,
                displayTitle = displayTitle,
                history = history,
                loadResponse = loadResponse,
                providerName = provider.name,
                onConfirmDownload = {
                    val targetLink = uiState.linkToDownload ?: return@DownloadConfirmationDialog
                    viewModel.onEvent(LinksUiEvent.OnSetLinkToDownload(null))
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

            // P2P Torrent Disclaimer Dialog
            P2pTorrentDisclaimerDialog(
                show = p2pDisclaimerTargetAction != null,
                onDismiss = { p2pDisclaimerTargetAction = null },
                onConfirm = {
                    val action = p2pDisclaimerTargetAction
                    p2pDisclaimerTargetAction = null
                    viewModel.onEvent(LinksUiEvent.OnP2pEnabledChanged(true))
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess("P2P Torrent Streaming enabled")
                    action?.invoke()
                },
            )

            com.lagradost.cloudstream3.desktop.ui.screens.player.SourcePriorityDialog(
                show = showPriorityDialog,
                onDismissRequest = { showPriorityDialog = false },
            )

            com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
                show = playerLaunchError != null,
                onDismissRequest = { viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null)) },
                title = { Text("Player error") },
                text = { Text(playerLaunchError ?: "") },
                confirmButton = {
                    TextButton(onClick = { viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null)) }) { Text("OK") }
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


