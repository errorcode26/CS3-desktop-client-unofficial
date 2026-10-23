package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.subtitles.LanguageNormalizer
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class SubtitleCatApi : SubtitleAPI() {
    override val name = "SubtitleCat"
    override val idPrefix = "subtitlecat"

    override val icon = null
    override val hasInApp = false
    override val requiresLogin = false

    companion object {
        private const val TAG = "SubtitleCatApi"
        private const val BASE_URL = "https://www.subtitlecat.com"
        private val USER_AGENT_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
        )
        private val ORIG_REGEX = Regex("""translate_from_server_folder\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*\)""")
    }

    private data class CandidateRelease(
        val detailUrl: String,
        val title: String,
        val originLang: String?,
    )

    override suspend fun search(
        auth: AuthData?,
        query: SubtitleSearch,
    ): List<SubtitleEntity>? = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.query.replace(Regex("[:\\-_/\\\\]"), " ").replace(Regex("\\s+"), " ").trim()
            val searchQuery = when {
                query.seasonNumber != null && query.epNumber != null -> {
                    val s = query.seasonNumber.toString().padStart(2, '0')
                    val e = query.epNumber.toString().padStart(2, '0')
                    "$cleanQuery S${s}E$e"
                }
                query.year != null && query.year!! > 0 -> {
                    "$cleanQuery ${query.year}"
                }
                else -> cleanQuery
            }

            val encoded = URLEncoder.encode(searchQuery, "UTF-8")
            val searchUrl = "$BASE_URL/index.php?search=$encoded"

            AppLogger.i(TAG, "Searching for '$searchQuery' -> $searchUrl")
            val searchResp = app.get(searchUrl, headers = USER_AGENT_HEADERS, timeout = 10000L).text
            val doc = Jsoup.parse(searchResp)

            val rows = doc.select("table.sub-table tbody tr")
            if (rows.isEmpty()) {
                AppLogger.d(TAG, "No search rows found for '$searchQuery'")
                return@withContext emptyList()
            }

            val candidates = mutableListOf<CandidateRelease>()
            for (row in rows) {
                val linkEl = row.selectFirst("td a[href^=subs/]") ?: row.selectFirst("td a[href*=/subs/]") ?: continue
                val rawHref = linkEl.attr("href")
                val detailUrl = if (rawHref.startsWith("http")) rawHref else "$BASE_URL/${rawHref.removePrefix("/")}"
                val title = linkEl.text().trim()

                val cellText = row.selectFirst("td")?.text() ?: ""
                val originLang = if (cellText.contains("translated from ", ignoreCase = true)) {
                    cellText.substringAfter("translated from ", "").substringBefore(")", "").trim()
                } else {
                    null
                }

                candidates.add(CandidateRelease(detailUrl, title, originLang))
                if (candidates.size >= 4) break
            }

            if (candidates.isEmpty()) return@withContext emptyList()

            val entities = coroutineScope {
                candidates.map { candidate ->
                    async {
                        fetchSubtitlesForRelease(candidate, query)
                    }
                }.awaitAll().flatten()
            }

            AppLogger.i(TAG, "Resolved ${entities.size} subtitles for '$searchQuery'")
            return@withContext entities
        } catch (e: Exception) {
            AppLogger.e(TAG, "Search error: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    private suspend fun fetchSubtitlesForRelease(
        candidate: CandidateRelease,
        query: SubtitleSearch,
    ): List<SubtitleEntity> {
        val results = mutableListOf<SubtitleEntity>()
        try {
            val pageResp = app.get(candidate.detailUrl, headers = USER_AGENT_HEADERS, timeout = 10000L).text
            val doc = Jsoup.parse(pageResp)

            val subSingles = doc.select(".sub-single")
            var origAdded = false

            for (single in subSingles) {
                val dlBtn = single.selectFirst("a.green-link")
                if (dlBtn != null) {
                    val rawHref = dlBtn.attr("href")
                    if (rawHref.isNotBlank() && rawHref.contains(".srt", ignoreCase = true)) {
                        val fullUrl = if (rawHref.startsWith("http")) rawHref else "$BASE_URL/${rawHref.removePrefix("/")}"
                        val flagAlt = single.selectFirst("img.flag")?.attr("alt")?.trim() ?: ""
                        val langName = single.select("span").getOrNull(1)?.text()?.trim() ?: ""
                        val rawLang = flagAlt.ifBlank { dlBtn.id().removePrefix("download_") }.ifBlank { langName }

                        if (matchesLanguageFilter(rawLang, query.lang)) {
                            val resolvedLang = langName.ifBlank { rawLang }
                            results.add(
                                SubtitleEntity(
                                    idPrefix = idPrefix,
                                    name = "$resolvedLang • ${candidate.title}",
                                    lang = rawLang,
                                    data = fullUrl,
                                    source = name,
                                    epNumber = query.epNumber,
                                    seasonNumber = query.seasonNumber,
                                    year = query.year,
                                ),
                            )
                        }
                    }
                }

                if (!origAdded) {
                    val btn = single.selectFirst("button[onclick*='translate_from_server_folder']")
                    if (btn != null) {
                        val onclick = btn.attr("onclick")
                        val match = ORIG_REGEX.find(onclick)
                        if (match != null) {
                            val origFile = match.groupValues[2]
                            val origFolder = match.groupValues[3]
                            val fullOrigUrl = "$BASE_URL/${origFolder.removePrefix("/").removeSuffix("/")}/$origFile"
                            val originLang = candidate.originLang ?: "English"

                            if (matchesLanguageFilter(originLang, query.lang)) {
                                results.add(
                                    SubtitleEntity(
                                        idPrefix = idPrefix,
                                        name = "$originLang [Original] • ${candidate.title}",
                                        lang = originLang,
                                        data = fullOrigUrl,
                                        source = name,
                                        epNumber = query.epNumber,
                                        seasonNumber = query.seasonNumber,
                                        year = query.year,
                                    ),
                                )
                            }
                            origAdded = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse detail page '${candidate.detailUrl}': ${e.message}")
        }
        return results
    }

    private fun matchesLanguageFilter(candidateLang: String, targetLang: String?): Boolean {
        if (targetLang.isNullOrBlank() || targetLang.equals("all", ignoreCase = true)) return true
        val target = targetLang.trim().lowercase()
        val norm = LanguageNormalizer.normalize(candidateLang)
        return norm.code2.equals(target, ignoreCase = true) ||
            norm.code3.equals(target, ignoreCase = true) ||
            norm.displayName.equals(target, ignoreCase = true) ||
            candidateLang.equals(target, ignoreCase = true)
    }

    override suspend fun load(auth: AuthData?, subtitle: SubtitleEntity): String? {
        return subtitle.data.takeIf { it.isNotBlank() }
    }
}
