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
import kotlinx.coroutines.launch
import java.io.File

enum class DesktopWatchType(val id: Int, val stringRes: String) {
    WATCHING(0, "Watching"),
    COMPLETED(1, "Completed"),
    ONHOLD(2, "On Hold"),
    DROPPED(3, "Dropped"),
    PLANTOWATCH(4, "Plan to Watch"),
    REWATCHING(5, "Re-watching"),
}

data class DesktopBookmark(
    val id: String,
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
    val watchType: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
)

data class WatchHistory(
    val parentId: String,
    val showName: String,
    val showUrl: String,
    val apiName: String,
    val posterUrl: String?,
    val episodeThumbnailUrl: String?,
    val screenshotUrl: String?,
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
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
)

object DesktopDataStore {
    @PublishedApi internal val mapper: ObjectMapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val dataFile = File(PlatformPaths.dataDir, "datastore.json")

    val rawKeyCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    val historyUpdates = MutableStateFlow(0)
    val pluginUpdatesFlow = MutableStateFlow(0)

    fun init() {
        // Initialize the database
        val db = DatabaseFactory.database

        // Pre-load all key-values into RAM cache for zero-latency O(1) reads
        try {
            db.cloudstreamDBQueries.selectAllKeyValues().executeAsList().forEach { row ->
                rawKeyCache[row.key] = row.value_
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to pre-cache key-values", e)
        }

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
                                        db.cloudstreamDBQueries.insertBookmark(b.id, b.name, b.url, b.apiName, b.posterUrl, b.watchType.toLong(), b.dateAdded)
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
                                            h.episodeThumbnailUrl, h.screenshotUrl,
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
                                rawKeyCache[key] = jsonStr
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

    private val ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun <T> setKey(key: String, value: T) {
        try {
            val json = mapper.writeValueAsString(value)
            rawKeyCache[key] = json
            ioScope.launch {
                try {
                    DatabaseFactory.database.cloudstreamDBQueries.insertKeyValue(key, json)
                } catch (e: Exception) {
                    AppLogger.e("Failed to persist key $key to SQLite", e)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to serialize key $key", e)
        }
    }

    fun <T> getKey(key: String, clazz: Class<T>): T? {
        val json = rawKeyCache[key] ?: run {
            val dbJson = DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(key).executeAsOneOrNull()
            if (dbJson != null) {
                rawKeyCache[key] = dbJson
            }
            dbJson
        } ?: return null
        return try {
            mapper.readValue(json, clazz)
        } catch (e: Exception) {
            null
        }
    }

    inline fun <reified T> getKey(key: String): T? {
        val json = rawKeyCache[key] ?: run {
            val dbJson = DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(key).executeAsOneOrNull()
            if (dbJson != null) {
                rawKeyCache[key] = dbJson
            }
            dbJson
        } ?: return null
        return try {
            mapper.readValue(json)
        } catch (e: Exception) {
            null
        }
    }

    fun containsKey(key: String): Boolean {
        return rawKeyCache.containsKey(key) || DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(key).executeAsOneOrNull() != null
    }

    fun removeKey(key: String) {
        rawKeyCache.remove(key)
        ioScope.launch {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.deleteKeyValue(key)
            } catch (e: Exception) {
                AppLogger.e("Failed to delete key $key from SQLite", e)
            }
        }
    }

    var activeProfileProvider: () -> Int = { 0 }
    val activeProfileId: Int
        get() = activeProfileProvider()

    fun getProfileKey(key: String, profileId: Int = activeProfileId): String = "$profileId/$key"

    fun <T> setProfileKey(key: String, value: T, profileId: Int = activeProfileId) {
        setKey(getProfileKey(key, profileId), value)
    }

    fun <T> getProfileKey(key: String, clazz: Class<T>, profileId: Int = activeProfileId): T? {
        return getKey(getProfileKey(key, profileId), clazz)
    }

    inline fun <reified T> getProfileKey(key: String, profileId: Int = activeProfileId): T? {
        return getKey<T>(getProfileKey(key, profileId))
    }

    fun removeProfileKey(key: String, profileId: Int = activeProfileId) {
        removeKey(getProfileKey(key, profileId))
    }

    fun getAllKeysWithPrefix(prefix: String): List<String> {
        return DatabaseFactory.database.cloudstreamDBQueries.selectAllKeyValues()
            .executeAsList()
            .map { it.key }
            .filter { it.startsWith(prefix) }
    }

    fun getBookmarks(profileId: Int = activeProfileId): List<DesktopBookmark> {
        val prefix = "p${profileId}_"
        return DatabaseFactory.database.cloudstreamDBQueries.selectAllBookmarks().executeAsList()
            .filter {
                if (profileId == 0) {
                    it.id.startsWith(prefix) || !it.id.startsWith("p")
                } else {
                    it.id.startsWith(prefix)
                }
            }
            .map {
                DesktopBookmark(it.id, it.name, it.url, it.apiName, it.posterUrl, it.watchType?.toInt() ?: 0, it.dateAdded ?: 0L)
            }
    }

    fun addBookmark(bookmark: DesktopBookmark, profileId: Int = activeProfileId) {
        val resolvedId = if (bookmark.id.startsWith("p")) bookmark.id else "p${profileId}_${bookmark.id}"
        DatabaseFactory.database.cloudstreamDBQueries.insertBookmark(
            resolvedId,
            bookmark.name,
            bookmark.url,
            bookmark.apiName,
            bookmark.posterUrl,
            bookmark.watchType.toLong(),
            bookmark.dateAdded,
        )
    }

    fun removeBookmark(id: String, profileId: Int = activeProfileId) {
        val resolvedId = if (id.startsWith("p")) id else "p${profileId}_$id"
        DatabaseFactory.database.cloudstreamDBQueries.deleteBookmark(resolvedId)
        if (profileId == 0) {
            DatabaseFactory.database.cloudstreamDBQueries.deleteBookmark(id)
        }
    }

    fun isBookmarked(id: String, profileId: Int = activeProfileId): Boolean {
        val resolvedId = if (id.startsWith("p")) id else "p${profileId}_$id"
        val exists = DatabaseFactory.database.cloudstreamDBQueries.selectBookmarkById(resolvedId).executeAsOneOrNull() != null
        if (exists) return true
        return profileId == 0 && DatabaseFactory.database.cloudstreamDBQueries.selectBookmarkById(id).executeAsOneOrNull() != null
    }

    fun getAllWatchHistory(profileId: Int = activeProfileId): List<WatchHistory> {
        val prefix = "p${profileId}_"
        return DatabaseFactory.database.cloudstreamDBQueries.selectAllWatchHistory().executeAsList()
            .filter {
                if (profileId == 0) {
                    it.parentId.startsWith(prefix) || !it.parentId.startsWith("p")
                } else {
                    it.parentId.startsWith(prefix)
                }
            }
            .map {
                WatchHistory(
                    parentId = it.parentId,
                    showName = it.showName,
                    showUrl = it.showUrl,
                    apiName = it.apiName,
                    posterUrl = it.posterUrl,
                    episodeThumbnailUrl = it.episodeThumbnailUrl,
                    screenshotUrl = it.screenshotUrl,
                    episode = it.episode?.toInt(),
                    season = it.season?.toInt(),
                    episodeId = it.episodeId.takeIf { id -> id.isNotEmpty() },
                    position = it.position,
                    duration = it.duration,
                    updateTime = it.updateTime,
                )
            }
    }

    private var lastHistoryNotifyMs = 0L

    fun notifyHistoryChanged(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastHistoryNotifyMs >= 1000L) {
            lastHistoryNotifyMs = now
            historyUpdates.value++
        }
    }

    fun clearAllWatchHistory(profileId: Int = activeProfileId) {
        val history = getAllWatchHistory(profileId)
        DatabaseFactory.database.cloudstreamDBQueries.transaction {
            history.forEach {
                DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByParent(it.parentId)
            }
        }
        notifyHistoryChanged(force = true)
    }

    fun removeWatchHistory(parentId: String) {
        DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByParent(parentId)
        notifyHistoryChanged(force = true)
    }

    fun removeEpisodeWatched(parentId: String, episodeId: String) {
        DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByEpisode(parentId, episodeId)
        notifyHistoryChanged(force = true)
    }

    fun removeMultipleEpisodesWatched(parentId: String, episodeIds: List<String>) {
        if (episodeIds.isEmpty()) return
        DatabaseFactory.database.cloudstreamDBQueries.transaction {
            episodeIds.forEach { episodeId ->
                DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByEpisode(parentId, episodeId)
            }
        }
        notifyHistoryChanged(force = true)
    }

    fun watchHistoryId(
        apiName: String,
        showUrl: String,
        season: Int? = null,
        episode: Int? = null,
        episodeData: String? = null,
        profileId: Int = activeProfileId,
    ): String {
        val base = "p${profileId}_${apiName}_${showUrl.hashCode()}"
        return if (season != null || episode != null || !episodeData.isNullOrBlank()) {
            "${base}_s${season ?: 0}_e${episode ?: 0}_${episodeData?.hashCode() ?: 0}"
        } else {
            base
        }
    }

    fun setLastWatched(history: WatchHistory, forceNotify: Boolean = false) {
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
            episodeThumbnailUrl = history.episodeThumbnailUrl,
            screenshotUrl = history.screenshotUrl,
            episode = history.episode?.toLong(),
            season = history.season?.toLong(),
            position = normalizedPosition,
            duration = normalizedDuration,
            updateTime = history.updateTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        notifyHistoryChanged(force = forceNotify)
    }

    fun setMultipleLastWatched(histories: List<WatchHistory>) {
        if (histories.isEmpty()) return
        DatabaseFactory.database.cloudstreamDBQueries.transaction {
            histories.forEach { history ->
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
                    episodeThumbnailUrl = history.episodeThumbnailUrl,
                    screenshotUrl = history.screenshotUrl,
                    episode = history.episode?.toLong(),
                    season = history.season?.toLong(),
                    position = normalizedPosition,
                    duration = normalizedDuration,
                    updateTime = history.updateTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                )
            }
        }
        notifyHistoryChanged(force = true)
    }

    fun getLastWatched(parentId: String): WatchHistory? {
        return DatabaseFactory.database.cloudstreamDBQueries
            .selectWatchHistoryByParent(parentId)
            .executeAsList()
            .firstOrNull()
            ?.let {
                WatchHistory(
                    parentId = it.parentId,
                    showName = it.showName,
                    showUrl = it.showUrl,
                    apiName = it.apiName,
                    posterUrl = it.posterUrl,
                    episodeThumbnailUrl = it.episodeThumbnailUrl,
                    screenshotUrl = it.screenshotUrl,
                    episode = it.episode?.toInt(),
                    season = it.season?.toInt(),
                    episodeId = it.episodeId.takeIf { id -> id.isNotEmpty() },
                    position = it.position,
                    duration = it.duration,
                    updateTime = it.updateTime,
                )
            }
    }

    fun getWatchHistoryByParent(parentId: String): List<WatchHistory> {
        return DatabaseFactory.database.cloudstreamDBQueries
            .selectWatchHistoryByParent(parentId)
            .executeAsList()
            .map {
                WatchHistory(
                    parentId = it.parentId,
                    showName = it.showName,
                    showUrl = it.showUrl,
                    apiName = it.apiName,
                    posterUrl = it.posterUrl,
                    episodeThumbnailUrl = it.episodeThumbnailUrl,
                    screenshotUrl = it.screenshotUrl,
                    episode = it.episode?.toInt(),
                    season = it.season?.toInt(),
                    episodeId = it.episodeId.takeIf { id -> id.isNotEmpty() },
                    position = it.position,
                    duration = it.duration,
                    updateTime = it.updateTime,
                )
            }
    }

    fun getLatestWatchHistoryForShow(showUrl: String): WatchHistory? {
        return DatabaseFactory.database.cloudstreamDBQueries
            .selectLatestWatchHistoryForShow(showUrl)
            .executeAsOneOrNull()
            ?.let {
                WatchHistory(
                    parentId = it.parentId,
                    showName = it.showName,
                    showUrl = it.showUrl,
                    apiName = it.apiName,
                    posterUrl = it.posterUrl,
                    episodeThumbnailUrl = it.episodeThumbnailUrl,
                    screenshotUrl = it.screenshotUrl,
                    episode = it.episode?.toInt(),
                    season = it.season?.toInt(),
                    episodeId = it.episodeId.takeIf { id -> id.isNotEmpty() },
                    position = it.position,
                    duration = it.duration,
                    updateTime = it.updateTime,
                )
            }
    }

    fun getEpisodeWatched(
        parentId: String,
        episodeId: String?,
    ): WatchHistory? {
        val searchId = episodeId ?: ""
        return DatabaseFactory.database.cloudstreamDBQueries
            .selectWatchHistoryByEpisode(parentId, searchId)
            .executeAsOneOrNull()
            ?.let {
                WatchHistory(
                    parentId = it.parentId,
                    showName = it.showName,
                    showUrl = it.showUrl,
                    apiName = it.apiName,
                    posterUrl = it.posterUrl,
                    episodeThumbnailUrl = it.episodeThumbnailUrl,
                    screenshotUrl = it.screenshotUrl,
                    episode = it.episode?.toInt(),
                    season = it.season?.toInt(),
                    episodeId = it.episodeId.takeIf { id -> id.isNotEmpty() },
                    position = it.position,
                    duration = it.duration,
                    updateTime = it.updateTime,
                )
            }
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

    fun clearUpdatesHistory() {
        DatabaseFactory.database.cloudstreamDBQueries.deleteAllPluginUpdates()
        pluginUpdatesFlow.value++
    }

    fun hasUnreadUpdates(): Boolean {
        return getKey<Boolean>(UNREAD_UPDATES_KEY) ?: false
    }

    fun setUnreadUpdates(hasUnread: Boolean) {
        setKey(UNREAD_UPDATES_KEY, hasUnread)
        pluginUpdatesFlow.value++
    }

    const val PREF_ALLOW_EXTERNAL_BROWSER = "ALLOW_EXTERNAL_BROWSER"
    const val PREF_ALLOW_CF_BYPASS = "ALLOW_CF_BYPASS"
    const val PREF_ISOLATED_EXTERNAL_BROWSER = "ISOLATED_EXTERNAL_BROWSER"
    const val PREF_DONT_ASK_EXTERNAL_LINKS = "DONT_ASK_EXTERNAL_LINKS"

    const val PREF_DISCORD_RPC_ENABLED = "DISCORD_RPC_ENABLED"
    const val PREF_DISCORD_RPC_SHOW_TITLE = "DISCORD_RPC_SHOW_TITLE"
    const val PREF_DISCORD_RPC_SHOW_PROGRESS = "DISCORD_RPC_SHOW_PROGRESS"
    const val PREF_DISCORD_RPC_SHOW_BROWSING = "DISCORD_RPC_SHOW_BROWSING"
    const val PREF_DISCORD_CUSTOM_APP_ID = "DISCORD_CUSTOM_APP_ID"

    const val PREF_P2P_ENABLED = "p2p_torrent_enabled"
    const val PREF_P2P_PORT = "p2p_torrent_port"
    const val PREF_P2P_CACHE_SIZE_GB = "p2p_torrent_cache_gb"
    const val PREF_P2P_SHOW_HUD = "p2p_torrent_show_hud"

    private const val TRUSTED_PLUGINS_KEY = "trusted_plugins_set"

    fun getTrustedPlugins(): Set<String> {
        val json = rawKeyCache[TRUSTED_PLUGINS_KEY] ?: DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(TRUSTED_PLUGINS_KEY).executeAsOneOrNull() ?: return emptySet()
        return try {
            val list: List<String> = mapper.readValue(json, object : TypeReference<List<String>>() {})
            list.map { it.lowercase().trim() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun isPluginTrusted(internalName: String): Boolean {
        val cleanName = internalName.removeSuffix("-jvm").lowercase().trim()
        val trusted = getTrustedPlugins()
        if (trusted.contains(cleanName) || trusted.contains(internalName.lowercase().trim())) return true
        val stripped = cleanName.removeSuffix("provider").removeSuffix("plugin").removePrefix("com.")
        if (stripped.isNotBlank() && (trusted.contains(stripped) || trusted.contains(stripped.substringAfterLast('.')))) return true
        val lastSegment = cleanName.substringAfterLast('.')
        if (lastSegment.isNotBlank() && (trusted.contains(lastSegment) || trusted.contains(lastSegment.removeSuffix("provider").removeSuffix("plugin")))) return true
        return false
    }

    fun setPluginTrusted(internalName: String, trusted: Boolean) {
        val cleanName = internalName.removeSuffix("-jvm").lowercase().trim()
        val current = getTrustedPlugins().toMutableSet()
        if (trusted) {
            current.add(cleanName)
            current.add(internalName.lowercase().trim())
        } else {
            current.remove(cleanName)
            current.remove(internalName.lowercase().trim())
        }
        val list = current.toList()
        try {
            val json = mapper.writeValueAsString(list)
            rawKeyCache[TRUSTED_PLUGINS_KEY] = json
            DatabaseFactory.database.cloudstreamDBQueries.insertKeyValue(TRUSTED_PLUGINS_KEY, json)
        } catch (e: Exception) {
            AppLogger.e("Failed to save trusted plugins", e)
        }
    }
}
