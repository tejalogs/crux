package org.crux.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.WebLocalStorage
import org.crux.data.io.FileStorage

internal actual fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    @Suppress("UNUSED_PARAMETER") fileStorage: FileStorage,
): Storage<T> =
    WebLocalStorage(
        serializer = serializer,
        name = name,
    )
