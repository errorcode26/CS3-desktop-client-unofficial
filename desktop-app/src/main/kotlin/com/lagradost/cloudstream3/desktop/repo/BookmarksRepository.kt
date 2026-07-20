package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.cloudstream3.desktop.utils.appScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BookmarksRepository {
    private val _bookmarksFlow = MutableStateFlow<Map<String, DesktopBookmark>>(emptyMap())
    val bookmarksFlow: StateFlow<Map<String, DesktopBookmark>> = _bookmarksFlow.asStateFlow()

    init {
        appScope.launch(Dispatchers.IO) {
            refresh()
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val list = DesktopDataStore.getBookmarks()
            _bookmarksFlow.value = list.associateBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addBookmark(bookmark: DesktopBookmark) {
        appScope.launch(Dispatchers.IO) {
            try {
                DesktopDataStore.addBookmark(bookmark)
                _bookmarksFlow.update { it + (bookmark.id to bookmark) }
            } catch (e: Exception) {
                e.printStackTrace()
                refresh()
            }
        }
    }

    fun removeBookmark(id: String) {
        appScope.launch(Dispatchers.IO) {
            try {
                DesktopDataStore.removeBookmark(id)
                _bookmarksFlow.update { it - id }
            } catch (e: Exception) {
                e.printStackTrace()
                refresh()
            }
        }
    }
}
