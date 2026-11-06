package org.crux.data.network.rss

import java.nio.charset.Charset

internal actual fun decodeBytes(
    bytes: ByteArray,
    charset: String,
): String? =
    runCatching {
        bytes.toString(Charset.forName(charset))
    }.getOrNull()
