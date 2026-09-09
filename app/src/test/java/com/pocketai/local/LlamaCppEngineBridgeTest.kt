package com.pocketai.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pocketai.local.engines.GgufHeaderParser
import com.pocketai.local.engines.LlamaCppEngineBridge
import com.pocketai.local.engines.ModelInferenceParams
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LlamaCppEngineBridgeTest {

    @Test
    fun testContentResolverWithLocalFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "dummy.gguf")
        file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00))

        val uriParsed = android.net.Uri.parse(file.absolutePath)
        var parsedWorked = false
        try {
            context.contentResolver.openInputStream(uriParsed)?.use {
                parsedWorked = true
            }
        } catch (e: Exception) {
            println("openInputStream with parsed path failed: ${e::class.java.name}: ${e.message}")
        }

        val uriFromFile = android.net.Uri.fromFile(file)
        var fileUriWorked = false
        try {
            context.contentResolver.openInputStream(uriFromFile)?.use {
                fileUriWorked = true
            }
        } catch (e: Exception) {
            println("openInputStream with file:// failed: ${e::class.java.name}: ${e.message}")
        }

        println("parsedWorked (no scheme): $parsedWorked")
        println("fileUriWorked (file://): $fileUriWorked")
        assertTrue("Absolute path without scheme must be readable via ContentResolver", parsedWorked)
        assertTrue("File URI must be readable via ContentResolver", fileUriWorked)
    }

    @Test
    fun testLlamaCppEngineBridgeInitialization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = LlamaCppEngineBridge(context)

        assertEquals("PrismML llama.cpp Engine", bridge.engineName)
        assertFalse("Bridge should not report loaded initially", bridge.isLoaded())
    }

    @Test
    fun testHardwareDiagnostics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = LlamaCppEngineBridge(context)
        val diagnostics = bridge.getHardwareDiagnostics()

        assertTrue("Total RAM should be positive", diagnostics.totalRamMb > 0)
        assertTrue("Available RAM should be positive", diagnostics.availableRamMb > 0)
        assertTrue("Cores count should be positive", diagnostics.processorCores > 0)
        assertTrue("Recommended model size should be at least 400MB", diagnostics.recommendedMaxModelSizeMb >= 400)
    }

    @Test
    fun testPrismLlamaClassesExistOnClasspath() {
        val classLoader = this.javaClass.classLoader
        val nativeJniClass = Class.forName("com.pocketai.local.engines.NativeLlamaJni", false, classLoader)
        val nativeEngineClass = Class.forName("com.pocketai.local.engines.NativeLlamaEngine", false, classLoader)

        assertNotNull(nativeJniClass)
        assertNotNull(nativeEngineClass)

        // Verify key native methods exist on NativeLlamaJni
        val initMethod = nativeJniClass.methods.find { it.name == "nativeInitBackend" }
        val loadMethod = nativeJniClass.methods.find { it.name == "nativeLoadModel" }
        val ctxMethod = nativeJniClass.methods.find { it.name == "nativeCreateContext" }
        val streamMethod = nativeJniClass.methods.find { it.name == "nativeGenerateStream" }
        val stopMethod = nativeJniClass.methods.find { it.name == "nativeStopGeneration" }

        assertNotNull("nativeInitBackend method must exist on NativeLlamaJni", initMethod)
        assertNotNull("nativeLoadModel method must exist on NativeLlamaJni", loadMethod)
        assertNotNull("nativeCreateContext method must exist on NativeLlamaJni", ctxMethod)
        assertNotNull("nativeGenerateStream method must exist on NativeLlamaJni", streamMethod)
        assertNotNull("nativeStopGeneration method must exist on NativeLlamaJni", stopMethod)

        // Verify required API methods exist on NativeLlamaEngine
        val loadModelMethod = nativeEngineClass.methods.find { it.name.startsWith("loadModel") }
        val unloadModelMethod = nativeEngineClass.methods.find { it.name.startsWith("unloadModel") }
        val createContextMethod = nativeEngineClass.methods.find { it.name.startsWith("createContext") }
        val getModelInfoMethod = nativeEngineClass.methods.find { it.name == "getModelInfo" }
        val isModelLoadedMethod = nativeEngineClass.methods.find { it.name == "isModelLoaded" }

        assertNotNull("loadModel method must exist on NativeLlamaEngine", loadModelMethod)
        assertNotNull("unloadModel method must exist on NativeLlamaEngine", unloadModelMethod)
        assertNotNull("createContext method must exist on NativeLlamaEngine", createContextMethod)
        assertNotNull("getModelInfo method must exist on NativeLlamaEngine", getModelInfoMethod)
        assertNotNull("isModelLoaded method must exist on NativeLlamaEngine", isModelLoadedMethod)
    }

    @Test
    fun testGgufHeaderParserValidGemma2bQ2K() {
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46554747) // "GGUF"
        buffer.putInt(3)          // version 3
        buffer.putLong(100L)      // tensor count
        buffer.putLong(10L)       // kv count
        buffer.put("gemma.context_length".toByteArray(Charsets.ISO_8859_1))

        val stream = ByteArrayInputStream(buffer.array())
        val metadata = GgufHeaderParser.parse(stream, "gemma-2b-it-Q2_K.gguf")

        assertTrue("Should be valid GGUF", metadata.isValidGguf)
        assertEquals("gemma", metadata.architecture)
        assertEquals("Q2_K", metadata.quantization)
    }

    @Test
    fun testGgufHeaderParserValidBonsai17bQ1_0() {
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46554747) // "GGUF"
        buffer.putInt(3)          // version 3
        buffer.putLong(80L)       // tensor count
        buffer.putLong(12L)       // kv count
        buffer.put("bonsai.context_length".toByteArray(Charsets.ISO_8859_1))

        val stream = ByteArrayInputStream(buffer.array())
        val metadata = GgufHeaderParser.parse(stream, "Bonsai-1.7B-Q1_0.gguf")

        assertTrue("Should be valid GGUF", metadata.isValidGguf)
        assertEquals("bonsai", metadata.architecture)
        assertEquals("Q1_0", metadata.quantization)
    }

    @Test
    fun testGgufHeaderParserRejectsNonGguf() {
        val nonGgufBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
        val stream = ByteArrayInputStream(nonGgufBytes)
        val metadata = GgufHeaderParser.parse(stream)

        assertFalse("Non-GGUF bytes should be rejected", metadata.isValidGguf)
    }

    @Test
    fun testLoadNonExistentModelFailsGracefully() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = LlamaCppEngineBridge(context)
        val nonExistentUri = android.net.Uri.fromFile(File(context.filesDir, "non_existent.gguf"))

        val result = bridge.loadModelFromUri(
            nonExistentUri,
            "NonExistent",
            ModelInferenceParams()
        )

        assertTrue("Loading non-existent file should return failure", result.isFailure)
        assertFalse("Bridge should not report loaded", bridge.isLoaded())
    }

    @Test
    fun testUnloadWhenIdleSucceeds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = LlamaCppEngineBridge(context)
        val unloadResult = bridge.unload()

        assertTrue("Unload when idle should succeed", unloadResult.isSuccess)
        assertFalse("Bridge should remain unloaded", bridge.isLoaded())
    }

    @Test
    fun testStopGenerationWhenIdleDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = LlamaCppEngineBridge(context)
        // Calling stopGeneration when no generation is ongoing should be completely safe
        bridge.stopGeneration()
    }
}
