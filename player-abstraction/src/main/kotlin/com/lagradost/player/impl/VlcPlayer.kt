package com.lagradost.player.impl

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.player.api.MediaPlayer
import com.lagradost.player.api.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class VlcPlayer : MediaPlayer {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stdoutJob: Job? = null
    private val currentProcess = AtomicReference<Process?>(null)

    private fun killCurrent() {
        currentProcess.getAndSet(null)?.let { proc ->
            try {
                proc.destroyForcibly()
                AppLogger.i("VlcPlayer: Killed previous VLC process.")
            } catch (_: Exception) {}
        }
        stdoutJob?.cancel()
        _state.update { it.copy(isPlaying = false, isPaused = false, currentUrl = null) }
    }

    override suspend fun play(link: ExtractorLink, title: String?, subtitles: List<String>, startPositionMs: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                val validated = PlayerLinkHandler.validate(link, title).getOrElse {
                    _state.update { state -> state.copy(isLoading = false, error = it.message) }
                    return@withContext Result.failure(it)
                }

                val vlcExecutable = findVlcExecutable()
                    ?: run {
                        _state.update { state -> state.copy(isLoading = false, error = "VLC not found") }
                        return@withContext Result.failure(IllegalStateException("VLC not found. Install VLC or use MPV."))
                    }

                val startSec = startPositionMs / 1000L

                val args = mutableListOf(vlcExecutable)

                val userAgent = validated.headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value
                if (!userAgent.isNullOrBlank()) {
                    args.add("--http-user-agent=$userAgent")
                }

                val referer = validated.headers.entries.firstOrNull {
                    it.key.equals("referer", true) || it.key.equals("referrer", true)
                }?.value
                if (!referer.isNullOrBlank()) {
                    args.add("--http-referrer=$referer")
                }

                subtitles.filter { it.isNotBlank() }.forEach { sub ->
                    args.add("--sub-file=$sub")
                }

                if (startSec > 0) {
                    args.add("--start-time=$startSec")
                }

                // Force VLC to display the correct title instead of raw URL strings
                args.add("--meta-title=${validated.displayTitle}")
                args.add("--video-title=${validated.displayTitle}")

                if (validated.useUrlFile) {
                    val listFile = PlayerLinkHandler.writeUrlListFile(
                        "cloudstream_vlc_url_",
                        validated.displayTitle,
                        validated.url,
                    )
                    args.add(listFile.absolutePath)
                } else {
                    args.add(validated.url)
                }

                AppLogger.i("Launching VLC (${validated.streamKind}): ${validated.displayTitle}")

                killCurrent()

                val process = ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start()

                currentProcess.set(process)
                _state.update { it.copy(isPlaying = true, isLoading = false, currentUrl = validated.url) }

                stdoutJob = playerScope.launch(Dispatchers.IO) {
                    try {
                        process.inputStream.bufferedReader().use { reader ->
                            reader.lineSequence().forEach { AppLogger.i("VLC: $it") }
                        }
                        process.waitFor()
                    } catch (e: Exception) {
                        AppLogger.i("VLC process ended: ${e.message}")
                    } finally {
                        if (currentProcess.compareAndSet(process, null)) {
                            _state.update { it.copy(isPlaying = false, isFinished = true) }
                        }
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                AppLogger.i("VLC launch failed: ${e.message}")
                _state.update { it.copy(isPlaying = false, error = e.message) }
                Result.failure(e)
            }
        }

    override fun playLocal(file: File) {
        playerScope.launch {
            play(
                link = com.lagradost.cloudstream3.utils.newExtractorLink(
                    source = "Local",
                    name = "Local File",
                    url = file.absolutePath,
                ),
                title = file.name,
                subtitles = emptyList(),
                startPositionMs = 0,
            )
        }
    }

    override fun pause() {
        // VLC remote control requires extra setup (rc interface). Ignored for now.
    }

    override fun resume() {
        // Ignored for now.
    }

    override fun seek(positionMs: Long) {
        // Ignored for now.
    }

    override fun stop() {
        killCurrent()
    }

    override fun destroy() {
        killCurrent()
    }

    private fun findVlcExecutable(): String? {
        try {
            fun probeCommand(vararg cmd: String): String? {
                return try {
                    val process = ProcessBuilder(cmd.toList())
                        .redirectErrorStream(true)
                        .start()
                    process.inputStream.bufferedReader().use { it.readLine()?.trim()?.takeIf { it.isNotBlank() } }
                } catch (_: Exception) {
                    null
                }
            }

            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                probeCommand("where", "vlc")?.let {
                    if (File(it).exists()) return it
                }
                val bases = listOfNotNull(
                    System.getenv("ProgramFiles"),
                    System.getenv("ProgramFiles(x86)"),
                    System.getenv("ProgramW6432"),
                )
                for (base in bases) {
                    val p = File(base, "VideoLAN\\VLC\\vlc.exe")
                    if (p.exists()) return p.absolutePath
                }
            } else {
                probeCommand("which", "vlc")?.let {
                    if (File(it).exists()) return it
                }
                listOf(
                    "/Applications/VLC.app/Contents/MacOS/VLC",
                    "/usr/local/bin/vlc",
                    "/opt/homebrew/bin/vlc",
                ).forEach { if (File(it).exists()) return it }
            }
        } catch (_: Exception) {
        }
        return null
    }
}
