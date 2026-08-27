package com.lagradost.cloudstream3.desktop.discord

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicLong

object DiscordRpcManager {
    private const val TAG = "DiscordRpcManager"
    const val DEFAULT_CLIENT_ID = "1534799947826991266"

    private val client = DiscordIpcClient()

    // Single-threaded dispatcher: guarantees all IPC operations are sequential
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val mapper = jacksonObjectMapper()

    // Session start in UNIX seconds (what Discord expects)
    private val sessionStartEpochSec = System.currentTimeMillis() / 1000L

    private val lastUpdateTimestamp = AtomicLong(0L)

    sealed interface PresenceState {
        data object None : PresenceState
        data class Browsing(val screen: String, val extra: String? = null) : PresenceState
        data class Playing(
            val title: String,
            val episodeInfo: String?,
            val positionSeconds: Long,
            val durationSeconds: Long,
            val isPaused: Boolean,
            val posterUrl: String? = null,
            val isFullscreen: Boolean = false,
            val isLive: Boolean = false,
        ) : PresenceState
    }

    @Volatile private var currentState: PresenceState = PresenceState.None

    // CONFLATED: only the latest state matters; drops stale intermediate updates
    private val updateChannel = Channel<PresenceState>(Channel.CONFLATED)

    private var lastPlayingTitle: String? = null
    private var lastPlayingEpisode: String? = null
    private var lastPlayingPaused: Boolean? = null
    private var lastPlayingPositionSec: Long = -1L
    private var lastPlayingDurationSec: Long = -1L
    private var lastPlayingTimestampMs: Long = 0L
    private var lastPlayingFullscreen: Boolean = false

    // Track the last paused state sent to Discord so we can re-anchor on resume
    private var lastSentIsPaused: Boolean? = null

    // Track offline status and last connection attempt to prevent spam
    @Volatile private var lastConnectAttemptMs = 0L
    @Volatile private var hasLoggedOffline = false

    fun init() {
        // Single dispatcher worker — all IPC calls are serialized here
        scope.launch {
            AppLogger.d(TAG, "Starting DiscordRpcManager dispatcher worker...")
            for (state in updateChannel) {
                try {
                    processState(state)
                } catch (e: Throwable) {
                    AppLogger.d(TAG, "Error processing state $state: ${e.message}")
                }
            }
        }

        // Reconnect heartbeat — uses its own IO scope so it never starves the dispatch loop
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                delay(30_000L)
                val state = currentState
                if (isRpcEnabled() && state !is PresenceState.None) {
                    try {
                        if (!client.isConnected()) {
                            val connected = withTimeout(5_000) { client.connect(getActiveClientId()) }
                            if (connected) {
                                if (hasLoggedOffline) {
                                    AppLogger.i(TAG, "✓ Connected to Discord RPC")
                                    hasLoggedOffline = false
                                }
                                val payload = buildActivityPayload(state)
                                if (payload != null) client.sendActivity(payload)
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun updateBrowsing(screen: String, extra: String? = null) {
        val isEnabled = isRpcEnabled()
        val showBrowsing = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_DISCORD_RPC_SHOW_BROWSING) ?: true
        AppLogger.d(TAG, "updateBrowsing('$screen', extra='$extra') enabled=$isEnabled showBrowsing=$showBrowsing")
        if (!isEnabled || !showBrowsing) {
            if (currentState is PresenceState.Browsing) {
                currentState = PresenceState.None
                updateChannel.trySend(PresenceState.None)
            }
            return
        }
        val newState = PresenceState.Browsing(screen, extra)
        currentState = newState
        updateChannel.trySend(newState)
    }

    fun updatePlaying(
        title: String,
        episodeInfo: String?,
        positionSeconds: Long,
        durationSeconds: Long,
        isPaused: Boolean,
        posterUrl: String? = null,
        isLive: Boolean = false,
    ) {
        if (!isRpcEnabled()) {
            currentState = PresenceState.None
            updateChannel.trySend(PresenceState.None)
            return
        }

        val now = System.currentTimeMillis()
        val isMediaChanged = title != lastPlayingTitle || episodeInfo != lastPlayingEpisode
        val isPauseChanged = isPaused != lastPlayingPaused
        val isDurationChanged = durationSeconds > 0 && durationSeconds != lastPlayingDurationSec
        val elapsedSec = if (lastPlayingTimestampMs > 0 && !isPaused) (now - lastPlayingTimestampMs) / 1000L else 0L
        val expectedPosSec = lastPlayingPositionSec + elapsedSec
        val isSeekDetected = lastPlayingPositionSec >= 0 && kotlin.math.abs(positionSeconds - expectedPosSec) >= 2L
        val isResuming = lastSentIsPaused == true && !isPaused

        if (isMediaChanged || isPauseChanged || isDurationChanged || isSeekDetected || isResuming || lastPlayingPositionSec < 0) {
            AppLogger.d(TAG, "updatePlaying: title='$title' ep='$episodeInfo' pos=${positionSeconds}s dur=${durationSeconds}s live=$isLive paused=$isPaused")
            lastPlayingTitle = title
            lastPlayingEpisode = episodeInfo
            lastPlayingPaused = isPaused
            lastPlayingDurationSec = durationSeconds
            lastPlayingPositionSec = positionSeconds
            lastPlayingTimestampMs = now

            val newState = PresenceState.Playing(
                title = title,
                episodeInfo = episodeInfo,
                positionSeconds = positionSeconds.coerceAtLeast(0),
                durationSeconds = durationSeconds.coerceAtLeast(0),
                isPaused = isPaused,
                posterUrl = posterUrl,
                isFullscreen = lastPlayingFullscreen,
                isLive = isLive,
            )
            currentState = newState
            updateChannel.trySend(newState)
        }
    }

    fun updateFullscreen(isFullscreen: Boolean) {
        if (!isRpcEnabled()) return
        lastPlayingFullscreen = isFullscreen
        val state = currentState
        if (state is PresenceState.Playing && state.isFullscreen != isFullscreen) {
            AppLogger.d(TAG, "updateFullscreen: $isFullscreen")
            val newState = state.copy(isFullscreen = isFullscreen)
            currentState = newState
            updateChannel.trySend(newState)
        }
    }

    fun onPlayerStopped() {
        AppLogger.d(TAG, "onPlayerStopped")
        lastPlayingTitle = null
        lastPlayingEpisode = null
        lastPlayingPaused = null
        lastPlayingPositionSec = -1L
        lastPlayingDurationSec = -1L
        lastPlayingTimestampMs = 0L
        lastSentIsPaused = null

        val showBrowsing = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_DISCORD_RPC_SHOW_BROWSING) ?: true
        val newState = if (isRpcEnabled() && showBrowsing) PresenceState.Browsing("Home") else PresenceState.None
        currentState = newState
        updateChannel.trySend(newState)
    }

    fun clearPresence() {
        AppLogger.d(TAG, "clearPresence")
        currentState = PresenceState.None
        updateChannel.trySend(PresenceState.None)
    }

    fun onSettingsChanged() {
        scope.launch {
            val enabled = isRpcEnabled()
            AppLogger.d(TAG, "onSettingsChanged: enabled=$enabled")
            if (!enabled) {
                client.clearActivity()
                client.close()
                currentState = PresenceState.None
                updateChannel.trySend(PresenceState.None)
            } else {
                lastConnectAttemptMs = 0L // reset backoff
                val state = currentState
                val toSend = if (state is PresenceState.None) PresenceState.Browsing("Home") else state
                currentState = toSend
                updateChannel.trySend(toSend)
            }
        }
    }

    fun shutdown() {
        try {
            client.clearActivity()
            Thread.sleep(60)
        } catch (_: Exception) {}
        try {
            client.close()
        } catch (_: Exception) {}
    }

    // ---- Internal ----

    private fun isRpcEnabled(): Boolean =
        DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_DISCORD_RPC_ENABLED) ?: false

    private fun getActiveClientId(): String =
        DesktopDataStore.getKey<String>(DesktopDataStore.PREF_DISCORD_CUSTOM_APP_ID)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID

    private suspend fun processState(state: PresenceState) {
        if (!isRpcEnabled() || state is PresenceState.None) {
            if (client.isConnected()) {
                client.clearActivity()
                client.close()
            }
            return
        }

        if (!client.isConnected()) {
            val now = System.currentTimeMillis()
            // Backoff: do not probe named pipes more than once per 20 seconds during UI events
            if (now - lastConnectAttemptMs < 20_000L) {
                return
            }
            lastConnectAttemptMs = now

            val ok = try {
                withTimeout(5_000) { client.connect(getActiveClientId()) }
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                if (!hasLoggedOffline) {
                    AppLogger.i(TAG, "Discord client not detected (Rich Presence standing by)")
                    hasLoggedOffline = true
                }
                return
            } else {
                AppLogger.i(TAG, "✓ Connected to Discord RPC")
                hasLoggedOffline = false
            }
        }

        val payload = buildActivityPayload(state) ?: return

        // Respect Discord's 5 updates/20s rate limit (one per ~500ms minimum)
        val now = System.currentTimeMillis()
        val elapsed = now - lastUpdateTimestamp.get()
        if (elapsed < 500L) delay(500L - elapsed)
        lastUpdateTimestamp.set(System.currentTimeMillis())

        val success = client.sendActivity(payload)
        if (success && state is PresenceState.Playing) {
            lastSentIsPaused = state.isPaused
        }
    }

    private const val DEFAULT_ASSET_KEY = "logo"

    private fun formatDuration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val hrs = s / 3600
        val mins = (s % 3600) / 60
        val secs = s % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    private fun String.limit(max: Int): String =
        if (length > max) substring(0, max - 1) + "…" else this

    private fun buildActivityPayload(state: PresenceState): String? {
        val showTitle = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_DISCORD_RPC_SHOW_TITLE) ?: true
        val showProgress = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_DISCORD_RPC_SHOW_PROGRESS) ?: true

        val activityMap = mutableMapOf<String, Any>()

        when (state) {
            is PresenceState.Browsing -> {
                activityMap["type"] = 0 // PLAYING
                val detailsText = when (state.screen.lowercase()) {
                    "settings" -> "Tuning the flux capacitor"
                    "search" -> "Searching for something to watch for 45 minutes"
                    "extensions" -> "Hoarding every plugin in existence"
                    "history", "watch history" -> "Revisiting past life choices"
                    "library" -> "Organizing the watchlist archive"
                    "explore", "explore & catalogs" -> "Scouring the global catalog"
                    "details" -> if (!state.extra.isNullOrBlank()) "Deciding if '${state.extra}' is worth 2 hours" else "Deciding if this title is worth 2 hours"
                    else -> "Doom-scrolling through movies"
                }
                activityMap["details"] = detailsText.limit(128)
                activityMap["state"] = "CloudStream Desktop"
                // Timestamps: Discord expects UNIX seconds, not milliseconds
                activityMap["timestamps"] = mapOf("start" to sessionStartEpochSec)
                activityMap["assets"] = mapOf(
                    "large_image" to DEFAULT_ASSET_KEY,
                    "large_text" to "CloudStream Desktop",
                )
            }

            is PresenceState.Playing -> {
                activityMap["type"] = 3 // WATCHING

                val displayTitle = if (showTitle) state.title else "Media"
                activityMap["details"] = displayTitle.limit(128)

                val episodeText = if (showTitle && !state.episodeInfo.isNullOrBlank()) state.episodeInfo else null

                // Timestamps: Discord expects UNIX seconds
                // Paused: send frozen start-only timestamp so progress bar stops at current position
                // Playing: send start + end for the full progress bar
                val nowSec = System.currentTimeMillis() / 1000L
                val fsSuffix = if (state.isFullscreen) " (Fullscreen)" else ""

                if (state.isLive) {
                    val liveState = if (state.isPaused) "🔴 Live Stream • Paused" else "🔴 Live Stream"
                    activityMap["state"] = ((if (episodeText != null) "$episodeText • $liveState" else liveState) + fsSuffix).limit(128)
                    if (!state.isPaused) {
                        // Live streams count elapsed watching time
                        activityMap["timestamps"] = mapOf("start" to (nowSec - state.positionSeconds.coerceAtLeast(0)))
                    }
                } else if (state.isPaused) {
                    val timeInfo = if (state.durationSeconds > 0) {
                        "${formatDuration(state.positionSeconds)} / ${formatDuration(state.durationSeconds)} • Paused"
                    } else {
                        "Paused"
                    }
                    activityMap["state"] = ((if (episodeText != null) "$episodeText • $timeInfo" else timeInfo) + fsSuffix).limit(128)
                    // When paused, do NOT set timestamps so Discord's internal live timer stays static
                } else {
                    if (episodeText != null) {
                        activityMap["state"] = (episodeText + fsSuffix).limit(128)
                    } else if (state.isFullscreen) {
                        activityMap["state"] = "Watching in Fullscreen"
                    }
                    if (state.durationSeconds > 0 && showProgress) {
                        val startSec = nowSec - state.positionSeconds
                        val endSec = startSec + state.durationSeconds
                        activityMap["timestamps"] = mapOf("start" to startSec, "end" to endSec)
                    } else {
                        activityMap["timestamps"] = mapOf("start" to (nowSec - state.positionSeconds))
                    }
                }

                val assetsMap = mutableMapOf<String, String>()
                if (!state.posterUrl.isNullOrBlank() && state.posterUrl.startsWith("http")) {
                    assetsMap["large_image"] = state.posterUrl
                    assetsMap["large_text"] = displayTitle.limit(128)
                    assetsMap["small_image"] = DEFAULT_ASSET_KEY

                    val statusText = when {
                        state.isLive -> "🔴 Live Broadcast"
                        state.isPaused -> "Paused"
                        state.isFullscreen -> "Watching in Fullscreen"
                        else -> "CloudStream Desktop"
                    }
                    assetsMap["small_text"] = statusText
                } else {
                    assetsMap["large_image"] = DEFAULT_ASSET_KEY
                    assetsMap["large_text"] = displayTitle.limit(128)
                }
                activityMap["assets"] = assetsMap
            }

            is PresenceState.None -> return null
        }

        return try {
            mapper.writeValueAsString(activityMap)
        } catch (e: Exception) {
            AppLogger.e(TAG, "buildActivityPayload serialize error: ${e.message}", e)
            null
        }
    }
}
