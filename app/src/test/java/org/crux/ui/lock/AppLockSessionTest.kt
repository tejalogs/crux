package org.crux.ui.lock

import org.junit.Assert.assertFalse
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
}
