package com.lagradost.cloudstream3.desktop.domain.category.model

import com.lagradost.common.storage.DesktopWatchType

data class Category(
    val id: Int,
    val name: String,
    val watchType: DesktopWatchType? = null,
    val order: Int = 0,
    val isDefault: Boolean = false,
    val isHidden: Boolean = false,
) {
    companion object {
        fun defaultCategories(): List<Category> = listOf(
            Category(id = DesktopWatchType.WATCHING.id, name = "Watching", watchType = DesktopWatchType.WATCHING, order = 0, isDefault = true),
            Category(id = DesktopWatchType.PLANTOWATCH.id, name = "Plan to Watch", watchType = DesktopWatchType.PLANTOWATCH, order = 1, isDefault = true),
            Category(id = DesktopWatchType.COMPLETED.id, name = "Completed", watchType = DesktopWatchType.COMPLETED, order = 2, isDefault = true),
            Category(id = DesktopWatchType.ONHOLD.id, name = "On Hold", watchType = DesktopWatchType.ONHOLD, order = 3, isDefault = true),
            Category(id = DesktopWatchType.DROPPED.id, name = "Dropped", watchType = DesktopWatchType.DROPPED, order = 4, isDefault = true),
        )
    }
}
