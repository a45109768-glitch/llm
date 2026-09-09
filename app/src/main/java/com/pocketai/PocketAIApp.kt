package com.pocketai

import android.app.Application
import com.pocketai.brain.BrainCore
import com.pocketai.brain.ContextManager
import com.pocketai.brain.ModelRouter
import com.pocketai.brain.PromptManager
import com.pocketai.cloud.ApiTester
import com.pocketai.cloud.CloudModelRepository
import com.pocketai.cloud.CloudProviderManager
import com.pocketai.core.common.NetworkMonitor
import com.pocketai.core.logging.AppLogger
import com.pocketai.data.local.database.PocketAIDatabase
import com.pocketai.data.preferences.PreferencesManager
import com.pocketai.data.repositories.ConversationRepository
import com.pocketai.local.LocalModelManager
import com.pocketai.local.LocalModelRepository
import com.pocketai.local.engines.LlamaCppEngineBridge
import com.pocketai.memory.MemoryManager
import com.pocketai.memory.MemoryRepository
import com.pocketai.obsidian.ObsidianManager
import com.pocketai.obsidian.ObsidianRepository
import com.pocketai.robot.RobotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PocketAIApp : Application() {

    companion object {
        lateinit var instance: PocketAIApp
            private set
    }

    // Singletons
    val database by lazy { PocketAIDatabase.getInstance(this) }
    val preferencesManager by lazy { PreferencesManager(this) }
    val networkMonitor by lazy { NetworkMonitor(this) }

    val conversationRepository by lazy { ConversationRepository(database.conversationDao()) }
    val localModelRepository by lazy { LocalModelRepository(database.localModelDao()) }
    val inferenceEngine by lazy { LlamaCppEngineBridge(this) }
    val localModelManager by lazy {
        LocalModelManager(this, localModelRepository, inferenceEngine, preferencesManager)
    }

    val cloudModelRepository by lazy { CloudModelRepository(database.cloudProviderDao()) }
    val apiTester by lazy { ApiTester() }
    val cloudProviderManager by lazy {
        CloudProviderManager(cloudModelRepository, apiTester)
    }

    val memoryRepository by lazy { MemoryRepository(database.memoryDao()) }
    val memoryManager by lazy { MemoryManager(memoryRepository) }

    val obsidianRepository by lazy { ObsidianRepository(this) }
    val obsidianManager by lazy {
        ObsidianManager(obsidianRepository, preferencesManager)
    }

    val robotManager by lazy { RobotManager(database.robotDao()) }

    val modelRouter by lazy {
        ModelRouter(
            localModelManager = localModelManager,
            localModelRepository = localModelRepository,
            cloudModelRepository = cloudModelRepository,
            preferencesManager = preferencesManager,
            networkMonitor = networkMonitor
        )
    }
    val promptManager by lazy {
        PromptManager(
            preferencesManager = preferencesManager,
            memoryManager = memoryManager,
            obsidianManager = obsidianManager
        )
    }
    val contextManager by lazy { ContextManager() }

    val brainCore by lazy {
        BrainCore(
            modelRouter = modelRouter,
            promptManager = promptManager,
            contextManager = contextManager,
            localModelManager = localModelManager,
            cloudProviderManager = cloudProviderManager
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.i("PocketAIApp", "PocketAI application initialized.")

        // Warm up / verify PrismML native inference engine with app context
        com.pocketai.local.engines.NativeLlamaJni.ensureInitialized(this)

        // Seed initial presets, restore Obsidian vault, and reset stale loaded states on IO scope
        CoroutineScope(Dispatchers.IO).launch {
            localModelRepository.markAllUnloaded()
            seedInitialCloudPresets()
            restoreObsidianVault()
        }
    }

    private suspend fun seedInitialCloudPresets() {
        try {
            val existing = cloudModelRepository.allProviders.first()
            if (existing.isEmpty()) {
                AppLogger.i("PocketAIApp", "Seeding default cloud provider presets.")
                CloudProviderManager.PRESET_PROVIDERS.forEach { preset ->
                    cloudModelRepository.saveProvider(preset)
                }
                // Set default selected cloud provider to DeepSeek preset
                preferencesManager.setSelectedCloudProviderId("preset_deepseek")
            }
        } catch (e: Exception) {
            AppLogger.e("PocketAIApp", "Error seeding presets: ${e.message}")
        }
    }

    private suspend fun restoreObsidianVault() {
        try {
            val prefs = preferencesManager.preferencesFlow.first()
            obsidianManager.initializeVault(prefs.obsidianVaultUri)
        } catch (e: Exception) {
            AppLogger.w("PocketAIApp", "Error restoring Obsidian vault: ${e.message}")
        }
    }
}
