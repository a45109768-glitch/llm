package com.pocketai.brain

import com.pocketai.data.preferences.PreferencesManager
import com.pocketai.memory.MemoryManager
import com.pocketai.obsidian.ObsidianManager
import kotlinx.coroutines.flow.first

class PromptManager(
    private val preferencesManager: PreferencesManager,
    private val memoryManager: MemoryManager,
    private val obsidianManager: ObsidianManager
) {

    suspend fun buildSystemPrompt(userQuery: String): String {
        val prefs = preferencesManager.preferencesFlow.first()
        val sb = StringBuilder()

        // 1. Base persona / Identity
        sb.append(prefs.systemPrompt)
        sb.append("\n\n")

        // 2. Persistent User Memory (independent of model)
        if (prefs.isMemoryEnabled) {
            val memoryBlock = memoryManager.buildMemoryContext(limit = 8)
            if (memoryBlock.isNotEmpty()) {
                sb.append(memoryBlock)
                sb.append("\n")
            }
        }

        // 3. Obsidian Knowledge retrieval (if vault connected & sync enabled)
        if (prefs.isObsidianSyncEnabled) {
            try {
                val keywords = userQuery.split(" ").filter { it.length > 3 }.take(3)
                if (keywords.isNotEmpty()) {
                    val matchingNotes = obsidianManager.searchKnowledge(keywords.first())
                    if (matchingNotes.isNotEmpty()) {
                        sb.append("\n[OBSIDIAN VAULT KNOWLEDGE REFERENCES]\n")
                        matchingNotes.take(2).forEach { note ->
                            sb.append("Note: ${note.title}\n")
                            sb.append(note.content.take(300))
                            sb.append("\n---\n")
                        }
                        sb.append("[END OF VAULT KNOWLEDGE]\n\n")
                    }
                }
            } catch (_: Exception) {}
        }

        return sb.toString().trim()
    }
}
