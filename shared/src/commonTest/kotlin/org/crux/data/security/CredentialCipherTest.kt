package org.crux.data.security

import org.crux.data.io.InMemoryFileStorage
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
