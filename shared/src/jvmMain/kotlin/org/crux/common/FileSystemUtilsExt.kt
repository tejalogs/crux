package org.crux.common

import org.apache.commons.lang3.SystemUtils
import java.io.File

public object FileSystemUtilsExt {
    public fun cruxDirectory(): File =
        File(SystemUtils.getUserHome(), ".crux").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

    public fun cruxCacheDirectory(): File =
        File(SystemUtils.getJavaIoTmpDir(), ".crux").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

    @Deprecated("Use cruxDirectory()", ReplaceWith("cruxDirectory()"))
    public fun flareDirectory(): File = cruxDirectory()

    @Deprecated("Use cruxCacheDirectory()", ReplaceWith("cruxCacheDirectory()"))
    public fun flareCacheDirectory(): File = cruxCacheDirectory()
}
