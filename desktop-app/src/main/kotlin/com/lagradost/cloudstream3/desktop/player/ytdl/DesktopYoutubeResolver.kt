package com.lagradost.cloudstream3.desktop.player.ytdl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

object DesktopYoutubeResolver {

    private val jsonMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun isYoutubeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.ROOT)
        return DesktopYtDlpBinary.isYouTubeUrl(url) ||
            lower.contains("youtube.com/channel/") ||
            lower.contains("youtube.com/@") ||
            lower.contains("youtube.com/c/") ||
            lower.contains("youtube.com/playlist") ||
            lower.contains("youtube.com/user/")
    }

    fun isYoutubeChannelUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("/channel/") ||
            lower.contains("/@") ||
            lower.contains("/c/") ||
            lower.contains("/user/")
    }

    fun isYoutubeChannelOrPlaylistUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.ROOT)
        return isYoutubeChannelUrl(url) || lower.contains("/playlist")
    }

    suspend fun resolve(url: String, fallbackName: String? = null): LoadResponse? = withContext(Dispatchers.IO) {
        val ytdl = DesktopYtDlpBinary()
        if (!ytdl.isInstalled()) {
            AppLogger.w("DesktopYoutubeResolver", "yt-dlp binary is not installed, queuing download...")
            ytdl.downloadWithManager()
            return@withContext null
        }

        val targetUrl = if (isYoutubeChannelUrl(url)) {
            val clean = url.trimEnd('/')
            if (!clean.endsWith("/videos") && !clean.contains("/videos?")) {
                "$clean/videos"
            } else {
                url
            }
        } else {
            url
        }

        val exe = ytdl.getBinaryFile()
        val isChannelOrPlaylist = isYoutubeChannelOrPlaylistUrl(targetUrl)

        val args = if (isChannelOrPlaylist) {
            listOf(
                exe.absolutePath,
                "--flat-playlist",
                "--dump-single-json",
                "--no-warnings",
                "--playlist-end", "50",
                targetUrl
            )
        } else {
            listOf(
                exe.absolutePath,
                "--dump-single-json",
                "--no-warnings",
                targetUrl
            )
        }

        try {
            AppLogger.i("DesktopYoutubeResolver", "Resolving YouTube metadata via yt-dlp: $url")
            val process = ProcessBuilder(args)
                .redirectErrorStream(false)
                .start()

            val stdoutDeferred = async(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderrDeferred = async(Dispatchers.IO) {
                process.errorStream.bufferedReader().use { it.readText() }
            }

            val completed = withTimeoutOrNull(25_000L) {
                process.waitFor()
            }

            if (completed == null) {
                process.destroyForcibly()
                AppLogger.w("DesktopYoutubeResolver", "yt-dlp execution timed out after 25s for $url")
                return@withContext null
            }

            val stdout = stdoutDeferred.await()
            if (stdout.isBlank()) {
                val stderr = stderrDeferred.await()
                AppLogger.w("DesktopYoutubeResolver", "yt-dlp returned empty stdout. Stderr: $stderr")
                return@withContext null
            }

            val rootNode = jsonMapper.readTree(stdout)
            return@withContext parseYtdlResponse(rootNode, url, fallbackName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.e("DesktopYoutubeResolver", "Failed to resolve YouTube metadata via yt-dlp", e)
            null
        }
    }

    private fun parseYtdlResponse(root: JsonNode, originalUrl: String, fallbackName: String?): LoadResponse {
        val type = root.path("_type").asText("")
        val isPlaylistOrChannel = type == "playlist" || root.has("entries") || isYoutubeChannelOrPlaylistUrl(originalUrl)

        if (isPlaylistOrChannel) {
            val title = root.path("title").asText().takeIf { it.isNotBlank() }
                ?: root.path("channel").asText().takeIf { it.isNotBlank() }
                ?: fallbackName
                ?: "YouTube Channel"
            val description = root.path("description").asText("")
            val channel = root.path("channel").asText(title)
            val thumbnailsNode = root.path("thumbnails")
            var bannerUrl: String? = null
            if (thumbnailsNode.isArray && thumbnailsNode.size() > 0) {
                bannerUrl = thumbnailsNode.get(thumbnailsNode.size() - 1).path("url").asText(null)
            }
            val avatarUrl = root.path("channel_avatar").asText().takeIf { it.isNotBlank() }
                ?: root.path("avatar").asText().takeIf { it.isNotBlank() }
                ?: bannerUrl

            val entriesNode = root.path("entries")
            val episodes = mutableListOf<Episode>()
            if (entriesNode.isArray) {
                var epIndex = 1
                for (entry in entriesNode) {
                    val entryId = entry.path("id").asText("").trim()
                    val rawUrl = entry.path("url").asText("").trim()

                    // Skip channel shelf objects or non-video items
                    if (entryId.startsWith("UC") || (entryId.length != 11 && !rawUrl.contains("watch?v="))) {
                        continue
                    }

                    val entryUrl = when {
                        rawUrl.startsWith("http") && rawUrl.contains("watch?v=") -> rawUrl
                        entryId.length == 11 -> "https://www.youtube.com/watch?v=$entryId"
                        rawUrl.length == 11 -> "https://www.youtube.com/watch?v=$rawUrl"
                        else -> continue
                    }
                    val entryTitle = entry.path("title").asText("").takeIf { it.isNotBlank() } ?: "Episode $epIndex"
                    val entryDuration = entry.path("duration").asDouble(0.0).toInt()
                    val entryThumbs = entry.path("thumbnails")
                    var entryPoster: String? = null
                    if (entryThumbs.isArray && entryThumbs.size() > 0) {
                        entryPoster = entryThumbs.get(entryThumbs.size() - 1).path("url").asText(null)
                    }
                    val vidId = if (entryId.length == 11) entryId else entryUrl.substringAfter("watch?v=").substringBefore("&").take(11)
                    if (entryPoster.isNullOrBlank() && vidId.isNotBlank()) {
                        entryPoster = "https://i.ytimg.com/vi/$vidId/hqdefault.jpg"
                    }

                    @Suppress("DEPRECATION_ERROR")
                    episodes.add(
                        Episode(
                            data = entryUrl,
                            name = entryTitle,
                            season = 1,
                            episode = epIndex,
                            posterUrl = entryPoster,
                            runTime = if (entryDuration > 0) entryDuration else null
                        )
                    )
                    epIndex++
                }
            }

            val distinctEpisodes = episodes.distinctBy { it.data }
                .mapIndexed { idx, ep -> ep.copy(episode = idx + 1) }

            @Suppress("DEPRECATION_ERROR")
            return TvSeriesLoadResponse(
                name = title,
                url = originalUrl,
                apiName = "YouTube",
                type = TvType.TvSeries,
                episodes = distinctEpisodes,
                comingSoon = distinctEpisodes.isEmpty()
            ).apply {
                this.plot = description
                this.posterUrl = avatarUrl
                this.backgroundPosterUrl = bannerUrl
                this.tags = listOf("Channel")
                this.actors = listOf(ActorData(Actor(channel, avatarUrl ?: "")))
            }
        } else {
            val title = root.path("title").asText(fallbackName ?: "YouTube Video")
            val description = root.path("description").asText("")
            val isLive = root.path("is_live").asBoolean(false) ||
                root.path("live_status").asText("").equals("is_live", ignoreCase = true)
            val durationSecs = root.path("duration").asDouble(0.0).toInt()
            val uploader = root.path("uploader").asText(root.path("channel").asText(""))
            val thumbnailsNode = root.path("thumbnails")
            var posterUrl: String? = null
            if (thumbnailsNode.isArray && thumbnailsNode.size() > 0) {
                posterUrl = thumbnailsNode.get(thumbnailsNode.size() - 1).path("url").asText(null)
            }
            val id = root.path("id").asText("")
            if (posterUrl.isNullOrBlank() && id.isNotBlank()) {
                posterUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            }

            @Suppress("DEPRECATION_ERROR")
            return MovieLoadResponse(
                name = title,
                url = originalUrl,
                apiName = "YouTube",
                type = if (isLive) TvType.Live else TvType.Others,
                dataUrl = originalUrl,
                posterUrl = posterUrl
            ).apply {
                this.plot = description
                this.duration = if (!isLive && durationSecs > 0) durationSecs / 60 else null
                this.backgroundPosterUrl = posterUrl
                if (isLive) {
                    this.tags = listOf("Live")
                }
                if (uploader.isNotBlank()) {
                    this.actors = listOf(ActorData(Actor(uploader, posterUrl ?: "")))
                }
            }
        }
    }
}
