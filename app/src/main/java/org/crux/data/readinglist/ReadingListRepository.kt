package org.crux.data.readinglist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.crux.data.io.FileStorage
import org.koin.core.annotation.Single

@Single
public class ReadingListRepository internal constructor(
    private val fileStorage: FileStorage,
) {
    private val mutex = Mutex()
    private val filePath by lazy { fileStorage.dataStoreFile("reading_list.json") }
    private val foldersFilePath by lazy { fileStorage.dataStoreFile("bookmark_folders.json") }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    private val _items = MutableStateFlow<List<ReadingListItem>>(loadFromDisk())
    public val items: StateFlow<List<ReadingListItem>> = _items.asStateFlow()

    private val _folders = MutableStateFlow<List<BookmarkFolder>>(loadFoldersFromDisk())
    public val folders: StateFlow<List<BookmarkFolder>> = _folders.asStateFlow()

    private fun loadFromDisk(): List<ReadingListItem> =
        runCatching {
            if (fileStorage.exists(filePath)) {
                val bytes = fileStorage.read(filePath)
                json.decodeFromString<List<ReadingListItem>>(bytes.decodeToString())
            } else {
                emptyList()
            }
        }.getOrElse { emptyList() }

    private fun loadFoldersFromDisk(): List<BookmarkFolder> =
        runCatching {
            if (fileStorage.exists(foldersFilePath)) {
                val bytes = fileStorage.read(foldersFilePath)
                val decoded = json.decodeFromString<List<BookmarkFolder>>(bytes.decodeToString())
                decoded.ifEmpty { defaultBookmarkFolders() }
            } else {
                defaultBookmarkFolders()
            }
        }.getOrElse { defaultBookmarkFolders() }

    private fun persistToDisk(list: List<ReadingListItem>) {
        runCatching {
            val bytes = json.encodeToString(list).encodeToByteArray()
            fileStorage.write(filePath, bytes)
        }
    }

    private fun persistFoldersToDisk(list: List<BookmarkFolder>) {
        runCatching {
            val bytes = json.encodeToString(list).encodeToByteArray()
            fileStorage.write(foldersFilePath, bytes)
        }
    }

    public suspend fun save(item: ReadingListItem) {
        mutex.withLock {
            val current = _items.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.id == item.id }
            if (existingIndex >= 0) {
                current[existingIndex] = item.copy(isRead = current[existingIndex].isRead)
            } else {
                current.add(0, item)
            }
            persistToDisk(current)
            _items.value = current
        }
    }

    public suspend fun remove(id: String) {
        mutex.withLock {
            val current = _items.value.toMutableList()
            if (current.removeAll { it.id == id }) {
                persistToDisk(current)
                _items.value = current
            }
        }
    }

    public suspend fun markAsRead(
        id: String,
        isRead: Boolean,
    ) {
        mutex.withLock {
            val current = _items.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(isRead = isRead)
                persistToDisk(current)
                _items.value = current
            }
        }
    }

    public suspend fun clearAll() {
        mutex.withLock {
            persistToDisk(emptyList())
            _items.value = emptyList()
        }
    }

    public fun isSaved(id: String): Flow<Boolean> = items.map { list -> list.any { it.id == id } }

    public fun getItem(id: String): ReadingListItem? = _items.value.firstOrNull { it.id == id }

    public suspend fun moveToFolder(
        id: String,
        folderId: String,
    ) {
        mutex.withLock {
            val current = _items.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(folderId = folderId)
                persistToDisk(current)
                _items.value = current
            }
        }
    }

    public suspend fun updateNote(
        id: String,
        note: String,
    ) {
        mutex.withLock {
            val current = _items.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(note = note.trim())
                persistToDisk(current)
                _items.value = current
            }
        }
    }

    public suspend fun createFolder(
        name: String,
        iconName: String = "folder",
        colorHex: Long = 0xFF6366F1L,
        createdAtTimestamp: Long = 0L,
    ): BookmarkFolder {
        val trimmed = name.trim().ifEmpty { "New Folder" }
        val slug =
            trimmed
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifEmpty { "folder" }
        return mutex.withLock {
            val current = _folders.value.toMutableList()
            val existing = current.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            if (existing != null) return@withLock existing
            var candidateId = slug
            var counter = 2
            while (current.any { it.id == candidateId }) {
                candidateId = "${slug}_$counter"
                counter++
            }
            val folder =
                BookmarkFolder(
                    id = candidateId,
                    name = trimmed,
                    iconName = iconName,
                    colorHex = colorHex,
                    createdAtTimestamp = createdAtTimestamp,
                    isDefault = false,
                )
            current.add(folder)
            persistFoldersToDisk(current)
            _folders.value = current
            folder
        }
    }

    public suspend fun updateFolder(
        folderId: String,
        name: String,
        iconName: String,
        colorHex: Long,
    ): BookmarkFolder? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return mutex.withLock {
            val current = _folders.value.toMutableList()
            val index = current.indexOfFirst { it.id == folderId }
            if (index < 0) return@withLock null
            val updated =
                current[index].copy(
                    name = trimmed,
                    iconName = iconName,
                    colorHex = colorHex,
                )
            current[index] = updated
            persistFoldersToDisk(current)
            _folders.value = current
            updated
        }
    }

    public suspend fun deleteFolder(folderId: String) {
        if (folderId == DEFAULT_BOOKMARK_FOLDER_ID) return
        mutex.withLock {
            val updatedFolders = _folders.value.filterNot { it.id == folderId }
            if (updatedFolders.size != _folders.value.size) {
                persistFoldersToDisk(updatedFolders)
                _folders.value = updatedFolders
            }
            val currentItems = _items.value
            if (currentItems.any { it.folderId == folderId }) {
                val reassigned =
                    currentItems.map { item ->
                        if (item.folderId == folderId) item.copy(folderId = DEFAULT_BOOKMARK_FOLDER_ID) else item
                    }
                persistToDisk(reassigned)
                _items.value = reassigned
            }
        }
    }

    public companion object {
        public fun defaultBookmarkFolders(): List<BookmarkFolder> =
            listOf(
                BookmarkFolder(
                    id = DEFAULT_BOOKMARK_FOLDER_ID,
                    name = "Quick Saves",
                    iconName = "bolt",
                    colorHex = 0xFF6366F1L,
                    isDefault = true,
                ),
                BookmarkFolder(
                    id = "inspiration",
                    name = "Inspiration",
                    iconName = "sparkles",
                    colorHex = 0xFFEC4899L,
                    isDefault = true,
                ),
                BookmarkFolder(
                    id = "tech_ai",
                    name = "Tech & AI",
                    iconName = "microchip",
                    colorHex = 0xFF10B981L,
                    isDefault = true,
                ),
                BookmarkFolder(
                    id = "threads",
                    name = "Threads & Reads",
                    iconName = "layer_group",
                    colorHex = 0xFFF59E0BL,
                    isDefault = true,
                ),
                BookmarkFolder(
                    id = "career",
                    name = "Career & Ideas",
                    iconName = "briefcase",
                    colorHex = 0xFF0A66C2L,
                    isDefault = true,
                ),
            )
    }
}
