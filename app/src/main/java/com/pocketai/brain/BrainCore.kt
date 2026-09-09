package com.pocketai.brain

import com.pocketai.cloud.CloudProviderManager
import com.pocketai.core.logging.AppLogger
import com.pocketai.local.LocalModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class BrainStreamEvent {
    data class Metadata(val source: ModelSourceType, val modelName: String) : BrainStreamEvent()
    data class Chunk(val text: String) : BrainStreamEvent()
    data class Complete(
        val fullResponse: String,
        val latencyMs: Long,
        val tokensPerSecond: Double = 0.0
    ) : BrainStreamEvent()
    data class Error(val message: String, val cause: Throwable? = null) : BrainStreamEvent()
}

class BrainCore(
    private val modelRouter: ModelRouter,
    private val promptManager: PromptManager,
    private val contextManager: ContextManager,
    private val localModelManager: LocalModelManager,
    private val cloudProviderManager: CloudProviderManager
) {

    fun stopGeneration() {
        AppLogger.i("BrainCore", "Stop generation requested")
        localModelManager.stopGeneration()
    }

    /**
     * Central entry point to stream AI responses.
     * Delegates entirely through provider abstractions resolved by ModelRouter.
     */
    fun processPromptStream(
        prompt: String,
        conversationHistory: List<ChatMessage>
    ): Flow<BrainStreamEvent> = flow {
        val startTime = System.currentTimeMillis()
        try {
            AppLogger.i("BrainCore", "Processing user prompt (${prompt.length} chars)")

            // 1. Resolve target provider via ModelRouter
            val target = modelRouter.resolveExecutionTarget()

            // 2. Prepare System Prompt (injected with Memory & Obsidian context)
            val systemPrompt = promptManager.buildSystemPrompt(prompt)

            // 3. Assemble sliding context window
            val preparedContext = contextManager.prepareContext(systemPrompt, conversationHistory, prompt)

            val fullResponseBuilder = StringBuilder()

            when (target) {
                is SelectedExecutionTarget.LocalTarget -> {
                    val modelName = target.model.name
                    emit(BrainStreamEvent.Metadata(ModelSourceType.LOCAL, modelName))

                    localModelManager.streamGenerate(prompt, preparedContext).collect { chunk ->
                        fullResponseBuilder.append(chunk)
                        emit(BrainStreamEvent.Chunk(chunk))
                    }
                }

                is SelectedExecutionTarget.CloudTarget -> {
                    val modelDisplayName = "${target.config.name} (${target.config.modelName})"
                    emit(BrainStreamEvent.Metadata(ModelSourceType.CLOUD, modelDisplayName))

                    cloudProviderManager.streamGenerate(
                        config = target.config,
                        prompt = prompt,
                        systemPrompt = systemPrompt,
                        history = preparedContext
                    ).collect { chunk ->
                        fullResponseBuilder.append(chunk)
                        emit(BrainStreamEvent.Chunk(chunk))
                    }
                }
            }

            val latency = System.currentTimeMillis() - startTime
            val tokPerSec = localModelManager.getLatestGenerationDiagnostics()?.tokensPerSecond ?: 0.0
            emit(BrainStreamEvent.Complete(fullResponseBuilder.toString(), latency, tokPerSec))
        } catch (e: Exception) {
            AppLogger.e("BrainCore", "Inference orchestration failed: ${e.message}", e)
            emit(BrainStreamEvent.Error(e.message ?: "An unexpected error occurred during inference.", e))
        }
    }

    /**
     * Synchronous / One-shot generation.
     */
    suspend fun processPrompt(
        prompt: String,
        conversationHistory: List<ChatMessage>
    ): Result<Pair<String, ActiveRoute>> {
        return try {
            val target = modelRouter.resolveExecutionTarget()
            val systemPrompt = promptManager.buildSystemPrompt(prompt)
            val preparedContext = contextManager.prepareContext(systemPrompt, conversationHistory, prompt)

            val (response, route) = when (target) {
                is SelectedExecutionTarget.LocalTarget -> {
                    val result = localModelManager.generate(prompt, preparedContext).getOrThrow()
                    val activeRoute = ActiveRoute(
                        sourceType = ModelSourceType.LOCAL,
                        modelId = target.model.id,
                        displayName = "Local • ${target.model.name}",
                        isReady = true,
                        statusMessage = "Local response"
                    )
                    Pair(result, activeRoute)
                }

                is SelectedExecutionTarget.CloudTarget -> {
                    val result = cloudProviderManager.generate(
                        config = target.config,
                        prompt = prompt,
                        systemPrompt = systemPrompt,
                        history = preparedContext
                    ).getOrThrow()
                    val activeRoute = ActiveRoute(
                        sourceType = ModelSourceType.CLOUD,
                        modelId = target.config.id,
                        displayName = "Cloud • ${target.config.name}",
                        isReady = true,
                        statusMessage = "Cloud response"
                    )
                    Pair(result, activeRoute)
                }
            }
            Result.success(Pair(response, route))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
