package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebPullToRefreshTest {
    private val slop = 8f

    @Test
    fun gestureStartedAtTopMayRefresh() {
        val down = PullToRefreshGesture.down(100f, 100f, atTop = true)
        assertTrue(down.mayIntercept)
        assertTrue(down.moved(102f, 160f, slop).mayIntercept)
    }

    @Test
    fun gestureStartedMidFeedNeverRefreshes() {
        // The page may report "at top" late, mid-gesture; the latch from DOWN still wins.
        var gesture = PullToRefreshGesture.down(100f, 100f, atTop = false)
        listOf(120f, 180f, 300f).forEach { y -> gesture = gesture.moved(100f, y, slop) }
        assertFalse(gesture.mayIntercept)
        assertFalse(gesture.downAtTop)
    }

    @Test
    fun sidewaysDragNeverRefreshesEvenAtTop() {
        val gesture = PullToRefreshGesture.down(100f, 100f, atTop = true).moved(140f, 110f, slop)
        assertTrue(gesture.horizontal)
        assertFalse(gesture.mayIntercept)
        // Once sideways, a later vertical pull in the same gesture stays exempt.
        assertFalse(gesture.moved(140f, 400f, slop).mayIntercept)
    }

    @Test
    fun smallSidewaysJitterWithinSlopKeepsRefresh() {
        val gesture = PullToRefreshGesture.down(100f, 100f, atTop = true).moved(105f, 101f, slop)
        assertFalse(gesture.horizontal)
        assertTrue(gesture.mayIntercept)
    }

    @Test
    fun newDownResetsTheLatch() {
        val sideways = PullToRefreshGesture.down(0f, 0f, atTop = true).moved(50f, 0f, slop)
        val next = PullToRefreshGesture.down(10f, 10f, atTop = true)
        assertFalse(sideways.mayIntercept)
        assertTrue(next.mayIntercept)
        assertEquals(10f, next.startX)
    }

    @Test
    fun movedReturnsNewCopyWithoutChangingOriginal() {
        val down = PullToRefreshGesture.down(0f, 0f, atTop = true)
        down.moved(80f, 0f, slop)
        assertFalse(down.horizontal)
    }

    @Test
    fun innerContainerSiteIsNotAtTopUntilBridgeSpeaks() {
        assertFalse(liveWebPageAllowsPull(bridgeAllows = true, bridgeHeard = false, scrollsInnerContainer = true))
        assertTrue(liveWebPageAllowsPull(bridgeAllows = true, bridgeHeard = true, scrollsInnerContainer = true))
        assertFalse(liveWebPageAllowsPull(bridgeAllows = false, bridgeHeard = true, scrollsInnerContainer = true))
    }

    @Test
    fun documentScrollingSiteKeepsDefaultBeforeBridge() {
        assertTrue(liveWebPageAllowsPull(bridgeAllows = true, bridgeHeard = false, scrollsInnerContainer = false))
        assertFalse(liveWebPageAllowsPull(bridgeAllows = false, bridgeHeard = true, scrollsInnerContainer = false))
    }

    @Test
    fun onlyLinkedInScrollsAnInnerContainer() {
        assertTrue(LiveWebSite.LinkedIn.feedScrollsInnerContainer)
        LiveWebSite.entries.filter { it != LiveWebSite.LinkedIn }.forEach {
            assertFalse(it.name, it.feedScrollsInnerContainer)
        }
    }
}
