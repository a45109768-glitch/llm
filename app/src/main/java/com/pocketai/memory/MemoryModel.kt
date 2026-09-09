package com.pocketai.memory

enum class MemoryCategory(val displayName: String, val iconLabel: String) {
    FACT("Fact", "📌"),
    PREFERENCE("Preference", "⭐"),
    IDENTITY("Identity", "👤"),
    GOAL("Goal", "🎯"),
    PROJECT("Project", "💼"),
    SYSTEM("System Note", "⚙️")
}

data class MemoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val category: MemoryCategory = MemoryCategory.FACT,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val importance: Int = 3, // 1 (low) to 5 (critical)
    val source: String = "manual",
    val tags: List<String> = emptyList(),
    val embeddingReference: String? = null
)
