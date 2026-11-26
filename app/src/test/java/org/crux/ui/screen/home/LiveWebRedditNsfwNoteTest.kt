package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebRedditNsfwNoteTest {
    @Test
    fun samePostReachedDifferentWaysSharesOneKey() {
        val a = redditNsfwNoteKey("https://www.reddit.com/r/sub/comments/abc123/title/")
        val b = redditNsfwNoteKey("https://reddit.com/r/sub/comments/abc123/title?utm_source=share#c1")
        assertEquals("reddit.com/r/sub/comments/abc123/title", a)
        assertEquals(a, b)
    }

    @Test
    fun differentPostsHaveDifferentKeys() {
        assertNotEquals(
            redditNsfwNoteKey("https://www.reddit.com/r/sub/comments/abc123/"),
            redditNsfwNoteKey("https://www.reddit.com/r/sub/comments/def456/"),
        )
    }

    @Test
    fun nonRedditOrMalformedUrlsHaveNoKey() {
        assertNull(redditNsfwNoteKey(null))
        assertNull(redditNsfwNoteKey(""))
        assertNull(redditNsfwNoteKey("https://redgifs.com/watch/x"))
        assertNull(redditNsfwNoteKey("https://evilreddit.com/r/sub/"))
        assertNull(redditNsfwNoteKey("javascript:alert(1)"))
        assertNull(redditNsfwNoteKey("https://www.reddit.com/%zz"))
    }

    @Test
    fun rootPathKeepsASlash() {
        assertEquals("reddit.com/", redditNsfwNoteKey("https://www.reddit.com"))
    }

    @Test
    fun signedInOpensRedditPreferencesOnly() {
        val action = redditNsfwNoteAction(signedIn = true)
        assertTrue(action.signedIn)
        assertEquals("https://www.reddit.com/settings/preferences", action.url)
    }

    @Test
    fun signedOutOpensRedditSignIn() {
        val action = redditNsfwNoteAction(signedIn = false)
        assertFalse(action.signedIn)
        assertEquals(LiveWebSite.Reddit.loginUrl, action.url)
    }

    @Test
    fun bridgeMessageIsTokenScoped() {
        assertTrue(bridgeRedditNsfwNoteMessage().startsWith(PageMarkers.consolePrefix + ":"))
        assertTrue(bridgeRedditNsfwNoteMessage("t0k").startsWith(PageMarkers.consolePrefix + ":t0k:"))
        assertEquals(bridgeRedditNsfwNoteMessage("t0k"), bridgeRedditNsfwNoteMessage("t0k"))
        assertNotEquals(bridgeRedditNsfwNoteMessage("a"), bridgeRedditNsfwNoteMessage("b"))
        assertFalse(bridgeRedditNsfwNoteMessage("t0k").contains("crux", ignoreCase = true))
    }

    @Test
    fun noteIsShownWhenUnlockedAndNew() {
        assertEquals(RedditNsfwNoteStep.Show, redditNsfwNoteStep("reddit.com/r/a", alreadyShown = false, unlocked = true))
    }

    @Test
    fun noteIsDeferredBehindTheAppLock() {
        assertEquals(RedditNsfwNoteStep.Defer, redditNsfwNoteStep("reddit.com/r/a", alreadyShown = false, unlocked = false))
    }

    @Test
    fun noteIsIgnoredOffRedditOrOnceShown() {
        assertEquals(RedditNsfwNoteStep.Ignore, redditNsfwNoteStep(null, alreadyShown = false, unlocked = true))
        assertEquals(RedditNsfwNoteStep.Ignore, redditNsfwNoteStep(null, alreadyShown = false, unlocked = false))
        assertEquals(RedditNsfwNoteStep.Ignore, redditNsfwNoteStep("reddit.com/r/a", alreadyShown = true, unlocked = true))
        assertEquals(RedditNsfwNoteStep.Ignore, redditNsfwNoteStep("reddit.com/r/a", alreadyShown = true, unlocked = false))
    }
}
