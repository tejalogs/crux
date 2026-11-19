package org.crux.ui.screen.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePathTest {
    @Test
    fun reelsAndStoriesAreImmersive() {
        assertTrue(isImmersivePath("https://www.instagram.com/reels/C1abc/"))
        assertTrue(isImmersivePath("https://www.instagram.com/reels"))
        assertTrue(isImmersivePath("https://www.instagram.com/reel/C1abc/?igsh=x"))
        assertTrue(isImmersivePath("https://www.instagram.com/natgeo/reel/C1abc/"))
        assertTrue(isImmersivePath("https://www.instagram.com/stories/someone/123/"))
        assertTrue(isImmersivePath("https://www.instagram.com/direct/t/12345/"))
        assertTrue(isImmersivePath("https://www.youtube.com/shorts/abc123xyz"))
    }

    @Test
    fun feedsAndProfilesKeepPullToRefresh() {
        assertFalse(isImmersivePath("https://www.instagram.com/"))
        assertFalse(isImmersivePath("https://www.instagram.com/reelsfan/"))
        assertFalse(isImmersivePath("https://www.instagram.com/natgeo/"))
        assertFalse(isImmersivePath("https://www.instagram.com/p/C1abc/"))
        assertFalse(isImmersivePath("https://www.reddit.com/r/android/"))
        assertFalse(isImmersivePath(null))
        assertFalse(isImmersivePath("not a url %%"))
    }

    @Test
    fun pageScansWaitForScrollingAndSkipShadowWalksOffReddit() {
        val script = requireNotNull(buildSiteScript(LiveWebSite.Instagram, setOf(SiteTweak.InstagramAppPrompts)))
        assertTrue(script.contains("SCROLL_IDLE_MS"))
        assertTrue(script.contains("if(hasShadowDom)walkShadowRoots(document)"))
    }
}
