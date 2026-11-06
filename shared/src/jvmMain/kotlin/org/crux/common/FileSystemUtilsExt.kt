package org.crux.common

import org.apache.commons.lang3.SystemUtils
import java.io.File

public object FileSystemUtilsExt {
    public fun flareDirectory(): File =
        File(SystemUtils.getUserHome(), ".crux").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

    public fun flareCacheDirectory(): File =
        File(SystemUtils.getJavaIoTmpDir(), ".crux").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
}
