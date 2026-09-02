package com.lagradost.cloudstream3.desktop.domain.category.interactor

import com.lagradost.cloudstream3.desktop.domain.category.repository.CategoryRepository

class RenameCategory(
    private val repository: CategoryRepository,
) {
    suspend fun await(id: Int, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        return repository.renameCategory(id, trimmed)
    }
}
