package org.crux.data.security

internal actual object PlatformVaultKeyProtector {
    actual fun wrap(rawKey: ByteArray): ByteArray = rawKey.copyOf()

    actual fun unwrap(storedBytes: ByteArray): Pair<ByteArray, Boolean> {
        require(storedBytes.size == CredentialCipher.KEY_SIZE_BYTES) {
            "Corrupt vault key length: ${storedBytes.size}"
        }
        return storedBytes.copyOf() to false
    }
}
