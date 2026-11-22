package org.crux.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionTest {
    private var now = 1_000L
    private val session = AppLockSession(clock = { now }, graceMs = 10_000L)

    private fun unlock() {
        assertTrue(session.beginAutoPrompt())
        session.onPromptResult(success = true)
    }

    @Test
    fun cancellingThePromptDoesNotReopenItByItself() {
        session.onActivityStarted(lockEnabled = true)
        assertTrue(session.beginAutoPrompt())
        session.onPromptResult(success = false)
        assertFalse(session.isUnlocked)
        assertFalse(session.beginAutoPrompt())
        assertTrue(session.beginManualPrompt())
    }

    @Test
    fun promptCannotBeOpenedTwice() {
        session.onActivityStarted(lockEnabled = true)
        assertTrue(session.beginAutoPrompt())
        assertFalse(session.beginManualPrompt())
    }

    @Test
    fun rotationDoesNotRelock() {
        session.onActivityStarted(lockEnabled = true)
        unlock()
        session.onActivityStopped(changingConfigurations = true)
        now += 60_000L
        session.onActivityStarted(lockEnabled = true)
        assertTrue(session.isUnlocked)
    }

    @Test
    fun theUnlockScreenItselfDoesNotCountAsLeaving() {
        session.onActivityStarted(lockEnabled = true)
        assertTrue(session.beginAutoPrompt())
        session.onActivityStopped(changingConfigurations = false)
        now += 60_000L
        session.onActivityStarted(lockEnabled = true)
        session.onPromptResult(success = true)
        assertTrue(session.isUnlocked)
    }

    @Test
    fun movingBetweenCruxScreensDoesNotRelock() {
        session.onActivityStarted(lockEnabled = true)
        unlock()
        session.onActivityStarted(lockEnabled = true) // share screen starts first
        session.onActivityStopped(changingConfigurations = false) // main screen stops
        now += 60_000L
        session.onActivityStopped(changingConfigurations = false)
        session.onActivityStarted(lockEnabled = true)
        assertTrue(session.isUnlocked)
    }

    @Test
    fun shortTripOutsideStaysUnlockedLongOneRelocks() {
        session.onActivityStarted(lockEnabled = true)
        unlock()
        session.onActivityStopped(changingConfigurations = false)
        now += 5_000L
        session.onActivityStarted(lockEnabled = true)
        assertTrue(session.isUnlocked)

        session.onActivityStopped(changingConfigurations = false)
        now += 10_000L
        session.onActivityStarted(lockEnabled = true)
        assertFalse(session.isUnlocked)
        assertTrue(session.beginAutoPrompt())
    }

    @Test
    fun lockOffNeverRelocks() {
        session.markUnlockedWithoutPrompt()
        session.onActivityStarted(lockEnabled = false)
        session.onActivityStopped(changingConfigurations = false)
        now += 60_000L
        session.onActivityStarted(lockEnabled = false)
        assertTrue(session.isUnlocked)
    }

    @Test
    fun slowButSuccessfulConfirmPromptStaysUnlocked() {
        session.onActivityStarted(lockEnabled = true)
        unlock()
        assertTrue(session.beginConfirmPrompt())
        assertFalse(session.beginConfirmPrompt())
        session.onActivityStopped(changingConfigurations = false)
        now += 60_000L
        session.onActivityStarted(lockEnabled = true)
        session.onConfirmPromptFinished(success = true)
        assertTrue(session.isUnlocked)
        assertTrue(session.beginConfirmPrompt())
    }

    @Test
    fun confirmPromptCancelledAfterGracePeriodRelocks() {
        session.onActivityStarted(lockEnabled = true)
        unlock()
        assertTrue(session.beginConfirmPrompt())
        session.onActivityStopped(changingConfigurations = false) // left on the PIN screen
        now += 60_000L
        session.onActivityStarted(lockEnabled = true) // someone else reopens Crux
        assertTrue(session.isUnlocked)
        session.onConfirmPromptFinished(success = false)
        assertFalse(session.isUnlocked)
        assertTrue(session.beginAutoPrompt())
    }

    @Test
    fun quickCancelOfConfirmPromptStaysUnlocked() {
        session.onActivityStarted(lockEnabled = true)
        unlock()
        assertTrue(session.beginConfirmPrompt())
        now += 2_000L
        session.onConfirmPromptFinished(success = false)
        assertTrue(session.isUnlocked)
    }

    @Test
    fun confirmPromptThatCouldNotLaunchStaysUnlocked() {
        session.onActivityStarted(lockEnabled = true)
        unlock()
        assertTrue(session.beginConfirmPrompt())
        session.onConfirmPromptFinished(success = false)
        assertTrue(session.isUnlocked)
        assertTrue(session.beginConfirmPrompt())
    }

    @Test
    fun leavingAfterConfirmPromptFinishedStillRelocks() {
        session.onActivityStarted(lockEnabled = true)
        unlock()
        assertTrue(session.beginConfirmPrompt())
        session.onConfirmPromptFinished(success = true)
        session.onActivityStopped(changingConfigurations = false)
        now += 10_000L
        session.onActivityStarted(lockEnabled = true)
        assertFalse(session.isUnlocked)
    }

    @Test
    fun deferredRequestRunsAtOnceWhenUnlocked() {
        val deferred = DeferredWhileLocked<String>()
        assertEquals("crux://a", deferred.offer("crux://a", unlocked = true))
        assertNull(deferred.release(unlocked = true))
    }

    @Test
    fun deferredRequestWaitsForUnlockAndKeepsOnlyTheNewest() {
        val deferred = DeferredWhileLocked<String>()
        assertNull(deferred.offer("crux://a", unlocked = false))
        assertNull(deferred.offer("crux://b", unlocked = false))
        assertNull(deferred.release(unlocked = false))
        assertEquals("crux://b", deferred.release(unlocked = true))
        assertNull(deferred.release(unlocked = true))
    }

    @Test
    fun requestHandledWhileUnlockedDropsAnOlderQueuedOne() {
        val deferred = DeferredWhileLocked<String>()
        assertNull(deferred.offer("crux://old", unlocked = false))
        assertEquals("crux://new", deferred.offer("crux://new", unlocked = true))
        assertNull(deferred.release(unlocked = true))
    }

    @Test
    fun turningLockOffNeedsCredentialWhenDeviceIsSecure() {
        assertEquals(AppLockToggleAction.ConfirmFirst, appLockToggleAction(enable = false, deviceSecure = true))
    }

    @Test
    fun turningLockOffWithNoScreenLockSavesDirectly() {
        assertEquals(AppLockToggleAction.Save, appLockToggleAction(enable = false, deviceSecure = false))
    }

    @Test
    fun turningLockOnNeedsAScreenLock() {
        assertEquals(AppLockToggleAction.NeedsScreenLock, appLockToggleAction(enable = true, deviceSecure = false))
        assertEquals(AppLockToggleAction.Save, appLockToggleAction(enable = true, deviceSecure = true))
    }
}
