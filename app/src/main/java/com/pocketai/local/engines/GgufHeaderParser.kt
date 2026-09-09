package com.pocketai.local.engines

import com.pocketai.core.logging.AppLogger
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
    val isValidGguf: Boolean,
    val version: Int = 0,
    val tensorCount: Long = 0L,
    val metadataKvCount: Long = 0L,
    val architecture: String? = null,
    val contextLength: Int? = null,
    val quantization: String? = null
)

object GgufHeaderParser {

    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in little-endian

    private fun readFully(stream: InputStream, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Boolean {
        var total = 0
        while (total < length) {
            val count = stream.read(buffer, offset + total, length - total)
            if (count == -1) return false
            total += count
        }
        return true
    }

    /**
     * Attempts to parse GGUF header metadata from an InputStream.
     * Reads the magic header and basic key-values without loading the full file.
     */
    fun parse(inputStream: InputStream, filenameHint: String? = null): GgufMetadata {
        return try {
            val headerBytes = ByteArray(16)
            if (!readFully(inputStream, headerBytes)) {
                return GgufMetadata(isValidGguf = false)
            }

            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.int
            if (magic != GGUF_MAGIC) {
                return GgufMetadata(isValidGguf = false)
            }

            val version = buffer.int
            val tensorCount = buffer.long

            // Read metadata kv count (8 bytes uint64 in GGUF v2/v3)
            val kvCountBytes = ByteArray(8)
            val metadataKvCount = if (readFully(inputStream, kvCountBytes)) {
                ByteBuffer.wrap(kvCountBytes).order(ByteOrder.LITTLE_ENDIAN).long
            } else 0L

            // Best-effort key scanner from the header region (up to 64KB)
            var detectedArchitecture: String? = null
            var detectedContextLength: Int? = null
            var detectedQuant: String? = null

            val scanBuffer = ByteArray(65536)
            var scanTotal = 0
            while (scanTotal < scanBuffer.size) {
                val r = inputStream.read(scanBuffer, scanTotal, scanBuffer.size - scanTotal)
                if (r == -1) break
                scanTotal += r
            }

            if (scanTotal > 0) {
                val scanStr = String(scanBuffer, 0, scanTotal, Charsets.ISO_8859_1)

                // Inspect common architecture tokens
                when {
                    scanStr.contains("bonsai", ignoreCase = true) -> detectedArchitecture = "bonsai"
                    scanStr.contains("qwen2", ignoreCase = true) -> detectedArchitecture = "qwen2"
                    scanStr.contains("qwen", ignoreCase = true) -> detectedArchitecture = "qwen"
                    scanStr.contains("gemma2", ignoreCase = true) -> detectedArchitecture = "gemma2"
                    scanStr.contains("gemma", ignoreCase = true) -> detectedArchitecture = "gemma"
                    scanStr.contains("llama", ignoreCase = true) -> detectedArchitecture = "llama"
                    scanStr.contains("phi3", ignoreCase = true) || scanStr.contains("phi", ignoreCase = true) -> detectedArchitecture = "phi"
                    scanStr.contains("mistral", ignoreCase = true) -> detectedArchitecture = "mistral"
                    scanStr.contains("deepseek", ignoreCase = true) -> detectedArchitecture = "deepseek"
                }

                // Inspect common quantization tokens including PrismML low-bit formats (PQ2_0, PTQ1_0, etc.)
                val quantRegex = Regex("""(?i)\b(PQ2_0|PTQ1_0|TQ1_0|TQ2_0|Q1_0|Q1_1|Q2_0|Q2_K(?:_[A-Z])?|Q3_K(?:_[A-Z])?|Q4_0|Q4_1|Q4_K(?:_[A-Z])?|Q5_0|Q5_1|Q5_K(?:_[A-Z])?|Q6_K|Q8_0|IQ[1-4]_[A-Z]+|BF16|F16)\b""")
                val quantMatch = quantRegex.find(scanStr)
                if (quantMatch != null) {
                    detectedQuant = quantMatch.value.uppercase()
                }

                // Inspect context length hint
                if (scanStr.contains("context_length", ignoreCase = true)) {
                    detectedContextLength = 2048
                }
            }

            // Fallback to filename hint for architecture and quantization if not found in scan
            if (filenameHint != null) {
                val lowerName = filenameHint.lowercase()
                if (detectedArchitecture == null) {
                    when {
                        lowerName.contains("bonsai") -> detectedArchitecture = "bonsai"
                        lowerName.contains("qwen2") -> detectedArchitecture = "qwen2"
                        lowerName.contains("qwen") -> detectedArchitecture = "qwen"
                        lowerName.contains("gemma2") -> detectedArchitecture = "gemma2"
                        lowerName.contains("gemma") -> detectedArchitecture = "gemma"
                        lowerName.contains("llama") -> detectedArchitecture = "llama"
                        lowerName.contains("phi") -> detectedArchitecture = "phi"
                        lowerName.contains("mistral") -> detectedArchitecture = "mistral"
                    }
                }
                if (detectedQuant == null) {
                    val fnQuantRegex = Regex("""(?i)\b(PQ2_0|PTQ1_0|TQ1_0|TQ2_0|Q1_0|Q1_1|Q2_0|Q2_K(?:_[A-Z])?|Q3_K(?:_[A-Z])?|Q4_0|Q4_1|Q4_K(?:_[A-Z])?|Q5_0|Q5_1|Q5_K(?:_[A-Z])?|Q6_K|Q8_0|IQ[1-4]_[A-Z]+|BF16|F16)\b""")
                    fnQuantRegex.find(filenameHint)?.let {
                        detectedQuant = it.value.uppercase()
                    }
                }
            }

            GgufMetadata(
                isValidGguf = true,
                version = version,
                tensorCount = tensorCount,
                metadataKvCount = metadataKvCount,
                architecture = detectedArchitecture ?: "transformer",
                contextLength = detectedContextLength ?: 2048,
                quantization = detectedQuant
            )
        } catch (e: Exception) {
            AppLogger.w("GgufHeaderParser", "Error parsing GGUF header: ${e.message}")
            GgufMetadata(isValidGguf = false)
        }
    }
}
