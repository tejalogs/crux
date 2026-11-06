package org.crux.data.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyClassifierTest {
    @Test
    fun timelineIsContent() {
        val verdict =
            PrivacyClassifier.classify(
                method = "GET",
                host = "api.x.com",
                path = "/i/api/2/timeline/home",
            )
        assertEquals(PrivacyCategory.Content, verdict.category)
        assertFalse(verdict.blocked)
    }

    @Test
    fun userProfileIsContent() {
        val verdict =
            PrivacyClassifier.classify(
                method = "GET",
                host = "api.x.com",
                path = "/i/api/1.1/users/show.json",
            )
        assertEquals(PrivacyCategory.Content, verdict.category)
    }

    @Test
    fun impressionPingsAreTelemetry() {
        val verdict =
            PrivacyClassifier.classify(
                method = "POST",
                host = "api.x.com",
                path = "/i/api/2/impressions",
            )
        assertEquals(PrivacyCategory.Telemetry, verdict.category)
    }

    @Test
    fun clientEventsAreTelemetry() {
        val verdict =
            PrivacyClassifier.classify(
                method = "POST",
                host = "api.x.com",
                path = "/i/api/1.1/jot/client_event",
            )
        assertEquals(PrivacyCategory.Telemetry, verdict.category)
    }

    @Test
    fun adaptiveSearchIsProfiling() {
        val verdict =
            PrivacyClassifier.classify(
                method = "GET",
                host = "api.x.com",
                path = "/i/api/1.1/search/adaptive.json",
            )
        assertEquals(PrivacyCategory.Profiling, verdict.category)
    }

    @Test
    fun loginIsAuth() {
        val verdict =
            PrivacyClassifier.classify(
                method = "POST",
                host = "api.x.com",
                path = "/i/api/1.1/account/login.json",
            )
        assertEquals(PrivacyCategory.Auth, verdict.category)
    }

    @Test
    fun mediaIsMedia() {
        val verdict =
            PrivacyClassifier.classify(
                method = "GET",
                host = "pbs.twimg.com",
                path = "/media/abc123.jpg",
            )
        assertEquals(PrivacyCategory.Media, verdict.category)
    }

    @Test
    fun uploadsAreMediaNotBlockedByMediaRules() {
        val verdict =
            PrivacyClassifier.classify(
                method = "POST",
                host = "upload.x.com",
                path = "/i/media/upload.json",
            )
        assertEquals(PrivacyCategory.Media, verdict.category)
    }

    @Test
    fun blockingTelemetryOnlyBlocksTelemetry() {
        val verdict =
            PrivacyClassifier.classify(
                method = "POST",
                host = "api.x.com",
                path = "/i/api/2/impressions",
                blockTelemetry = true,
            )
        assertTrue(verdict.blocked)
    }

    @Test
    fun blockingTelemetryLeavesContentAlone() {
        val verdict =
            PrivacyClassifier.classify(
                method = "GET",
                host = "api.x.com",
                path = "/i/api/2/timeline/home",
                blockTelemetry = true,
            )
        assertFalse(verdict.blocked)
        assertEquals(PrivacyCategory.Content, verdict.category)
    }

    @Test
    fun blockingProfilingLeavesTelemetryAlone() {
        val verdict =
            PrivacyClassifier.classify(
                method = "POST",
                host = "api.x.com",
                path = "/i/api/2/impressions",
                blockProfiling = true,
            )
        assertFalse(verdict.blocked)
    }

    @Test
    fun unknownPathDefaultsToContentNotDropped() {
        // Conservative: an unrecognised route is kept, because dropping a route the app needs is
        // far worse than letting one telemetry call through.
        val verdict =
            PrivacyClassifier.classify(
                method = "GET",
                host = "example.social",
                path = "/some/new/endpoint",
            )
        assertEquals(PrivacyCategory.Content, verdict.category)
        assertFalse(verdict.blocked)
    }

    @Test
    fun hostMatchIsSuffixAware() {
        val blocked = setOf("example.com")
        assertTrue(PrivacyClassifier.hostMatches("example.com", blocked))
        assertTrue(PrivacyClassifier.hostMatches("metrics.example.com", blocked))
        assertTrue(PrivacyClassifier.hostMatches("a.b.example.com", blocked))
    }

    @Test
    fun hostMatchIsNotSubstring() {
        val blocked = setOf("example.com")
        assertFalse(PrivacyClassifier.hostMatches("notexample.com", blocked))
        assertFalse(PrivacyClassifier.hostMatches("example.com.evil.net", blocked))
    }

    @Test
    fun hostMatchHandlesCaseAndLeadingDot() {
        assertTrue(PrivacyClassifier.hostMatches("API.Example.COM", setOf(".example.com")))
    }

    @Test
    fun emptyBlocklistBlocksNothing() {
        assertFalse(PrivacyClassifier.hostMatches("example.com", emptySet()))
    }
}

class TrackingParametersTest {
    @Test
    fun knownParametersAreStripped() {
        assertTrue(TrackingParameters.shouldStrip("twclid"))
        assertTrue(TrackingParameters.shouldStrip("fbclid"))
        assertTrue(TrackingParameters.shouldStrip("utm_source"))
        assertTrue(TrackingParameters.shouldStrip("si"))
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertTrue(TrackingParameters.shouldStrip("TWCLID"))
    }

    @Test
    fun legitimateParametersSurvive() {
        assertFalse(TrackingParameters.shouldStrip("cursor"))
        assertFalse(TrackingParameters.shouldStrip("count"))
        assertFalse(TrackingParameters.shouldStrip("max_id"))
        assertFalse(TrackingParameters.shouldStrip("q"))
    }

    @Test
    fun substringLookalikesAreNotStripped() {
        // Substring matching would destroy these.
        assertFalse(TrackingParameters.shouldStrip("refund_id"))
        assertFalse(TrackingParameters.shouldStrip("source_language"))
        assertFalse(TrackingParameters.shouldStrip("gclid_extra"))
    }
}
