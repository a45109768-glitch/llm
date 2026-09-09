package com.pocketai.local.engines

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pocketai.core.error.PocketAIException
import com.pocketai.core.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production-ready native local LLM inference engine powered directly by
 * the official PrismML llama.cpp runtime (prism branch).
 *
 * Supports Bonsai and other low-bit quantized GGUF models on mobile hardware.
 */
class NativeLlamaEngine(private val context: Context) : InferenceEngine {

    companion object {
        private const val TAG = "NativeLlamaEngine"
    }

    override val engineName: String = "PrismML llama.cpp Engine"

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        NativeLlamaJni.ensureInitialized(context)
    }

    override val isNativeEngineAvailable: Boolean
        get() = NativeLlamaJni.isAvailable || NativeLlamaJni.ensureInitialized(context)

    @Volatile
    private var modelHandle: Long = 0L

    @Volatile
    private var contextHandle: Long = 0L

    @Volatile
    private var activeModelName: String? = null

    @Volatile
    private var activeModelPath: String? = null

    @Volatile
    private var lastLoadTimeMs: Long = 0L

    @Volatile
    private var latestGenerationDiagnostics: GenerationDiagnostics? = null

    @Volatile
    private var cachedModelInfo: String? = null

    /**
     * Checks if a model and context are currently loaded in native memory.
     */
    fun isModelLoaded(): Boolean = modelHandle != 0L && contextHandle != 0L

    override fun isLoaded(): Boolean = isModelLoaded()

    override fun getLatestGenerationDiagnostics(): GenerationDiagnostics? = latestGenerationDiagnostics

    /**
     * Retrieve metadata string directly from the native model.
     */
    fun getModelInfo(): String? {
        val h = modelHandle
        if (h == 0L) return null
        return cachedModelInfo ?: NativeLlamaJni.nativeGetModelInfo(h).also { cachedModelInfo = it }
    }

    override fun getHardwareDiagnostics(): HardwareDiagnostics {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val rawTotal = if (memInfo.totalMem > 0) memInfo.totalMem else Runtime.getRuntime().maxMemory()
        val rawAvail = if (memInfo.availMem > 0) memInfo.availMem else Runtime.getRuntime().freeMemory()

        val totalMb = (rawTotal / (1024 * 1024)).coerceAtLeast(1024)
        val availMb = (rawAvail / (1024 * 1024)).coerceAtLeast(512)
        val cores = Runtime.getRuntime().availableProcessors()

        val maxSafeModelMb = (availMb - 1500).coerceAtLeast(400)

        return HardwareDiagnostics(
            totalRamMb = totalMb,
            availableRamMb = availMb,
            isHardwareSufficient = availMb >= 1600,
            recommendedMaxModelSizeMb = maxSafeModelMb,
            processorCores = cores
        )
    }

    private fun validateGgufHeader(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val magicBytes = ByteArray(4)
                var total = 0
                while (total < 4) {
                    val r = stream.read(magicBytes, total, 4 - total)
                    if (r == -1) return false
                    total += r
                }
                magicBytes[0] == 'G'.code.toByte() &&
                magicBytes[1] == 'G'.code.toByte() &&
                magicBytes[2] == 'U'.code.toByte() &&
                magicBytes[3] == 'F'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun queryFileSize(uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).length() } ?: 0L
        }
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun ensureLocalModelFile(uri: Uri, modelName: String): File {
        val modelsDir = File(context.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }

        if (uri.scheme == "file" && uri.path != null) {
            val existingFile = File(uri.path!!)
            if (existingFile.exists() && existingFile.length() > 0) {
                if (validateGgufHeader(existingFile)) {
                    AppLogger.i(TAG, "GGUF header valid: ${existingFile.name} (${existingFile.length()} bytes)")
                    return existingFile
                }
            }
        }

        val queryName = queryDisplayName(uri) ?: modelName
        val cleanName = if (queryName.endsWith(".gguf", ignoreCase = true)) queryName else "$queryName.gguf"
        val safeFileName = cleanName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(modelsDir, safeFileName)
        val expectedSize = queryFileSize(uri)

        if (targetFile.exists() && targetFile.length() > 0) {
            val matchesSize = expectedSize <= 0 || targetFile.length() == expectedSize
            if (matchesSize && validateGgufHeader(targetFile)) {
                AppLogger.i(TAG, "Reusing verified local model file: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                return targetFile
            }
        }

        AppLogger.i(TAG, "Copying model from SAF $uri to ${targetFile.absolutePath}")
        val tempFile = File(modelsDir, "${safeFileName}.tmp.${System.currentTimeMillis()}")
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw PocketAIException.ModelNotFoundException("Failed to open input stream for $uri")

            val buffer = ByteArray(64 * 1024)
            var totalCopied = 0L
            tempFile.outputStream().use { outputStream ->
                inputStream.use { stream ->
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalCopied += bytesRead
                    }
                    outputStream.flush()
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            AppLogger.i(TAG, "Successfully copied $totalCopied bytes to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            AppLogger.e(TAG, "Error copying model file: ${e.message}", e)
            throw e
        }

        if (!validateGgufHeader(targetFile)) {
            targetFile.delete()
            throw PocketAIException.ModelLoadFailedException(modelName, "Copied file is not in valid GGUF format")
        }

        return targetFile
    }

    /**
     * High-level loading from URI or local path.
     */
    override suspend fun loadModelFromUri(
        uri: Uri,
        modelName: String,
        params: ModelInferenceParams
    ): Result<EngineStatus> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "loadModelFromUri: modelName='$modelName', uri='$uri'")

        try {
            if (!isNativeEngineAvailable) {
                NativeLlamaJni.ensureInitialized(context)
            }
            if (!isNativeEngineAvailable) {
                val detail = NativeLlamaJni.getLoadFailureReason()
                val err = "Inference engine unavailable: $detail"
                AppLogger.e(TAG, err)
                return@withContext Result.failure(PocketAIException.NativeEngineUnavailableException(err))
            }

            val localFile = try {
                ensureLocalModelFile(uri, modelName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to prepare local model file: ${e.message}", e)
                return@withContext Result.failure(e)
            }

            val fileSizeMb = localFile.length() / (1024 * 1024)
            val diagnostics = getHardwareDiagnostics()

            val effectiveContextLength = if (params.contextLength > 0) params.contextLength else 2048
            val effectiveThreads = if (params.threads > 0) params.threads.coerceIn(2, 4) else diagnostics.processorCores.coerceIn(2, 4)

            // Safe memory validation
            val estimatedKvCacheMb = (effectiveContextLength * 4) / 1024 + 100
            val requiredMemoryMb = fileSizeMb + estimatedKvCacheMb

            if (diagnostics.availableRamMb < requiredMemoryMb) {
                val errorMsg = "Insufficient RAM for '$modelName'. Required: ~${requiredMemoryMb}MB, Available: ${diagnostics.availableRamMb}MB"
                AppLogger.e(TAG, errorMsg)
                return@withContext Result.failure(
                    PocketAIException.InsufficientMemoryException(requiredMemoryMb, diagnostics.availableRamMb)
                )
            }

            // Unload any previously loaded model first
            if (isModelLoaded()) {
                unloadModel()
            }

            val startTime = System.currentTimeMillis()
            val loadResult = loadModel(
                modelPath = localFile.absolutePath,
                modelName = modelName,
                contextLength = effectiveContextLength,
                threads = effectiveThreads,
                useMmap = true,
                nGpuLayers = 0
            )

            if (loadResult.isFailure) {
                return@withContext Result.failure(loadResult.exceptionOrNull()!!)
            }

            lastLoadTimeMs = System.currentTimeMillis() - startTime
            val infoStr = getModelInfo() ?: ""

            val detectedArch = extractMetaValue(infoStr, "Architecture") ?: "transformer"
            val detectedParams = extractMetaValue(infoStr, "Parameters")?.toDoubleOrNull()?.let { num ->
                val pB = num / 1_000_000_000.0
                if (pB >= 0.1) String.format("%.1fB", pB) else null
            }

            AppLogger.i(TAG, "Model '$modelName' successfully initialized in ${lastLoadTimeMs}ms ($detectedArch, $detectedParams)")

            Result.success(
                EngineStatus(
                    isLoaded = true,
                    modelName = modelName,
                    memoryFootprintMb = fileSizeMb,
                    statusMessage = "Loaded via PrismML llama.cpp in ${lastLoadTimeMs}ms. Threads: $effectiveThreads, Ctx: $effectiveContextLength",
                    architecture = detectedArch,
                    contextLength = effectiveContextLength,
                    parametersCount = detectedParams
                )
            )
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Unexpected error in loadModelFromUri: ${e.message}", e)
            unloadModel()
            Result.failure(
                PocketAIException.ModelLoadFailedException(modelName, e.message ?: "Native engine failure")
            )
        }
    }

    /**
     * Directly loads a model by file path into native memory and initializes context.
     */
    fun loadModel(
        modelPath: String,
        modelName: String,
        contextLength: Int = 2048,
        threads: Int = 4,
        useMmap: Boolean = true,
        nGpuLayers: Int = 0
    ): Result<Unit> {
        val errorHolder = arrayOfNulls<String>(1)

        AppLogger.i(TAG, "Loading native model via PrismML: $modelPath")
        val mHandle = NativeLlamaJni.nativeLoadModel(modelPath, nGpuLayers, useMmap, errorHolder)
        if (mHandle == 0L) {
            val err = errorHolder[0] ?: "Native llama.cpp engine failed to initialize context from GGUF weights."
            AppLogger.e(TAG, "nativeLoadModel failed: $err")
            return Result.failure(PocketAIException.ModelLoadFailedException(modelName, err))
        }

        val cHandle = createContext(mHandle, contextLength, 512, threads, errorHolder)
        if (cHandle == 0L) {
            NativeLlamaJni.nativeFreeModel(mHandle)
            val err = errorHolder[0] ?: "Failed to allocate native llama context."
            AppLogger.e(TAG, "createContext failed: $err")
            return Result.failure(PocketAIException.ModelLoadFailedException(modelName, err))
        }

        modelHandle = mHandle
        contextHandle = cHandle
        activeModelName = modelName
        activeModelPath = modelPath
        cachedModelInfo = null

        return Result.success(Unit)
    }

    /**
     * Creates a native context from a model handle.
     */
    fun createContext(
        modelH: Long,
        nCtx: Int = 2048,
        nBatch: Int = 512,
        nThreads: Int = 4,
        errorHolder: Array<String?> = arrayOfNulls(1)
    ): Long {
        return NativeLlamaJni.nativeCreateContext(modelH, nCtx, nBatch, nThreads, errorHolder)
    }

    /**
     * Unloads model and frees all associated native allocations.
     */
    fun unloadModel(): Result<Unit> {
        val cHandle = contextHandle
        val mHandle = modelHandle

        contextHandle = 0L
        modelHandle = 0L
        activeModelName = null
        activeModelPath = null
        cachedModelInfo = null

        if (NativeLlamaJni.isAvailable) {
            if (cHandle != 0L) {
                NativeLlamaJni.nativeFreeContext(cHandle)
            }
            if (mHandle != 0L) {
                NativeLlamaJni.nativeFreeModel(mHandle)
            }
        }

        AppLogger.i(TAG, "Native model and context freed from memory.")
        return Result.success(Unit)
    }

    override suspend fun unload(): Result<Unit> = withContext(Dispatchers.IO) {
        unloadModel()
    }

    override suspend fun generate(
        prompt: String,
        contextHistory: String,
        params: ModelInferenceParams
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            streamGenerate(prompt, contextHistory, params).collect { token ->
                sb.append(token)
            }
            Result.success(sb.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun streamGenerate(
        prompt: String,
        contextHistory: String,
        params: ModelInferenceParams
    ): Flow<String> = channelFlow {
        val cHandle = contextHandle
        val mHandle = modelHandle
        val currentModel = activeModelName

        if (!NativeLlamaJni.isAvailable) {
            throw PocketAIException.NativeEngineUnavailableException("PrismML native engine is not available.")
        }

        if (cHandle == 0L || mHandle == 0L || currentModel == null) {
            throw PocketAIException.ModelNotLoadedException(
                activeModelName ?: "No model loaded. Please load a local GGUF model in Settings."
            )
        }

        val startTime = System.currentTimeMillis()
        var firstTokenTimeMs = 0L
        val tokenCounter = AtomicInteger(0)

        val callback = NativeTokenCallback { token ->
            if (token.isNotEmpty()) {
                if (tokenCounter.get() == 0) {
                    firstTokenTimeMs = System.currentTimeMillis() - startTime
                }
                tokenCounter.incrementAndGet()
                trySend(token)
            }
        }

        try {
            withContext(Dispatchers.IO) {
                val success = NativeLlamaJni.nativeGenerateStream(
                    contextHandle = cHandle,
                    modelHandle = mHandle,
                    prompt = prompt,
                    temperature = params.temperature,
                    topP = params.topP,
                    topK = params.topK,
                    repeatPenalty = params.repeatPenalty,
                    maxTokens = params.maxTokens,
                    stopTokens = ChatTemplateFormatter.GLOBAL_STOP_TOKENS.toTypedArray(),
                    callback = callback
                )
                if (!success) {
                    AppLogger.w(TAG, "nativeGenerateStream returned false")
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                AppLogger.i(TAG, "Generation cancelled by user.")
                NativeLlamaJni.nativeStopGeneration()
            } else {
                AppLogger.e(TAG, "Generation failed: ${e.message}", e)
                throw PocketAIException.InferenceGenerationException(
                    e.message ?: "PrismML llama.cpp generation error"
                )
            }
        } finally {
            val totalTimeMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val tokens = tokenCounter.get()
            val tokPerSec = if (tokens > 0) (tokens.toDouble() / (totalTimeMs.toDouble() / 1000.0)) else 0.0

            latestGenerationDiagnostics = GenerationDiagnostics(
                loadTimeMs = lastLoadTimeMs,
                tokensGenerated = tokens,
                generationTimeMs = totalTimeMs,
                tokensPerSecond = tokPerSec,
                timeToFirstTokenMs = firstTokenTimeMs,
                activeModelName = currentModel
            )

            AppLogger.i(
                TAG,
                "Completion finished: $tokens tokens in ${totalTimeMs}ms (${String.format("%.2f", tokPerSec)} tok/s)"
            )
        }
    }

    override fun stopGeneration() {
        AppLogger.i(TAG, "stopGeneration invoked.")
        if (NativeLlamaJni.isAvailable) {
            NativeLlamaJni.nativeStopGeneration()
        }
    }

    private fun extractMetaValue(infoStr: String, key: String): String? {
        val prefix = "$key: "
        return infoStr.lineSequence()
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
