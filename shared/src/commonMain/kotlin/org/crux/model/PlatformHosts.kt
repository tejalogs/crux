package org.crux.model

import kotlin.io.encoding.Base64
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public val xqtOldHost: String =
    buildString {
        append(Base64.decode("dHc=").decodeToString())
        append(Base64.decode("aXR0").decodeToString())
        append(Base64.decode("ZXI=").decodeToString())
        append(Base64.decode("LmNvbQ==").decodeToString())
    }

@HiddenFromObjC
public val xqtHost: String =
    buildString {
        append("x")
        append(".com")
    }

@HiddenFromObjC
public val ilink: String =
    buildString {
        append(Base64.decode("aHR0cHM6Ly90").decodeToString())
        append(Base64.decode("Lm1lLytWWjYzZnFOUQ==").decodeToString())
        append(Base64.decode("WElBME16Vmw=").decodeToString())
    }

@HiddenFromObjC
@Suppress("ktlint:standard:property-naming")
public const val instagramHost: String = "instagram.com"
