package org.crux.ui.screen.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDockFitTest {
    @Test
    fun fivePillsPlusFeedsShareTheWidthInIconMode() {
        assertTrue(fitsWithoutScrolling(iconOnly = true, tabCount = 6))
    }

    @Test
    fun labelledOrVeryLongStripsStillScroll() {
        assertFalse(fitsWithoutScrolling(iconOnly = false, tabCount = 3))
        assertFalse(fitsWithoutScrolling(iconOnly = true, tabCount = MAX_EQUAL_WIDTH_PILLS + 1))
        assertFalse(fitsWithoutScrolling(iconOnly = true, tabCount = 0))
    }
}
