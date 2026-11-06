package org.crux

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.crux.common.FileItem
import org.crux.common.FileType
import java.io.File
import java.nio.file.Files
import kotlin.uuid.Uuid

internal actual fun createTestRootPath(): Path = Files.createTempDirectory("draft-media-store-test").toString().toPath()

internal actual fun deleteTestRootPath(path: Path) {
    File(path.toString()).deleteRecursively()
}

internal actual fun createTestFileSystem(): FileSystem = FileSystem.SYSTEM

internal actual fun createTestFileItem(
    root: Path,
    name: String?,
    bytes: ByteArray,
    type: FileType,
): FileItem {
    val file = File(root.toString(), "source_${Uuid.random()}.bin")
    file.parentFile?.mkdirs()
    file.writeBytes(bytes)
    return FileItem(file, name = name, type = type)
}
