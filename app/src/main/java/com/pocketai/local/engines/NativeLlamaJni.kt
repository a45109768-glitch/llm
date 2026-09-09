package com.pocketai.local.engines

import android.content.Context
import android.os.Build
import com.pocketai.core.logging.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

fun interface NativeTokenCallback {
    fun onToken(token: String)
}

/**
 * Direct JNI bridge to the PrismML llama.cpp native shared library.
 * Loads the compiled arm64-v8a / x86_64 binaries (libggml-base, libggml-cpu,
 * libggml, libllama, libpocketai_llama).
 * Provides resilient multi-tier loading (System.loadLibrary -> nativeLibraryDir -> APK extraction).
 */
object NativeLlamaJni {
    private const val TAG = "NativeLlamaJni"

    private val REQUIRED_LIBS = listOf(
        "ggml-base",
        "ggml-cpu",
        "ggml",
        "llama",
        "pocketai_llama"
    )

    @Volatile
    var isAvailable: Boolean = false
        private set

    @Volatile
    var loadErrorDetail: String? = null
        private set

    @Volatile
    var backendInfo: String? = null
        private set

    private val lock = Any()

    init {
        // Attempt initial standard load on class initialization
        tryLoad(context = null)
    }

    /**
     * Ensures native libraries are loaded. If the initial static load failed,
     * this method utilizes the Android Context to locate libraries in nativeLibraryDir
     * or extract them from the application APK package directly.
     */
    fun ensureInitialized(context: Context?): Boolean {
        if (isAvailable) return true
        synchronized(lock) {
            if (isAvailable) return true
            return tryLoad(context)
        }
    }

    fun getLoadFailureReason(): String {
        return loadErrorDetail ?: "PrismML llama.cpp native binary is not available on this device runtime."
    }

    private fun tryLoad(context: Context?): Boolean {
        // Stage 1: Standard System.loadLibrary
        val stage1Errors = mutableListOf<String>()
        var stage1Success = true
        for (lib in REQUIRED_LIBS) {
            try {
                System.loadLibrary(lib)
            } catch (t: Throwable) {
                stage1Success = false
                stage1Errors.add("$lib: ${t.message}")
                break
            }
        }

        if (stage1Success && initBackendSafe()) {
            isAvailable = true
            loadErrorDetail = null
            AppLogger.i(TAG, "PrismML llama.cpp loaded successfully via System.loadLibrary: $backendInfo")
            return true
        }

        if (context == null) {
            loadErrorDetail = "Standard load failed: ${stage1Errors.joinToString("; ")}"
            AppLogger.w(TAG, "Stage 1 failed without Context: $loadErrorDetail")
            return false
        }

        // Stage 2: Direct load from applicationInfo.nativeLibraryDir
        val nativeDir = try {
            File(context.applicationInfo.nativeLibraryDir)
        } catch (e: Exception) {
            null
        }

        if (nativeDir != null && nativeDir.exists()) {
            val stage2Errors = mutableListOf<String>()
            var stage2Success = true
            for (lib in REQUIRED_LIBS) {
                val libFile = File(nativeDir, "lib$lib.so")
                if (!libFile.exists()) {
                    stage2Success = false
                    stage2Errors.add("lib$lib.so missing in ${nativeDir.absolutePath}")
                    break
                }
                try {
                    System.load(libFile.absolutePath)
                } catch (t: Throwable) {
                    stage2Success = false
                    stage2Errors.add("$lib: ${t.message}")
                    break
                }
            }

            if (stage2Success && initBackendSafe()) {
                isAvailable = true
                loadErrorDetail = null
                AppLogger.i(TAG, "PrismML llama.cpp loaded successfully from nativeLibraryDir: $backendInfo")
                return true
            }
        }

        // Stage 3: Extract from APK package into context.filesDir/native_libs
        try {
            val extractedDir = File(context.filesDir, "native_libs").apply { if (!exists()) mkdirs() }
            val apkPath = context.applicationInfo.sourceDir
            if (apkPath != null && File(apkPath).exists()) {
                val supportedAbis = Build.SUPPORTED_ABIS ?: emptyArray()
                ZipFile(File(apkPath)).use { zip ->
                    for (lib in REQUIRED_LIBS) {
                        val libFileName = "lib$lib.so"
                        val targetFile = File(extractedDir, libFileName)

                        // Find matching entry across supported ABIs in order
                        var matchedEntry: java.util.zip.ZipEntry? = null
                        for (abi in supportedAbis) {
                            val entry = zip.getEntry("lib/$abi/$libFileName")
                            if (entry != null) {
                                matchedEntry = entry
                                break
                            }
                        }

                        if (matchedEntry != null) {
                            if (!targetFile.exists() || targetFile.length() != matchedEntry.size) {
                                zip.getInputStream(matchedEntry).use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                targetFile.setReadable(true, false)
                                targetFile.setExecutable(true, false)
                            }
                            System.load(targetFile.absolutePath)
                        } else if (!targetFile.exists()) {
                            throw RuntimeException("Entry lib/*/$libFileName not found in APK for ABIs: ${supportedAbis.joinToString()}")
                        } else {
                            System.load(targetFile.absolutePath)
                        }
                    }
                }

                if (initBackendSafe()) {
                    isAvailable = true
                    loadErrorDetail = null
                    AppLogger.i(TAG, "PrismML llama.cpp loaded successfully from APK extraction: $backendInfo")
                    return true
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Stage 3 APK extraction failed: ${t.message}", t)
            loadErrorDetail = "APK native extraction failed on ABI (${Build.SUPPORTED_ABIS?.joinToString()}): ${t.message}"
        }

        if (loadErrorDetail == null) {
            loadErrorDetail = "PrismML native libraries failed to load: ${stage1Errors.joinToString("; ")}"
        }
        AppLogger.e(TAG, "All native loader stages failed: $loadErrorDetail")
        return false
    }

    private fun initBackendSafe(): Boolean {
        return try {
            nativeInitBackend()
            backendInfo = nativeGetBackendInfo()
            true
        } catch (t: Throwable) {
            AppLogger.e(TAG, "nativeInitBackend failed: ${t.message}", t)
            loadErrorDetail = "JNI nativeInitBackend failure: ${t.message}"
            false
        }
    }

    @JvmStatic
    external fun nativeInitBackend(): Boolean

    @JvmStatic
    external fun nativeGetBackendInfo(): String

    @JvmStatic
    external fun nativeLoadModel(
        modelPath: String,
        nGpuLayers: Int,
        useMmap: Boolean,
        errorHolder: Array<String?>
    ): Long

    @JvmStatic
    external fun nativeCreateContext(
        modelHandle: Long,
        nCtx: Int,
        nBatch: Int,
        nThreads: Int,
        errorHolder: Array<String?>
    ): Long

    @JvmStatic
    external fun nativeFreeContext(contextHandle: Long)

    @JvmStatic
    external fun nativeFreeModel(modelHandle: Long)

    @JvmStatic
    external fun nativeGetModelInfo(modelHandle: Long): String?

    @JvmStatic
    external fun nativeStopGeneration()

    @JvmStatic
    external fun nativeGenerateStream(
        contextHandle: Long,
        modelHandle: Long,
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        maxTokens: Int,
        stopTokens: Array<String>?,
        callback: NativeTokenCallback
    ): Boolean
}
