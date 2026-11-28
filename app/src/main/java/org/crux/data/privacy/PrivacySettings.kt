package org.crux.data.privacy

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
    /** Retired: no longer shown or applied. Kept so stored settings keep their layout. */
    val blockTelemetry: Boolean = false,
    /** Retired: no longer shown or applied. Kept so stored settings keep their layout. */
    val blockProfiling: Boolean = false,
    /** Remove known tracking query parameters from outbound URLs. */
    val scrubTrackingParameters: Boolean = true,
    /** Hosts to refuse entirely. Suffix-matched, so `example.com` covers `metrics.example.com`. */
    val blockedHosts: Set<String> = emptySet(),
    /** Retired: no longer shown or applied. Kept so stored settings keep their layout. */
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
