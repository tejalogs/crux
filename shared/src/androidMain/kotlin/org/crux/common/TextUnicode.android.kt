package org.crux.common

import java.text.Normalizer

public actual fun String.normalizeNfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)
