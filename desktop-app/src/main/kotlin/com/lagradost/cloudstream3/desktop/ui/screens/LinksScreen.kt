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
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import com.lagradost.player.impl.VlcPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val vlcPlayer = VlcPlayer()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksSidePanel(provider: MainAPI, dataUrl: String, history: WatchHistory, loadResponse: com.lagradost.cloudstream3.LoadResponse?, onClose: () -> Unit) {
    val links = remember { mutableStateListOf<ExtractorLink>() }
    val subtitles = remember { mutableStateListOf<SubtitleFile>() }
    var statusText by remember { mutableStateOf("Finding streams for you...") }
    var isScraping by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current
    var selectedPlayer by remember { mutableStateOf(DesktopDataStore.getKey<String>("preferred_player") ?: "mpv") }
    var isLaunchingPlayer by remember { mutableStateOf(false) }
    var playerLaunchError by remember { mutableStateOf<String?>(null) }
    var scrapeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var embeddedError by remember { mutableStateOf<String?>(null) }

    var selectedQuality by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf("All") }
    val availableTypes = listOf("All", "HLS (Fast Stream)", "MP4 (Downloadable)")
    var currentPlayingUrl by remember { mutableStateOf<String?>(null) }

    val availableQualities = remember(links.size) { links.map { it.quality.toString() }.distinct().sorted() }
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

    DisposableEffect(Unit) {
        onDispose {
            // no-op, player manages its own lifecycle or we could stop it if desired
        }
    }

    val vlcState = vlcPlayer.state.collectAsState().value
    val isAnyPlaying = vlcState.isPlaying

    var lastVlcSavedPositionSec by remember { mutableStateOf(0L) }

    LaunchedEffect(vlcState.position) {
        val currentPositionMs = if (vlcState.isPlaying) {
            vlcState.position
        } else {
            0L
        }
        val currentDurationMs = if (vlcState.isPlaying) {
            vlcState.duration
        } else {
            0L
        }

        if (currentPositionMs > 0 && currentDurationMs > 0) {
            val currentPosSec = currentPositionMs / 1000L
            if (kotlin.math.abs(currentPosSec - lastVlcSavedPositionSec) >= 5) {
                lastVlcSavedPositionSec = currentPosSec
                val updatedHistory = history.copy(
                    position = currentPosSec,
                    duration = currentDurationMs / 1000L,
                    updateTime = System.currentTimeMillis(),
                )
                DesktopDataStore.setLastWatched(updatedHistory)
            }
        }
    }

    DisposableEffect(isAnyPlaying) {
        onDispose {
            if (!isAnyPlaying && vlcState.position > 0 && vlcState.duration > 0) {
                val finalPosSec = vlcState.position / 1000L
                val finalDurSec = vlcState.duration / 1000L
                val updatedHistory = history.copy(
                    position = finalPosSec,
                    duration = finalDurSec,
                    updateTime = System.currentTimeMillis(),
                )
                DesktopDataStore.setLastWatched(updatedHistory)
            }
        }
    }

    LaunchedEffect(isAnyPlaying) {
        if (!isAnyPlaying) {
            if (statusText == "Player started." || statusText.startsWith("Playing:")) {
                statusText = "Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available."
            }
            isLaunchingPlayer = false
            currentPlayingUrl = null
        }
    }

    val playLink: (ExtractorLink) -> Unit = { link ->
        if (!(isLaunchingPlayer && currentPlayingUrl == null)) {
            val validation = PlayerLinkHandler.validate(link, displayTitle)
            if (validation.isFailure) {
                statusText = validation.exceptionOrNull()?.message ?: "Invalid stream"
            } else {
                isLaunchingPlayer = true
                currentPlayingUrl = link.url
                val effectivePlayer =
                    if (selectedPlayer == "vlc" && PlayerLinkHandler.shouldPreferMpv(link)) {
                        "mpv"
                    } else {
                        selectedPlayer
                    }
                statusText = "Launching ${effectivePlayer.uppercase()}..."

                val latestHistory = DesktopDataStore.getEpisodeWatched(history.parentId, history.episodeId) ?: history
                val isLive = loadResponse?.type == com.lagradost.cloudstream3.TvType.Live
                val startSec = if (isLive) 0L else PlayerLinkHandler.resumeStartSeconds(latestHistory.position, latestHistory.duration)
                val startMs = startSec * 1000L

                val subUrls = subtitles.map { it.url }.filter { it.isNotBlank() }
                if (effectivePlayer == "vlc") {
                    coroutineScope.launch {
                        val result = vlcPlayer.play(link, displayTitle, subUrls, startMs)
                        if (result.isSuccess) {
                            statusText = "Playing: ${link.name}"
                            playerLaunchError = null
                        } else {
                            playerLaunchError = result.exceptionOrNull()?.message ?: "Failed to launch player"
                            statusText = "Could not start player."
                            isLaunchingPlayer = false
                            currentPlayingUrl = null
                        }
                    }
                } else {
                    val initialIndex = links.indexOfFirst { it.url == link.url }.coerceAtLeast(0)
                    playVideo(
                        com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                            links = links,
                            initialIndex = initialIndex,
                            title = displayTitle,
                            subtitles = subtitles.filter { it.url.isNotBlank() },
                            startPositionMs = startMs,
                            history = history,
                            loadResponse = loadResponse,
                            onError = { err ->
                                embeddedError = err
                            },
                            onClosed = {
                                isLaunchingPlayer = false
                                currentPlayingUrl = null
                                statusText = "Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available."
                            },
                        ),
                    )
                    statusText = "Playing in embedded player: ${link.name}"
                    // We don't set isLaunchingPlayer=false here because the embedded player is an overlay
                    // and we want it to block interaction until it closes.
                }
            }
        }
    }

    LaunchedEffect(vlcState.error, embeddedError) {
        val errorMessage = vlcState.error ?: embeddedError
        if (errorMessage != null) {
            val autoPlay = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
            val currentIndex = filteredLinks.indexOfFirst { it.url == currentPlayingUrl }
            val isVlcError = vlcState.error != null

            // Only auto-skip for VLC. Embedded player handles its own internal auto-skip.
            // If embeddedError is set, it means the embedded player completely ran out of links.
            if (autoPlay && isVlcError && currentIndex != -1 && currentIndex + 1 < filteredLinks.size) {
                val nextLink = filteredLinks[currentIndex + 1]
                statusText = "Link failed. Auto-trying next: ${nextLink.name}"
                embeddedError = null
                playerLaunchError = null
                // Small delay to prevent UI freezing if many links fail instantly
                delay(800)
                playLink(nextLink)
            } else {
                playerLaunchError = errorMessage
                statusText = "Playback failed: $errorMessage"
                isLaunchingPlayer = false
                currentPlayingUrl = null
                embeddedError = null
            }
        }
    }

    LaunchedEffect(dataUrl) {
        links.clear()
        subtitles.clear()
        isScraping = true
        statusText = "Finding streams for you..."
        playerLaunchError = null
        currentPlayingUrl = null

        scrapeJob = launch(Dispatchers.IO) {
            try {
                provider.loadLinks(
                    data = dataUrl,
                    isCasting = false,
                    subtitleCallback = { sub: SubtitleFile -> coroutineScope.launch { subtitles.add(sub) } },
                    callback = { link: ExtractorLink ->
                        coroutineScope.launch {
                            links.add(link)
                            statusText = "Found ${links.size} stream${if (links.size == 1) "" else "s"}..."
                        }
                    },
                )
                isScraping = false
                statusText = when {
                    links.isEmpty() -> "No streams found for this title."
                    else -> "Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                isScraping = false
                statusText = "Search stopped (${links.size} found)."
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading links", e)
                isScraping = false
                statusText = "Error: ${e.message}"
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
                    onStop = { scrapeJob?.cancel() },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
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
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
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
                    itemsIndexed(filteredLinks, key = { index, it -> "${it.name}-${it.url}-$index" }) { index, link ->
                        StreamLinkCard(
                            link = link,
                            isBusy = isLaunchingPlayer && currentPlayingUrl != link.url,
                            onPlay = {
                                playLink(link)
                            },
                            onCopy = {
                                if (link.url.isNotBlank()) {
                                    val selection = java.awt.datatransfer.StringSelection(link.url)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(selection, selection)
                                    statusText = "URL copied to clipboard."
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = DesktopUi.Accent,
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = DesktopUi.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (isScraping) {
                FilledTonalButton(
                    onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF3D2028),
                        contentColor = Color(0xFFFF8A8A),
                    ),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Text("Player", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedPlayer == "mpv",
                onClick = { onSelect("mpv") },
                label = { Text("MPV") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DesktopUi.AccentSoft,
                    selectedLabelColor = DesktopUi.Accent,
                ),
            )
            FilterChip(
                selected = selectedPlayer == "vlc",
                onClick = { onSelect("vlc") },
                label = { Text("VLC") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DesktopUi.AccentSoft,
                    selectedLabelColor = DesktopUi.Accent,
                ),
            )
        }
    }
}

@Composable
private fun StreamLinkCard(
    link: ExtractorLink,
    isBusy: Boolean,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.01f else 1f, tween(150), label = "linkScale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .hoverable(interaction),
        shape = RoundedCornerShape(12.dp),
        color = if (hovered) DesktopUi.SurfaceElevated else DesktopUi.SurfaceCard,
        tonalElevation = if (hovered) 6.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    link.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = DesktopUi.TextPrimary,
                )
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
            OutlinedButton(
                onClick = onCopy,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Copy")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onPlay,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesktopUi.Accent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play")
            }
        }
    }
}
