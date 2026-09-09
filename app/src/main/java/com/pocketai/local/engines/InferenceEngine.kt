package com.pocketai.local.engines

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class ModelInferenceParams(
    val contextLength: Int = 2048,
    val maxTokens: Int = 512,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f
)

data class GenerationDiagnostics(
    val loadTimeMs: Long = 0L,
    val tokensGenerated: Int = 0,
    val generationTimeMs: Long = 0L,
    val tokensPerSecond: Double = 0.0,
    val timeToFirstTokenMs: Long = 0L,
    val activeModelName: String = ""
)

data class EngineStatus(
    val isLoaded: Boolean,
    val modelName: String,
    val memoryFootprintMb: Long,
    val statusMessage: String,
    val architecture: String? = null,
    val contextLength: Int? = null,
    val parametersCount: String? = null
)

data class HardwareDiagnostics(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isHardwareSufficient: Boolean,
    val recommendedMaxModelSizeMb: Long,
    val processorCores: Int
)

interface InferenceEngine {
    val engineName: String
    val isNativeEngineAvailable: Boolean

    suspend fun loadModelFromUri(
        uri: Uri,
        modelName: String,
        params: ModelInferenceParams = ModelInferenceParams()
    ): Result<EngineStatus>

    suspend fun unload(): Result<Unit>
    suspend fun generate(
        prompt: String,
        contextHistory: String,
        params: ModelInferenceParams = ModelInferenceParams()
    ): Result<String>

    fun streamGenerate(
        prompt: String,
        contextHistory: String,
        params: ModelInferenceParams = ModelInferenceParams()
    ): Flow<String>

    fun stopGeneration()
    fun getHardwareDiagnostics(): HardwareDiagnostics
    fun getLatestGenerationDiagnostics(): GenerationDiagnostics?
    fun isLoaded(): Boolean
}
