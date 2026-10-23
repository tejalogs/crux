package dev.dimension.flare.data.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual object PlatformAesGcm {
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"

    private val secureRandom = SecureRandom()

    actual fun encrypt(key: ByteArray, plainBytes: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val secretKey = SecretKeySpec(key, KEY_ALGORITHM)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        val cipherBytes = cipher.doFinal(plainBytes)

        val result = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(cipherBytes, 0, result, iv.size, cipherBytes.size)
        return result
    }

    actual fun decrypt(key: ByteArray, cipherPayload: ByteArray): ByteArray {
        require(cipherPayload.size > GCM_IV_LENGTH_BYTES) { "Invalid cipher payload length" }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(cipherPayload, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherBytesLength = cipherPayload.size - GCM_IV_LENGTH_BYTES
        val cipherBytes = ByteArray(cipherBytesLength)
        System.arraycopy(cipherPayload, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytesLength)

        val secretKey = SecretKeySpec(key, KEY_ALGORITHM)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        return cipher.doFinal(cipherBytes)
    }
}
