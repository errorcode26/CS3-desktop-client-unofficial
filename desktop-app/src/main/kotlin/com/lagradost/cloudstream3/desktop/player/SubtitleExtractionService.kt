package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.common.logging.AppLogger
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

object SubtitleExtractionService {
    suspend fun searchSubtitles(
        query: String,
        lang: String?,
        season: Int?,
        episode: Int?
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val search = com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch(
            query = query,
            lang = lang,
            seasonNumber = season,
            epNumber = episode,
        )

        val allResults = mutableListOf<Map<String, Any?>>()
        for (provider in AccountManager.subtitleProviders) {
            val auth = AccountManager.cachedAccounts[provider.idPrefix]?.firstOrNull()
            val subList = SafePluginInvoker.invokeOrNull(
                tag = "SubSearch:${provider.name}",
                providerName = provider.name,
                timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
            ) {
                provider.search(auth, search)
            }
            subList?.forEach { sub ->
                allResults.add(
                    mapOf(
                        "idPrefix" to sub.idPrefix,
                        "name" to sub.name,
                        "lang" to sub.lang,
                        "data" to sub.data,
                        "source" to sub.source,
                        "seasonNumber" to sub.seasonNumber,
                        "epNumber" to sub.epNumber,
                    ),
                )
            }
        }
        return@withContext allResults
    }

    suspend fun downloadAndExtractSubtitle(
        idPrefix: String,
        data: String,
        name: String,
        lang: String,
        source: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val provider = AccountManager.subtitleProviders.firstOrNull { it.idPrefix == idPrefix } ?: return@withContext null
            val auth = AccountManager.cachedAccounts[idPrefix]?.firstOrNull()

            val sub = SubtitleEntity(
                idPrefix = idPrefix,
                name = name,
                data = data,
                lang = lang,
                source = source,
            )

            val fileUrl = SafePluginInvoker.invokeOrNull(
                tag = "SubLoad:${provider.name}",
                providerName = provider.name,
                timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
            ) {
                provider.load(auth, sub)
            } ?: return@withContext null

            var finalUrl: String = fileUrl
            val cleanUrl = fileUrl.substringBefore("?")
            if (cleanUrl.endsWith(".zip", ignoreCase = true)) {
                val zipFile = if (fileUrl.startsWith("http", ignoreCase = true)) {
                    val tmp = File.createTempFile("sub", ".zip")
                    val res = com.lagradost.cloudstream3.app.get(fileUrl).okhttpResponse
                    val bytes = res.body.bytes()
                    if (bytes.isNotEmpty()) {
                        tmp.writeBytes(bytes)
                        tmp
                    } else {
                        null
                    }
                } else if (fileUrl.startsWith("file://", ignoreCase = true)) {
                    File(URI(fileUrl))
                } else {
                    File(fileUrl)
                }

                if (zipFile != null && zipFile.exists()) {
                    ZipFile(zipFile).use { zip ->
                        val entry = zip.entries().toList().firstOrNull {
                            it.name.endsWith(".srt", true) || it.name.endsWith(".vtt", true) || it.name.endsWith(".ass", true)
                        }
                        if (entry != null) {
                            val ext = "." + entry.name.substringAfterLast('.', "srt")
                            val extracted = File.createTempFile("sub_ext", ext)
                            zip.getInputStream(entry).use { input ->
                                extracted.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            finalUrl = extracted.absolutePath
                        }
                    }
                }
            }
            return@withContext finalUrl.replace("\\", "/")
        } catch (e: Exception) {
            AppLogger.e("SubtitleExtractionService", "downloadAndExtractSubtitle error: ${e.message}", e)
            return@withContext null
        }
    }
}
