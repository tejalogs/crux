package dev.dimension.flare.ui.screen.instagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
