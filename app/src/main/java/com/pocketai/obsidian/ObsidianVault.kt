package com.pocketai.obsidian

data class ObsidianVault(
    val vaultUri: String,
    val name: String,
    val isConnected: Boolean,
    val subfoldersFound: List<String> = emptyList(),
    val totalNotesCount: Int = 0,
    val lastSyncTimestamp: Long? = null
)

data class ObsidianVaultStatus(
    val isConnected: Boolean = false,
    val vaultName: String? = null,
    val vaultUri: String? = null,
    val totalNotesCount: Int = 0,
    val subfoldersFound: List<String> = emptyList()
)

data class ObsidianNote(
    val fileName: String,
    val relativeFolder: String,
    val title: String,
    val content: String,
    val lastModified: Long,
    val fileUri: String
)

enum class VaultFolder(val folderName: String) {
    MEMORY("Memory"),
    KNOWLEDGE("Knowledge"),
    CONVERSATIONS("Conversations"),
    PREFERENCES("Preferences"),
    LEARNED("Learned")
}

object ObsidianConstants {
    const val FOLDER_MEMORY = "Memory"
    const val FOLDER_KNOWLEDGE = "Knowledge"
    const val FOLDER_CONVERSATIONS = "Conversations"
    const val FOLDER_PREFERENCES = "Preferences"
    const val FOLDER_LEARNED = "Learned"

    val REQUIRED_FOLDERS = listOf(
        FOLDER_MEMORY,
        FOLDER_KNOWLEDGE,
        FOLDER_CONVERSATIONS,
        FOLDER_PREFERENCES,
        FOLDER_LEARNED
    )
}
