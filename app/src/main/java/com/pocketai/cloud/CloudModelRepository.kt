package com.pocketai.cloud

import com.pocketai.data.local.database.dao.CloudProviderDao
import com.pocketai.data.local.database.entities.CloudProviderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class CloudModelRepository(private val dao: CloudProviderDao) {

    val allProviders: Flow<List<CloudProviderConfig>> = dao.getAllProviders().map { list ->
        list.map { it.toDomain() }
    }

    val enabledProviders: Flow<List<CloudProviderConfig>> = dao.getEnabledProviders().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getProviderById(id: String): CloudProviderConfig? {
        return dao.getProviderById(id)?.toDomain()
    }

    suspend fun saveProvider(config: CloudProviderConfig) {
        dao.insertProvider(config.toEntity())
    }

    suspend fun toggleProviderEnabled(id: String, enabled: Boolean) {
        val provider = getProviderById(id) ?: return
        saveProvider(provider.copy(isEnabled = enabled))
    }

    suspend fun deleteProvider(id: String) {
        dao.deleteProviderById(id)
    }

    suspend fun updateTestResult(id: String, success: Boolean, latencyMs: Long, message: String) {
        dao.updateTestResult(
            id = id,
            timestamp = System.currentTimeMillis(),
            success = success,
            latencyMs = latencyMs,
            message = message
        )
    }

    private fun CloudProviderEntity.toDomain(): CloudProviderConfig {
        val parsedType = try {
            CloudProviderType.valueOf(providerType)
        } catch (_: Exception) {
            CloudProviderType.OPENAI_COMPATIBLE
        }

        val headersMap = mutableMapOf<String, String>()
        try {
            val json = JSONObject(customHeadersJson)
            for (key in json.keys()) {
                headersMap[key] = json.getString(key)
            }
        } catch (_: Exception) {}

        return CloudProviderConfig(
            id = id,
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            organizationId = organizationId,
            isEnabled = isEnabled,
            providerType = parsedType,
            customHeaders = headersMap,
            timeoutSeconds = timeoutSeconds,
            lastTestedTimestamp = lastTestedTimestamp,
            lastTestSuccess = lastTestSuccess,
            lastTestLatencyMs = lastTestLatencyMs,
            lastTestMessage = lastTestMessage
        )
    }

    private fun CloudProviderConfig.toEntity(): CloudProviderEntity {
        val json = JSONObject()
        customHeaders.forEach { (k, v) -> json.put(k, v) }

        return CloudProviderEntity(
            id = id,
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            organizationId = organizationId,
            isEnabled = isEnabled,
            providerType = providerType.name,
            customHeadersJson = json.toString(),
            timeoutSeconds = timeoutSeconds,
            lastTestedTimestamp = lastTestedTimestamp,
            lastTestSuccess = lastTestSuccess,
            lastTestLatencyMs = lastTestLatencyMs,
            lastTestMessage = lastTestMessage
        )
    }
}
