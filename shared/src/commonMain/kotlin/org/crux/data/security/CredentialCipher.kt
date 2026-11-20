package org.crux.data.security

import okio.Path
import org.crux.data.io.FileStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
        if (fileStorage.exists(keyPath)) {
            val stored = fileStorage.read(keyPath)
            val (rawKey, needsRewrap) = PlatformVaultKeyProtector.unwrap(stored)
            require(rawKey.size == KEY_SIZE_BYTES) { "Invalid unwrapped vault key length: ${rawKey.size}" }
            if (needsRewrap) {
                fileStorage.write(keyPath, PlatformVaultKeyProtector.wrap(rawKey))
            }
            return rawKey
        }
        val generated = PlatformAesGcm.randomBytes(KEY_SIZE_BYTES)
        fileStorage.write(keyPath, PlatformVaultKeyProtector.wrap(generated))
        return generated
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(ENVELOPE_PREFIX)

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return plainText
        if (isEncrypted(plainText)) return plainText
        val plainBytes = plainText.encodeToByteArray()
        val cipherBytes = PlatformAesGcm.encrypt(secretKey, plainBytes)
        return "$ENVELOPE_PREFIX${Base64.encode(cipherBytes)}"
    }

    fun decrypt(cipherText: String): String {
        if (!isEncrypted(cipherText)) {
            // Legacy unencrypted plaintext value
            return cipherText
        }
        val rawPayload = cipherText.removePrefix(ENVELOPE_PREFIX)
        val cipherBytes = Base64.decode(rawPayload)
        val plainBytes = PlatformAesGcm.decrypt(secretKey, cipherBytes)
        return plainBytes.decodeToString()
    }

    companion object {
        const val ENVELOPE_PREFIX = "enc:v1:"
        const val KEY_SIZE_BYTES = 32
        const val VAULT_KEY_FILE = ".master_vault.key"
    }
}
