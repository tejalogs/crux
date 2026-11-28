package org.crux.common

import org.crux.BuildConfig as AndroidBuildConfig

internal object BuildConfig {
    val versionName: String
        get() = AndroidBuildConfig.VERSION_NAME

    val debug: Boolean
        get() = AndroidBuildConfig.DEBUG
}
