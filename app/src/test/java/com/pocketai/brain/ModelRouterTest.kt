package com.pocketai.brain

import com.pocketai.cloud.CloudProviderConfig
import com.pocketai.cloud.CloudProviderType
import com.pocketai.data.preferences.AppPreferences
import com.pocketai.data.preferences.RoutingMode
import com.pocketai.local.LocalModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterTest {

    @Test
    fun testRoutingModeNames() {
        assertEquals(5, RoutingMode.values().size)
        assertTrue(RoutingMode.values().contains(RoutingMode.LOCAL_FIRST))
        assertTrue(RoutingMode.values().contains(RoutingMode.CLOUD_FIRST))
        assertTrue(RoutingMode.values().contains(RoutingMode.LOCAL_ONLY))
        assertTrue(RoutingMode.values().contains(RoutingMode.CLOUD_ONLY))
        assertTrue(RoutingMode.values().contains(RoutingMode.AUTO))
    }

    @Test
    fun testLocalModelInfoData() {
        val model = LocalModelInfo(
            id = "test_model_1",
            name = "Bonsai 1.7B",
            fileName = "bonsai-1.7b.gguf",
            fileUri = "content://test/bonsai",
            sizeBytes = 1_500_000_000L,
            quantization = "Q4_K_M",
            architecture = "llama",
            contextLength = 4096,
            isLoaded = true
        )

        assertEquals("test_model_1", model.id)
        assertEquals("Bonsai 1.7B", model.name)
        assertTrue(model.isLoaded)
        assertEquals("Q4_K_M", model.quantization)
    }

    @Test
    fun testCloudProviderConfigData() {
        val config = CloudProviderConfig(
            id = "deepseek_1",
            name = "DeepSeek AI",
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "sk-test",
            modelName = "deepseek-chat",
            providerType = CloudProviderType.DEEPSEEK,
            isEnabled = true
        )

        assertEquals("deepseek_1", config.id)
        assertEquals("DeepSeek AI", config.name)
        assertTrue(config.isEnabled)
        assertEquals(CloudProviderType.DEEPSEEK, config.providerType)
    }

    @Test
    fun testActiveRouteModelPill() {
        val route = ActiveRoute(
            sourceType = ModelSourceType.LOCAL,
            modelId = "local_model_1",
            displayName = "Local • Bonsai 1.7B",
            isReady = true,
            statusMessage = "Offline Ready"
        )

        assertEquals(ModelSourceType.LOCAL, route.sourceType)
        assertTrue(route.isReady)
        assertEquals("Local • Bonsai 1.7B", route.displayName)
    }

    @Test
    fun testAppPreferencesDefaults() {
        val prefs = AppPreferences()
        assertEquals(RoutingMode.LOCAL_FIRST, prefs.routingMode)
        assertTrue(prefs.isMemoryEnabled)
        assertFalse(prefs.isObsidianSyncEnabled)
    }
}
