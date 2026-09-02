package com.lagradost.cloudstream3.desktop.domain.category.repository

import com.lagradost.cloudstream3.desktop.domain.category.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun subscribeCategories(): Flow<List<Category>>
    suspend fun getCategories(): List<Category>
    suspend fun createCategory(name: String): Category
    suspend fun renameCategory(id: Int, name: String): Boolean
    suspend fun reorderCategories(categories: List<Category>): Boolean
    suspend fun deleteCategory(id: Int): Boolean
    suspend fun setItemCategory(bookmarkId: String, categoryId: Int): Boolean
}
