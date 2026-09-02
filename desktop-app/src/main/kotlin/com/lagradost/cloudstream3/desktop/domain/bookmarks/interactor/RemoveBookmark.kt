package com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor

import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RemoveBookmark(
    private val repository: BookmarksRepository,
) {
    suspend fun await(id: String) = withContext(Dispatchers.IO) {
        repository.removeBookmark(id)
    }
}
