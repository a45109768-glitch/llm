package com.pocketai.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pocketai.data.local.database.entities.ChatMessageEntity
import com.pocketai.data.local.database.entities.CloudProviderEntity
import com.pocketai.data.local.database.entities.ConversationEntity
import com.pocketai.data.local.database.entities.LocalModelEntity
import com.pocketai.data.local.database.entities.MemoryEntity
import com.pocketai.data.local.database.entities.RobotConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY importance DESC, updatedAt DESC")
    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun getTopMemories(limit: Int): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}

@Dao
interface LocalModelDao {
    @Query("SELECT * FROM local_models ORDER BY importTimestamp DESC")
    fun getAllModels(): Flow<List<LocalModelEntity>>

    @Query("SELECT * FROM local_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE isLoaded = 1 LIMIT 1")
    fun getLoadedModel(): Flow<LocalModelEntity?>

    @Query("SELECT * FROM local_models WHERE isLoaded = 1 LIMIT 1")
    suspend fun getCurrentlyLoadedModelSync(): LocalModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LocalModelEntity)

    @Update
    suspend fun updateModel(model: LocalModelEntity)

    @Query("UPDATE local_models SET isLoaded = 0, status = 'UNLOADED'")
    suspend fun markAllUnloaded()

    @Query("UPDATE local_models SET isLoaded = 1, status = 'LOADED', lastLoadedTimestamp = :timestamp, lastError = null WHERE id = :id")
    suspend fun markLoaded(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE local_models SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE local_models SET architecture = :architecture, contextLength = :contextLength, parametersCount = :parametersCount WHERE id = :id")
    suspend fun updateModelMetadata(id: String, architecture: String?, contextLength: Int?, parametersCount: String?)

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteModelById(id: String)
}

@Dao
interface CloudProviderDao {
    @Query("SELECT * FROM cloud_providers ORDER BY name ASC")
    fun getAllProviders(): Flow<List<CloudProviderEntity>>

    @Query("SELECT * FROM cloud_providers WHERE isEnabled = 1 ORDER BY name ASC")
    fun getEnabledProviders(): Flow<List<CloudProviderEntity>>

    @Query("SELECT * FROM cloud_providers WHERE id = :id LIMIT 1")
    suspend fun getProviderById(id: String): CloudProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: CloudProviderEntity)

    @Update
    suspend fun updateProvider(provider: CloudProviderEntity)

    @Query("DELETE FROM cloud_providers WHERE id = :id")
    suspend fun deleteProviderById(id: String)

    @Query("UPDATE cloud_providers SET lastTestedTimestamp = :timestamp, lastTestSuccess = :success, lastTestLatencyMs = :latencyMs, lastTestMessage = :message WHERE id = :id")
    suspend fun updateTestResult(id: String, timestamp: Long, success: Boolean, latencyMs: Long, message: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversationSync(conversationId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)
}

@Dao
interface RobotDao {
    @Query("SELECT * FROM robot_configs WHERE id = :id LIMIT 1")
    fun getRobotConfig(id: String = "default_robot"): Flow<RobotConfigEntity?>

    @Query("SELECT * FROM robot_configs WHERE id = :id LIMIT 1")
    suspend fun getRobotConfigSync(id: String = "default_robot"): RobotConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRobotConfig(config: RobotConfigEntity)
}
