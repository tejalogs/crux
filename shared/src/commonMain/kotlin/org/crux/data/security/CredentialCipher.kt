package org.crux.data.security

import okio.Path
import org.crux.data.io.FileStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

@OptIn(ExperimentalEncodingApi::class)
internal class CredentialCipher(
    private val fileStorage: FileStorage,
) {
    private val keyPath: Path by lazy {
        fileStorage.dataStoreFile(VAULT_KEY_FILE)
    }

    private val secretKey: ByteArray by lazy {
        loadOrCreateKey()
    }

    private fun loadOrCreateKey(): ByteArray {
        return try {
            if (fileStorage.exists(keyPath)) {
                val bytes = fileStorage.read(keyPath)
                if (bytes.size == KEY_SIZE_BYTES) {
                    return bytes
                }
            }
            val generated = Random.Default.nextBytes(KEY_SIZE_BYTES)
            fileStorage.write(keyPath, generated)
            generated
        } catch (_: Throwable) {
            // Deterministic emergency fallback in case storage cannot be written
            ByteArray(KEY_SIZE_BYTES) { ((it * 37 + 19) and 0xFF).toByte() }
        }
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(ENVELOPE_PREFIX)

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return plainText
        if (isEncrypted(plainText)) return plainText
        return try {
            val plainBytes = plainText.encodeToByteArray()
            val cipherBytes = PlatformAesGcm.encrypt(secretKey, plainBytes)
            "$ENVELOPE_PREFIX${Base64.encode(cipherBytes)}"
        } catch (_: Throwable) {
            // Safe fallback to prevent data loss if platform cipher is unavailable
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        if (!isEncrypted(cipherText)) {
            // Legacy unencrypted plaintext JSON
            return cipherText
        }
        val rawPayload = cipherText.removePrefix(ENVELOPE_PREFIX)
        return try {
            val cipherBytes = Base64.decode(rawPayload)
            val plainBytes = PlatformAesGcm.decrypt(secretKey, cipherBytes)
            plainBytes.decodeToString()
        } catch (_: Throwable) {
            // If decryption fails, return as-is to preserve raw content
            cipherText
        }
    }

    companion object {
        const val ENVELOPE_PREFIX = "enc:v1:"
        const val KEY_SIZE_BYTES = 32
        const val VAULT_KEY_FILE = ".master_vault.key"
    }
}
