package org.crux.ui.screen.home

import android.view.View
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebPrivateScreensTest {
    @After
    fun reset() {
        LiveWebPrivateScreens.resetForTest()
    }

    @Test
    fun messagePagesOnEverySiteArePrivate() {
        assertTrue(isMessagingUrl("https://www.instagram.com/direct/inbox/"))
        assertTrue(isMessagingUrl("https://www.instagram.com/direct/t/1234567890/"))
        assertTrue(isMessagingUrl("https://x.com/messages"))
        assertTrue(isMessagingUrl("https://x.com/messages/123-456"))
        assertTrue(isMessagingUrl("https://x.com/i/chat/123"))
        assertTrue(isMessagingUrl("https://www.linkedin.com/messaging/thread/2-abc/"))
        assertTrue(isMessagingUrl("https://www.reddit.com/chat/room/abc"))
        assertTrue(isMessagingUrl("https://chat.reddit.com/room/abc"))
        assertTrue(isMessagingUrl("https://old.reddit.com/message/inbox/"))
        assertTrue(isMessagingUrl("https://mastodon.social/conversations"))
    }

    @Test
    fun feedsReelsAndLookAlikeNamesAreNotMessagePages() {
        assertFalse(isMessagingUrl("https://www.instagram.com/"))
        assertFalse(isMessagingUrl("https://www.instagram.com/reels/C1abc/"))
        assertFalse(isMessagingUrl("https://www.instagram.com/stories/natgeo/123/"))
        assertFalse(isMessagingUrl("https://x.com/home"))
        assertFalse(isMessagingUrl("https://www.reddit.com/r/chat/"))
        assertFalse(isMessagingUrl("https://www.reddit.com/r/Kotlin/comments/abc/messages_from_space/"))
        assertFalse(isMessagingUrl("https://www.linkedin.com/in/messaging-expert/"))
        assertFalse(isMessagingUrl("https://www.linkedin.com/feed/"))
        assertFalse(isMessagingUrl(null))
        assertFalse(isMessagingUrl(""))
        assertFalse(isMessagingUrl("javascript:alert(1)"))
    }

    @Test
    fun xChatCountsAsPrivateAndImmersiveEverywhere() {
        listOf("https://x.com/i/chat", "https://x.com/i/chat/", "https://x.com/i/chat/123-456", "https://twitter.com/i/chat/9").forEach {
            assertTrue(it, isMessagingUrl(it))
            assertTrue(it, isPrivateScreenUrl(it))
            assertTrue(it, isPrivateOrImmersiveUrl(it))
        }
        assertTrue(isPrivateOrImmersiveUrl("https://www.instagram.com/reels/C1abc/"))
        assertFalse(isPrivateOrImmersiveUrl("https://x.com/i/chatter"))
        assertFalse(isPrivateOrImmersiveUrl("https://x.com/nasa/status/1"))
        assertTrue(Regex(MESSAGING_PATH_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn("/i/chat/1"))
    }

    @Test
    fun signInPagesArePrivateToo() {
        assertTrue(isPrivateScreenUrl("https://www.instagram.com/accounts/login/"))
        assertTrue(isPrivateScreenUrl("https://x.com/i/flow/login"))
        assertTrue(isPrivateScreenUrl("https://www.linkedin.com/login"))
        assertTrue(isPrivateScreenUrl("https://www.reddit.com/login/"))
        assertTrue(isPrivateScreenUrl("https://accounts.google.com/o/oauth2/v2/auth?client_id=1"))
        assertTrue(isPrivateScreenUrl("https://appleid.apple.com/auth/authorize"))
        assertFalse(isPrivateScreenUrl("https://www.reddit.com/new/"))
        assertFalse(isPrivateScreenUrl("https://x.com/login_expert"))
        assertFalse(isPrivateScreenUrl("https://www.linkedin.com/company/register/"))
    }

    @Test
    fun onlyAVisibleTabAsksForASecureWindow() {
        assertTrue(tabNeedsSecureWindow(isVisible = true, url = "https://x.com/messages"))
        assertFalse(tabNeedsSecureWindow(isVisible = false, url = "https://x.com/messages"))
        assertFalse(tabNeedsSecureWindow(isVisible = true, url = "https://x.com/home"))
    }

    @Test
    fun secureWindowIsAnyOfTheUserSettingAppLockOnOldAndroidOrAPrivatePage() {
        assertFalse(secureWindowWanted(userSecureWindow = false, requireAppLock = false, privateScreenShown = false, legacyRecents = true))
        assertTrue(secureWindowWanted(userSecureWindow = true, requireAppLock = false, privateScreenShown = false, legacyRecents = false))
        assertTrue(secureWindowWanted(userSecureWindow = false, requireAppLock = true, privateScreenShown = false, legacyRecents = true))
        assertFalse(secureWindowWanted(userSecureWindow = false, requireAppLock = true, privateScreenShown = false, legacyRecents = false))
        assertTrue(secureWindowWanted(userSecureWindow = false, requireAppLock = false, privateScreenShown = true, legacyRecents = false))
    }

    @Test
    fun recentsPreviewIsHiddenUnderAppLockOrOnceSitePagesWereOpened() {
        assertFalse(recentsPreviewHidden(requireAppLock = false, webContentShown = false))
        assertTrue(recentsPreviewHidden(requireAppLock = true, webContentShown = false))
        assertTrue(recentsPreviewHidden(requireAppLock = false, webContentShown = true))
    }

    @Test
    fun protectionStaysOnWhileAnyTabStillNeedsIt() =
        runTest {
            val first = Any()
            val second = Any()
            LiveWebPrivateScreens.update(first, needsSecure = true)
            LiveWebPrivateScreens.update(second, needsSecure = true)
            LiveWebPrivateScreens.update(first, needsSecure = false)
            assertTrue(LiveWebPrivateScreens.isActive)
            assertTrue(LiveWebPrivateScreens.active.first())
            LiveWebPrivateScreens.update(second, needsSecure = false)
            assertFalse(LiveWebPrivateScreens.isActive)
            assertFalse(LiveWebPrivateScreens.active.first())
        }

    @Test
    fun webContentShownOnlyEverTurnsOn() {
        assertFalse(LiveWebPrivateScreens.webContentShown.value)
        LiveWebPrivateScreens.markWebContentShown()
        LiveWebPrivateScreens.markWebContentShown()
        assertTrue(LiveWebPrivateScreens.webContentShown.value)
    }

    @Test
    fun autofillIsOffOnlyOnMessagePagesSoPasswordManagersStillFillSignIn() {
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, autofillModeFor("https://www.instagram.com/direct/t/1/"))
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_AUTO, autofillModeFor("https://www.instagram.com/accounts/login/"))
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_AUTO, autofillModeFor("https://accounts.google.com/signin"))
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_AUTO, autofillModeFor("https://x.com/home"))
    }

    @Test
    fun keyboardIsAskedNotToLearnAndKeepsThePageImeOptions() {
        val pageOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_FULLSCREEN
        val result = withNoPersonalizedLearning(pageOptions)
        assertEquals(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, result and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertEquals(EditorInfo.IME_ACTION_SEND, result and EditorInfo.IME_MASK_ACTION)
        assertEquals(EditorInfo.IME_FLAG_NO_FULLSCREEN, result and EditorInfo.IME_FLAG_NO_FULLSCREEN)
        assertEquals(result, withNoPersonalizedLearning(result))
        assertEquals(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, withNoPersonalizedLearning(0))
    }
}
