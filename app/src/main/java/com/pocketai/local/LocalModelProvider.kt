package com.pocketai.local

import com.pocketai.brain.ChatMessage
import kotlinx.coroutines.flow.Flow

data class LocalModelInfo(
    val id: String,
    val name: String,
    val fileName: String,
    val fileUri: String,
    val format: String = "GGUF",
    val sizeBytes: Long = 0L,
    val architecture: String? = null,
    val quantization: String? = null,
    val contextLength: Int? = null,
    val parametersCount: String? = null,
    val status: LocalModelStatus = LocalModelStatus.UNLOADED,
    val isLoaded: Boolean = false,
    val importTimestamp: Long = System.currentTimeMillis(),
    val memoryFootprintMb: Long = 0L,
    val lastError: String? = null
)

enum class LocalModelStatus {
    NOT_IMPORTED,
    IMPORTED,
    LOADING,
    LOADED,
    UNLOADED,
    UNLOADING,
    ERROR
}

interface LocalModelProvider {
    suspend fun loadModel(modelId: String): Result<Unit>
    suspend fun unloadModel(modelId: String): Result<Unit>
    suspend fun generate(prompt: String, context: List<ChatMessage>): Result<String>
    fun streamGenerate(prompt: String, context: List<ChatMessage>): Flow<String>
    fun stopGeneration()
    fun getModelInfo(modelId: String): LocalModelInfo?
    fun isLoaded(): Boolean
    fun releaseResources()
}
