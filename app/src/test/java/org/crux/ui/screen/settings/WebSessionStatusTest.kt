package org.crux.ui.screen.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionStatusTest {
    @Test
    fun signedOutSiteWithOnlyOtherCookiesIsNotSignedIn() {
        val status = webSessionStatus(signedIn = false, hasStoredData = true)
        assertFalse(status.signedIn)
        assertTrue(status.canClear)
    }

    @Test
    fun nothingStoredOffersNoClear() {
        assertEquals(WebSessionStatus(signedIn = false, canClear = false), webSessionStatus(false, false))
    }

    @Test
    fun signedInCanAlwaysClear() {
        assertTrue(webSessionStatus(signedIn = true, hasStoredData = false).canClear)
    }
}
