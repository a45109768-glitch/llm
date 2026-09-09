package com.pocketai.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pocketai.data.local.database.dao.CloudProviderDao
import com.pocketai.data.local.database.dao.ConversationDao
import com.pocketai.data.local.database.dao.LocalModelDao
import com.pocketai.data.local.database.dao.MemoryDao
import com.pocketai.data.local.database.dao.RobotDao
import com.pocketai.data.local.database.entities.ChatMessageEntity
import com.pocketai.data.local.database.entities.CloudProviderEntity
import com.pocketai.data.local.database.entities.ConversationEntity
import com.pocketai.data.local.database.entities.LocalModelEntity
import com.pocketai.data.local.database.entities.MemoryEntity
import com.pocketai.data.local.database.entities.RobotConfigEntity

@Database(
    entities = [
        MemoryEntity::class,
        LocalModelEntity::class,
        CloudProviderEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        RobotConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PocketAIDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun localModelDao(): LocalModelDao
    abstract fun cloudProviderDao(): CloudProviderDao
    abstract fun conversationDao(): ConversationDao
    abstract fun robotDao(): RobotDao

    companion object {
        @Volatile
        private var INSTANCE: PocketAIDatabase? = null

        fun getInstance(context: Context): PocketAIDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PocketAIDatabase::class.java,
                    "pocketai_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
