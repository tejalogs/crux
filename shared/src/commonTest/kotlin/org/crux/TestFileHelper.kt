package org.crux

import okio.FileSystem
import okio.Path
import org.crux.common.FileItem
import org.crux.common.FileType

internal expect fun createTestFileSystem(): FileSystem

internal expect fun createTestRootPath(): Path

internal expect fun deleteTestRootPath(path: Path)

internal expect fun createTestFileItem(
    root: Path,
    name: String?,
    bytes: ByteArray,
    type: FileType,
): FileItem
