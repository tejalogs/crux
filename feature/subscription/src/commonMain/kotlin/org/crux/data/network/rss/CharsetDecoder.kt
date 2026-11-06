package org.crux.data.network.rss

internal expect fun decodeBytes(
    bytes: ByteArray,
    charset: String,
): String?
