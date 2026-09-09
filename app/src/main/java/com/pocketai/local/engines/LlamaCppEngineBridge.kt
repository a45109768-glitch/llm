package com.pocketai.local.engines

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * High-performance inference bridge powered by the PrismML llama.cpp native engine.
 * Delegates all lifecycle and execution directly to NativeLlamaEngine.
 */
class LlamaCppEngineBridge(context: Context) : InferenceEngine {

    private val delegate = NativeLlamaEngine(context)

    override val engineName: String
        get() = delegate.engineName

    override val isNativeEngineAvailable: Boolean
        get() = delegate.isNativeEngineAvailable

    override suspend fun loadModelFromUri(
        uri: Uri,
        modelName: String,
        params: ModelInferenceParams
    ): Result<EngineStatus> = delegate.loadModelFromUri(uri, modelName, params)

    override suspend fun unload(): Result<Unit> = delegate.unload()

    override suspend fun generate(
        prompt: String,
        contextHistory: String,
        params: ModelInferenceParams
    ): Result<String> = delegate.generate(prompt, contextHistory, params)

    override fun streamGenerate(
        prompt: String,
        contextHistory: String,
        params: ModelInferenceParams
    ): Flow<String> = delegate.streamGenerate(prompt, contextHistory, params)

    override fun stopGeneration() = delegate.stopGeneration()

    override fun getHardwareDiagnostics(): HardwareDiagnostics = delegate.getHardwareDiagnostics()

    override fun getLatestGenerationDiagnostics(): GenerationDiagnostics? = delegate.getLatestGenerationDiagnostics()

    override fun isLoaded(): Boolean = delegate.isLoaded()
}
