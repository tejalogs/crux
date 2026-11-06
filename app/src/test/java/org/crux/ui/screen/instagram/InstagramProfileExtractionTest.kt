package org.crux.ui.screen.instagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class InstagramProfileExtractionTest {
    @Test
    fun extractsValidUsernamesFromInstagramUrls() {
        assertEquals("natgeo", extractInstagramProfile("https://www.instagram.com/natgeo"))
        assertEquals("natgeo", extractInstagramProfile("https://www.instagram.com/natgeo/"))
        assertEquals("photography.hub", extractInstagramProfile("https://instagram.com/photography.hub/"))
        assertEquals("user_123", extractInstagramProfile("http://instagram.com/user_123"))
    }

    @Test
    fun rejectsNonProfilePathsAndSpecialPages() {
        assertNull(extractInstagramProfile("https://www.instagram.com/explore/"))
        assertNull(extractInstagramProfile("https://www.instagram.com/direct/inbox/"))
        assertNull(extractInstagramProfile("https://www.instagram.com/accounts/login/"))
        assertNull(extractInstagramProfile("https://www.instagram.com/stories/natgeo/12345/"))
        assertNull(extractInstagramProfile("https://www.instagram.com/p/DF2145X/"))
        assertNull(extractInstagramProfile("https://www.instagram.com/reel/DF2145X/"))
        assertNull(extractInstagramProfile("https://www.instagram.com/reels/DF2145X/"))
        assertNull(extractInstagramProfile("https://www.instagram.com/developer/"))
        assertNull(extractInstagramProfile("https://www.instagram.com/about/"))
    }

    @Test
    fun rejectsNonInstagramUrlsAndInvalidFormats() {
        assertNull(extractInstagramProfile("https://facebook.com/natgeo"))
        assertNull(extractInstagramProfile("https://threads.net/@natgeo"))
        assertNull(extractInstagramProfile("not-a-valid-url"))
        assertNull(extractInstagramProfile("https://www.instagram.com/"))
        assertNull(extractInstagramProfile(""))
    }

    @Test
    fun identifiesAdAndTelemetryUrls() {
        // Third-party trackers are identified
        assertTrue(isAdOrTrackingUrl("https://www.google-analytics.com/analytics.js"))
        assertTrue(isAdOrTrackingUrl("https://www.googletagmanager.com/gtm.js"))
        assertTrue(isAdOrTrackingUrl("https://adservice.google.com/adsid/integrator.js"))
        assertTrue(isAdOrTrackingUrl("https://stats.g.doubleclick.net/r/collect"))
        assertTrue(isAdOrTrackingUrl("https://app.adjust.com/event"))
        assertTrue(isAdOrTrackingUrl("https://api2.branch.io/v1/open"))
        assertTrue(isAdOrTrackingUrl("https://launches.appsflyer.com/api/v2"))

        // First-party Instagram/Meta endpoints and regular content must NOT be blocked
        assertFalse(isAdOrTrackingUrl("https://www.instagram.com/ajax/bz?__a=1"))
        assertFalse(isAdOrTrackingUrl("https://www.instagram.com/logging/client_events"))
        assertFalse(isAdOrTrackingUrl("https://www.instagram.com/"))
        assertFalse(isAdOrTrackingUrl("https://www.instagram.com/natgeo/"))
        assertFalse(isAdOrTrackingUrl("https://static.cdninstagram.com/rsrc.php/v4/yN/r/feed.js"))
        assertFalse(isAdOrTrackingUrl("https://instagram.fbcdn.net/v/t51.2885-15/photo.jpg?_nc_cat=1"))
        assertFalse(isAdOrTrackingUrl("https://instagram.fbcdn.net/v/t51.82787-15/photo.jpg?_nc_ad=z-m"))
        assertFalse(isAdOrTrackingUrl("https://www.instagram.com/api/v1/feed/timeline/"))
    }
}
