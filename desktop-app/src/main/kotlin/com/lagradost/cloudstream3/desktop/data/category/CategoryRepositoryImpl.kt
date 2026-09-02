package com.lagradost.cloudstream3.desktop.data.category

import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.desktop.data.bookmarks.BookmarksRepositoryImpl
import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository
import com.lagradost.cloudstream3.desktop.domain.category.model.Category
import com.lagradost.cloudstream3.desktop.domain.category.repository.CategoryRepository
import com.lagradost.cloudstream3.desktop.repo.PluginNetworkClient
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

private const val PREF_CUSTOM_CATEGORIES = "desktop_custom_categories_v1"

class CategoryRepositoryImpl(
    private val bookmarksRepo: BookmarksRepository = BookmarksRepositoryImpl(),
) : CategoryRepository {

    private val _categories = MutableStateFlow<List<Category>>(loadInitialCategories())

    override fun subscribeCategories(): Flow<List<Category>> = _categories.asStateFlow()

    override suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        _categories.value
    }

    override suspend fun createCategory(name: String): Category = withContext(Dispatchers.IO) {
        val current = _categories.value
        val nextId = (current.maxOfOrNull { it.id } ?: 100) + 1
        val newCategory = Category(
            id = nextId,
            name = name,
            order = current.size,
            isDefault = false,
        )
        val updated = current + newCategory
        persistCategories(updated)
        newCategory
    }

    override suspend fun renameCategory(id: Int, name: String): Boolean = withContext(Dispatchers.IO) {
        val current = _categories.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@withContext false
        val updated = current.toMutableList()
        updated[index] = updated[index].copy(name = name)
        persistCategories(updated)
        true
    }

    override suspend fun reorderCategories(categories: List<Category>): Boolean = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext false
        persistCategories(categories)
        true
    }

    override suspend fun deleteCategory(id: Int): Boolean = withContext(Dispatchers.IO) {
        val current = _categories.value
        val target = current.find { it.id == id }
        if (target == null || target.isDefault) return@withContext false // Protect default categories
        val updated = current.filter { it.id != id }
        persistCategories(updated)
        true
    }

    override suspend fun setItemCategory(bookmarkId: String, categoryId: Int): Boolean = withContext(Dispatchers.IO) {
        val existing = bookmarksRepo.getById(bookmarkId) ?: return@withContext false
        val updated = existing.copy(watchType = categoryId)
        bookmarksRepo.addBookmark(updated)
        true
    }

    private fun loadInitialCategories(): List<Category> {
        val defaults = Category.defaultCategories()
        val customJson: String? = DesktopDataStore.getKey(PREF_CUSTOM_CATEGORIES)
        if (customJson.isNullOrBlank()) return defaults
        return try {
            val customList: List<Category> = PluginNetworkClient.mapper.readValue(
                customJson,
                object : TypeReference<List<Category>>() {},
            )
            if (customList.isNotEmpty()) customList else defaults
        } catch (_: Exception) {
            defaults
        }
    }

    private fun persistCategories(list: List<Category>) {
        _categories.update { list }
        try {
            val json = PluginNetworkClient.mapper.writeValueAsString(list)
            DesktopDataStore.setKey(PREF_CUSTOM_CATEGORIES, json)
        } catch (_: Exception) {}
    }
}
