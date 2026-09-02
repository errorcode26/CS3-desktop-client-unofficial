package com.lagradost.cloudstream3.desktop.domain.category.interactor

import com.lagradost.cloudstream3.desktop.domain.category.model.Category
import com.lagradost.cloudstream3.desktop.domain.category.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class GetCategories(
    private val repository: CategoryRepository,
) {
    fun subscribe(): Flow<List<Category>> = repository.subscribeCategories()

    suspend fun awaitAll(): List<Category> = repository.getCategories()
}
