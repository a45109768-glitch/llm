package com.pocketai.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val category: String, // FACT, PREFERENCE, IDENTITY, GOAL, PROJECT, SYSTEM
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val importance: Int = 3, // 1 to 5
    val source: String = "manual", // manual, chat_extraction, obsidian_sync
    val tags: String = "", // Comma-separated
    val embeddingReference: String? = null
)

@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val fileName: String,
    val fileUri: String,
    val format: String = "GGUF",
    val sizeBytes: Long = 0L,
    val architecture: String? = null,
    val quantization: String? = null,
    val contextLength: Int? = null,
    val status: String = "UNLOADED", // NOT_IMPORTED, IMPORTED, LOADING, LOADED, UNLOADED, UNLOADING, ERROR
    val isLoaded: Boolean = false,
    val importTimestamp: Long = System.currentTimeMillis(),
    val lastLoadedTimestamp: Long? = null,
    val parametersCount: String? = null,
    val lastError: String? = null
)

@Entity(tableName = "cloud_providers")
data class CloudProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val organizationId: String? = null,
    val isEnabled: Boolean = true,
    val providerType: String = "OPENAI_COMPATIBLE", // OPENAI_COMPATIBLE, DEEPSEEK, CUSTOM
    val customHeadersJson: String = "{}",
    val timeoutSeconds: Int = 60,
    val lastTestedTimestamp: Long? = null,
    val lastTestSuccess: Boolean? = null,
    val lastTestLatencyMs: Long? = null,
    val lastTestMessage: String? = null
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelId: String? = null,
    val modelName: String? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelSource: String = "local", // "local", "cloud", "system"
    val modelName: String? = null,
    val latencyMs: Long? = null
)

@Entity(tableName = "robot_configs")
data class RobotConfigEntity(
    @PrimaryKey val id: String = "default_robot",
    val robotName: String = "PocketBot-01",
    val serverUrl: String = "https://server.pocketai.internal",
    val authToken: String = "",
    val connectionStatus: String = "STANDBY", // DISCONNECTED, STANDBY, CONNECTED
    val lastSyncTimestamp: Long? = null
)
