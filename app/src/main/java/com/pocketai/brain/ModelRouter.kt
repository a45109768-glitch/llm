package com.pocketai.brain

import com.pocketai.cloud.CloudModelRepository
import com.pocketai.cloud.CloudProviderConfig
import com.pocketai.cloud.CloudProviderManager
import com.pocketai.core.common.NetworkMonitor
import com.pocketai.core.error.PocketAIException
import com.pocketai.data.preferences.AppPreferences
import com.pocketai.data.preferences.PreferencesManager
import com.pocketai.data.preferences.RoutingMode
import com.pocketai.local.LocalModelInfo
import com.pocketai.local.LocalModelManager
import com.pocketai.local.LocalModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class ActiveRoute(
    val sourceType: ModelSourceType,
    val modelId: String,
    val displayName: String,
    val isReady: Boolean,
    val statusMessage: String
)

sealed class SelectedExecutionTarget {
    data class LocalTarget(val model: LocalModelInfo) : SelectedExecutionTarget()
    data class CloudTarget(val config: CloudProviderConfig) : SelectedExecutionTarget()
}

class ModelRouter(
    private val localModelManager: LocalModelManager,
    private val localModelRepository: LocalModelRepository,
    private val cloudModelRepository: CloudModelRepository,
    private val preferencesManager: PreferencesManager,
    private val networkMonitor: NetworkMonitor
) {

    /**
     * Reactively emits the current active route and target model information.
     */
    val activeRouteFlow: Flow<ActiveRoute> = combine(
        preferencesManager.preferencesFlow,
        localModelRepository.loadedModel,
        cloudModelRepository.enabledProviders,
        networkMonitor.isOnlineFlow
    ) { prefs, loadedLocal, enabledCloudList, isOnline ->
        resolveActiveRoute(prefs, loadedLocal, enabledCloudList, isOnline)
    }

    private fun resolveActiveRoute(
        prefs: AppPreferences,
        loadedLocal: LocalModelInfo?,
        enabledCloudList: List<CloudProviderConfig>,
        isOnline: Boolean
    ): ActiveRoute {
        val selectedCloud = enabledCloudList.find { it.id == prefs.selectedCloudProviderId }
            ?: enabledCloudList.firstOrNull()

        return when (prefs.routingMode) {
            RoutingMode.LOCAL_ONLY -> {
                if (loadedLocal != null && loadedLocal.isLoaded) {
                    ActiveRoute(
                        sourceType = ModelSourceType.LOCAL,
                        modelId = loadedLocal.id,
                        displayName = "Local • ${loadedLocal.name}",
                        isReady = true,
                        statusMessage = "Offline Ready (${loadedLocal.architecture ?: "GGUF"})"
                    )
                } else {
                    ActiveRoute(
                        sourceType = ModelSourceType.LOCAL,
                        modelId = "",
                        displayName = "Local • No Model Loaded",
                        isReady = false,
                        statusMessage = "Tap Settings to load a local GGUF model"
                    )
                }
            }

            RoutingMode.CLOUD_ONLY -> {
                if (!isOnline) {
                    ActiveRoute(
                        sourceType = ModelSourceType.CLOUD,
                        modelId = selectedCloud?.id ?: "",
                        displayName = "Cloud • Offline",
                        isReady = false,
                        statusMessage = "No internet connection for cloud model"
                    )
                } else if (selectedCloud != null) {
                    ActiveRoute(
                        sourceType = ModelSourceType.CLOUD,
                        modelId = selectedCloud.id,
                        displayName = "Cloud • ${selectedCloud.name} (${selectedCloud.modelName})",
                        isReady = true,
                        statusMessage = "Cloud Active"
                    )
                } else {
                    ActiveRoute(
                        sourceType = ModelSourceType.CLOUD,
                        modelId = "",
                        displayName = "Cloud • Not Configured",
                        isReady = false,
                        statusMessage = "Tap Settings to configure a Cloud Provider"
                    )
                }
            }

            RoutingMode.LOCAL_FIRST -> {
                if (loadedLocal != null && loadedLocal.isLoaded) {
                    ActiveRoute(
                        sourceType = ModelSourceType.LOCAL,
                        modelId = loadedLocal.id,
                        displayName = "Local • ${loadedLocal.name}",
                        isReady = true,
                        statusMessage = "Local First"
                    )
                } else if (isOnline && selectedCloud != null) {
                    ActiveRoute(
                        sourceType = ModelSourceType.CLOUD,
                        modelId = selectedCloud.id,
                        displayName = "Cloud • ${selectedCloud.name} (Fallback)",
                        isReady = true,
                        statusMessage = "Cloud Fallback (No local model loaded)"
                    )
                } else {
                    ActiveRoute(
                        sourceType = ModelSourceType.LOCAL,
                        modelId = "",
                        displayName = "No Active Model",
                        isReady = false,
                        statusMessage = "Load a local model or configure cloud in Settings"
                    )
                }
            }

            RoutingMode.CLOUD_FIRST -> {
                if (isOnline && selectedCloud != null) {
                    ActiveRoute(
                        sourceType = ModelSourceType.CLOUD,
                        modelId = selectedCloud.id,
                        displayName = "Cloud • ${selectedCloud.name}",
                        isReady = true,
                        statusMessage = "Cloud First"
                    )
                } else if (loadedLocal != null && loadedLocal.isLoaded) {
                    ActiveRoute(
                        sourceType = ModelSourceType.LOCAL,
                        modelId = loadedLocal.id,
                        displayName = "Local • ${loadedLocal.name} (Offline Fallback)",
                        isReady = true,
                        statusMessage = "Local Fallback"
                    )
                } else {
                    ActiveRoute(
                        sourceType = ModelSourceType.CLOUD,
                        modelId = "",
                        displayName = "No Active Model",
                        isReady = false,
                        statusMessage = "Check internet or load a local model"
                    )
                }
            }

            RoutingMode.AUTO -> {
                if (!isOnline) {
                    if (loadedLocal != null && loadedLocal.isLoaded) {
                        ActiveRoute(
                            sourceType = ModelSourceType.LOCAL,
                            modelId = loadedLocal.id,
                            displayName = "Local • ${loadedLocal.name} (Offline Mode)",
                            isReady = true,
                            statusMessage = "Auto: Network offline -> Local"
                        )
                    } else {
                        ActiveRoute(
                            sourceType = ModelSourceType.LOCAL,
                            modelId = "",
                            displayName = "Offline • No Local Model",
                            isReady = false,
                            statusMessage = "Auto mode: Offline and no local model loaded"
                        )
                    }
                } else {
                    if (selectedCloud != null) {
                        ActiveRoute(
                            sourceType = ModelSourceType.CLOUD,
                            modelId = selectedCloud.id,
                            displayName = "Cloud • ${selectedCloud.name}",
                            isReady = true,
                            statusMessage = "Auto: Online -> Cloud"
                        )
                    } else if (loadedLocal != null && loadedLocal.isLoaded) {
                        ActiveRoute(
                            sourceType = ModelSourceType.LOCAL,
                            modelId = loadedLocal.id,
                            displayName = "Local • ${loadedLocal.name}",
                            isReady = true,
                            statusMessage = "Auto: Local active"
                        )
                    } else {
                        ActiveRoute(
                            sourceType = ModelSourceType.LOCAL,
                            modelId = "",
                            displayName = "No Active Model",
                            isReady = false,
                            statusMessage = "Configure a model in Settings"
                        )
                    }
                }
            }
        }
    }

    suspend fun resolveExecutionTarget(): SelectedExecutionTarget {
        val prefs = preferencesManager.preferencesFlow.first()
        val loadedLocal = localModelRepository.getCurrentlyLoadedModelSync()
        val enabledCloudList = cloudModelRepository.enabledProviders.first()
        val isOnline = networkMonitor.isCurrentlyConnected()

        val selectedCloud = enabledCloudList.find { it.id == prefs.selectedCloudProviderId }
            ?: enabledCloudList.firstOrNull()

        when (prefs.routingMode) {
            RoutingMode.LOCAL_ONLY -> {
                if (loadedLocal != null && loadedLocal.isLoaded) {
                    return SelectedExecutionTarget.LocalTarget(loadedLocal)
                }
                throw PocketAIException.NoActiveModelException()
            }

            RoutingMode.CLOUD_ONLY -> {
                if (!isOnline) throw PocketAIException.NetworkUnavailableException()
                if (selectedCloud != null) return SelectedExecutionTarget.CloudTarget(selectedCloud)
                throw PocketAIException.NoActiveModelException()
            }

            RoutingMode.LOCAL_FIRST -> {
                if (loadedLocal != null && loadedLocal.isLoaded) {
                    return SelectedExecutionTarget.LocalTarget(loadedLocal)
                }
                if (isOnline && selectedCloud != null) {
                    return SelectedExecutionTarget.CloudTarget(selectedCloud)
                }
                throw PocketAIException.NoActiveModelException()
            }

            RoutingMode.CLOUD_FIRST -> {
                if (isOnline && selectedCloud != null) {
                    return SelectedExecutionTarget.CloudTarget(selectedCloud)
                }
                if (loadedLocal != null && loadedLocal.isLoaded) {
                    return SelectedExecutionTarget.LocalTarget(loadedLocal)
                }
                throw PocketAIException.NoActiveModelException()
            }

            RoutingMode.AUTO -> {
                if (isOnline && selectedCloud != null) {
                    return SelectedExecutionTarget.CloudTarget(selectedCloud)
                }
                if (loadedLocal != null && loadedLocal.isLoaded) {
                    return SelectedExecutionTarget.LocalTarget(loadedLocal)
                }
                throw PocketAIException.NoActiveModelException()
            }
        }
    }
}
