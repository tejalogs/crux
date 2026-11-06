package org.crux.data.repository

import org.crux.common.FileItem
import org.crux.common.FileType
import java.io.File

internal actual fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String?,
    loader: suspend () -> ByteArray,
): FileItem =
    FileItem(
        name = name,
        type = type,
        file = File(path),
    )
