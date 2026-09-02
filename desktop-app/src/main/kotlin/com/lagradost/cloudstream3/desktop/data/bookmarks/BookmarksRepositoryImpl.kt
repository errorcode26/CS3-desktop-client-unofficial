package com.lagradost.cloudstream3.desktop.data.bookmarks

import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class BookmarksRepositoryImpl : BookmarksRepository {

    private val _bookmarksFlow = MutableStateFlow<Map<String, DesktopBookmark>>(emptyMap())
    private var isInitialized = false

    private suspend fun ensureLoaded() {
        if (!isInitialized) {
            val list = DesktopDataStore.getBookmarks()
            _bookmarksFlow.value = list.associateBy { it.id }
            isInitialized = true
        }
    }

    override fun subscribeAll(): StateFlow<Map<String, DesktopBookmark>> {
        if (!isInitialized) {
            val list = DesktopDataStore.getBookmarks()
            _bookmarksFlow.value = list.associateBy { it.id }
            isInitialized = true
        }
        return _bookmarksFlow.asStateFlow()
    }

    override suspend fun getAll(): List<DesktopBookmark> = withContext(Dispatchers.IO) {
        ensureLoaded()
        DesktopDataStore.getBookmarks()
    }

    override suspend fun getById(id: String): DesktopBookmark? = withContext(Dispatchers.IO) {
        ensureLoaded()
        _bookmarksFlow.value[id] ?: DesktopDataStore.getBookmarks().firstOrNull { it.id == id }
    }

    override suspend fun isBookmarked(id: String): Boolean = withContext(Dispatchers.IO) {
        ensureLoaded()
        _bookmarksFlow.value.containsKey(id) || DesktopDataStore.isBookmarked(id)
    }

    override suspend fun addBookmark(bookmark: DesktopBookmark) = withContext(Dispatchers.IO) {
        DesktopDataStore.addBookmark(bookmark)
        _bookmarksFlow.update { it + (bookmark.id to bookmark) }
    }

    override suspend fun removeBookmark(id: String) = withContext(Dispatchers.IO) {
        DesktopDataStore.removeBookmark(id)
        _bookmarksFlow.update { it - id }
    }
}
