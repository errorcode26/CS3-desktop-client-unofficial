package com.lagradost.cloudstream3.desktop.domain.bookmarks.repository

import com.lagradost.common.storage.DesktopBookmark
import kotlinx.coroutines.flow.StateFlow

interface BookmarksRepository {
    fun subscribeAll(): StateFlow<Map<String, DesktopBookmark>>
    suspend fun getAll(): List<DesktopBookmark>
    suspend fun getById(id: String): DesktopBookmark?
    suspend fun isBookmarked(id: String): Boolean
    suspend fun addBookmark(bookmark: DesktopBookmark)
    suspend fun removeBookmark(id: String)
}
