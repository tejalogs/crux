package org.crux.common

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public actual object Locale {
    public actual val language: String
        get() = NSLocale.currentLocale.localeIdentifier.replace('_', '-')
}
