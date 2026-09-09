package com.pocketai.memory

import com.pocketai.core.logging.AppLogger

class MemoryManager(private val repository: MemoryRepository) {

    val allMemories = repository.allMemories

    suspend fun addMemory(
        content: String,
        category: MemoryCategory = MemoryCategory.FACT,
        importance: Int = 3,
        tags: List<String> = emptyList(),
        source: String = "manual"
    ): MemoryItem {
        val memory = MemoryItem(
            category = category,
            content = content.trim(),
            importance = importance.coerceIn(1, 5),
            tags = tags,
            source = source
        )
        repository.saveMemory(memory)
        AppLogger.i("MemoryManager", "Added memory: ${memory.id} in category $category")
        return memory
    }

    suspend fun deleteMemory(id: String) {
        repository.deleteMemory(id)
    }

    suspend fun clearAllMemories() {
        repository.clearAll()
        AppLogger.i("MemoryManager", "Cleared all user memories.")
    }

    /**
     * Builds a structured prompt block of user memories to inject into system prompt.
     */
    suspend fun buildMemoryContext(limit: Int = 8): String {
        val topMemories = repository.getTopMemoriesForContext(limit)
        if (topMemories.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n[PERSISTENT USER MEMORY & CONTEXT]\n")
        topMemories.forEach { memory ->
            sb.append("- [${memory.category.name}] ${memory.content}\n")
        }
        sb.append("[END OF MEMORY]\n")
        return sb.toString()
    }
}
