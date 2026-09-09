package com.pocketai.cloud

import com.pocketai.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class ApiTestResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val httpStatusCode: Int?,
    val testedModel: String,
    val errorMessage: String?,
    val responseSnippet: String?
)

class ApiTester(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testEndpoint(config: CloudProviderConfig): ApiTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val url = formatEndpointUrl(config.baseUrl)
        val testModel = config.modelName.trim().ifEmpty { "default" }

        AppLogger.i("ApiTester", "Starting API test for '${config.name}' targeting $url with model '$testModel'")

        // Build a lightweight test payload
        val root = JSONObject()
        root.put("model", testModel)
        root.put("max_tokens", 10)
        root.put("stream", false)

        val messages = JSONArray()
        val msgObj = JSONObject()
        msgObj.put("role", "user")
        msgObj.put("content", "ping")
        messages.put(msgObj)
        root.put("messages", messages)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(root.toString().toRequestBody(jsonMediaType))

        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        if (!config.organizationId.isNullOrBlank()) {
            requestBuilder.header("OpenAI-Organization", config.organizationId.trim())
        }
        config.customHeaders.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            val statusCode = response.code
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val snippet = try {
                    val json = JSONObject(body)
                    val choices = json.optJSONArray("choices")
                    choices?.getJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")?.trim() ?: "OK"
                } catch (_: Exception) {
                    "OK"
                }

                AppLogger.i("ApiTester", "API test PASSED for '${config.name}' ($latency ms, HTTP $statusCode)")
                ApiTestResult(
                    isSuccess = true,
                    latencyMs = latency,
                    httpStatusCode = statusCode,
                    testedModel = testModel,
                    errorMessage = null,
                    responseSnippet = snippet.take(80)
                )
            } else {
                val friendlyError = when (statusCode) {
                    401 -> "Authentication failed: Invalid or missing API key."
                    403 -> "Forbidden: Access denied or token lacks required permissions."
                    404 -> "Endpoint not found: Check that base URL is correct."
                    429 -> "Rate limit exceeded or quota exhausted."
                    500, 502, 503 -> "Remote provider server error (HTTP $statusCode)."
                    else -> try {
                        val errJson = JSONObject(body)
                        errJson.optJSONObject("error")?.optString("message") ?: "HTTP $statusCode"
                    } catch (_: Exception) {
                        "HTTP $statusCode: ${body.take(100)}"
                    }
                }

                AppLogger.w("ApiTester", "API test FAILED for '${config.name}': $friendlyError")
                ApiTestResult(
                    isSuccess = false,
                    latencyMs = latency,
                    httpStatusCode = statusCode,
                    testedModel = testModel,
                    errorMessage = friendlyError,
                    responseSnippet = null
                )
            }
        } catch (e: UnknownHostException) {
            val latency = System.currentTimeMillis() - startTime
            ApiTestResult(
                isSuccess = false,
                latencyMs = latency,
                httpStatusCode = null,
                testedModel = testModel,
                errorMessage = "Host not found. Check network connection and domain URL.",
                responseSnippet = null
            )
        } catch (e: SocketTimeoutException) {
            val latency = System.currentTimeMillis() - startTime
            ApiTestResult(
                isSuccess = false,
                latencyMs = latency,
                httpStatusCode = null,
                testedModel = testModel,
                errorMessage = "Connection timed out after ${latency}ms.",
                responseSnippet = null
            )
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            ApiTestResult(
                isSuccess = false,
                latencyMs = latency,
                httpStatusCode = null,
                testedModel = testModel,
                errorMessage = "Network error: ${e.message ?: "Failed to connect"}",
                responseSnippet = null
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ApiTestResult(
                isSuccess = false,
                latencyMs = latency,
                httpStatusCode = null,
                testedModel = testModel,
                errorMessage = "Test error: ${e.message ?: "Unknown error"}",
                responseSnippet = null
            )
        }
    }

    private fun formatEndpointUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().removeSuffix("/")
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else if (trimmed.endsWith("/v1")) {
            "$trimmed/chat/completions"
        } else {
            "$trimmed/v1/chat/completions"
        }
    }
}
