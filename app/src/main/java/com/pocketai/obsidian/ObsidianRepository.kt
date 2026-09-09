package com.pocketai.obsidian

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.pocketai.core.error.PocketAIException
import com.pocketai.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ObsidianRepository(private val context: Context) {

    /**
     * Persists URI permissions for the selected Obsidian vault tree.
     */
    fun takePersistablePermissions(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            AppLogger.i("ObsidianRepository", "Persistable permission granted for $treeUri")
        } catch (e: SecurityException) {
            AppLogger.w("ObsidianRepository", "Failed to take persistable URI permission: ${e.message}")
        }
    }

    /**
     * Finds or creates a subfolder within the vault tree.
     */
    suspend fun getOrCreateSubfolder(treeUri: Uri, folderName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

            // Check if folder already exists
            val existingUri = findChildByName(treeUri, rootDocId, folderName)
            if (existingUri != null) {
                return@withContext existingUri
            }

            // Create directory
            DocumentsContract.createDocument(
                context.contentResolver,
                rootDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                folderName
            )
        } catch (e: Exception) {
            AppLogger.e("ObsidianRepository", "Failed to create folder $folderName: ${e.message}", e)
            null
        }
    }

    /**
     * Searches children under parentDocId for a specific name.
     */
    private fun findChildByName(treeUri: Uri, parentDocId: String, name: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val docName = cursor.getString(nameIndex)
                if (docName.equals(name, ignoreCase = true)) {
                    val docId = cursor.getString(idIndex)
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                }
            }
        }
        return null
    }

    /**
     * Reads all markdown notes from a subfolder.
     */
    suspend fun listNotesInFolder(treeUri: Uri, folderName: String): List<ObsidianNote> = withContext(Dispatchers.IO) {
        val notes = mutableListOf<ObsidianNote>()
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val folderUri = findChildByName(treeUri, rootDocId, folderName) ?: return@withContext emptyList()
            val folderDocId = DocumentsContract.getDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIndex)
                    if (displayName != null && (displayName.endsWith(".md") || displayName.endsWith(".markdown"))) {
                        val docId = cursor.getString(idIndex)
                        val lastMod = cursor.getLong(modIndex)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                        val content = readNoteContent(docUri)
                        val title = displayName.removeSuffix(".md").removeSuffix(".markdown")
                        notes.add(
                            ObsidianNote(
                                fileName = displayName,
                                relativeFolder = folderName,
                                title = title,
                                content = content,
                                lastModified = lastMod,
                                fileUri = docUri.toString()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("ObsidianRepository", "Error listing notes in $folderName: ${e.message}")
        }
        notes
    }

    /**
     * Reads text content of a markdown document.
     */
    fun readNoteContent(documentUri: Uri): String {
        return try {
            context.contentResolver.openInputStream(documentUri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (e: Exception) {
            AppLogger.w("ObsidianRepository", "Failed to read document $documentUri: ${e.message}")
            ""
        }
    }

    /**
     * Creates a new markdown note or writes content safely.
     */
    suspend fun writeNote(
        treeUri: Uri,
        folderName: String,
        fileName: String,
        content: String,
        overwrite: Boolean = false
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val folderUri = getOrCreateSubfolder(treeUri, folderName)
                ?: return@withContext Result.failure(PocketAIException.ObsidianStorageException("Cannot create folder $folderName"))

            val folderDocId = DocumentsContract.getDocumentId(folderUri)
            val targetName = if (fileName.endsWith(".md")) fileName else "$fileName.md"

            val existingChildUri = findChildByName(treeUri, folderDocId, targetName)
            val targetFileUri = if (existingChildUri != null) {
                if (!overwrite) {
                    // Do not blindly overwrite user notes! Append timestamp to make unique
                    val uniqueName = "${targetName.removeSuffix(".md")}_${System.currentTimeMillis()}.md"
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        folderUri,
                        "text/markdown",
                        uniqueName
                    ) ?: return@withContext Result.failure(PocketAIException.ObsidianStorageException("Could not create unique note"))
                } else {
                    existingChildUri
                }
            } else {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    folderUri,
                    "text/markdown",
                    targetName
                ) ?: return@withContext Result.failure(PocketAIException.ObsidianStorageException("Could not create note file"))
            }

            // Write content
            context.contentResolver.openOutputStream(targetFileUri, "wt")?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }

            Result.success(targetFileUri)
        } catch (e: Exception) {
            AppLogger.e("ObsidianRepository", "Error writing note $fileName: ${e.message}", e)
            Result.failure(PocketAIException.ObsidianStorageException(e.message ?: "Failed writing note"))
        }
    }
}
