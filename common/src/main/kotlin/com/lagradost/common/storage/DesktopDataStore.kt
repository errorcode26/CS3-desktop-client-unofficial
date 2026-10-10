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
import kotlinx.coroutines.flow.MutableSharedFlow
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
    val episodeName: String? = null,
    val episodeDescription: String? = null,
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
    @Volatile @PublishedApi internal var isPreCacheLoaded = false

    val historyUpdates = MutableStateFlow(0)
    val pluginUpdatesFlow = MutableStateFlow(0)
    val keyUpdates = MutableSharedFlow<String>(extraBufferCapacity = 64)

    @PublishedApi
    internal val dbLock = java.util.concurrent.locks.ReentrantLock()

    @PublishedApi
    internal inline fun <T> withDbLock(block: () -> T): T {
        dbLock.lock()
        try {
            return block()
        } finally {
            dbLock.unlock()
        }
    }

    fun init() {
        withDbLock {
            // Initialize the database
            val db = DatabaseFactory.database

            // Pre-load all key-values into RAM cache for zero-latency O(1) reads
            try {
                db.cloudstreamDBQueries.selectAllKeyValues().executeAsList().forEach { row ->
                    rawKeyCache[row.key] = row.value_
                }
                isPreCacheLoaded = true
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
                                                h.episodeName, h.episodeDescription,
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
    }

    private val dbWriteDispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1)
    private val ioScope = kotlinx.coroutines.CoroutineScope(dbWriteDispatcher + kotlinx.coroutines.SupervisorJob())

    fun <T> setKey(key: String, value: T) {
        try {
            val json = mapper.writeValueAsString(value)
            if (rawKeyCache[key] == json) return
            rawKeyCache[key] = json
            keyUpdates.tryEmit(key)
            ioScope.launch {
                try {
                    withDbLock {
                        DatabaseFactory.database.cloudstreamDBQueries.insertKeyValue(key, json)
                    }
                } catch (e: Exception) {
                    AppLogger.e("Failed to persist key $key to SQLite", e)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to serialize key $key", e)
        }
    }

    fun <T> getKey(key: String, clazz: Class<T>): T? {
        val json = rawKeyCache[key] ?: if (!isPreCacheLoaded) {
            val dbJson = withDbLock {
                try {
                    DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(key).executeAsOneOrNull()
                } catch (e: Exception) {
                    AppLogger.e("Failed to select key $key from SQLite", e)
                    null
                }
            }
            if (dbJson != null) {
                rawKeyCache[key] = dbJson
            }
            dbJson
        } else {
            null
        } ?: return null
        return try {
            mapper.readValue(json, clazz)
        } catch (e: Exception) {
            AppLogger.e("DesktopDataStore: Failed to deserialize key '$key' to ${clazz.simpleName}", e)
            null
        }
    }

    inline fun <reified T> getKey(key: String): T? {
        val json = rawKeyCache[key] ?: if (!isPreCacheLoaded) {
            val dbJson = withDbLock {
                try {
                    DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(key).executeAsOneOrNull()
                } catch (e: Exception) {
                    AppLogger.e("Failed to select key $key from SQLite", e)
                    null
                }
            }
            if (dbJson != null) {
                rawKeyCache[key] = dbJson
            }
            dbJson
        } else {
            null
        } ?: return null
        return try {
            mapper.readValue(json)
        } catch (e: Exception) {
            AppLogger.e("DesktopDataStore: Failed to deserialize key '$key'", e)
            null
        }
    }

    fun containsKey(key: String): Boolean {
        if (rawKeyCache.containsKey(key)) return true
        if (isPreCacheLoaded) return false
        return withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(key).executeAsOneOrNull() != null
            } catch (e: Exception) {
                AppLogger.e("Failed to check if key exists: $key", e)
                false
            }
        }
    }

    fun removeKey(key: String) {
        rawKeyCache.remove(key)
        keyUpdates.tryEmit(key)
        ioScope.launch {
            try {
                withDbLock {
                    DatabaseFactory.database.cloudstreamDBQueries.deleteKeyValue(key)
                }
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
        return withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.selectAllKeyValues()
                    .executeAsList()
                    .map { it.key }
                    .filter { it.startsWith(prefix) }
            } catch (e: Exception) {
                AppLogger.e("Failed to get keys with prefix: $prefix", e)
                emptyList()
            }
        }
    }

    fun getBookmarks(profileId: Int = activeProfileId): List<DesktopBookmark> {
        val prefix = "p${profileId}_"
        return withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.selectAllBookmarks().executeAsList()
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
            } catch (e: Exception) {
                AppLogger.e("Failed to fetch bookmarks for profile $profileId", e)
                emptyList()
            }
        }
    }

    fun addBookmark(bookmark: DesktopBookmark, profileId: Int = activeProfileId) {
        val resolvedId = if (bookmark.id.startsWith("p")) bookmark.id else "p${profileId}_${bookmark.id}"
        withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.insertBookmark(
                    resolvedId,
                    bookmark.name,
                    bookmark.url,
                    bookmark.apiName,
                    bookmark.posterUrl,
                    bookmark.watchType.toLong(),
                    bookmark.dateAdded,
                )
            } catch (e: Exception) {
                AppLogger.e("Failed to insert bookmark $resolvedId", e)
            }
        }
    }

    fun removeBookmark(id: String, profileId: Int = activeProfileId) {
        val resolvedId = if (id.startsWith("p")) id else "p${profileId}_$id"
        withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.deleteBookmark(resolvedId)
                if (profileId == 0) {
                    DatabaseFactory.database.cloudstreamDBQueries.deleteBookmark(id)
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to delete bookmark $resolvedId", e)
            }
        }
    }

    fun isBookmarked(id: String, profileId: Int = activeProfileId): Boolean {
        val resolvedId = if (id.startsWith("p")) id else "p${profileId}_$id"
        return withDbLock {
            try {
                val exists = DatabaseFactory.database.cloudstreamDBQueries.selectBookmarkById(resolvedId).executeAsOneOrNull() != null
                if (exists) return@withDbLock true
                profileId == 0 && DatabaseFactory.database.cloudstreamDBQueries.selectBookmarkById(id).executeAsOneOrNull() != null
            } catch (e: Exception) {
                AppLogger.e("Failed to check if bookmarked: $id", e)
                false
            }
        }
    }

    fun getAllWatchHistory(profileId: Int = activeProfileId): List<WatchHistory> {
        val prefix = "p${profileId}_"
        return withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.selectAllWatchHistory().executeAsList()
                    .filter {
                        if (profileId == 0) {
                            it.parentId.startsWith(prefix) || !it.parentId.startsWith("p")
                        } else {
                            it.parentId.startsWith(prefix)
                        }
                    }
                    .filter {
                        it.showName.isNotBlank() &&
                        !it.showName.equals("Unknown Channel", ignoreCase = true) &&
                        !it.showName.equals("Unknown", ignoreCase = true) &&
                        (it.duration > 0 || it.position > 0)
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
                            episodeName = it.episodeName,
                            episodeDescription = it.episodeDescription,
                        )
                    }
            } catch (e: Exception) {
                AppLogger.e("Failed to get watch history for profile $profileId", e)
                emptyList()
            }
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
        withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.transaction {
                    history.forEach {
                        DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByParent(it.parentId)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to clear all watch history", e)
            }
        }
        notifyHistoryChanged(force = true)
    }

    fun removeWatchHistory(parentId: String) {
        withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByParent(parentId)
                val legacyId = if (parentId.startsWith("p") && parentId.contains("_")) {
                    parentId.substringAfter("_")
                } else null
                if (legacyId != null && legacyId != parentId) {
                    DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByParent(legacyId)
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to remove watch history for $parentId", e)
            }
        }
        notifyHistoryChanged(force = true)
    }

    fun removeEpisodeWatched(
        parentId: String,
        episodeId: String,
        season: Int? = null,
        episode: Int? = null,
        extraEpisodeIds: List<String> = emptyList(),
    ) {
        val allParentIds = mutableListOf(parentId)
        val legacyId = if (parentId.startsWith("p") && parentId.contains("_")) {
            parentId.substringAfter("_")
        } else null
        if (legacyId != null && legacyId != parentId) {
            allParentIds.add(legacyId)
        }

        val allEpisodeIds = (listOf(episodeId) + extraEpisodeIds).filter { it.isNotBlank() }.distinct()

        withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.transaction {
                    allParentIds.forEach { pid ->
                        allEpisodeIds.forEach { eid ->
                            DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByEpisode(pid, eid)
                        }
                        if (episode != null) {
                            val rows = DatabaseFactory.database.cloudstreamDBQueries.selectWatchHistoryByParent(pid).executeAsList()
                            rows.forEach { row ->
                                val rowSeason = row.season?.toInt() ?: 1
                                val targetSeason = season ?: 1
                                if (row.episode?.toInt() == episode && rowSeason == targetSeason) {
                                    DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByEpisode(pid, row.episodeId)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to remove episode watched for $parentId", e)
            }
        }
        notifyHistoryChanged(force = true)
    }

    fun removeMultipleEpisodesWatched(
        parentId: String,
        episodeIds: List<String>,
        extraParentIds: List<String> = emptyList(),
    ) {
        if (episodeIds.isEmpty()) return
        val allParentIds = (listOf(parentId) + extraParentIds).toMutableList()
        val legacyId = if (parentId.startsWith("p") && parentId.contains("_")) {
            parentId.substringAfter("_")
        } else null
        if (legacyId != null && !allParentIds.contains(legacyId)) {
            allParentIds.add(legacyId)
        }

        val targetEpisodeIds = episodeIds.filter { it.isNotBlank() }.distinct()
        withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.transaction {
                    allParentIds.forEach { pid ->
                        targetEpisodeIds.forEach { episodeId ->
                            DatabaseFactory.database.cloudstreamDBQueries.deleteWatchHistoryByEpisode(pid, episodeId)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to remove multiple episodes watched", e)
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
        val name = history.showName.trim()
        if (name.isBlank() || name.equals("Unknown Channel", ignoreCase = true) || name.equals("Unknown", ignoreCase = true)) {
            return
        }
        val normalizedDuration = history.duration.coerceAtLeast(0)
        val normalizedPosition = if (normalizedDuration > 0) {
            history.position.coerceIn(0, normalizedDuration)
        } else {
            history.position.coerceAtLeast(0)
        }
        if (normalizedDuration <= 0 && normalizedPosition <= 0) {
            return
        }

        withDbLock {
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
                episodeName = history.episodeName,
                episodeDescription = history.episodeDescription,
            )
        }
        notifyHistoryChanged(force = forceNotify)
    }

    fun setMultipleLastWatched(histories: List<WatchHistory>) {
        val validHistories = histories.filter {
            val name = it.showName.trim()
            name.isNotBlank() &&
            !name.equals("Unknown Channel", ignoreCase = true) &&
            !name.equals("Unknown", ignoreCase = true) &&
            (it.duration > 0 || it.position > 0)
        }
        if (validHistories.isEmpty()) return
        withDbLock {
            DatabaseFactory.database.cloudstreamDBQueries.transaction {
                validHistories.forEach { history ->
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
                        episodeName = history.episodeName,
                        episodeDescription = history.episodeDescription,
                    )
                }
            }
        }
        notifyHistoryChanged(force = true)
    }

    fun getLastWatched(parentId: String): WatchHistory? {
        return withDbLock {
            DatabaseFactory.database.cloudstreamDBQueries
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
                        episodeName = it.episodeName,
                        episodeDescription = it.episodeDescription,
                    )
                }
        }
    }

    fun getWatchHistoryByParent(parentId: String): List<WatchHistory> {
        return withDbLock {
            DatabaseFactory.database.cloudstreamDBQueries
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
                        episodeName = it.episodeName,
                        episodeDescription = it.episodeDescription,
                    )
                }
        }
    }

    fun getLatestWatchHistoryForShow(showUrl: String): WatchHistory? {
        return withDbLock {
            DatabaseFactory.database.cloudstreamDBQueries
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
                        episodeName = it.episodeName,
                        episodeDescription = it.episodeDescription,
                    )
                }
        }
    }

    fun getEpisodeWatched(
        parentId: String,
        episodeId: String?,
    ): WatchHistory? {
        val searchId = episodeId ?: ""
        return withDbLock {
            DatabaseFactory.database.cloudstreamDBQueries
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
                        episodeName = it.episodeName,
                        episodeDescription = it.episodeDescription,
                    )
                }
        }
    }

    private const val UNREAD_UPDATES_KEY = "unread_plugin_updates"

    fun getUpdatesHistory(): List<PluginUpdateRecord> {
        return withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.selectAllPluginUpdates().executeAsList().map {
                    PluginUpdateRecord(it.pluginName, it.version.toInt(), it.iconUrl, it.timestamp)
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to get updates history", e)
                emptyList()
            }
        }
    }

    fun addUpdateHistory(history: List<PluginUpdateRecord>) {
        if (history.isEmpty()) return

        withDbLock {
            try {
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
            } catch (e: Exception) {
                AppLogger.e("Failed to add update history", e)
            }
        }
        pluginUpdatesFlow.value++
    }

    fun clearUpdatesHistory() {
        withDbLock {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.deleteAllPluginUpdates()
            } catch (e: Exception) {
                AppLogger.e("Failed to clear updates history", e)
            }
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

    const val PREF_ENABLE_DOWNLOAD_BUTTONS = "ENABLE_DOWNLOAD_BUTTONS"
    const val PREF_DOWNLOAD_THREADS = "DOWNLOAD_THREADS"
    const val PREF_DOWNLOAD_MAX_CONCURRENT = "DOWNLOAD_MAX_CONCURRENT"
    const val PREF_DOWNLOAD_PATH = "DOWNLOAD_PATH"

    private const val TRUSTED_PLUGINS_KEY = "trusted_plugins_set"

    fun getTrustedPlugins(): Set<String> {
        val cached = rawKeyCache[TRUSTED_PLUGINS_KEY]
        val json = if (cached != null) {
            cached
        } else {
            val dbVal = withDbLock {
                try {
                    DatabaseFactory.database.cloudstreamDBQueries.selectKeyValue(TRUSTED_PLUGINS_KEY).executeAsOneOrNull()
                } catch (e: Exception) {
                    AppLogger.e("Failed to select trusted plugins from SQLite", e)
                    null
                }
            }
            if (dbVal != null) rawKeyCache[TRUSTED_PLUGINS_KEY] = dbVal
            dbVal
        } ?: return emptySet()

        return try {
            val list: List<String> = mapper.readValue(json, object : TypeReference<List<String>>() {})
            list.map { it.lowercase().trim() }.toSet()
        } catch (e: Exception) {
            AppLogger.e("DesktopDataStore: Failed to deserialize trusted plugins set", e)
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

    fun setPluginsTrusted(internalNames: Collection<String>, trusted: Boolean) = withDbLock {
        if (internalNames.isEmpty()) return@withDbLock
        val current = getTrustedPlugins().toMutableSet()
        for (internalName in internalNames) {
            val cleanName = internalName.removeSuffix("-jvm").lowercase().trim()
            val lower = internalName.lowercase().trim()
            if (trusted) {
                current.add(cleanName)
                current.add(lower)
            } else {
                current.remove(cleanName)
                current.remove(lower)
            }
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

    fun setPluginTrusted(internalName: String, trusted: Boolean) {
        setPluginsTrusted(listOf(internalName), trusted)
    }
}
