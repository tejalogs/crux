package org.crux.data.security

internal expect object PlatformAesGcm {
    fun encrypt(
        key: ByteArray,
        plainBytes: ByteArray,
    ): ByteArray

    fun decrypt(
        key: ByteArray,
        cipherPayload: ByteArray,
    ): ByteArray
}
