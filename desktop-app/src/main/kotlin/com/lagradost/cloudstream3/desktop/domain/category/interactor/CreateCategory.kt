package com.lagradost.cloudstream3.desktop.domain.category.interactor

import com.lagradost.cloudstream3.desktop.domain.category.model.Category
import com.lagradost.cloudstream3.desktop.domain.category.repository.CategoryRepository

class CreateCategory(
    private val repository: CategoryRepository,
) {
    suspend fun await(name: String): Category {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Category name cannot be empty" }
        return repository.createCategory(trimmed)
    }
}
