package com.lagradost.player.api

import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface MediaPlayer {
    val state: StateFlow<PlayerState>

    suspend fun play(link: ExtractorLink, title: String?, subtitles: List<String>, startPositionMs: Long): Result<Unit>
    fun playLocal(file: File)
    fun pause()
    fun resume()
    fun seek(positionMs: Long)
    fun stop()
    fun destroy()
}
