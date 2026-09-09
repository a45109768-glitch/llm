package com.pocketai.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.pocketai.brain.ChatMessage
import com.pocketai.core.error.PocketAIException
import com.pocketai.core.logging.AppLogger
import com.pocketai.data.preferences.PreferencesManager
import com.pocketai.local.engines.ChatTemplateFormatter
import com.pocketai.local.engines.GenerationDiagnostics
import com.pocketai.local.engines.GgufHeaderParser
import com.pocketai.local.engines.InferenceEngine
import com.pocketai.local.engines.ModelInferenceParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

class LocalModelManager(
    private val context: Context,
    private val repository: LocalModelRepository,
    val engine: InferenceEngine,
    private val preferencesManager: PreferencesManager? = null
) : LocalModelProvider {

    suspend fun importModelFromUri(uri: Uri): Result<LocalModelInfo> = withContext(Dispatchers.IO) {
        try {
            // Persist read permissions for Storage Access Framework URI
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                AppLogger.w("LocalModelManager", "Could not take persistable URI permission: ${e.message}")
            }

            var displayName = "model.gguf"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            // Ensure app-private models directory
            val modelsDir = java.io.File(context.filesDir, "models").apply {
                if (!exists()) mkdirs()
            }
            val cleanFileName = if (displayName.endsWith(".gguf", ignoreCase = true)) displayName else "$displayName.gguf"
            val safeFileName = cleanFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val localModelFile = java.io.File(modelsDir, safeFileName)

            // Copy to local app-private storage if not already present or if incomplete
            if (!localModelFile.exists() || localModelFile.length() == 0L || (fileSize > 0 && localModelFile.length() != fileSize)) {
                AppLogger.i("LocalModelManager", "Copying SAF model to local storage: ${localModelFile.absolutePath}")
                val tempFile = java.io.File(modelsDir, "${safeFileName}.tmp.${System.currentTimeMillis()}")
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw PocketAIException.ModelNotFoundException("Failed to open input stream for $uri")
                tempFile.outputStream().use { out ->
                    stream.use { inp ->
                        inp.copyTo(out, bufferSize = 64 * 1024)
                    }
                }
                if (localModelFile.exists()) localModelFile.delete()
                if (!tempFile.renameTo(localModelFile)) {
                    tempFile.copyTo(localModelFile, overwrite = true)
                    tempFile.delete()
                }
            }

            fileSize = localModelFile.length()

            // Inspect GGUF header from local file
            val metadata = localModelFile.inputStream().use { stream ->
                GgufHeaderParser.parse(stream, cleanFileName)
            }

            val cleanModelName = displayName.removeSuffix(".gguf").removeSuffix(".GGUF")
                .replace("-", " ")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            val modelInfo = LocalModelInfo(
                id = UUID.randomUUID().toString(),
                name = cleanModelName,
                fileName = displayName,
                fileUri = Uri.fromFile(localModelFile).toString(),
                format = if (metadata.isValidGguf) "GGUF v${metadata.version}" else "GGUF",
                sizeBytes = fileSize,
                architecture = metadata.architecture ?: "transformer",
                quantization = metadata.quantization,
                contextLength = metadata.contextLength ?: 2048,
                status = LocalModelStatus.IMPORTED,
                isLoaded = false,
                importTimestamp = System.currentTimeMillis()
            )

            repository.saveModel(modelInfo)
            AppLogger.i("LocalModelManager", "Imported model '$cleanModelName' (${fileSize / (1024 * 1024)}MB) stored at ${localModelFile.absolutePath}")
            Result.success(modelInfo)
        } catch (e: Exception) {
            AppLogger.e("LocalModelManager", "Failed to import model from URI: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val model = repository.getModelById(modelId)
            ?: return@withContext Result.failure(PocketAIException.ModelNotFoundException(modelId))

        AppLogger.i("LocalModelManager", "Transitioning state -> LOADING for '${model.name}'")
        repository.updateStatus(modelId, LocalModelStatus.LOADING)

        val uri = Uri.parse(model.fileUri)

        // Read user-configured inference parameters
        val prefs = preferencesManager?.preferencesFlow?.firstOrNull()
        val params = ModelInferenceParams(
            contextLength = prefs?.localContextLength ?: (model.contextLength ?: 2048),
            maxTokens = prefs?.localMaxTokens ?: 512,
            threads = prefs?.localCpuThreads ?: 4,
            temperature = prefs?.temperature ?: 0.7f,
            topP = prefs?.localTopP ?: 0.9f,
            topK = prefs?.localTopK ?: 40,
            repeatPenalty = prefs?.localRepeatPenalty ?: 1.1f
        )

        val loadResult = engine.loadModelFromUri(uri, model.name, params)
        if (loadResult.isSuccess) {
            val status = loadResult.getOrNull()
            repository.markLoaded(modelId)

            if (status != null && (status.architecture != null || status.contextLength != null || status.parametersCount != null)) {
                repository.updateMetadata(
                    id = modelId,
                    architecture = status.architecture ?: model.architecture,
                    contextLength = status.contextLength ?: model.contextLength,
                    parametersCount = status.parametersCount ?: model.parametersCount
                )
            }
            AppLogger.i("LocalModelManager", "Transitioning state -> LOADED for '${model.name}'")
            Result.success(Unit)
        } else {
            val error = loadResult.exceptionOrNull() ?: Exception("Failed to load model")
            AppLogger.e("LocalModelManager", "Transitioning state -> ERROR for '${model.name}': ${error.message}")
            repository.updateStatus(modelId, LocalModelStatus.ERROR, error.message)
            Result.failure(error)
        }
    }

    override suspend fun unloadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        AppLogger.i("LocalModelManager", "Transitioning state -> UNLOADING for model $modelId")
        repository.updateStatus(modelId, LocalModelStatus.UNLOADING)
        val unloadResult = engine.unload()
        repository.markAllUnloaded()
        AppLogger.i("LocalModelManager", "Transitioning state -> UNLOADED for model $modelId")
        unloadResult
    }

    suspend fun deleteModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val model = repository.getModelById(modelId)
        if (model?.isLoaded == true) {
            unloadModel(modelId)
        }
        // Cleanup local file if stored in app private storage
        try {
            val uriStr = model?.fileUri
            if (uriStr != null) {
                val uri = Uri.parse(uriStr)
                if (uri.scheme == "file" && uri.path != null) {
                    val file = java.io.File(uri.path!!)
                    if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                        file.delete()
                        AppLogger.i("LocalModelManager", "Deleted local model file: ${file.absolutePath}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("LocalModelManager", "Could not delete local file for model $modelId: ${e.message}")
        }
        repository.deleteModel(modelId)
        AppLogger.i("LocalModelManager", "Model $modelId deleted from registry.")
        Result.success(Unit)
    }

    private suspend fun buildInferenceParams(model: LocalModelInfo?): ModelInferenceParams {
        val prefs = preferencesManager?.preferencesFlow?.firstOrNull()
        return ModelInferenceParams(
            contextLength = prefs?.localContextLength ?: (model?.contextLength ?: 2048),
            maxTokens = prefs?.localMaxTokens ?: 512,
            threads = prefs?.localCpuThreads ?: 4,
            temperature = prefs?.temperature ?: 0.7f,
            topP = prefs?.localTopP ?: 0.9f,
            topK = prefs?.localTopK ?: 40,
            repeatPenalty = prefs?.localRepeatPenalty ?: 1.1f
        )
    }

    private suspend fun formatPromptWithTemplate(prompt: String, context: List<ChatMessage>): Pair<String, LocalModelInfo?> {
        val loadedModel = repository.getCurrentlyLoadedModelSync()
        val template = ChatTemplateFormatter.detectTemplate(
            architecture = loadedModel?.architecture,
            modelName = loadedModel?.name ?: "Bonsai"
        )

        val systemMsg = context.firstOrNull { it.isSystem }?.content ?: ""
        val historyTurns = context.filter { !it.isSystem }

        val formattedPrompt = ChatTemplateFormatter.format(
            templateType = template,
            systemPrompt = systemMsg,
            contextHistory = historyTurns,
            userPrompt = prompt
        )

        return Pair(formattedPrompt, loadedModel)
    }

    override suspend fun generate(prompt: String, context: List<ChatMessage>): Result<String> {
        val (formattedPrompt, loadedModel) = formatPromptWithTemplate(prompt, context)
        val params = buildInferenceParams(loadedModel)
        return engine.generate(formattedPrompt, "", params)
    }

    override fun streamGenerate(prompt: String, context: List<ChatMessage>): Flow<String> {
        val loadedModel = kotlinx.coroutines.runBlocking { repository.getCurrentlyLoadedModelSync() }
        val template = ChatTemplateFormatter.detectTemplate(
            architecture = loadedModel?.architecture,
            modelName = loadedModel?.name ?: "Bonsai"
        )

        val systemMsg = context.firstOrNull { it.isSystem }?.content ?: ""
        val historyTurns = context.filter { !it.isSystem }

        val formattedPrompt = ChatTemplateFormatter.format(
            templateType = template,
            systemPrompt = systemMsg,
            contextHistory = historyTurns,
            userPrompt = prompt
        )

        val prefs = kotlinx.coroutines.runBlocking { preferencesManager?.preferencesFlow?.firstOrNull() }
        val params = ModelInferenceParams(
            contextLength = prefs?.localContextLength ?: (loadedModel?.contextLength ?: 2048),
            maxTokens = prefs?.localMaxTokens ?: 512,
            threads = prefs?.localCpuThreads ?: 4,
            temperature = prefs?.temperature ?: 0.7f,
            topP = prefs?.localTopP ?: 0.9f,
            topK = prefs?.localTopK ?: 40,
            repeatPenalty = prefs?.localRepeatPenalty ?: 1.1f
        )

        return engine.streamGenerate(formattedPrompt, "", params)
    }

    override fun stopGeneration() {
        engine.stopGeneration()
    }

    override fun getModelInfo(modelId: String): LocalModelInfo? {
        return kotlinx.coroutines.runBlocking { repository.getModelById(modelId) }
    }

    override fun isLoaded(): Boolean {
        return engine.isLoaded()
    }

    fun getLatestGenerationDiagnostics(): GenerationDiagnostics? {
        return engine.getLatestGenerationDiagnostics()
    }

    override fun releaseResources() {
        kotlinx.coroutines.runBlocking {
            engine.unload()
        }
    }
}
