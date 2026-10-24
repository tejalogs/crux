package dev.dimension.flare.ui.presenter.home.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstagramRssDetectionTest {
    @Test
    fun extractsValidInstagramProfiles() {
        assertEquals("natgeo", extractInstagramUsername("https://instagram.com/natgeo"))
        assertEquals("natgeo", extractInstagramUsername("https://www.instagram.com/natgeo/"))
        assertEquals("photography.hub", extractInstagramUsername("http://instagram.com/photography.hub"))
        assertEquals("traveler_99", extractInstagramUsername("instagram.com/traveler_99"))
        assertEquals("traveler_99", extractInstagramUsername("  https://instagram.com/traveler_99/  "))
    }

    @Test
    fun rejectsNonProfileInstagramPaths() {
        assertNull(extractInstagramUsername("https://instagram.com/p/C_abc123/"))
        assertNull(extractInstagramUsername("https://instagram.com/reel/C_abc123/"))
        assertNull(extractInstagramUsername("https://instagram.com/reels/C_abc123/"))
        assertNull(extractInstagramUsername("https://instagram.com/stories/natgeo/12345/"))
        assertNull(extractInstagramUsername("https://instagram.com/explore"))
        assertNull(extractInstagramUsername("https://instagram.com/accounts/login"))
        assertNull(extractInstagramUsername("https://example.com/natgeo"))
    }
}
