package com.pocketai.cloud.providers

import com.pocketai.brain.ChatMessage
import com.pocketai.brain.MessageRole
import com.pocketai.cloud.CloudModelProvider
import com.pocketai.cloud.CloudProviderConfig
import com.pocketai.core.error.PocketAIException
import com.pocketai.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider : CloudModelProvider {

    override val providerId: String = "openai_compatible"
    override val providerName: String = "OpenAI Compatible Provider"

    private fun getClient(config: CloudProviderConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
    }

    private fun buildEndpoint(baseUrl: String): String {
        val clean = baseUrl.trim().removeSuffix("/")
        return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
    }

    private fun buildRequestBody(
        prompt: String,
        systemPrompt: String?,
        history: List<ChatMessage>,
        config: CloudProviderConfig,
        stream: Boolean
    ): String {
        val messagesArray = JSONArray()

        if (!systemPrompt.isNullOrBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        // Include prior history
        for (msg in history) {
            val roleStr = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            messagesArray.put(JSONObject().apply {
                put("role", roleStr)
                put("content", msg.content)
            })
        }

        // Add latest user prompt if not already in context
        if (history.isEmpty() || history.last().content != prompt) {
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        return JSONObject().apply {
            put("model", config.modelName)
            put("messages", messagesArray)
            put("stream", stream)
            put("temperature", 0.7)
        }.toString()
    }

    override suspend fun generate(
        config: CloudProviderConfig,
        prompt: String,
        systemPrompt: String?,
        history: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = getClient(config)
            val endpoint = buildEndpoint(config.baseUrl)
            val jsonPayload = buildRequestBody(prompt, systemPrompt, history, config, stream = false)
            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("Content-Type", "application/json")

            if (config.apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
            }
            if (!config.organizationId.isNullOrBlank()) {
                requestBuilder.header("OpenAI-Organization", config.organizationId.trim())
            }
            for ((k, v) in config.customHeaders) {
                requestBuilder.header(k, v)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val statusCode = response.code
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    PocketAIException.ApiEndpointException(endpoint, statusCode, bodyString.take(200))
                )
            }

            val json = JSONObject(bodyString)
            val choices = json.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val messageObj = firstChoice?.optJSONObject("message")
            val content = messageObj?.optString("content", "") ?: ""

            Result.success(content)
        } catch (e: Exception) {
            AppLogger.e("OpenAiCompatibleProvider", "Generation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun streamGenerate(
        config: CloudProviderConfig,
        prompt: String,
        systemPrompt: String?,
        history: List<ChatMessage>
    ): Flow<String> = callbackFlow {
        val client = getClient(config)
        val endpoint = buildEndpoint(config.baseUrl)
        val jsonPayload = buildRequestBody(prompt, systemPrompt, history, config, stream = true)
        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")

        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        if (!config.organizationId.isNullOrBlank()) {
            requestBuilder.header("OpenAI-Organization", config.organizationId.trim())
        }
        for ((k, v) in config.customHeaders) {
            requestBuilder.header(k, v)
        }

        val call = client.newCall(requestBuilder.build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(PocketAIException.ApiEndpointException(endpoint, 0, e.message ?: "Network error"))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string()?.take(200) ?: ""
                    close(PocketAIException.ApiEndpointException(endpoint, code, errorBody))
                    return
                }

                val responseBody = response.body
                if (responseBody == null) {
                    close()
                    return
                }

                try {
                    val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.removePrefix("data: ").trim()
                            if (data == "[DONE]") {
                                break
                            }
                            try {
                                val chunkJson = JSONObject(data)
                                val choices = chunkJson.optJSONArray("choices")
                                val first = choices?.optJSONObject(0)
                                val delta = first?.optJSONObject("delta")
                                val textChunk = delta?.optString("content", "") ?: ""
                                if (textChunk.isNotEmpty()) {
                                    trySend(textChunk)
                                }
                            } catch (_: Exception) {
                                // Skip non-json SSE lines
                            }
                        }
                    }
                    reader.close()
                    close()
                } catch (e: Exception) {
                    close(e)
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
