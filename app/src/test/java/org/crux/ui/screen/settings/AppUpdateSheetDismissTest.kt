package org.crux.ui.screen.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateSheetDismissTest {
    @Test
    fun sheetCannotCloseWhileDownloading() {
        assertFalse(canDismissUpdateSheet(hiding = true, isDownloading = true))
    }

    @Test
    fun sheetClosesNormallyWhenIdle() {
        assertTrue(canDismissUpdateSheet(hiding = true, isDownloading = false))
    }

    @Test
    fun nonHidingStateChangesAreAlwaysAllowed() {
        assertTrue(canDismissUpdateSheet(hiding = false, isDownloading = true))
        assertTrue(canDismissUpdateSheet(hiding = false, isDownloading = false))
    }
}
