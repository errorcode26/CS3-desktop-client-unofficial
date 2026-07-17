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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import com.lagradost.player.impl.VlcPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val vlcPlayer = VlcPlayer()

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
    val viewModel = remember(coroutineScope) { LinksViewModel(coroutineScope) }

    // Observe ViewModel state
    val links by viewModel.links.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val isScraping by viewModel.isScraping.collectAsState()

    // Local UI-only state (player launch feedback, filters)
    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current
    var selectedPlayer by remember { mutableStateOf(DesktopDataStore.getKey<String>("preferred_player") ?: "mpv") }
    var isLaunchingPlayer by remember { mutableStateOf(false) }
    var playerLaunchError by remember { mutableStateOf<String?>(null) }
    var embeddedError by remember { mutableStateOf<String?>(null) }
    var currentPlayingUrl by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf("All") }
    val availableTypes = listOf("All", "HLS (Fast Stream)", "MP4 (Downloadable)")

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

    val availableQualities = remember(links.size) {
        links.map { it.quality.toString() }.distinct().sorted()
    }
    val filteredLinks = remember(links.size, selectedQuality, selectedType) {
        links.filter { link ->
            val qualityMatches = selectedQuality == null || link.quality.toString() == selectedQuality
            val isHls = link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8")
            val typeMatches = when (selectedType) {
                "HLS (Fast Stream)" -> isHls
                "MP4 (Downloadable)" -> !isHls
                else -> true
            }
            qualityMatches && typeMatches
        }
    }

    // Kick off scraping whenever dataUrl changes
    LaunchedEffect(dataUrl) {
        viewModel.scrapeLinks(provider, dataUrl)
    }

    // VLC state observations
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
                viewModel.saveWatchPosition(history, posMs, durMs)
            }
        }
    }

    DisposableEffect(isAnyPlaying) {
        onDispose {
            if (!isAnyPlaying && vlcState.position > 0 && vlcState.duration > 0) {
                viewModel.saveWatchPosition(history, vlcState.position, vlcState.duration)
            }
        }
    }

    LaunchedEffect(isAnyPlaying) {
        if (!isAnyPlaying) {
            if (statusText == "Player started." || statusText.startsWith("Playing:")) {
                viewModel.setStatus("Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available.")
            }
            isLaunchingPlayer = false
            currentPlayingUrl = null
        }
    }

    // Auto-skip on VLC error
    LaunchedEffect(vlcState.error, embeddedError) {
        val errorMessage = vlcState.error ?: embeddedError
        if (errorMessage != null) {
            val autoPlay = DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
            val currentIndex = filteredLinks.indexOfFirst { it.url == currentPlayingUrl }
            val isVlcError = vlcState.error != null
            if (autoPlay && isVlcError && currentIndex != -1 && currentIndex + 1 < filteredLinks.size) {
                val nextLink = filteredLinks[currentIndex + 1]
                viewModel.setStatus("Link failed. Auto-trying next: ${nextLink.name}")
                embeddedError = null
                playerLaunchError = null
                delay(800)
                playLink(
                    link = nextLink,
                    links = links,
                    subtitles = subtitles.map { it.url }.filter { it.isNotBlank() },
                    subtitleFiles = subtitles.filter { it.url.isNotBlank() },
                    selectedPlayer = selectedPlayer,
                    displayTitle = displayTitle,
                    history = history,
                    loadResponse = loadResponse,
                    isLaunchingPlayer = isLaunchingPlayer,
                    currentPlayingUrl = currentPlayingUrl,
                    filteredLinks = filteredLinks,
                    coroutineScope = coroutineScope,
                    playVideo = playVideo,
                    onStatusChange = viewModel::setStatus,
                    onLaunching = { isLaunchingPlayer = it },
                    onCurrentUrl = { currentPlayingUrl = it },
                    onEmbeddedError = { embeddedError = it },
                )
            } else {
                playerLaunchError = errorMessage
                viewModel.setStatus("Playback failed: $errorMessage")
                isLaunchingPlayer = false
                currentPlayingUrl = null
                embeddedError = null
            }
        }
    }


    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 700.dp).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Select stream",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DesktopUi.TextPrimary,
                        )
                        Text(
                            history.showName,
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopUi.TextMuted,
                        )
                    }
                }
                HorizontalDivider(color = DesktopUi.Divider)

                StreamStatusCard(
                    statusText = statusText,
                    isLoading = isScraping || isLaunchingPlayer,
                    isScraping = isScraping,
                    onStop = { viewModel.cancelScrape() },
                )

                PlayerSelector(
                    selectedPlayer = selectedPlayer,
                    onSelect = { player ->
                        selectedPlayer = player
                        DesktopDataStore.setKey("preferred_player", player)
                    },
                )

                if (availableQualities.size > 1) {
                    QualitySelector(
                        availableQualities = availableQualities,
                        selectedQuality = selectedQuality,
                        onSelect = { selectedQuality = it },
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Text("Stream", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableTypes.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DesktopUi.AccentSoft,
                                    selectedLabelColor = DesktopUi.Accent,
                                ),
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!isScraping && filteredLinks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No Streams Found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No playable links were returned. Try another episode or provider.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    itemsIndexed(filteredLinks, key = { index, it -> "${it.name}-${it.url}-$index" }) { _, link ->
                        StreamLinkCard(
                            link = link,
                            isBusy = isLaunchingPlayer && currentPlayingUrl != link.url,
                            onPlay = {
                                playLink(
                                    link = link,
                                    links = links,
                                    subtitles = subtitles.map { it.url }.filter { it.isNotBlank() },
                                    subtitleFiles = subtitles.filter { it.url.isNotBlank() },
                                    selectedPlayer = selectedPlayer,
                                    displayTitle = displayTitle,
                                    history = history,
                                    loadResponse = loadResponse,
                                    isLaunchingPlayer = isLaunchingPlayer,
                                    currentPlayingUrl = currentPlayingUrl,
                                    filteredLinks = filteredLinks,
                                    coroutineScope = coroutineScope,
                                    playVideo = playVideo,
                                    onStatusChange = viewModel::setStatus,
                                    onLaunching = { isLaunchingPlayer = it },
                                    onCurrentUrl = { currentPlayingUrl = it },
                                    onEmbeddedError = { embeddedError = it },
                                )
                            },
                            onCopy = {
                                if (link.url.isNotBlank()) {
                                    val selection = java.awt.datatransfer.StringSelection(link.url)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                    viewModel.setStatus("URL copied to clipboard.")
                                }
                            },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }

            if (playerLaunchError != null) {
                AlertDialog(
                    onDismissRequest = { playerLaunchError = null },
                    title = { Text("Player error") },
                    text = { Text(playerLaunchError!!) },
                    confirmButton = {
                        TextButton(onClick = { playerLaunchError = null }) { Text("OK") }
                    },
                )
            }
        }
    }
}

/** Pure function — no state. Handles player launch routing. */
private fun playLink(
    link: ExtractorLink,
    links: List<ExtractorLink>,
    subtitles: List<String>,
    subtitleFiles: List<com.lagradost.cloudstream3.SubtitleFile>,
    selectedPlayer: String,
    displayTitle: String,
    history: WatchHistory,
    loadResponse: com.lagradost.cloudstream3.LoadResponse?,
    isLaunchingPlayer: Boolean,
    currentPlayingUrl: String?,
    filteredLinks: List<ExtractorLink>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    playVideo: (com.lagradost.cloudstream3.desktop.ui.VideoLaunchData?) -> Unit,
    onStatusChange: (String) -> Unit,
    onLaunching: (Boolean) -> Unit,
    onCurrentUrl: (String?) -> Unit,
    onEmbeddedError: (String?) -> Unit,
) {
    if (isLaunchingPlayer && currentPlayingUrl == null) return
    val validation = PlayerLinkHandler.validate(link, displayTitle)
    if (validation.isFailure) {
        onStatusChange(validation.exceptionOrNull()?.message ?: "Invalid stream")
        return
    }
    onLaunching(true)
    onCurrentUrl(link.url)

    val effectivePlayer = if (selectedPlayer == "vlc" && PlayerLinkHandler.shouldPreferMpv(link)) "mpv" else selectedPlayer
    onStatusChange("Launching ${effectivePlayer.uppercase()}...")

    val latestHistory = DesktopDataStore.getEpisodeWatched(history.parentId, history.episodeId) ?: history
    val isLive = loadResponse?.type == com.lagradost.cloudstream3.TvType.Live
    val startSec = if (isLive) 0L else PlayerLinkHandler.resumeStartSeconds(latestHistory.position, latestHistory.duration)
    val startMs = startSec * 1000L

    if (effectivePlayer == "vlc") {
        coroutineScope.launch {
            val result = vlcPlayer.play(link, displayTitle, subtitles, startMs)
            if (result.isSuccess) {
                onStatusChange("Playing: ${link.name}")
            } else {
                onStatusChange("Could not start player.")
                onLaunching(false)
                onCurrentUrl(null)
            }
        }
    } else {
        val initialIndex = links.indexOfFirst { it.url == link.url }.coerceAtLeast(0)
        playVideo(
            com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                links = links,
                initialIndex = initialIndex,
                title = displayTitle,
                subtitles = subtitleFiles,
                startPositionMs = startMs,
                history = history,
                loadResponse = loadResponse,
                onError = { err -> onEmbeddedError(err) },
                onClosed = {
                    onLaunching(false)
                    onCurrentUrl(null)
                    onStatusChange("Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available.")
                },
            ),
        )
        onStatusChange("Playing in embedded player: ${link.name}")
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = DesktopUi.Accent)
                Spacer(modifier = Modifier.width(14.dp))
            }
            Text(statusText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary, modifier = Modifier.weight(1f))
            if (isScraping) {
                FilledTonalButton(
                    onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF3D2028), contentColor = Color(0xFFFF8A8A)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSelector(selectedPlayer: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("Player", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("mpv", "vlc").forEach { id ->
                FilterChip(
                    selected = selectedPlayer == id,
                    onClick = { onSelect(id) },
                    label = { Text(id.uppercase()) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DesktopUi.AccentSoft, selectedLabelColor = DesktopUi.Accent),
                )
            }
        }
    }
}

@Composable
private fun StreamLinkCard(link: ExtractorLink, isBusy: Boolean, onPlay: () -> Unit, onCopy: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.01f else 1f, tween(150), label = "linkScale")

    Surface(
        modifier = Modifier.fillMaxWidth().scale(scale).hoverable(interaction),
        shape = RoundedCornerShape(12.dp),
        color = if (hovered) DesktopUi.SurfaceElevated else DesktopUi.SurfaceCard,
        tonalElevation = if (hovered) 6.dp else 2.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(link.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = DesktopUi.TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildString {
                        append(link.quality.toString())
                        append(" · ")
                        append(
                            if (link.isM3u8) {
                                "HLS (Best for Streaming)"
                            } else if (link.isDash) {
                                "DASH (Best for Streaming)"
                            } else {
                                "Direct (Best for Download)"
                            },
                        )
                    },
                    color = DesktopUi.Accent,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onCopy, enabled = !isBusy, shape = RoundedCornerShape(10.dp)) { Text("Copy") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onPlay,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.Accent, contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play")
            }
        }
    }
}
