package com.pocketai.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.PocketAIApp
import com.pocketai.cloud.CloudProviderConfig
import com.pocketai.cloud.CloudProviderType
import com.pocketai.data.preferences.AppPreferences
import com.pocketai.data.preferences.RoutingMode
import com.pocketai.local.LocalModelInfo
import com.pocketai.local.engines.HardwareDiagnostics
import com.pocketai.memory.MemoryCategory
import com.pocketai.memory.MemoryItem
import com.pocketai.obsidian.ObsidianVaultStatus
import com.pocketai.robot.RobotDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class SettingsUiState(
    val snackbarMessage: String? = null,
    val isTestingApi: Boolean = false,
    val isSyncEnabled: Boolean = false
)

class SettingsViewModel(
    private val app: PocketAIApp = PocketAIApp.instance
) : ViewModel() {

    // --- App Preferences & Routing ---
    val appPreferences: StateFlow<AppPreferences?> = app.preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferences: StateFlow<AppPreferences> = app.preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            app.preferencesManager.preferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(isSyncEnabled = prefs.isObsidianSyncEnabled)
            }
        }
    }

    // --- Local Models ---
    val localModels: StateFlow<List<LocalModelInfo>> = app.localModelRepository.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loadedLocalModel: StateFlow<LocalModelInfo?> = app.localModelRepository.loadedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _loadingModelId = MutableStateFlow<String?>(null)
    val loadingModelId: StateFlow<String?> = _loadingModelId.asStateFlow()

    fun getHardwareDiagnostics(): HardwareDiagnostics {
        return app.inferenceEngine.getHardwareDiagnostics()
    }

    fun getLatestGenerationDiagnostics() = app.localModelManager.getLatestGenerationDiagnostics()

    fun importLocalModel(uri: Uri) {
        viewModelScope.launch {
            _operationMessage.value = "Reading GGUF header and importing..."
            val result = app.localModelManager.importModelFromUri(uri)
            if (result.isSuccess) {
                val model = result.getOrNull()
                _operationMessage.value = "Imported '${model?.name ?: "Model"}' successfully."
                _snackbarMessage.value = "Model imported successfully!"
            } else {
                _operationMessage.value = "Import failed: ${result.exceptionOrNull()?.message}"
                _snackbarMessage.value = "Failed to import model: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun loadLocalModel(modelId: String) {
        viewModelScope.launch {
            _loadingModelId.value = modelId
            _operationMessage.value = "Loading model weights into RAM..."
            val result = app.localModelManager.loadModel(modelId)
            _loadingModelId.value = null
            if (result.isSuccess) {
                app.preferencesManager.setSelectedLocalModelId(modelId)
                _operationMessage.value = "Model loaded and active in memory."
                _snackbarMessage.value = "Model loaded and ready for chat!"
            } else {
                _operationMessage.value = "Failed to load model: ${result.exceptionOrNull()?.message}"
                _snackbarMessage.value = "Failed to load model: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun setActiveLocalModel(modelId: String) = loadLocalModel(modelId)

    fun unloadLocalModel(modelId: String) {
        viewModelScope.launch {
            _operationMessage.value = "Unloading model from RAM..."
            app.localModelManager.unloadModel(modelId)
            app.preferencesManager.setSelectedLocalModelId(null)
            _operationMessage.value = "Model unloaded. RAM freed."
            _snackbarMessage.value = "Model unloaded from RAM."
        }
    }

    fun updateLocalInferenceSettings(
        contextLength: Int,
        maxTokens: Int,
        threads: Int,
        temperature: Float,
        topP: Float,
        repeatPenalty: Float
    ) {
        viewModelScope.launch {
            app.preferencesManager.setLocalInferenceConfig(
                contextLength = contextLength,
                maxTokens = maxTokens,
                threads = threads,
                temperature = temperature,
                topP = topP,
                repeatPenalty = repeatPenalty
            )
            _snackbarMessage.value = "Inference settings saved."
        }
    }

    fun deleteLocalModel(modelId: String) {
        viewModelScope.launch {
            app.localModelManager.deleteModel(modelId)
            _snackbarMessage.value = "Model removed from registry."
        }
    }

    fun dismissOperationMessage() {
        _operationMessage.value = null
        _snackbarMessage.value = null
    }

    fun clearOperationMessage() = dismissOperationMessage()

    // --- Cloud Providers ---
    val cloudProviders: StateFlow<List<CloudProviderConfig>> = app.cloudModelRepository.allProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _testingProviderId = MutableStateFlow<String?>(null)
    val isTestingApi: StateFlow<String?> = _testingProviderId.asStateFlow()

    fun setActiveCloudProvider(providerId: String) {
        viewModelScope.launch {
            app.preferencesManager.setSelectedCloudProviderId(providerId)
            _snackbarMessage.value = "Active cloud provider updated"
            _operationMessage.value = "Active cloud provider updated"
        }
    }

    fun toggleCloudProvider(providerId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            app.cloudModelRepository.toggleProviderEnabled(providerId, isEnabled)
        }
    }

    fun toggleCloudProviderEnabled(provider: CloudProviderConfig, enabled: Boolean) {
        toggleCloudProvider(provider.id, enabled)
    }

    fun deleteCloudProvider(providerId: String) {
        viewModelScope.launch {
            app.cloudModelRepository.deleteProvider(providerId)
            _snackbarMessage.value = "Cloud provider deleted"
            _operationMessage.value = "Cloud provider deleted"
        }
    }

    fun saveCloudProvider(config: CloudProviderConfig) {
        viewModelScope.launch {
            app.cloudModelRepository.saveProvider(config)
            _snackbarMessage.value = "Saved '${config.name}' configuration."
        }
    }

    fun addCloudProvider(
        name: String,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        type: CloudProviderType
    ) {
        viewModelScope.launch {
            val newProvider = CloudProviderConfig(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                modelName = modelName.trim(),
                providerType = type,
                isEnabled = true
            )
            app.cloudModelRepository.saveProvider(newProvider)
            _operationMessage.value = "Provider '$name' added"
            _snackbarMessage.value = "Provider '$name' added"
        }
    }

    fun testCloudProvider(config: CloudProviderConfig, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _testingProviderId.value = config.id
            _uiState.value = _uiState.value.copy(isTestingApi = true)
            try {
                val result = app.cloudProviderManager.testProvider(config)
                val msg = if (result.isSuccess) {
                    "Success! Endpoint reachable (Latency: ${result.latencyMs}ms)"
                } else {
                    result.errorMessage ?: "API test failed"
                }
                onResult?.invoke(result.isSuccess, msg)
                _snackbarMessage.value = msg
                _operationMessage.value = msg
            } catch (e: Exception) {
                onResult?.invoke(false, e.message ?: "Unknown test error")
                _snackbarMessage.value = "Test error: ${e.message}"
                _operationMessage.value = "Test error: ${e.message}"
            } finally {
                _testingProviderId.value = null
                _uiState.value = _uiState.value.copy(isTestingApi = false)
            }
        }
    }

    // --- Personal Memory ---
    val memories: StateFlow<List<MemoryItem>> = app.memoryManager.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMemory(
        content: String,
        category: MemoryCategory = MemoryCategory.FACT,
        importance: Int = 3,
        tags: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            app.memoryManager.addMemory(
                content = content,
                category = category,
                importance = importance,
                tags = tags
            )
            _snackbarMessage.value = "Saved to personal memory vault."
            _operationMessage.value = "Saved to personal memory vault."
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            app.memoryManager.deleteMemory(id)
            _snackbarMessage.value = "Memory item deleted."
            _operationMessage.value = "Memory item deleted."
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            app.memoryManager.clearAllMemories()
            _snackbarMessage.value = "All memories cleared."
            _operationMessage.value = "All memories cleared."
        }
    }

    // --- Obsidian Vault ---
    val vaultStatus: StateFlow<ObsidianVaultStatus> = app.obsidianManager.vaultStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ObsidianVaultStatus())

    fun connectObsidianVault(treeUri: Uri) {
        viewModelScope.launch {
            val result = app.obsidianManager.connectVault(treeUri)
            if (result.isSuccess) {
                _snackbarMessage.value = "Obsidian vault connected: ${result.getOrNull()?.name}"
            } else {
                _snackbarMessage.value = "Failed to connect vault: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun connectVault(treeUri: Uri) = connectObsidianVault(treeUri)

    fun disconnectObsidianVault() {
        viewModelScope.launch {
            app.obsidianManager.disconnectVault()
            _snackbarMessage.value = "Obsidian vault disconnected."
        }
    }

    fun disconnectVault() = disconnectObsidianVault()

    fun createObsidianNote(title: String, content: String, folderName: String = "Knowledge") {
        viewModelScope.launch {
            val result = app.obsidianManager.createKnowledgeNote(title, content, folderName)
            if (result.isSuccess) {
                _snackbarMessage.value = "Note created in Obsidian ($folderName/${result.getOrNull()})"
            } else {
                _snackbarMessage.value = "Failed to write note: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun createQuickNote(title: String, content: String) = createObsidianNote(title, content, "Knowledge")

    fun createKnowledgeNote(title: String, content: String, folderName: String = "Knowledge") =
        createObsidianNote(title, content, folderName)

    fun toggleSync(enabled: Boolean) = setObsidianSyncEnabled(enabled)

    // --- Robot Architecture & Gateway ---
    val robotDevice: StateFlow<RobotDevice> = app.robotManager.activeDevice
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            RobotDevice(
                id = "esp32_bot_01",
                name = "PocketBot Alpha",
                serverGatewayUrl = "https://server.pocketai.internal",
                authToken = ""
            )
        )

    fun updateRobotConfig(name: String, gatewayUrl: String) {
        viewModelScope.launch {
            app.robotManager.updateServerConfig(name, gatewayUrl)
            _snackbarMessage.value = "Robot gateway configuration saved."
        }
    }

    fun updateConfig(gatewayUrl: String, name: String) = updateRobotConfig(name, gatewayUrl)

    fun generateRobotToken(): String {
        var token = ""
        viewModelScope.launch {
            token = app.robotManager.generateNewPairingCredential()
            _snackbarMessage.value = "New ESP32 pairing token generated."
        }
        return token
    }

    fun generateNewToken(robotId: String = "") = generateRobotToken()

    fun testRobotConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = app.robotManager.testServerGatewayConnection()
            onResult(result.first, result.second)
            _snackbarMessage.value = result.second
        }
    }

    // --- General Settings & Persona ---
    fun setRoutingMode(mode: RoutingMode) {
        viewModelScope.launch {
            app.preferencesManager.setRoutingMode(mode)
            _snackbarMessage.value = "Routing mode set to ${mode.name.replace("_", " ")}"
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.preferencesManager.setMemoryEnabled(enabled)
            _snackbarMessage.value = if (enabled) "Memory injection enabled" else "Memory injection disabled"
        }
    }

    fun setObsidianSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.preferencesManager.setObsidianSyncEnabled(enabled)
            _snackbarMessage.value = if (enabled) "Obsidian sync enabled" else "Obsidian sync disabled"
        }
    }

    fun setSystemPrompt(prompt: String) {
        viewModelScope.launch {
            app.preferencesManager.setSystemPrompt(prompt)
            _snackbarMessage.value = "Saved assistant persona"
        }
    }

    fun setTemperature(temperature: Float) {
        viewModelScope.launch {
            app.preferencesManager.setTemperature(temperature)
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }
}
