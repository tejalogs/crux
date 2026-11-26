package org.crux.ui.screen.instagram

import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WebScreenSessionTest {
    @Test
    fun alreadySignedInScreenNeverReportsAChange() {
        // Opening a link while signed in: every page callback sees the same state as at open.
        val tracker = WebSessionTracker(initialSignedIn = true)
        repeat(5) { assertEquals(WebSessionChange.None, tracker.update(isSignedIn = true)) }
        assertTrue(tracker.signedIn)
    }

    @Test
    fun signedOutScreenStaysQuietUntilSignIn() {
        val tracker = WebSessionTracker(initialSignedIn = false)
        assertEquals(WebSessionChange.None, tracker.update(isSignedIn = false))
        assertEquals(WebSessionChange.None, tracker.update(isSignedIn = false))
    }

    @Test
    fun signInIsReportedOnceThenSettles() {
        val tracker = WebSessionTracker(initialSignedIn = false)
        assertEquals(WebSessionChange.SignedIn, tracker.update(isSignedIn = true))
        assertEquals(WebSessionChange.None, tracker.update(isSignedIn = true))
        assertEquals(WebSessionChange.None, tracker.update(isSignedIn = true))
        assertTrue(tracker.signedIn)
    }

    @Test
    fun signOutAndSignInAgainAreEachReported() {
        val tracker = WebSessionTracker(initialSignedIn = true)
        assertEquals(WebSessionChange.SignedOut, tracker.update(isSignedIn = false))
        assertEquals(WebSessionChange.None, tracker.update(isSignedIn = false))
        assertFalse(tracker.signedIn)
        assertEquals(WebSessionChange.SignedIn, tracker.update(isSignedIn = true))
    }

    @Test
    fun recordingAClearedSessionSilencesTheNextSignedOutCheck() {
        // Clear session already reloads the home tab; the next page check must not reload again.
        val tracker = WebSessionTracker(initialSignedIn = true)
        tracker.update(isSignedIn = false)
        assertEquals(WebSessionChange.None, tracker.update(isSignedIn = false))
    }

    @Test
    fun signInFlowStartsSignedOutSoTheFirstSignedInCheckAutoReturns() {
        var cookieReads = 0
        val initial =
            initialSessionSignedIn(autoReturnOnSignIn = true) {
                cookieReads++
                true
            }
        assertFalse(initial)
        // The sign-in flow never trusts cookies at open; it must not even read them.
        assertEquals(0, cookieReads)
        // First check after the user signs in reports SignedIn, which drives auto-return.
        assertEquals(WebSessionChange.SignedIn, WebSessionTracker(initial).update(isSignedIn = true))
    }

    @Test
    fun alreadySignedInLinkOpenStartsSignedInSoTheHomeTabIsNotReloaded() {
        val initial = initialSessionSignedIn(autoReturnOnSignIn = false) { true }
        assertTrue(initial)
        assertEquals(WebSessionChange.None, WebSessionTracker(initial).update(isSignedIn = true))
    }

    @Test
    fun signedOutLinkOpenReportsALaterSignIn() {
        val initial = initialSessionSignedIn(autoReturnOnSignIn = false) { false }
        assertFalse(initial)
        assertEquals(WebSessionChange.SignedIn, WebSessionTracker(initial).update(isSignedIn = true))
    }

    @Test
    fun flushesOnlyOnSignInPagesOrRightAfterSignIn() {
        assertTrue(shouldFlushSessionCookies("https://www.instagram.com/accounts/login/", WebSessionChange.None))
        assertTrue(shouldFlushSessionCookies("https://www.linkedin.com/checkpoint/lg/login-submit", WebSessionChange.None))
        assertTrue(shouldFlushSessionCookies("https://www.instagram.com/", WebSessionChange.SignedIn))

        assertFalse(shouldFlushSessionCookies("https://www.instagram.com/reels/C1abc/", WebSessionChange.None))
        assertFalse(shouldFlushSessionCookies("https://www.instagram.com/natgeo/", WebSessionChange.None))
        assertFalse(shouldFlushSessionCookies("https://www.linkedin.com/feed/", WebSessionChange.None))
        assertFalse(shouldFlushSessionCookies("https://www.linkedin.com/feed/", WebSessionChange.SignedOut))
        assertFalse(shouldFlushSessionCookies(null, WebSessionChange.None))
    }

    @Test
    fun releaseRemovesAndDestroysTheWebView() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val webView = WebView(context)
        parent.addView(webView)

        releaseScreenWebView(webView)

        assertNull(webView.parent)
        assertEquals(0, parent.childCount)
        assertTrue(shadowOf(webView).wasDestroyCalled())
    }

    @Test
    fun releaseToleratesAWebViewWithoutParent() {
        val webView = WebView(RuntimeEnvironment.getApplication())
        releaseScreenWebView(webView)
        assertTrue(shadowOf(webView).wasDestroyCalled())
    }

    @Test
    fun defaultVideoPosterIsATransparentPixel() {
        val client = LockAwareWebChromeClient(isUnlocked = { true })
        val poster = client.getDefaultVideoPoster()

        assertEquals(1, poster.width)
        assertEquals(1, poster.height)
        assertEquals(Bitmap.Config.ARGB_8888, poster.config)
        assertEquals(0, Color.alpha(poster.getPixel(0, 0)))
        // One bitmap is reused for every video, not one per call.
        assertSame(poster, client.getDefaultVideoPoster())
    }
}
