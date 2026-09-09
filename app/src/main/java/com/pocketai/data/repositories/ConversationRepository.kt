package com.pocketai.data.repositories

import com.pocketai.brain.ChatMessage
import com.pocketai.brain.MessageRole
import com.pocketai.brain.ModelSourceType
import com.pocketai.data.local.database.dao.ConversationDao
import com.pocketai.data.local.database.entities.ChatMessageEntity
import com.pocketai.data.local.database.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelId: String? = null,
    val modelName: String? = null
)

class ConversationRepository(private val dao: ConversationDao) {

    val allConversations: Flow<List<Conversation>> = dao.getAllConversations().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getConversationById(id: String): Conversation? {
        return dao.getConversationById(id)?.toDomain()
    }

    suspend fun createNewConversation(
        title: String = "New Conversation",
        modelName: String? = null
    ): Conversation {
        val conv = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            modelId = null,
            modelName = modelName
        )
        dao.insertConversation(conv.toEntity())
        return conv
    }

    suspend fun updateConversation(conv: Conversation) {
        dao.updateConversation(conv.toEntity())
    }

    suspend fun deleteConversation(id: String) {
        dao.deleteMessagesForConversation(id)
        dao.deleteConversationById(id)
    }

    fun getMessages(conversationId: String): Flow<List<ChatMessage>> {
        return dao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMessagesSync(conversationId: String): List<ChatMessage> {
        return dao.getMessagesForConversationSync(conversationId).map { it.toDomain() }
    }

    suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        dao.insertMessage(message.toEntity(conversationId))
        // Update conversation timestamp
        dao.getConversationById(conversationId)?.let { conv ->
            val updatedTitle = if (conv.title == "New Conversation" && message.role == MessageRole.USER) {
                message.content.take(30).replace("\n", " ").trim()
            } else {
                conv.title
            }
            dao.updateConversation(conv.copy(title = updatedTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        modelId = modelId,
        modelName = modelName
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        modelId = modelId,
        modelName = modelName
    )

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        role = try { MessageRole.valueOf(role.uppercase()) } catch (_: Exception) { MessageRole.USER },
        content = content,
        timestamp = timestamp,
        modelSource = try { ModelSourceType.valueOf(modelSource.uppercase()) } catch (_: Exception) { ModelSourceType.LOCAL },
        modelName = modelName,
        latencyMs = latencyMs
    )

    private fun ChatMessage.toEntity(conversationId: String) = ChatMessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.name.lowercase(),
        content = content,
        timestamp = timestamp,
        modelSource = modelSource.name.lowercase(),
        modelName = modelName,
        latencyMs = latencyMs
    )
}
