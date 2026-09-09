package com.pocketai.core.error

sealed class PocketAIException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    class ModelNotFoundException(val modelId: String) :
        PocketAIException("Local model '$modelId' was not found on device storage.")

    class ModelNotLoadedException(val modelName: String) :
        PocketAIException("Model '$modelName' is not currently loaded in memory.")

    class ModelLoadFailedException(val modelName: String, val reason: String) :
        PocketAIException("Failed to load model '$modelName': $reason")

    class UnsupportedModelFormatException(val format: String) :
        PocketAIException("Unsupported model format '$format'. Only valid GGUF models are supported.")

    class InsufficientMemoryException(val requiredMb: Long, val availableMb: Long) :
        PocketAIException("Insufficient RAM to load model. Required: ${requiredMb}MB, Available: ${availableMb}MB.")

    class ApiAuthenticationException(val providerName: String, val statusCode: Int, val details: String) :
        PocketAIException("API authentication failed for '$providerName' (HTTP $statusCode): $details")

    class ApiEndpointException(val endpoint: String, val statusCode: Int, val details: String) :
        PocketAIException("Cloud API endpoint '$endpoint' returned error (HTTP $statusCode): $details")

    class NetworkUnavailableException :
        PocketAIException("No active internet connection. Please switch to a local model or connect to Wi-Fi/cellular.")

    class NoActiveModelException :
        PocketAIException("No active AI model is configured. Please load a local GGUF model or enable a cloud provider in Settings.")

    class ObsidianPermissionException(val uri: String) :
        PocketAIException("Permission denied to access Obsidian vault at '$uri'. Please re-select the vault folder.")

    class ObsidianStorageException(val details: String) :
        PocketAIException("Obsidian vault I/O error: $details")

    class DatabaseException(val details: String) :
        PocketAIException("Database operation failed: $details")

    class InvalidGgufException(val reason: String) :
        PocketAIException("Invalid GGUF file: $reason")

    class NativeEngineUnavailableException(val reason: String) :
        PocketAIException("Native inference engine is unavailable: $reason")

    class InferenceGenerationException(val reason: String) :
        PocketAIException("Local inference error: $reason")

    class StoragePermissionException(val uri: String) :
        PocketAIException("Permission denied to read model file at '$uri'. Please re-import the file.")
}
