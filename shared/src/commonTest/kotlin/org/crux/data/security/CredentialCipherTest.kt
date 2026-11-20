package org.crux.data.security

import okio.Buffer
import org.crux.common.protobufSerializer
import org.crux.data.datastore.EncryptedAppSettingsSerializer
import org.crux.data.datastore.model.AppSettings
import org.crux.data.io.InMemoryFileStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CredentialCipherTest {
    private val fileStorage = InMemoryFileStorage()

    @Test
    fun encryptAndDecryptRoundTrip() {
        val cipher = CredentialCipher(fileStorage)
        val originalJson = """{"accessToken":"secret_token_12345","refreshToken":"refresh_98765","host":"example.social"}"""

        val encrypted = cipher.encrypt(originalJson)
        assertTrue(cipher.isEncrypted(encrypted))
        assertTrue(encrypted.startsWith("enc:v1:"))
        assertNotEquals(originalJson, encrypted)

        val decrypted = cipher.decrypt(encrypted)
        assertEquals(originalJson, decrypted)
    }

    @Test
    fun legacyUnencryptedJsonPassesThroughUntouched() {
        val cipher = CredentialCipher(fileStorage)
        val legacyJson = """{"token":"kept","instance":"mastodon.social"}"""

        assertFalse(cipher.isEncrypted(legacyJson))
        val decrypted = cipher.decrypt(legacyJson)
        assertEquals(legacyJson, decrypted)
    }

    @Test
    fun encryptIsIdempotent() {
        val cipher = CredentialCipher(fileStorage)
        val plain = """{"key":"value"}"""

        val encryptedOnce = cipher.encrypt(plain)
        val encryptedTwice = cipher.encrypt(encryptedOnce)
        assertEquals(encryptedOnce, encryptedTwice)
    }

    @Test
    fun blankOrEmptyStringsArePreserved() {
        val cipher = CredentialCipher(fileStorage)

        assertEquals("", cipher.encrypt(""))
        assertEquals("   ", cipher.encrypt("   "))
        assertEquals("", cipher.decrypt(""))
    }

    @Test
    fun keyIsPersistedAcrossCipherInstances() {
        val cipherA = CredentialCipher(fileStorage)
        val cipherB = CredentialCipher(fileStorage)

        val secret = """{"session":"active_xyz_99"}"""
        val encryptedByA = cipherA.encrypt(secret)
        val decryptedByB = cipherB.decrypt(encryptedByA)

        assertEquals(secret, decryptedByB)
    }

    @Test
    fun corruptCiphertextFailsClosed() {
        val cipher = CredentialCipher(fileStorage)
        assertFailsWith<Throwable> {
            cipher.decrypt("enc:v1:not_valid_ciphertext_payload")
        }
    }

    @Test
    fun appSettingsSerializerEncryptsApiKeysAtRestAndDecryptsOnRead() =
        runTest {
            val cipher = CredentialCipher(fileStorage)
            val delegate = protobufSerializer(AppSettings(version = ""))
            val serializer = EncryptedAppSettingsSerializer(delegate, cipher)

            val original =
                AppSettings(
                    version = "1.0.8",
                    aiConfig =
                        AppSettings.AiConfig(
                            type =
                                AppSettings.AiConfig.Type.OpenAI(
                                    serverUrl = "https://api.openai.com/v1",
                                    apiKey = "sk-live-secret-openai-key",
                                    model = "gpt-4o-mini",
                                ),
                        ),
                    translateConfig =
                        AppSettings.TranslateConfig(
                            provider = AppSettings.TranslateConfig.Provider.DeepL(apiKey = "deepl-secret-token"),
                        ),
                )

            val rawBuffer = Buffer()
            serializer.writeTo(original, rawBuffer)
            val rawBytes = rawBuffer.readByteArray()

            // Verify plaintext API keys do NOT appear on disk and delegate reads encrypted envelopes.
            val rawText = rawBytes.decodeToString()
            assertFalse(rawText.contains("sk-live-secret-openai-key"))
            assertFalse(rawText.contains("deepl-secret-token"))

            val onDisk = delegate.readFrom(Buffer().write(rawBytes))
            assertTrue(cipher.isEncrypted((onDisk.aiConfig.type as AppSettings.AiConfig.Type.OpenAI).apiKey))
            assertTrue(cipher.isEncrypted((onDisk.translateConfig.provider as AppSettings.TranslateConfig.Provider.DeepL).apiKey))

            // Verify reading through EncryptedAppSettingsSerializer restores original plaintext API keys.
            val restored = serializer.readFrom(Buffer().write(rawBytes))
            assertEquals("sk-live-secret-openai-key", (restored.aiConfig.type as AppSettings.AiConfig.Type.OpenAI).apiKey)
            assertEquals("deepl-secret-token", (restored.translateConfig.provider as AppSettings.TranslateConfig.Provider.DeepL).apiKey)
        }
}
