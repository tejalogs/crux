package org.crux.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarAccessoryImmersiveTest {
    private val instagram = Any()
    private val x = Any()

    private val reelA = "https://www.instagram.com/reels/A/"
    private val reelB = "https://www.instagram.com/reels/B/"

    @Test
    fun immersivePageHidesTheDockEvenWhenTheTabAsksForIt() {
        val accessory = BottomBarAccessory()
        accessory.webDockExpanded = true
        accessory.reportWebPage(instagram, reelA)
        assertTrue(accessory.onImmersiveWebPage)
        assertTrue(accessory.immersiveDockHidden)
        assertEquals(reelA, accessory.immersivePageUrl)
        assertFalse(accessory.webDockExpanded)
        // The tab re-asserting "expanded" when it becomes visible must not undo the immersive hide.
        accessory.webDockExpanded = true
        assertFalse(accessory.webDockExpanded)
    }

    @Test
    fun leavingTheImmersivePageRestoresTheDock() {
        val accessory = BottomBarAccessory()
        accessory.reportWebPage(instagram, reelA)
        accessory.clearWebPage(instagram)
        assertFalse(accessory.onImmersiveWebPage)
        assertNull(accessory.immersivePageUrl)
        assertTrue(accessory.webDockExpanded)
    }

    @Test
    fun peekBringsTheDockBackUntilTheReporterMovesToTheNextPage() {
        val accessory = BottomBarAccessory()
        accessory.reportWebPage(instagram, reelA)
        accessory.peekDock()
        assertTrue(accessory.webDockExpanded)
        assertTrue(accessory.onImmersiveWebPage)
        assertFalse(accessory.immersiveDockHidden)
        // The reporter's effect is keyed on the URL: the old effect is disposed, then the new one reports.
        accessory.clearWebPage(instagram)
        accessory.reportWebPage(instagram, reelB)
        assertFalse(accessory.immersiveDockPeeked)
        assertTrue(accessory.immersiveDockHidden)
        assertFalse(accessory.webDockExpanded)
    }

    @Test
    fun aDirectReportOfAnotherAddressAlsoResetsThePeek() {
        val accessory = BottomBarAccessory()
        accessory.reportWebPage(instagram, reelA)
        accessory.peekDock()
        accessory.reportWebPage(instagram, reelB)
        assertTrue(accessory.immersiveDockHidden)
    }

    @Test
    fun peekDoesNothingOffImmersivePages() {
        val accessory = BottomBarAccessory()
        accessory.peekDock()
        assertFalse(accessory.immersiveDockPeeked)
        assertTrue(accessory.webDockExpanded)
    }

    @Test
    fun aLeavingTabNeverClearsTheArrivingTabsClaim() {
        val accessory = BottomBarAccessory()
        accessory.reportWebPage(x, "https://x.com/messages")
        // The new tab claims first, then the old tab's cleanup runs.
        accessory.reportWebPage(instagram, "https://www.instagram.com/stories/a/1/")
        accessory.clearWebPage(x)
        assertTrue(accessory.immersiveDockHidden)
        assertEquals("https://www.instagram.com/stories/a/1/", accessory.immersivePageUrl)
        accessory.clearWebPage(instagram)
        assertFalse(accessory.onImmersiveWebPage)
        assertTrue(accessory.webDockExpanded)
    }

    @Test
    fun aPeekOnTheLeavingTabDoesNotCarryOverToTheArrivingTab() {
        val accessory = BottomBarAccessory()
        accessory.reportWebPage(x, "https://x.com/messages/1-2")
        accessory.peekDock()
        // Arriving tab reports before the leaving tab's dispose runs.
        accessory.reportWebPage(instagram, reelA)
        assertTrue(accessory.immersiveDockHidden)
        accessory.clearWebPage(x)
        assertTrue(accessory.immersiveDockHidden)
    }

    @Test
    fun theTabsOwnCollapseStillAppliesOutsideImmersivePages() {
        val accessory = BottomBarAccessory()
        accessory.webDockExpanded = false
        assertFalse(accessory.webDockExpanded)
        accessory.reportWebPage(instagram, reelA)
        accessory.clearWebPage(instagram)
        assertFalse(accessory.webDockExpanded)
    }

    @Test
    fun theLiveDockGoesAwayWhileALiveTabHoldsTheBarOnAnImmersivePage() {
        val accessory = BottomBarAccessory()
        // No live tab holds the bar: shown, whatever the immersive state.
        assertTrue(accessory.liveDockShown)
        accessory.keepVisibleOwner = instagram
        accessory.webDockExpanded = true
        assertTrue(accessory.liveDockShown)
        accessory.reportWebPage(instagram, reelA)
        assertFalse(accessory.liveDockShown)
        accessory.peekDock()
        assertTrue(accessory.liveDockShown)
        accessory.clearWebPage(instagram)
        assertTrue(accessory.liveDockShown)
    }

    @Test
    fun liveDockShownMatchesHomeScreensBottomBarExpression() {
        val accessory = BottomBarAccessory()
        accessory.keepVisibleOwner = instagram
        accessory.reportWebPage(instagram, reelA)
        // HomeScreen.kt: (!keepVisible || webDockExpanded)
        assertEquals(!accessory.keepVisible || accessory.webDockExpanded, accessory.liveDockShown)
        assertFalse(!accessory.keepVisible || accessory.webDockExpanded)
    }
}
