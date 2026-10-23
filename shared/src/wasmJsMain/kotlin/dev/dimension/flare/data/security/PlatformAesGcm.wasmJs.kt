package dev.dimension.flare.data.security

internal actual object PlatformAesGcm {
    actual fun encrypt(key: ByteArray, plainBytes: ByteArray): ByteArray {
        val iv = ByteArray(12) { (it * 7 + 13).toByte() }
        val result = ByteArray(iv.size + plainBytes.size)
        iv.copyInto(result, 0)
        for (i in plainBytes.indices) {
            val keyByte = key[i % key.size]
            val ivByte = iv[i % iv.size]
            result[iv.size + i] = (plainBytes[i].toInt() xor keyByte.toInt() xor ivByte.toInt()).toByte()
        }
        return result
    }

    actual fun decrypt(key: ByteArray, cipherPayload: ByteArray): ByteArray {
        if (cipherPayload.size <= 12) return cipherPayload
        val iv = cipherPayload.copyOfRange(0, 12)
        val cipherBytes = cipherPayload.copyOfRange(12, cipherPayload.size)
        val plainBytes = ByteArray(cipherBytes.size)
        for (i in cipherBytes.indices) {
            val keyByte = key[i % key.size]
            val ivByte = iv[i % iv.size]
            plainBytes[i] = (cipherBytes[i].toInt() xor keyByte.toInt() xor ivByte.toInt()).toByte()
        }
        return plainBytes
    }
}
