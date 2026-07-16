package com.lagradost.common.storage

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.common.db.DatabaseFactory
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class DesktopBookmark(
    val id: String,
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
)

data class WatchHistory(
    val parentId: String,
    val showName: String,
    val showUrl: String,
    val apiName: String,
    val posterUrl: String?,
    val episode: Int?,
    val season: Int?,
    val episodeId: String?,
    val position: Long,
    val duration: Long,
    val updateTime: Long = System.currentTimeMillis(),
)

data class PluginUpdateRecord(
    val pluginName: String,
    val version: Int,
    val iconUrl: String?,
    val timestamp: Long = System.currentTimeMillis(),
)

object DesktopDataStore {
    @PublishedApi internal val mapper: ObjectMapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val dataFile = File(PlatformPaths.dataDir, "datastore.json")

    val historyUpdates = MutableStateFlow(0)
    val pluginUpdatesFlow = MutableStateFlow(0)

    fun init() {
        // Initialize the database
        val db = DatabaseFactory.database

        // Migration from old datastore.json
        if (dataFile.exists() && dataFile.length() > 0L) {
            try {
                AppLogger.i("Migrating legacy datastore.json to SQLDelight...")
                val cache: Map<String, String> = mapper.readValue(dataFile)

                db.cloudstreamDBQueries.transaction {
                    for ((key, jsonStr) in cache) {
                        when (key) {
                            "user_bookmarks" -> {
                                try {
                                    val bookmarks: List<DesktopBookmark> = mapper.readValue(jsonStr, object : TypeReference<List<DesktopBookmark>>() {})
                                    bookmarks.forEach { b ->
                                        db.cloudstreamDBQueries.insertBookmark(b.id, b.name, b.url, b.apiName, b.posterUrl)
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("Failed to migrate bookmarks", e)
                                }
                            }
                            "user_watch_history" -> {
                                try {
                                    val history: List<WatchHistory> = mapper.readValue(jsonStr, object : TypeReference<List<WatchHistory>>() {})
                                    history.forEach { h ->
                                        db.cloudstreamDBQueries.insertWatchHistory(
                                            h.parentId, h.episodeId ?: "", h.showName, h.showUrl, h.apiName, h.posterUrl,
                                            h.episode?.toLong(), h.season?.toLong(), h.position, h.duration, h.updateTime,
                                        )
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("Failed to migrate watch history", e)
                                }
                            }
                            "plugin_updates_history_v2" -> {
                                try {
                                    val updates: List<PluginUpdateRecord> = mapper.readValue(jsonStr, object : TypeReference<List<PluginUpdateRecord>>() {})
                                    updates.forEach { u ->
                                        db.cloudstreamDBQueries.insertPluginUpdate(u.pluginName, u.version.toLong(), u.iconUrl, u.timestamp)
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("Failed to migrate plugin updates", e)
                                }
                            }
                            else -> {
                                db.cloudstreamDBQueries.insertKeyValue(key, jsonStr)
                            }
                        }
                    }
                }
                val bakFile = File(PlatformPaths.dataDir, "datastore.json.bak")
                dataFile.renameTo(bakFile)
                AppLogger.i("Migration complete. Old file renamed to datastore.json.bak")
            } catch (e: Exception) {
                AppLogger.e("Critical failure migrating datastore.json", e)
            }
        }
    }

    fun <T> setKey(key: String, value: T) {
        try {
            val json = mapper.writeValueAsString(value)
            DatabaseFactory.database.cloudstreamDBQueries.insertKeyValue(key, json)
        } catch (e: Exception) {
            AppLogger.e("Failed to serialize key $key", e)
        }
    }

    fun <T> getKey(key: String, clazz: Class<T>): T? {
        val json = DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(key).executeAsOneOrNull() ?: return null
        return try {
            mapper.readValue(json, clazz)
        } catch (e: Exception) {
            null
        }
    }

    inline fun <reified T> getKey(key: String): T? {
        val json = DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(key).executeAsOneOrNull() ?: return null
        return try {
            mapper.readValue(json)
        } catch (e: Exception) {
            null
        }
    }

    fun removeKey(key: String) {
        DatabaseFactory.database.cloudstreamDBQueries.deleteKeyValue(key)
    }

    fun getBookmarks(): List<DesktopBookmark> {
        return DatabaseFactory.database.cloudstreamDBQueries.selectAllBookmarks().executeAsList().map {
            DesktopBookmark(it.id, it.name, it.url, it.apiName, it.posterUrl)
        }
    }

    fun addBookmark(bookmark: DesktopBookmark) {
        DatabaseFactory.database.cloudstreamDBQueries.insertBookmark(
            bookmark.id,
            bookmark.name,
            bookmark.url,
            bookmark.apiName,
            bookmark.posterUrl,
        )
    }

    fun removeBookmark(id: String) {
        DatabaseFactory.database.cloudstreamDBQueries.deleteBookmark(id)
    }

    fun isBookmarked(id: String): Boolean {
        return DatabaseFactory.database.cloudstreamDBQueries.selectBookmarkById(id).executeAsOneOrNull() != null
    }

    fun getAllWatchHistory(): List<WatchHistory> {
        return DatabaseFactory.database.cloudstreamDBQueries.selectAllWatchHistory().executeAsList().map {
            WatchHistory(
                parentId = it.parentId,
                showName = it.showName,
                showUrl = it.showUrl,
                apiName = it.apiName,
                posterUrl = it.posterUrl,
                episode = it.episode?.toInt(),
                season = it.season?.toInt(),
                episodeId = it.episodeId.takeIf { id -> id.isNotEmpty() },
                position = it.position,
                duration = it.duration,
                updateTime = it.updateTime,
            )
        }
    }

    fun clearAllWatchHistory() {
        DatabaseFactory.database.cloudstreamDBQueries.deleteAllWatchHistory()
        historyUpdates.value++
    }

    fun removeWatchHistory(parentId: String) {
        DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByParent(parentId)
        historyUpdates.value++
    }

    fun watchHistoryId(
        apiName: String,
        showUrl: String,
        season: Int? = null,
        episode: Int? = null,
        episodeData: String? = null,
    ): String {
        val base = "${apiName}_${showUrl.hashCode()}"
        return if (season != null || episode != null || !episodeData.isNullOrBlank()) {
            "${base}_s${season ?: 0}_e${episode ?: 0}_${episodeData?.hashCode() ?: 0}"
        } else {
            base
        }
    }

    fun setLastWatched(history: WatchHistory) {
        val normalizedDuration = history.duration.coerceAtLeast(0)
        val normalizedPosition = if (normalizedDuration > 0) {
            history.position.coerceIn(0, normalizedDuration)
        } else {
            history.position.coerceAtLeast(0)
        }

        DatabaseFactory.database.cloudstreamDBQueries.insertWatchHistory(
            parentId = history.parentId,
            episodeId = history.episodeId ?: "",
            showName = history.showName,
            showUrl = history.showUrl,
            apiName = history.apiName,
            posterUrl = history.posterUrl,
            episode = history.episode?.toLong(),
            season = history.season?.toLong(),
            position = normalizedPosition,
            duration = normalizedDuration,
            updateTime = System.currentTimeMillis(),
        )
        historyUpdates.value++
    }

    fun getLastWatched(parentId: String): WatchHistory? {
        return getAllWatchHistory()
            .filter { it.parentId == parentId }
            .maxByOrNull { it.updateTime }
    }

    fun getLatestWatchHistoryForShow(showUrl: String): WatchHistory? {
        return getAllWatchHistory()
            .filter { it.showUrl == showUrl }
            .maxByOrNull { it.updateTime }
    }

    fun getEpisodeWatched(
        parentId: String,
        episodeId: String?,
    ): WatchHistory? {
        val searchId = episodeId ?: ""
        return getAllWatchHistory().find { it.parentId == parentId && it.episodeId == searchId }
    }

    private const val UNREAD_UPDATES_KEY = "unread_plugin_updates"

    fun getUpdatesHistory(): List<PluginUpdateRecord> {
        return DatabaseFactory.database.cloudstreamDBQueries.selectAllPluginUpdates().executeAsList().map {
            PluginUpdateRecord(it.pluginName, it.version.toInt(), it.iconUrl, it.timestamp)
        }
    }

    fun addUpdateHistory(history: List<PluginUpdateRecord>) {
        if (history.isEmpty()) return

        DatabaseFactory.database.cloudstreamDBQueries.transaction {
            history.forEach {
                DatabaseFactory.database.cloudstreamDBQueries.insertPluginUpdate(
                    it.pluginName,
                    it.version.toLong(),
                    it.iconUrl,
                    it.timestamp,
                )
            }
            DatabaseFactory.database.cloudstreamDBQueries.deleteOldPluginUpdates()
        }
        pluginUpdatesFlow.value++
    }

    fun hasUnreadUpdates(): Boolean {
        return getKey<Boolean>(UNREAD_UPDATES_KEY) ?: false
    }

    fun setUnreadUpdates(hasUnread: Boolean) {
        setKey(UNREAD_UPDATES_KEY, hasUnread)
        pluginUpdatesFlow.value++
    }
}
