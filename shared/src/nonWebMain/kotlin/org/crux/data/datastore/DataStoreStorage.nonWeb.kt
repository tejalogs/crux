package org.crux.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import org.crux.data.io.FileStorage
import org.crux.data.io.OkioFileStorage

internal actual fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    fileStorage: FileStorage,
): Storage<T> {
    val okioFileStorage =
        requireNotNull(fileStorage as? OkioFileStorage) {
            "Non-web DataStore storage requires OkioFileStorage"
        }
    return OkioStorage(
        fileSystem = okioFileStorage.fileSystem,
        serializer = serializer,
        producePath = {
            fileStorage.dataStoreFile(name)
        },
    )
}
