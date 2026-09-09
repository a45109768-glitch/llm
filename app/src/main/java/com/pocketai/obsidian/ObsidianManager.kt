package com.pocketai.obsidian

import android.net.Uri
import com.pocketai.core.logging.AppLogger
import com.pocketai.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ObsidianManager(
    private val repository: ObsidianRepository,
    private val preferencesManager: PreferencesManager
) {
    private val _vaultStatus = MutableStateFlow(ObsidianVaultStatus())
    val vaultStatus: StateFlow<ObsidianVaultStatus> = _vaultStatus.asStateFlow()

    suspend fun initializeVault(uriString: String?) = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext
        try {
            connectVault(Uri.parse(uriString))
        } catch (e: Exception) {
            AppLogger.w("ObsidianManager", "Could not restore vault: ${e.message}")
        }
    }

    suspend fun connectVault(treeUri: Uri): Result<ObsidianVault> = withContext(Dispatchers.IO) {
        try {
            repository.takePersistablePermissions(treeUri)

            // Ensure the 5 canonical PocketAI Vault subdirectories exist
            val foundFolders = mutableListOf<String>()
            for (folder in VaultFolder.values()) {
                val folderUri = repository.getOrCreateSubfolder(treeUri, folder.folderName)
                if (folderUri != null) {
                    foundFolders.add(folder.folderName)
                }
            }

            // Count notes in Knowledge and Memory
            val memoryNotes = repository.listNotesInFolder(treeUri, VaultFolder.MEMORY.folderName)
            val knowledgeNotes = repository.listNotesInFolder(treeUri, VaultFolder.KNOWLEDGE.folderName)
            val totalNotes = memoryNotes.size + knowledgeNotes.size

            val vaultName = treeUri.lastPathSegment?.substringAfterLast(":") ?: "PocketAI Vault"

            val vault = ObsidianVault(
                vaultUri = treeUri.toString(),
                name = vaultName,
                isConnected = true,
                subfoldersFound = foundFolders,
                totalNotesCount = totalNotes,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            preferencesManager.setObsidianVaultUri(treeUri.toString())
            _vaultStatus.value = ObsidianVaultStatus(
                isConnected = true,
                vaultName = vaultName,
                vaultUri = treeUri.toString(),
                totalNotesCount = totalNotes,
                subfoldersFound = foundFolders
            )
            AppLogger.i("ObsidianManager", "Obsidian vault successfully connected: $vaultName")
            Result.success(vault)
        } catch (e: Exception) {
            AppLogger.e("ObsidianManager", "Failed to connect vault: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun disconnectVault() {
        preferencesManager.setObsidianVaultUri(null)
        preferencesManager.setObsidianSyncEnabled(false)
        _vaultStatus.value = ObsidianVaultStatus(isConnected = false)
        AppLogger.i("ObsidianManager", "Obsidian vault disconnected.")
    }

    suspend fun createKnowledgeNote(
        title: String,
        content: String,
        folderName: String = ObsidianConstants.FOLDER_KNOWLEDGE
    ): Result<String> = withContext(Dispatchers.IO) {
        val currentUri = _vaultStatus.value.vaultUri
            ?: return@withContext Result.failure(Exception("No Obsidian vault connected."))

        val treeUri = Uri.parse(currentUri)
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val noteContent = buildString {
            appendLine("---")
            appendLine("title: \"$title\"")
            appendLine("created: \"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\"")
            appendLine("tags: [pocketai, knowledge]")
            appendLine("---")
            appendLine()
            appendLine(content)
        }

        val writeResult = repository.writeNote(
            treeUri = treeUri,
            folderName = folderName,
            fileName = cleanTitle,
            content = noteContent,
            overwrite = false
        )
        if (writeResult.isSuccess) {
            Result.success(cleanTitle)
        } else {
            Result.failure(writeResult.exceptionOrNull() ?: Exception("Write failed"))
        }
    }

    suspend fun exportConversationToVault(
        title: String,
        messagesFormatted: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val currentUri = _vaultStatus.value.vaultUri
            ?: return@withContext Result.failure(Exception("No Obsidian vault connected. Connect in Settings."))

        val treeUri = Uri.parse(currentUri)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
        val timestamp = dateFormat.format(Date())
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val fileName = "Chat_${timestamp}_$cleanTitle"

        val markdownContent = buildString {
            appendLine("---")
            appendLine("title: \"$title\"")
            appendLine("date: \"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\"")
            appendLine("source: \"PocketAI Android\"")
            appendLine("tags: [pocketai, conversation, ai]")
            appendLine("---")
            appendLine()
            appendLine("# $title")
            appendLine()
            appendLine(messagesFormatted)
        }

        val writeResult = repository.writeNote(
            treeUri = treeUri,
            folderName = VaultFolder.CONVERSATIONS.folderName,
            fileName = fileName,
            content = markdownContent,
            overwrite = false
        )

        if (writeResult.isSuccess) {
            Result.success(fileName)
        } else {
            Result.failure(writeResult.exceptionOrNull() ?: Exception("Write failed"))
        }
    }

    suspend fun searchKnowledge(query: String): List<ObsidianNote> = withContext(Dispatchers.IO) {
        val currentUri = _vaultStatus.value.vaultUri ?: return@withContext emptyList()
        val treeUri = Uri.parse(currentUri)

        val notes = mutableListOf<ObsidianNote>()
        notes.addAll(repository.listNotesInFolder(treeUri, VaultFolder.KNOWLEDGE.folderName))
        notes.addAll(repository.listNotesInFolder(treeUri, VaultFolder.MEMORY.folderName))

        notes.filter {
            it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
        }
    }
}
