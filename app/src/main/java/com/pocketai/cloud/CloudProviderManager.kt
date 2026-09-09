package com.pocketai.cloud

import com.pocketai.brain.ChatMessage
import com.pocketai.cloud.providers.OpenAiCompatibleProvider
import com.pocketai.core.error.PocketAIException
import com.pocketai.core.logging.AppLogger
import kotlinx.coroutines.flow.Flow

class CloudProviderManager(
    val repository: CloudModelRepository,
    val apiTester: ApiTester = ApiTester(),
    private val openAiProvider: OpenAiCompatibleProvider = OpenAiCompatibleProvider()
) {

    suspend fun testProvider(config: CloudProviderConfig): ApiTestResult {
        val result = apiTester.testEndpoint(config)
        repository.updateTestResult(
            id = config.id,
            success = result.isSuccess,
            latencyMs = result.latencyMs,
            message = if (result.isSuccess) "Working (${result.latencyMs}ms)" else (result.errorMessage ?: "Failed")
        )
        return result
    }

    suspend fun generate(
        config: CloudProviderConfig,
        prompt: String,
        systemPrompt: String? = null,
        history: List<ChatMessage> = emptyList()
    ): Result<String> {
        if (!config.isEnabled) {
            return Result.failure(
                PocketAIException.ModelNotLoadedException("Cloud provider '${config.name}' is currently disabled.")
            )
        }

        AppLogger.i("CloudProviderManager", "Routing generation to cloud provider: ${config.name} (${config.modelName})")
        return openAiProvider.generate(config, prompt, systemPrompt, history)
    }

    suspend fun generate(
        providerId: String,
        prompt: String,
        systemPrompt: String?,
        history: List<ChatMessage>
    ): Result<String> {
        val config = repository.getProviderById(providerId)
            ?: return Result.failure(PocketAIException.NoActiveModelException())
        return generate(config, prompt, systemPrompt, history)
    }

    fun streamGenerate(
        config: CloudProviderConfig,
        prompt: String,
        systemPrompt: String? = null,
        history: List<ChatMessage> = emptyList()
    ): Flow<String> {
        AppLogger.i("CloudProviderManager", "Streaming generation from cloud provider: ${config.name}")
        return openAiProvider.streamGenerate(config, prompt, systemPrompt, history)
    }

    fun streamGenerate(
        providerId: String,
        prompt: String,
        systemPrompt: String?,
        history: List<ChatMessage>,
        config: CloudProviderConfig
    ): Flow<String> {
        return streamGenerate(config, prompt, systemPrompt, history)
    }

    companion object {
        val PRESET_PROVIDERS = listOf(
            CloudProviderConfig(
                id = "preset_deepseek",
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "",
                modelName = "deepseek-chat",
                isEnabled = true,
                providerType = CloudProviderType.DEEPSEEK
            ),
            CloudProviderConfig(
                id = "preset_openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                modelName = "gpt-4o-mini",
                isEnabled = true,
                providerType = CloudProviderType.OPENAI_COMPATIBLE
            ),
            CloudProviderConfig(
                id = "preset_openrouter",
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                apiKey = "",
                modelName = "mistralai/mistral-7b-instruct",
                isEnabled = true,
                providerType = CloudProviderType.OPENAI_COMPATIBLE
            ),
            CloudProviderConfig(
                id = "preset_ollama",
                name = "Local Ollama / Server",
                baseUrl = "http://10.0.2.2:11434/v1",
                apiKey = "",
                modelName = "llama3.2:1b",
                isEnabled = true,
                providerType = CloudProviderType.OPENAI_COMPATIBLE
            )
        )
    }
}
