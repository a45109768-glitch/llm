package com.pocketai.brain

class ContextManager(
    private val maxHistoryTurns: Int = 12,
    private val approximateMaxChars: Int = 8000
) {

    /**
     * Filters and truncates conversation history to fit within context limits.
     */
    fun prepareContext(
        systemPrompt: String,
        history: List<ChatMessage>,
        latestPrompt: String
    ): List<ChatMessage> {
        val contextList = mutableListOf<ChatMessage>()

        // System message first
        if (systemPrompt.isNotBlank()) {
            contextList.add(
                ChatMessage(
                    role = MessageRole.SYSTEM,
                    content = systemPrompt,
                    modelSource = ModelSourceType.SYSTEM
                )
            )
        }

        // Take last N messages
        val recentHistory = history.takeLast(maxHistoryTurns)
        var totalChars = systemPrompt.length + latestPrompt.length

        val filteredRecent = mutableListOf<ChatMessage>()
        for (msg in recentHistory.reversed()) {
            if (totalChars + msg.content.length > approximateMaxChars) {
                break
            }
            filteredRecent.add(0, msg)
            totalChars += msg.content.length
        }

        contextList.addAll(filteredRecent)
        return contextList
    }

    /**
     * Approximates token count (standard 1 token ~ 4 characters).
     */
    fun estimateTokenCount(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }
}
