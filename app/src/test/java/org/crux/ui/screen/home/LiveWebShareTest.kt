package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveWebShareTest {
    @Test
    fun xLinkCarriesTextAndUrl() {
        assertEquals(
            "https://x.com/intent/post?text=Hello%20world&url=https%3A%2F%2Fa.com%2Fb",
            LiveWebSite.X.shareUrl("Hello world", "https://a.com/b"),
        )
    }

    @Test
    fun linkedInPutsTheLinkInTheText() {
        assertEquals(
            "https://www.linkedin.com/feed/?shareActive=true&text=Hi%0A%0Ahttps%3A%2F%2Fa.com",
            LiveWebSite.LinkedIn.shareUrl("Hi", "https://a.com"),
        )
    }

    @Test
    fun redditUsesFirstLineAsTitle() {
        assertEquals(
            "https://www.reddit.com/submit?title=Title&url=https%3A%2F%2Fa.com",
            LiveWebSite.Reddit.shareUrl("Title\nBody", "https://a.com"),
        )
        assertEquals(
            "https://www.reddit.com/submit?title=Only&text=Only",
            LiveWebSite.Reddit.shareUrl("Only", ""),
        )
    }

    @Test
    fun instagramHasNoShareLink() {
        assertNull(LiveWebSite.Instagram.shareUrl("Hi", "https://a.com"))
    }

    @Test
    fun liveShareSitesIncludeAllSupportedPlatforms() {
        org.junit.Assert.assertTrue(LIVE_SHARE_SITES.contains(LiveWebSite.LinkedIn))
        org.junit.Assert.assertTrue(LIVE_SHARE_SITES.contains(LiveWebSite.Instagram))
        org.junit.Assert.assertTrue(LIVE_SHARE_SITES.contains(LiveWebSite.X))
        org.junit.Assert.assertTrue(LIVE_SHARE_SITES.contains(LiveWebSite.Reddit))
    }
}
