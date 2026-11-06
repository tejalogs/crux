package org.crux.common

import java.util.Locale

public actual object Locale {
    public actual val language: String
        get() = Locale.getDefault().toLanguageTag()
}
