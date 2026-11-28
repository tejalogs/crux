package org.crux.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object PlatformVaultKeyProtector {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEK_ALIAS = "crux_master_vault_kek_v1"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private val MAGIC_HEADER = byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())

    private fun isRobolectric(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEK_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(
                    KEK_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun wrap(rawKey: ByteArray): ByteArray {
        if (isRobolectric()) return rawKey.copyOf()
        val kek = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES) { "Unexpected AndroidKeyStore GCM IV size: ${iv.size}" }
        val encrypted = cipher.doFinal(rawKey)
        val out = ByteArray(MAGIC_HEADER.size + iv.size + encrypted.size)
        System.arraycopy(MAGIC_HEADER, 0, out, 0, MAGIC_HEADER.size)
        System.arraycopy(iv, 0, out, MAGIC_HEADER.size, iv.size)
        System.arraycopy(encrypted, 0, out, MAGIC_HEADER.size + iv.size, encrypted.size)
        return out
    }

    fun unwrap(storedBytes: ByteArray): Pair<ByteArray, Boolean> {
        if (hasWrappedHeader(storedBytes)) {
            require(!isRobolectric()) { "Cannot unwrap AndroidKeyStore payload under Robolectric" }
            val ivOffset = MAGIC_HEADER.size
            val cipherOffset = ivOffset + GCM_IV_BYTES
            require(storedBytes.size > cipherOffset) { "Invalid wrapped vault key length" }
            val iv = storedBytes.copyOfRange(ivOffset, cipherOffset)
            val cipherBytes = storedBytes.copyOfRange(cipherOffset, storedBytes.size)
            val kek = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(cipherBytes) to false
        }
        require(storedBytes.size == CredentialCipher.KEY_SIZE_BYTES) {
            "Corrupt vault key length: ${storedBytes.size}"
        }
        return storedBytes.copyOf() to !isRobolectric()
    }

    private fun hasWrappedHeader(bytes: ByteArray): Boolean =
        bytes.size > MAGIC_HEADER.size + GCM_IV_BYTES &&
            bytes[0] == MAGIC_HEADER[0] &&
            bytes[1] == MAGIC_HEADER[1] &&
            bytes[2] == MAGIC_HEADER[2] &&
            bytes[3] == MAGIC_HEADER[3]
}
