package org.crux.ui.screen.home

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebSessionRulesTest {
    @Test
    fun homeTabReloadsOnlyWhenTheSignedInStateChanged() {
        assertTrue(shouldReloadHomeTab(signedInBefore = false, signedInAfter = true))
        assertTrue(shouldReloadHomeTab(signedInBefore = true, signedInAfter = false))
        assertFalse(shouldReloadHomeTab(signedInBefore = true, signedInAfter = true))
        assertFalse(shouldReloadHomeTab(signedInBefore = false, signedInAfter = false))
    }

    @Test
    fun pagesNeverRunUnderAppLock() {
        assertTrue(webSessionMayRun(isVisible = true, isUnlocked = true))
        assertFalse(webSessionMayRun(isVisible = true, isUnlocked = false))
        assertFalse(webSessionMayRun(isVisible = false, isUnlocked = true))
        assertFalse(webSessionMayRun(isVisible = false, isUnlocked = false))
    }

    @Test
    fun javaScriptTimersRunOnlyWhileStartedAndUnlocked() {
        assertTrue(liveWebTimersMayRun(isUnlocked = true, state = Lifecycle.State.STARTED))
        assertTrue(liveWebTimersMayRun(isUnlocked = true, state = Lifecycle.State.RESUMED))
        assertFalse(liveWebTimersMayRun(isUnlocked = true, state = Lifecycle.State.CREATED))
        assertFalse(liveWebTimersMayRun(isUnlocked = false, state = Lifecycle.State.RESUMED))
    }

    @Test
    fun redditOver18CookieIsSetOnlyForSignedOutReadersWithTheTweak() {
        assertEquals(RedditOver18CookieAction.Set, redditOver18CookieAction(tweakOn = true, signedIn = false, maybeSetByCrux = false))
        assertEquals(RedditOver18CookieAction.Set, redditOver18CookieAction(tweakOn = true, signedIn = false, maybeSetByCrux = true))
    }

    @Test
    fun redditOver18CookieCruxSetIsTakenBackButAReadersOwnIsLeftAlone() {
        // Tweak now off (the 1.0.13 default) after 1.0.12 set the cookie.
        assertEquals(RedditOver18CookieAction.Expire, redditOver18CookieAction(tweakOn = false, signedIn = false, maybeSetByCrux = true))
        // Signed in: Reddit's own account setting decides, so Crux's cookie goes too.
        assertEquals(RedditOver18CookieAction.Expire, redditOver18CookieAction(tweakOn = true, signedIn = true, maybeSetByCrux = true))
        // Crux has already taken its cookie back: whatever is there now is the reader's own.
        assertEquals(RedditOver18CookieAction.None, redditOver18CookieAction(tweakOn = false, signedIn = false, maybeSetByCrux = false))
        assertEquals(RedditOver18CookieAction.None, redditOver18CookieAction(tweakOn = true, signedIn = true, maybeSetByCrux = false))
    }

    @Test
    fun tabSwitchesJumpWheneverALiveTabIsShownOrPassed() {
        // Feeds(0) RSS(1) X(2) Instagram(3) Bookmarks(4) Reddit(5)
        val live = setOf(2, 3, 5)
        val isLive: (Int) -> Boolean = { it in live }
        assertFalse(shouldJumpBetweenTabs(0, 1, isLive))
        assertFalse(shouldJumpBetweenTabs(1, 0, isLive))
        assertTrue(shouldJumpBetweenTabs(1, 2, isLive))
        assertTrue(shouldJumpBetweenTabs(2, 1, isLive))
        assertTrue(shouldJumpBetweenTabs(3, 5, isLive))
        // Two plain feeds with live tabs in between still jump, so those pages are not woken.
        assertTrue(shouldJumpBetweenTabs(0, 4, isLive))
        assertTrue(shouldJumpBetweenTabs(4, 1, isLive))
        assertFalse(shouldJumpBetweenTabs(4, 4, isLive))
    }
}
