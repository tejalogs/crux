package org.crux.data.repository

import org.crux.common.FileItem
import org.crux.common.FileType

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
        mimeType = mimeType,
        loader = loader,
    )
