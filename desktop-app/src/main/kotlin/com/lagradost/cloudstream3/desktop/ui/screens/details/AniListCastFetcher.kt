package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AniListCastFetcher {

    suspend fun fetch(title: String, year: Int?): List<ActorData>? {
        return withContext(Dispatchers.IO) {
            try {
                val searchQuery = """
                    query (${'$'}search: String) {
                        Page(page: 1, perPage: 5) {
                            media(search: ${'$'}search, type: ANIME) {
                                id
                                title { romaji english native userPreferred }
                                startDate { year }
                            }
                        }
                    }
                """.trimIndent()

                val searchPayload = mapOf(
                    "query" to searchQuery,
                    "variables" to mapOf("search" to title),
                )

                val searchResult = app.post(
                    "https://graphql.anilist.co",
                    json = searchPayload,
                    headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                ).parsedSafe<JsonNode>()

                val mediaList = searchResult?.get("data")?.get("Page")?.get("media")
                if (mediaList == null || !mediaList.isArray || mediaList.size() == 0) return@withContext null

                // Prefer exact title + year match, fall back to first result
                val mediaNode = mediaList.firstOrNull { media ->
                    val mediaYear = media.get("startDate")?.get("year")?.asInt()
                    val titles = media.get("title")
                    val allTitles = listOfNotNull(
                        titles?.get("romaji")?.asText(),
                        titles?.get("english")?.asText(),
                        titles?.get("native")?.asText(),
                        titles?.get("userPreferred")?.asText(),
                    )
                    val titleMatch = allTitles.any { it.equals(title, ignoreCase = true) }
                    val yearMatch = year == null || mediaYear == null || mediaYear == year
                    titleMatch && yearMatch
                } ?: mediaList.get(0)

                val mediaId = mediaNode.get("id")?.asInt() ?: return@withContext null

                val castQuery = """
                    query (${'$'}id: Int) {
                        Media(id: ${'$'}id, type: ANIME) {
                            characters(sort: ROLE, page: 1, perPage: 20) {
                                edges {
                                    role
                                    node {
                                        name { userPreferred full native }
                                        image { large medium }
                                    }
                                    voiceActors(language: JAPANESE) {
                                        name { userPreferred full native }
                                        image { large medium }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()

                val castPayload = mapOf(
                    "query" to castQuery,
                    "variables" to mapOf("id" to mediaId),
                )

                val castResult = app.post(
                    "https://graphql.anilist.co",
                    json = castPayload,
                    headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                ).parsedSafe<JsonNode>()

                val edges = castResult?.get("data")?.get("Media")?.get("characters")?.get("edges")
                if (edges == null || !edges.isArray) return@withContext null

                val actors = mutableListOf<ActorData>()
                edges.forEach { edge ->
                    val charNode = edge.get("node") ?: return@forEach
                    val charName = charNode.get("name")?.let {
                        it.get("userPreferred")?.asText()
                            ?: it.get("full")?.asText()
                            ?: it.get("native")?.asText()
                    } ?: return@forEach
                    val charImage = charNode.get("image")?.let {
                        it.get("large")?.asText() ?: it.get("medium")?.asText()
                    }?.takeIf { it != "null" }

                    val role = when (edge.get("role")?.asText()) {
                        "MAIN" -> ActorRole.Main
                        "SUPPORTING" -> ActorRole.Supporting
                        "BACKGROUND" -> ActorRole.Background
                        else -> null
                    }

                    val vaNodes = edge.get("voiceActors")
                    val voiceActor = if (vaNodes != null && vaNodes.isArray && vaNodes.size() > 0) {
                        val va = vaNodes.get(0)
                        val vaName = va.get("name")?.let {
                            it.get("userPreferred")?.asText()
                                ?: it.get("full")?.asText()
                                ?: it.get("native")?.asText()
                        }
                        val vaImage = va.get("image")?.let {
                            it.get("large")?.asText() ?: it.get("medium")?.asText()
                        }?.takeIf { it != "null" }
                        if (vaName != null) Actor(vaName, vaImage) else null
                    } else {
                        null
                    }

                    actors.add(
                        ActorData(
                            actor = Actor(charName, charImage),
                            role = role,
                            voiceActor = voiceActor,
                        ),
                    )
                }

                if (actors.isEmpty()) null else actors
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("[AniList] fetchAniListCast exception", e)
                null
            }
        }
    }
}
