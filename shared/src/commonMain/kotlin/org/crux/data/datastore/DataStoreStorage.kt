package org.crux.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import org.crux.data.io.FileStorage

internal expect fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    fileStorage: FileStorage,
): Storage<T>
