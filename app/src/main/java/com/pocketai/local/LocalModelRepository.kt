package com.pocketai.local

import com.pocketai.data.local.database.dao.LocalModelDao
import com.pocketai.data.local.database.entities.LocalModelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalModelRepository(private val dao: LocalModelDao) {

    val allModels: Flow<List<LocalModelInfo>> = dao.getAllModels().map { entities ->
        entities.map { it.toDomain() }
    }

    val loadedModel: Flow<LocalModelInfo?> = dao.getLoadedModel().map { entity ->
        entity?.toDomain()
    }

    suspend fun getModelById(id: String): LocalModelInfo? {
        return dao.getModelById(id)?.toDomain()
    }

    suspend fun getCurrentlyLoadedModelSync(): LocalModelInfo? {
        return dao.getCurrentlyLoadedModelSync()?.toDomain()
    }

    suspend fun saveModel(model: LocalModelInfo) {
        dao.insertModel(model.toEntity())
    }

    suspend fun markAllUnloaded() {
        dao.markAllUnloaded()
    }

    suspend fun markLoaded(id: String) {
        dao.markAllUnloaded()
        dao.markLoaded(id)
    }

    suspend fun updateStatus(id: String, status: LocalModelStatus, error: String? = null) {
        dao.updateStatus(id, status.name, error)
    }

    suspend fun updateMetadata(id: String, architecture: String?, contextLength: Int?, parametersCount: String?) {
        dao.updateModelMetadata(id, architecture, contextLength, parametersCount)
    }

    suspend fun deleteModel(id: String) {
        dao.deleteModelById(id)
    }

    private fun LocalModelEntity.toDomain(): LocalModelInfo {
        val modelStatus = try {
            LocalModelStatus.valueOf(status)
        } catch (_: Exception) {
            LocalModelStatus.UNLOADED
        }
        return LocalModelInfo(
            id = id,
            name = name,
            fileName = fileName,
            fileUri = fileUri,
            format = format,
            sizeBytes = sizeBytes,
            architecture = architecture,
            quantization = quantization,
            contextLength = contextLength,
            parametersCount = parametersCount,
            status = modelStatus,
            isLoaded = isLoaded,
            importTimestamp = importTimestamp,
            memoryFootprintMb = (sizeBytes / (1024 * 1024)),
            lastError = lastError
        )
    }

    private fun LocalModelInfo.toEntity(): LocalModelEntity {
        return LocalModelEntity(
            id = id,
            name = name,
            fileName = fileName,
            fileUri = fileUri,
            format = format,
            sizeBytes = sizeBytes,
            architecture = architecture,
            quantization = quantization,
            contextLength = contextLength,
            parametersCount = parametersCount,
            status = status.name,
            isLoaded = isLoaded,
            importTimestamp = importTimestamp,
            lastError = lastError
        )
    }
}
