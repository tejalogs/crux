package dev.dimension.flare.data.privacy

import kotlinx.serialization.Serializable

/**
 * User-controlled privacy policy.
 *
 * Defaults are chosen so the feature is useful immediately without changing what the app can do:
 * telemetry and profiling are *reported* rather than blocked, and the user opts into blocking.
 * Aggressive defaults that silently break functionality are a worse outcome than an honest log.
 */
public data class PrivacySettings(
    /** Record every request in the on-device traffic log. */
    val loggingEnabled: Boolean = true,
    /** Refuse measurement endpoints instead of sending them. */
    val blockTelemetry: Boolean = false,
    /** Refuse discovery/autocomplete endpoints instead of sending them. */
    val blockProfiling: Boolean = false,
    /** Remove known tracking query parameters from outbound URLs. */
    val scrubTrackingParameters: Boolean = true,
    /** Hosts to refuse entirely. Suffix-matched, so `example.com` covers `metrics.example.com`. */
    val blockedHosts: Set<String> = emptySet(),
    /**
     * Strip client-fingerprint headers.
     *
     * Off by default, and not supported on every network: on X these headers are load-bearing
     * (the client-transaction signature is verified against them) and removing them makes every
     * request fail rather than merely anonymous.
     */
    val stripFingerprintHeaders: Boolean = false,
    /** Require device biometric or credential unlock before displaying feeds on launch. */
    val requireAppLock: Boolean = false,
) {
    public companion object {
        public val Default: PrivacySettings = PrivacySettings()
    }
}

/** Serializable mirror of [PrivacySettings], used by the datastore. */
@Serializable
internal data class PrivacySettingsSurrogate(
    val loggingEnabled: Boolean,
    val blockTelemetry: Boolean,
    val blockProfiling: Boolean,
    val scrubTrackingParameters: Boolean,
    val stripFingerprintHeaders: Boolean,
    val blockedHosts: List<String>,
    val requireAppLock: Boolean = false,
)
