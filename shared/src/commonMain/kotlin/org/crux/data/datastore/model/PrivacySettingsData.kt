package org.crux.data.datastore.model

import kotlinx.serialization.Serializable
import org.crux.data.privacy.PrivacySettings

/**
 * Persisted form of [PrivacySettings].
 *
 * A security control that resets on process death is worse than no control: the user believes a
 * tracker is blocked while every request is flowing again. Blocklist and toggle state therefore
 * has to survive restarts, unlike the traffic log, which is deliberately transient.
 */
@Serializable
internal data class PrivacySettingsData(
    val loggingEnabled: Boolean = PrivacySettings.Default.loggingEnabled,
    val blockTelemetry: Boolean = PrivacySettings.Default.blockTelemetry,
    val blockProfiling: Boolean = PrivacySettings.Default.blockProfiling,
    val scrubTrackingParameters: Boolean = PrivacySettings.Default.scrubTrackingParameters,
    val stripFingerprintHeaders: Boolean = PrivacySettings.Default.stripFingerprintHeaders,
    val requireAppLock: Boolean = PrivacySettings.Default.requireAppLock,
    /**
     * Stored as a sorted list rather than a set: protobuf maps need explicit key/value wrappers,
     * and a stable order keeps the encoded bytes deterministic.
     */
    val blockedHosts: List<String> = emptyList(),
) {
    fun toPrivacySettings(): PrivacySettings =
        PrivacySettings(
            loggingEnabled = loggingEnabled,
            blockTelemetry = blockTelemetry,
            blockProfiling = blockProfiling,
            scrubTrackingParameters = scrubTrackingParameters,
            stripFingerprintHeaders = stripFingerprintHeaders,
            requireAppLock = requireAppLock,
            blockedHosts = blockedHosts.toSet(),
        )

    companion object {
        fun from(settings: PrivacySettings): PrivacySettingsData =
            PrivacySettingsData(
                loggingEnabled = settings.loggingEnabled,
                blockTelemetry = settings.blockTelemetry,
                blockProfiling = settings.blockProfiling,
                scrubTrackingParameters = settings.scrubTrackingParameters,
                stripFingerprintHeaders = settings.stripFingerprintHeaders,
                requireAppLock = settings.requireAppLock,
                blockedHosts = settings.blockedHosts.sorted(),
            )
    }
}
