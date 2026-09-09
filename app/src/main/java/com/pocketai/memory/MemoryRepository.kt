package com.pocketai.memory

import com.pocketai.data.local.database.dao.MemoryDao
import com.pocketai.data.local.database.entities.MemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoryRepository(private val dao: MemoryDao) {

    val allMemories: Flow<List<MemoryItem>> = dao.getAllMemories().map { list ->
        list.map { it.toDomain() }
    }

    fun getMemoriesByCategory(category: MemoryCategory): Flow<List<MemoryItem>> {
        return dao.getMemoriesByCategory(category.name).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun searchMemories(query: String): Flow<List<MemoryItem>> {
        return dao.searchMemories(query).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getTopMemoriesForContext(limit: Int = 10): List<MemoryItem> {
        return dao.getTopMemories(limit).map { it.toDomain() }
    }

    suspend fun saveMemory(item: MemoryItem) {
        dao.insertMemory(item.toEntity())
    }

    suspend fun deleteMemory(id: String) {
        dao.deleteMemoryById(id)
    }

    suspend fun clearAll() {
        dao.clearAllMemories()
    }

    private fun MemoryEntity.toDomain(): MemoryItem {
        val cat = try {
            MemoryCategory.valueOf(category)
        } catch (_: Exception) {
            MemoryCategory.FACT
        }
        val tagList = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }

        return MemoryItem(
            id = id,
            category = cat,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            importance = importance,
            source = source,
            tags = tagList,
            embeddingReference = embeddingReference
        )
    }

    private fun MemoryItem.toEntity(): MemoryEntity {
        return MemoryEntity(
            id = id,
            category = category.name,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            importance = importance,
            source = source,
            tags = tags.joinToString(","),
            embeddingReference = embeddingReference
        )
    }
}
