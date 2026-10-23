package com.lagradost.cloudstream3.desktop.data.bookmarks

import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class BookmarksRepositoryImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : BookmarksRepository {

    private val _bookmarksFlow = MutableStateFlow<Map<String, DesktopBookmark>>(emptyMap())
    private val isInitialized = AtomicBoolean(false)
    private val initMutex = Mutex()

    init {
        scope.launch {
            loadFromStorage()
        }
    }

    private suspend fun loadFromStorage() = withContext(Dispatchers.IO) {
        if (!isInitialized.get()) {
            initMutex.withLock {
                if (!isInitialized.get()) {
                    val list = DesktopDataStore.getBookmarks()
                    _bookmarksFlow.value = list.associateBy { it.id }
                    isInitialized.set(true)
                }
            }
        }
    }

    override fun subscribeAll(): StateFlow<Map<String, DesktopBookmark>> {
        if (!isInitialized.get()) {
            scope.launch {
                loadFromStorage()
            }
        }
        return _bookmarksFlow.asStateFlow()
    }

    override suspend fun getAll(): List<DesktopBookmark> = withContext(Dispatchers.IO) {
        loadFromStorage()
        _bookmarksFlow.value.values.toList()
    }

    override suspend fun getById(id: String): DesktopBookmark? = withContext(Dispatchers.IO) {
        loadFromStorage()
        val cleanId = if (id.startsWith("p") && id.contains("_")) id.substringAfter("_") else id
        _bookmarksFlow.value[cleanId] ?: _bookmarksFlow.value[id]
    }

    override suspend fun isBookmarked(id: String): Boolean = withContext(Dispatchers.IO) {
        loadFromStorage()
        val cleanId = if (id.startsWith("p") && id.contains("_")) id.substringAfter("_") else id
        _bookmarksFlow.value.containsKey(cleanId) || _bookmarksFlow.value.containsKey(id)
    }

    override suspend fun addBookmark(bookmark: DesktopBookmark) = withContext(Dispatchers.IO) {
        val cleanId = if (bookmark.id.startsWith("p") && bookmark.id.contains("_")) bookmark.id.substringAfter("_") else bookmark.id
        val cleanBookmark = if (cleanId != bookmark.id) bookmark.copy(id = cleanId) else bookmark
        DesktopDataStore.addBookmark(cleanBookmark)
        _bookmarksFlow.update { (it - bookmark.id) + (cleanBookmark.id to cleanBookmark) }
    }

    override suspend fun removeBookmark(id: String) = withContext(Dispatchers.IO) {
        val cleanId = if (id.startsWith("p") && id.contains("_")) id.substringAfter("_") else id
        DesktopDataStore.removeBookmark(cleanId)
        _bookmarksFlow.update { it - cleanId - id }
    }
}
