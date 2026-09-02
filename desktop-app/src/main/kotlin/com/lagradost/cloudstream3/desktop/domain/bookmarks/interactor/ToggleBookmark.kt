package com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor

import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository
import com.lagradost.common.storage.DesktopBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToggleBookmark(
    private val repository: BookmarksRepository,
) {
    suspend fun saveBookmark(bookmark: DesktopBookmark) = withContext(Dispatchers.IO) {
        repository.addBookmark(bookmark)
    }

    suspend fun toggle(
        id: String,
        name: String,
        url: String,
        apiName: String,
        posterUrl: String?,
        watchType: Int,
    ) = withContext(Dispatchers.IO) {
        val existing = repository.getById(id)
        if (existing != null && existing.watchType == watchType) {
            repository.removeBookmark(id)
        } else {
            val newBookmark = DesktopBookmark(
                id = id,
                name = name,
                url = url,
                apiName = apiName,
                posterUrl = posterUrl,
                watchType = watchType,
                dateAdded = existing?.dateAdded ?: System.currentTimeMillis(),
            )
            repository.addBookmark(newBookmark)
        }
    }
}
