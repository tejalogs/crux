package org.crux.ui.screen.instagram

import org.crux.ui.screen.home.LiveWebConfig
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.ProtectionLevel
import org.crux.ui.screen.home.SiteTweak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WebScreenBackAndConfigTest {
    @Test
    fun backArrowWalksPageHistoryBeforeLeaving() {
        assertEquals(ScreenBackAction.PageBack, screenBackAction(videoShowing = false, pageCanGoBack = true, onExternalOAuthPage = false))
        assertEquals(ScreenBackAction.Leave, screenBackAction(videoShowing = false, pageCanGoBack = false, onExternalOAuthPage = false))
    }

    @Test
    fun backArrowClosesFullscreenVideoFirst() {
        assertEquals(ScreenBackAction.ExitVideo, screenBackAction(videoShowing = true, pageCanGoBack = true, onExternalOAuthPage = true))
        assertEquals(ScreenBackAction.ExitVideo, screenBackAction(videoShowing = true, pageCanGoBack = false, onExternalOAuthPage = false))
    }

    @Test
    fun backArrowStepsOutOfAnExternalSignInPageEvenWithNoHistory() {
        // A Google sign-in page opened by location.replace has no history entry to go back to,
        // but the arrow must return to the site's own sign-in page, not close the screen.
        assertEquals(ScreenBackAction.PageBack, screenBackAction(videoShowing = false, pageCanGoBack = false, onExternalOAuthPage = true))
    }

    private val instagram = LiveWebConfig.defaultsFor(LiveWebSite.Instagram)

    @Test
    fun nothingToReapplyBeforeTheFirstWebViewIsSetUp() {
        val changed = instagram.copy(blockAutoplay = !instagram.blockAutoplay)
        assertFalse(screenConfigNeedsReapply(LiveWebSite.Instagram, applied = null, current = changed))
    }

    @Test
    fun sameConfigNeedsNoReload() {
        assertFalse(screenConfigNeedsReapply(LiveWebSite.Instagram, instagram, instagram.copy()))
    }

    @Test
    fun aChangedTweakIsWrittenIntoTheBuiltPage() {
        val withoutReels = instagram.copy(tweaks = instagram.tweaks - SiteTweak.InstagramReels)
        assertTrue(screenConfigNeedsReapply(LiveWebSite.Instagram, instagram, withoutReels))
    }

    @Test
    fun autoplayAndThirdPartyCookieChangesAreWrittenIn() {
        assertTrue(screenConfigNeedsReapply(LiveWebSite.Instagram, instagram, instagram.copy(blockAutoplay = !instagram.blockAutoplay)))
        assertTrue(
            screenConfigNeedsReapply(
                LiveWebSite.Instagram,
                instagram,
                instagram.copy(blockThirdPartyCookies = !instagram.blockThirdPartyCookies),
            ),
        )
    }

    @Test
    fun hidingCountsIsWrittenIn() {
        val calm = instagram.copy(hideEngagementCounts = !instagram.hideEngagementCounts)
        assertTrue(screenConfigNeedsReapply(LiveWebSite.Instagram, instagram, calm))
    }

    @Test
    fun linkAndRequestSettingsApplyLiveWithoutAReload() {
        // Link cleaning, the reader and the request decision are read on each use.
        assertFalse(screenConfigNeedsReapply(LiveWebSite.Instagram, instagram, instagram.copy(cleanLinks = !instagram.cleanLinks)))
        assertFalse(screenConfigNeedsReapply(LiveWebSite.Instagram, instagram, instagram.copy(openInReader = !instagram.openInReader)))
        val linkedIn = LiveWebConfig.defaultsFor(LiveWebSite.LinkedIn)
        val strict = linkedIn.copy(protection = ProtectionLevel.Strict, sendGpc = false)
        assertFalse(screenConfigNeedsReapply(LiveWebSite.LinkedIn, linkedIn.copy(sendGpc = false), strict))
    }

    @Test
    fun screenConfigServesTheLatestSettings() {
        val webConfig = ScreenWebConfig(LiveWebSite.Instagram, bridgeToken = "t", initial = instagram)
        assertSame(instagram, webConfig.current)
        val changed = instagram.copy(cleanLinks = !instagram.cleanLinks)
        // No WebView set up yet: nothing to reload, but the new config is what the clients read.
        assertFalse(webConfig.update(changed))
        assertSame(changed, webConfig.current)
    }
}
