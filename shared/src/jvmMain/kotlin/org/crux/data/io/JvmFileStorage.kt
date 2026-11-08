package org.crux.data.io

import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import org.crux.common.FileSystemUtilsExt
import org.koin.core.annotation.Single

@Single(binds = [FileStorage::class])
internal class JvmFileStorage :
    OkioFileStorage(
        fileSystem = FileSystem.SYSTEM,
        root = FileSystemUtilsExt.cruxDirectory().toOkioPath(),
    )
