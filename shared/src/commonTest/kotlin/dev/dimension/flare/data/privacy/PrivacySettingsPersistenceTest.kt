package dev.dimension.flare.data.privacy

import dev.dimension.flare.data.datastore.model.PrivacySettingsData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivacySettingsPersistenceTest {
    @Test
    fun roundTripPreservesEveryField() {
        val original =
            PrivacySettings(
                loggingEnabled = false,
                blockTelemetry = true,
                blockProfiling = true,
                scrubTrackingParameters = false,
                stripFingerprintHeaders = true,
                blockedHosts = setOf("a.example", "b.example"),
            )
        val restored = PrivacySettingsData.from(original).toPrivacySettings()
        assertEquals(original, restored)
    }

    @Test
    fun emptyDataYieldsDefaults() {
        assertEquals(PrivacySettings.Default, PrivacySettingsData().toPrivacySettings())
    }

    @Test
    fun blockedHostsAreStoredSorted() {
        // Deterministic bytes keep the protobuf stable across writes.
        val data = PrivacySettingsData.from(PrivacySettings(blockedHosts = setOf("z.example", "a.example", "m.example")))
        assertEquals(listOf("a.example", "m.example", "z.example"), data.blockedHosts)
    }

    @Test
    fun blocklistSurvivesRoundTripAndStillMatches() {
        // The property that matters: a persisted rule must still refuse the host after a restart.
        val restored =
            PrivacySettingsData
                .from(PrivacySettings(blockedHosts = setOf("tracker.example")))
                .toPrivacySettings()
        assertTrue(wouldBlock("metrics.tracker.example", restored.blockedHosts))
    }

    @Test
    fun togglesSurviveRoundTrip() {
        val restored =
            PrivacySettingsData
                .from(PrivacySettings(blockTelemetry = true, blockProfiling = true))
                .toPrivacySettings()
        assertTrue(restored.blockTelemetry)
        assertTrue(restored.blockProfiling)
    }
}
