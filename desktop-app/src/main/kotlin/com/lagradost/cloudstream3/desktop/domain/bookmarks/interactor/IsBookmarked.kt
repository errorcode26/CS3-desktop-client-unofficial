package com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor

import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IsBookmarked(
    private val repository: BookmarksRepository,
) {
    suspend fun await(id: String): Boolean = withContext(Dispatchers.IO) {
        repository.isBookmarked(id)
    }
}
