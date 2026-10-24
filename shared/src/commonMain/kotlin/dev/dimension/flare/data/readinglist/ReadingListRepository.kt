package dev.dimension.flare.data.readinglist

import dev.dimension.flare.data.io.FileStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Single
public class ReadingListRepository internal constructor(
    private val fileStorage: FileStorage,
) {
    private val mutex = Mutex()
    private val filePath by lazy { fileStorage.dataStoreFile("reading_list.json") }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    private val _items = MutableStateFlow<List<ReadingListItem>>(loadFromDisk())
    public val items: StateFlow<List<ReadingListItem>> = _items.asStateFlow()

    private fun loadFromDisk(): List<ReadingListItem> =
        runCatching {
            if (fileStorage.exists(filePath)) {
                val bytes = fileStorage.read(filePath)
                json.decodeFromString<List<ReadingListItem>>(bytes.decodeToString())
            } else {
                emptyList()
            }
        }.getOrElse { emptyList() }

    private fun persistToDisk(list: List<ReadingListItem>) {
        runCatching {
            val bytes = json.encodeToString(list).encodeToByteArray()
            fileStorage.write(filePath, bytes)
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

    public suspend fun markAsRead(id: String, isRead: Boolean) {
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

    public fun isSaved(id: String): Flow<Boolean> =
        items.map { list -> list.any { it.id == id } }

    public fun getItem(id: String): ReadingListItem? =
        _items.value.firstOrNull { it.id == id }
}
