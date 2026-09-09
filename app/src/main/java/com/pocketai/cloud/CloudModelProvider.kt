package com.pocketai.cloud

import com.pocketai.brain.ChatMessage
import kotlinx.coroutines.flow.Flow

enum class CloudProviderType {
    OPENAI_COMPATIBLE,
    DEEPSEEK,
    CUSTOM
}

data class CloudProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val organizationId: String? = null,
    val isEnabled: Boolean = true,
    val providerType: CloudProviderType = CloudProviderType.OPENAI_COMPATIBLE,
    val customHeaders: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 60,
    val lastTestedTimestamp: Long? = null,
    val lastTestSuccess: Boolean? = null,
    val lastTestLatencyMs: Long? = null,
    val lastTestMessage: String? = null
)

interface CloudModelProvider {
    val providerId: String
    val providerName: String

    suspend fun generate(
        config: CloudProviderConfig,
        prompt: String,
        systemPrompt: String?,
        history: List<ChatMessage>
    ): Result<String>

    fun streamGenerate(
        config: CloudProviderConfig,
        prompt: String,
        systemPrompt: String?,
        history: List<ChatMessage>
    ): Flow<String>
}
