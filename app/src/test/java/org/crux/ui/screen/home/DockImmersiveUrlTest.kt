package org.crux.ui.screen.home

import androidx.compose.ui.unit.dp
import org.crux.ui.component.BottomBarAccessory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockImmersiveUrlTest {
    @Test
    fun instagramReelsAndStoriesAreMedia() {
        assertEquals(DockImmersiveKind.Media, dockImmersiveKind("https://www.instagram.com/reels/C1abc/"))
        assertEquals(DockImmersiveKind.Media, dockImmersiveKind("https://www.instagram.com/reel/C1abc/?igsh=x"))
        assertEquals(DockImmersiveKind.Media, dockImmersiveKind("https://www.instagram.com/natgeo/reel/C1abc/"))
        assertEquals(DockImmersiveKind.Media, dockImmersiveKind("https://www.instagram.com/stories/someone/123/"))
    }

    @Test
    fun instagramXAndLinkedInMessagesAreMessages() {
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://www.instagram.com/direct/inbox/"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://www.instagram.com/direct/t/12345/"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://x.com/messages"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://x.com/messages/123-456"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://x.com/i/chat/123"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://x.com/i/messages"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://mobile.twitter.com/messages"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://www.linkedin.com/messaging/thread/2-abc/"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://www.linkedin.com/messaging"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://www.reddit.com/chat/channel/abc"))
        assertEquals(DockImmersiveKind.Messages, dockImmersiveKind("https://chat.reddit.com/room/123"))
        assertEquals(DockImmersiveKind.Media, dockImmersiveKind("https://x.com/nasa/status/12345/photo/1"))
        assertEquals(DockImmersiveKind.Media, dockImmersiveKind("https://x.com/i/web/status/12345/video/1"))
    }

    @Test
    fun feedsProfilesAndOtherSitesKeepTheDock() {
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/"))
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/reelsfan/"))
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/natgeo/reels/"))
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/p/C1abc/"))
        assertFalse(isDockImmersiveUrl("https://x.com/home"))
        assertFalse(isDockImmersiveUrl("https://x.com/someone/status/1"))
        assertFalse(isDockImmersiveUrl("https://x.com/i/bookmarks"))
        assertFalse(isDockImmersiveUrl("https://www.linkedin.com/feed/"))
        assertFalse(isDockImmersiveUrl("https://www.linkedin.com/in/chat/"))
        assertFalse(isDockImmersiveUrl("https://www.reddit.com/r/chat/"))
        // Scoped to the sites whose own bottom bar would stack under the dock.
        assertFalse(isDockImmersiveUrl("https://www.youtube.com/shorts/abc123xyz"))
        assertFalse(isDockImmersiveUrl("https://notinstagram.com/reels/A/"))
    }

    @Test
    fun profilesNamedLikeMessagePagesKeepTheDock() {
        // Real handles on X, not its message pages.
        assertFalse(isDockImmersiveUrl("https://x.com/chat"))
        assertFalse(isDockImmersiveUrl("https://x.com/direct"))
        assertFalse(isDockImmersiveUrl("https://x.com/message"))
        assertFalse(isDockImmersiveUrl("https://x.com/messaging"))
        assertFalse(isDockImmersiveUrl("https://x.com/conversations"))
        // Instagram accounts with those names.
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/shorts/"))
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/chat/"))
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/messages/"))
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/messaging/"))
        assertFalse(isDockImmersiveUrl("https://www.instagram.com/conversations/"))
        // LinkedIn's own pages that are not messaging.
        assertFalse(isDockImmersiveUrl("https://www.linkedin.com/messages/"))
        assertFalse(isDockImmersiveUrl("https://www.linkedin.com/direct/"))
    }

    @Test
    fun badInputKeepsTheDock() {
        assertNull(dockImmersiveKind(null))
        assertFalse(isDockImmersiveUrl(null))
        assertFalse(isDockImmersiveUrl(""))
        assertFalse(isDockImmersiveUrl("not a url %%"))
        assertFalse(isDockImmersiveUrl("javascript:alert(1)//instagram.com/reels/"))
        assertFalse(isDockImmersiveUrl("file:///www.instagram.com/reels/A/"))
    }

    @Test
    fun otherPagesKeepTheScaffoldPadding() {
        assertEquals(
            120.dp,
            liveTabBottomPadding(scaffoldBottom = 120.dp, navigationBarBottom = 24.dp, kind = null, dockPeeked = false, handleReserve = 20.dp),
        )
    }

    @Test
    fun aHiddenDockReservesTheHandleStripBelowThePage() {
        // Navigation bar plus the handle, so the handle sits under the page and not on the site's bar.
        assertEquals(
            44.dp,
            liveTabBottomPadding(
                scaffoldBottom = 120.dp,
                navigationBarBottom = 24.dp,
                kind = DockImmersiveKind.Media,
                dockPeeked = false,
                handleReserve = IMMERSIVE_HANDLE_TOUCH_HEIGHT,
            ),
        )
        assertEquals(
            44.dp,
            liveTabBottomPadding(
                scaffoldBottom = 120.dp,
                navigationBarBottom = 24.dp,
                kind = DockImmersiveKind.Messages,
                dockPeeked = false,
                handleReserve = IMMERSIVE_HANDLE_TOUCH_HEIGHT,
            ),
        )
        // The keyboard hides the handle, so nothing is reserved while typing.
        assertEquals(
            24.dp,
            liveTabBottomPadding(
                scaffoldBottom = 48.dp,
                navigationBarBottom = 24.dp,
                kind = DockImmersiveKind.Messages,
                dockPeeked = false,
                handleReserve = 0.dp,
            ),
        )
    }

    @Test
    fun aBroughtBackDockFloatsOverMediaButPushesMessagesUp() {
        // Reels and Stories: same size as while hidden, whatever the scaffold padding is now.
        val hidden =
            liveTabBottomPadding(scaffoldBottom = 48.dp, navigationBarBottom = 24.dp, kind = DockImmersiveKind.Media, dockPeeked = false, handleReserve = 20.dp)
        val peeked =
            liveTabBottomPadding(scaffoldBottom = 120.dp, navigationBarBottom = 24.dp, kind = DockImmersiveKind.Media, dockPeeked = true, handleReserve = 20.dp)
        assertEquals(hidden, peeked)
        // Message threads: the full scaffold padding, so the reply field moves above the dock.
        assertEquals(
            120.dp,
            liveTabBottomPadding(scaffoldBottom = 120.dp, navigationBarBottom = 24.dp, kind = DockImmersiveKind.Messages, dockPeeked = true, handleReserve = 20.dp),
        )
    }

    @Test
    fun theBaseInsetNeverExceedsTheScaffold() {
        assertEquals(24.dp, immersiveBaseBottomPadding(scaffoldBottom = 120.dp, navigationBarBottom = 24.dp))
        assertEquals(0.dp, immersiveBaseBottomPadding(scaffoldBottom = 0.dp, navigationBarBottom = 24.dp))
    }

    @Test
    fun theReporterSequenceHidesTheDockAgainOnTheNextReel() {
        val accessory = BottomBarAccessory()
        val instagram = Any()
        reportImmersivePage(accessory, instagram, "https://www.instagram.com/reels/A/")
        accessory.peekDock()
        assertFalse(accessory.immersiveDockHidden)
        // URL change: the keyed effect disposes (clear) and then reports the new page.
        accessory.clearWebPage(instagram)
        reportImmersivePage(accessory, instagram, "https://www.instagram.com/reels/B/")
        assertTrue(accessory.immersiveDockHidden)
        // Leaving for the feed: the reporter passes no immersive URL and the dock comes back.
        accessory.clearWebPage(instagram)
        reportImmersivePage(accessory, instagram, null)
        assertFalse(accessory.onImmersiveWebPage)
        assertTrue(accessory.webDockExpanded)
    }

    @Test
    fun theReporterSequenceSurvivesATabSwap() {
        val accessory = BottomBarAccessory()
        val instagram = Any()
        val x = Any()
        reportImmersivePage(accessory, x, "https://x.com/messages/1-2")
        // Switching to Instagram: X turns invisible (its effect re-keys to no URL) after Instagram reports.
        reportImmersivePage(accessory, instagram, "https://www.instagram.com/stories/a/1/")
        accessory.clearWebPage(x)
        reportImmersivePage(accessory, x, null)
        assertTrue(accessory.immersiveDockHidden)
        assertEquals("https://www.instagram.com/stories/a/1/", accessory.immersivePageUrl)
    }
}
