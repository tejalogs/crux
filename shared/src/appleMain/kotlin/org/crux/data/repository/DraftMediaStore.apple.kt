package org.crux.data.repository

import okio.Path.Companion.toPath
import org.crux.common.FileItem
import org.crux.common.FileType

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String?,
    loader: suspend () -> ByteArray,
): FileItem {
    val filePath = path.toPath()
    return FileItem(
        name = name ?: filePath.name,
        path = path,
        type = type,
        mimeType = mimeType,
    )
}
