package org.crux.ui.screen.home

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Reddit is used signed out ([LiveWebSite.isSignedInAccount] is false), so no cookie jar is read. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class LiveWebSessionStatePersistenceTest {
    private val site = LiveWebSite.Reddit
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val detailUrl = "https://www.reddit.com/r/Kotlin/comments/abc/title/"

    @Before
    fun clearSavedPlace() {
        LiveWebSessionState.clear(context, site)
    }

    @Test
    fun aDetailPageReopensAtItsOffset() {
        LiveWebSessionState.save(context, site, detailUrl, 900)
        assertEquals(detailUrl to 900, LiveWebSessionState.restore(context, site))
    }

    @Test
    fun leavingTheTabOnAPrivatePageForgetsTheOlderPlace() {
        LiveWebSessionState.save(context, site, detailUrl, 900)
        LiveWebSessionState.save(context, site, "https://www.reddit.com/chat/channel/abc", 0)
        assertNull(LiveWebSessionState.restore(context, site))
    }

    @Test
    fun leavingTheTabOnASignInPageForgetsTheOlderPlace() {
        LiveWebSessionState.save(context, site, detailUrl, 900)
        LiveWebSessionState.save(context, site, "https://www.reddit.com/login/", 0)
        assertNull(LiveWebSessionState.restore(context, site))
    }

    @Test
    fun aComposerOpenedOverAThreadKeepsTheThreadSaved() {
        LiveWebSessionState.save(context, site, detailUrl, 900)
        LiveWebSessionState.save(context, site, "https://www.reddit.com/r/Kotlin/submit", 0)
        assertEquals(detailUrl to 900, LiveWebSessionState.restore(context, site))
    }

    @Test
    fun aPageThatHasNotLoadedLeavesTheSavedPlaceAlone() {
        LiveWebSessionState.save(context, site, detailUrl, 900)
        LiveWebSessionState.save(context, site, null, 0)
        LiveWebSessionState.save(context, site, "about:blank", 0)
        LiveWebSessionState.save(context, site, "https://example.com/elsewhere", 0)
        assertEquals(detailUrl to 900, LiveWebSessionState.restore(context, site))
    }

    @Test
    fun aRankedFeedIsSavedAtTheTop() {
        LiveWebSessionState.save(context, site, "https://www.reddit.com/new/", 2400)
        assertEquals("https://www.reddit.com/new/" to 0, LiveWebSessionState.restore(context, site))
    }

    @Test
    fun aRankedFeedOffsetSavedByAnOlderVersionIsDropped() {
        context.getSharedPreferences("crux_web_state", Context.MODE_PRIVATE).edit()
            .putString("url.${site.name}", "https://www.reddit.com/")
            .putInt("scroll.${site.name}", 1500)
            .commit()
        assertEquals("https://www.reddit.com/" to 0, LiveWebSessionState.restore(context, site))
    }

    @Test
    fun aCommunityNamedAfterASignInWordIsStillRestorable() {
        val url = "https://www.reddit.com/r/challenge/comments/abc/title/"
        LiveWebSessionState.save(context, site, url, 700)
        assertEquals(url to 700, LiveWebSessionState.restore(context, site))
    }
}
