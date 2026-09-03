package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository as DomainBookmarksRepository
import com.lagradost.cloudstream3.desktop.utils.appScope
import com.lagradost.common.storage.DesktopBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object BookmarksRepository {
    val instance: DomainBookmarksRepository
        get() = AppContainerHolder.container.bookmarksRepository

    val bookmarksFlow: StateFlow<Map<String, DesktopBookmark>>
        get() = instance.subscribeAll()

    fun addBookmark(bookmark: DesktopBookmark) {
        appScope.launch(Dispatchers.IO) {
            instance.addBookmark(bookmark)
        }
    }

    fun removeBookmark(id: String) {
        appScope.launch(Dispatchers.IO) {
            instance.removeBookmark(id)
        }
    }
}
