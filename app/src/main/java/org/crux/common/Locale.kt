package org.crux.common

import java.util.Locale

internal fun currentLocale(): String = Locale.getDefault().toLanguageTag()

internal fun currentLanguage(): String = Locale.getDefault().language
