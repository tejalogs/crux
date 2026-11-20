package org.crux.data.security

internal expect object PlatformVaultKeyProtector {
    /** Wraps [rawKey] with a hardware/OS-backed key when supported by the platform. */
    fun wrap(rawKey: ByteArray): ByteArray

    /**
     * Unwraps [storedBytes] back into the raw symmetric key.
     * Returns `Pair(rawKey, needsRewrap)` where `needsRewrap` is true when a legacy raw key should
     * be persisted in wrapped form.
     */
    fun unwrap(storedBytes: ByteArray): Pair<ByteArray, Boolean>
}
