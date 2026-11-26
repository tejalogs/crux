package org.crux.ui.screen.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateSheetNoteTest {
    @Test
    fun cancelledIsNotStyledAsAnError() {
        assertFalse(UpdateSheetNote.Cancelled.isError)
    }

    @Test
    fun failureIsStyledAsAnErrorAndKeepsItsMessage() {
        val failure = UpdateSheetNote.Failed("Disk full")
        assertTrue(failure.isError)
        assertEquals("Disk full", failure.message)
        assertNull(UpdateSheetNote.Failed(null).message)
    }

    @Test
    fun notifyMeShowsOnlyWhenAutoCheckIsOnAndPermissionIsMissing() {
        assertTrue(
            showNotifyMeButton(notificationsGranted = false, alreadyAnswered = false, autoCheckEnabled = true, isDownloading = false),
        )
    }

    @Test
    fun notifyMeHiddenWhenAutoCheckIsOff() {
        // With auto-check off Crux never checks on its own, so no notification could ever come.
        assertFalse(
            showNotifyMeButton(notificationsGranted = false, alreadyAnswered = false, autoCheckEnabled = false, isDownloading = false),
        )
    }

    @Test
    fun notifyMeHiddenWhenGrantedAnsweredOrDownloading() {
        assertFalse(
            showNotifyMeButton(notificationsGranted = true, alreadyAnswered = false, autoCheckEnabled = true, isDownloading = false),
        )
        assertFalse(
            showNotifyMeButton(notificationsGranted = false, alreadyAnswered = true, autoCheckEnabled = true, isDownloading = false),
        )
        assertFalse(
            showNotifyMeButton(notificationsGranted = false, alreadyAnswered = false, autoCheckEnabled = true, isDownloading = true),
        )
    }
}
