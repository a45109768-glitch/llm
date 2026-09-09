package com.pocketai.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pocketai_settings")

enum class RoutingMode {
    LOCAL_FIRST,
    CLOUD_FIRST,
    LOCAL_ONLY,
    CLOUD_ONLY,
    AUTO
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class AppPreferences(
    val routingMode: RoutingMode = RoutingMode.LOCAL_FIRST,
    val selectedLocalModelId: String? = null,
    val selectedCloudProviderId: String? = null,
    val obsidianVaultUri: String? = null,
    val isObsidianSyncEnabled: Boolean = false,
    val isMemoryEnabled: Boolean = true,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val temperature: Float = 0.7f,
    val localContextLength: Int = 2048,
    val localMaxTokens: Int = 512,
    val localCpuThreads: Int = 4,
    val localTopP: Float = 0.9f,
    val localTopK: Int = 40,
    val localRepeatPenalty: Float = 1.1f
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are PocketAI, a local-first personal AI assistant and intelligent robotics companion. You are direct, thoughtful, accurate, and respect user privacy and autonomy."
    }
}

class PreferencesManager(private val context: Context) {

    private object Keys {
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val SELECTED_LOCAL_MODEL_ID = stringPreferencesKey("selected_local_model_id")
        val SELECTED_CLOUD_PROVIDER_ID = stringPreferencesKey("selected_cloud_provider_id")
        val OBSIDIAN_VAULT_URI = stringPreferencesKey("obsidian_vault_uri")
        val OBSIDIAN_SYNC_ENABLED = booleanPreferencesKey("obsidian_sync_enabled")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEMPERATURE = androidx.datastore.preferences.core.floatPreferencesKey("temperature")
        val LOCAL_CONTEXT_LENGTH = androidx.datastore.preferences.core.intPreferencesKey("local_context_length")
        val LOCAL_MAX_TOKENS = androidx.datastore.preferences.core.intPreferencesKey("local_max_tokens")
        val LOCAL_CPU_THREADS = androidx.datastore.preferences.core.intPreferencesKey("local_cpu_threads")
        val LOCAL_TOP_P = androidx.datastore.preferences.core.floatPreferencesKey("local_top_p")
        val LOCAL_TOP_K = androidx.datastore.preferences.core.intPreferencesKey("local_top_k")
        val LOCAL_REPEAT_PENALTY = androidx.datastore.preferences.core.floatPreferencesKey("local_repeat_penalty")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            routingMode = prefs[Keys.ROUTING_MODE]?.let {
                try { RoutingMode.valueOf(it) } catch (_: Exception) { RoutingMode.LOCAL_FIRST }
            } ?: RoutingMode.LOCAL_FIRST,
            selectedLocalModelId = prefs[Keys.SELECTED_LOCAL_MODEL_ID],
            selectedCloudProviderId = prefs[Keys.SELECTED_CLOUD_PROVIDER_ID],
            obsidianVaultUri = prefs[Keys.OBSIDIAN_VAULT_URI],
            isObsidianSyncEnabled = prefs[Keys.OBSIDIAN_SYNC_ENABLED] ?: false,
            isMemoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: AppPreferences.DEFAULT_SYSTEM_PROMPT,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                try { ThemeMode.valueOf(it) } catch (_: Exception) { ThemeMode.DARK }
            } ?: ThemeMode.DARK,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            localContextLength = prefs[Keys.LOCAL_CONTEXT_LENGTH] ?: 2048,
            localMaxTokens = prefs[Keys.LOCAL_MAX_TOKENS] ?: 512,
            localCpuThreads = prefs[Keys.LOCAL_CPU_THREADS] ?: 4,
            localTopP = prefs[Keys.LOCAL_TOP_P] ?: 0.9f,
            localTopK = prefs[Keys.LOCAL_TOP_K] ?: 40,
            localRepeatPenalty = prefs[Keys.LOCAL_REPEAT_PENALTY] ?: 1.1f
        )
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        context.dataStore.edit { it[Keys.ROUTING_MODE] = mode.name }
    }

    suspend fun setSelectedLocalModelId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[Keys.SELECTED_LOCAL_MODEL_ID] = id
            else it.remove(Keys.SELECTED_LOCAL_MODEL_ID)
        }
    }

    suspend fun setSelectedCloudProviderId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[Keys.SELECTED_CLOUD_PROVIDER_ID] = id
            else it.remove(Keys.SELECTED_CLOUD_PROVIDER_ID)
        }
    }

    suspend fun setObsidianVaultUri(uri: String?) {
        context.dataStore.edit {
            if (uri != null) it[Keys.OBSIDIAN_VAULT_URI] = uri
            else it.remove(Keys.OBSIDIAN_VAULT_URI)
        }
    }

    suspend fun setObsidianSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OBSIDIAN_SYNC_ENABLED] = enabled }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setTemperature(temperature: Float) {
        context.dataStore.edit { it[Keys.TEMPERATURE] = temperature }
    }

    suspend fun setLocalInferenceConfig(
        contextLength: Int,
        maxTokens: Int,
        threads: Int,
        temperature: Float,
        topP: Float,
        repeatPenalty: Float
    ) {
        context.dataStore.edit {
            it[Keys.LOCAL_CONTEXT_LENGTH] = contextLength
            it[Keys.LOCAL_MAX_TOKENS] = maxTokens
            it[Keys.LOCAL_CPU_THREADS] = threads
            it[Keys.TEMPERATURE] = temperature
            it[Keys.LOCAL_TOP_P] = topP
            it[Keys.LOCAL_REPEAT_PENALTY] = repeatPenalty
        }
    }
}
