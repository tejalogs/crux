package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebTabRestoreTest {
    @Test
    fun instagramPrivateAndFullScreenPagesAreNeverReopened() {
        val ig = LiveWebSite.Instagram
        assertFalse(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/direct/t/12345/"))
        assertFalse(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/direct/inbox/"))
        assertFalse(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/stories/someone/123/"))
        assertFalse(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/reels/C1abc/"))
        assertFalse(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/reel/C1abc/"))
        assertFalse(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/natgeo/reel/C1abc/"))

        assertTrue(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/"))
        assertTrue(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/natgeo/"))
        assertTrue(LiveWebSessionState.isRestorableUrl(ig, "https://www.instagram.com/p/C1abc/"))
    }

    @Test
    fun messageThreadsOnOtherSitesAreNotReopenedEither() {
        assertFalse(LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "https://x.com/messages/123-456"))
        assertFalse(LiveWebSessionState.isRestorableUrl(LiveWebSite.LinkedIn, "https://www.linkedin.com/messaging/thread/2-abc/"))
        assertFalse(LiveWebSessionState.isRestorableUrl(LiveWebSite.Reddit, "https://www.reddit.com/chat/channel/abc"))
    }

    @Test
    fun signInAndRecoveryPagesAreNeverReopened() {
        listOf(
            LiveWebSite.Instagram to "https://www.instagram.com/accounts/onetap/?next=%2F",
            LiveWebSite.Instagram to "https://www.instagram.com/auth_platform/codeentry/",
            LiveWebSite.Instagram to "https://www.instagram.com/accounts/password/reset/",
            LiveWebSite.Instagram to "https://www.instagram.com/accounts/emailsignup/",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/authwall?trk=x",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/uas/login-submit",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/checkpoint/challenge/abc",
            LiveWebSite.Reddit to "https://www.reddit.com/login/",
            LiveWebSite.Reddit to "https://www.reddit.com/register/",
            LiveWebSite.X to "https://x.com/i/flow/login",
            LiveWebSite.X to "https://x.com/account/access",
        ).forEach { (site, url) ->
            assertFalse(url, LiveWebSessionState.isRestorableUrl(site, url))
            assertTrue(url, LiveWebSessionState.isAuthOrLoginUrl(url))
        }
        assertFalse(LiveWebSessionState.isAuthOrLoginUrl("https://www.reddit.com/r/Kotlin/comments/abc/"))
        assertFalse(LiveWebSessionState.isAuthOrLoginUrl("https://www.linkedin.com/feed/"))
        assertFalse(LiveWebSessionState.isAuthOrLoginUrl(null))
    }

    @Test
    fun contentPagesNamedAfterSignInWordsAreNotSignInPages() {
        listOf(
            LiveWebSite.Reddit to "https://www.reddit.com/r/challenge/",
            LiveWebSite.Reddit to "https://www.reddit.com/r/register/comments/abc/title/",
            LiveWebSite.Reddit to "https://www.reddit.com/user/login/",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/company/register/",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/in/signup/",
            LiveWebSite.Instagram to "https://www.instagram.com/explore/tags/challenge/",
            LiveWebSite.X to "https://x.com/challenge",
            LiveWebSite.X to "https://x.com/register/status/123",
            LiveWebSite.X to "https://twitter.com/checkpoint",
        ).forEach { (site, url) ->
            assertFalse(url, LiveWebSessionState.isAuthOrLoginUrl(url))
            assertTrue(url, LiveWebSessionState.isRestorableUrl(site, url))
        }
        // X's own sign-in routes still count.
        listOf("https://x.com/login", "https://x.com/logout", "https://x.com/i/flow/signup", "https://x.com/account/login_verification")
            .forEach { assertTrue(it, LiveWebSessionState.isAuthOrLoginUrl(it)) }
        assertTrue(LiveWebSessionState.isAuthPath("www.instagram.com", "/challenge/abc/"))
    }

    @Test
    fun rankedHomeFeedsKeepTheirUrlButNotTheirOffset() {
        listOf(
            LiveWebSite.Instagram to "https://www.instagram.com/",
            LiveWebSite.Instagram to "https://www.instagram.com",
            LiveWebSite.Instagram to "https://www.instagram.com/?variant=following",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/feed/",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/feed",
            LiveWebSite.Reddit to "https://www.reddit.com/",
            LiveWebSite.Reddit to "https://www.reddit.com/new/",
            LiveWebSite.Reddit to "https://www.reddit.com/hot/",
            LiveWebSite.Reddit to "https://www.reddit.com/best/",
            LiveWebSite.Reddit to "https://www.reddit.com/top/?t=week",
            LiveWebSite.Reddit to "https://www.reddit.com/rising/",
            LiveWebSite.Reddit to "https://www.reddit.com/r/Kotlin/",
            LiveWebSite.Reddit to "https://www.reddit.com/r/Kotlin",
            LiveWebSite.Reddit to "https://www.reddit.com/r/Kotlin/new/",
            LiveWebSite.X to "https://x.com/home",
            LiveWebSite.X to "https://x.com/home/",
            LiveWebSite.X to "https://twitter.com/home",
            LiveWebSite.X to "https://x.com/explore",
            LiveWebSite.X to "https://x.com/explore/tabs/for-you",
            LiveWebSite.X to "https://x.com/notifications",
            LiveWebSite.X to "https://x.com/notifications/mentions",
            LiveWebSite.X to "https://x.com/i/bookmarks",
        ).forEach { (site, url) ->
            assertTrue(url, LiveWebSessionState.isRankedFeedUrl(site, url))
            assertEquals(url, 0, LiveWebSessionState.scrollToKeep(site, url, 1800))
        }
    }

    @Test
    fun detailPagesKeepTheirOffset() {
        listOf(
            LiveWebSite.Instagram to "https://www.instagram.com/natgeo/",
            LiveWebSite.Instagram to "https://www.instagram.com/p/C1abc/",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/feed/update/urn:li:activity:7000/",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/feed/?highlightedUpdateUrns=urn%3Ali%3Aactivity%3A7000",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/in/someone/",
            LiveWebSite.Reddit to "https://www.reddit.com/r/Kotlin/comments/abc/title/",
            LiveWebSite.Reddit to "https://www.reddit.com/user/someone/",
            LiveWebSite.X to "https://x.com/nasa/status/1790000000000000000",
            LiveWebSite.X to "https://x.com/nasa",
            LiveWebSite.X to "https://x.com/homeboy",
            LiveWebSite.X to "https://x.com/i/lists/123",
            LiveWebSite.Mastodon to "https://mastodon.social/home",
        ).forEach { (site, url) ->
            assertFalse(url, LiveWebSessionState.isRankedFeedUrl(site, url))
            assertEquals(url, 1800, LiveWebSessionState.scrollToKeep(site, url, 1800))
        }
        assertEquals(0, LiveWebSessionState.scrollToKeep(LiveWebSite.X, "https://x.com/nasa/status/1", -5))
        assertFalse(LiveWebSessionState.isRankedFeedUrl(LiveWebSite.Reddit, null))
        assertFalse(LiveWebSessionState.isRankedFeedUrl(LiveWebSite.Reddit, "not a url %%"))
    }

    @Test
    fun xChatInboxIsNotReopenedLikeMessages() {
        listOf("https://x.com/i/chat", "https://x.com/i/chat/123-456", "https://x.com/messages/compose").forEach {
            assertFalse(it, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, it))
            assertTrue(it, isPrivateOrImmersiveUrl(it))
        }
        assertFalse(isPrivateOrImmersiveUrl("https://x.com/home"))
        assertTrue(LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "https://x.com/nasa/status/1"))
    }

    @Test
    fun composersAndMediaViewersAreNeverReopened() {
        listOf(
            LiveWebSite.X to "https://x.com/compose/post",
            LiveWebSite.X to "https://x.com/compose/tweet",
            LiveWebSite.X to "https://x.com/intent/post?text=hi",
            LiveWebSite.X to "https://twitter.com/intent/tweet",
            LiveWebSite.X to "https://x.com/nasa/status/12/photo/1",
            LiveWebSite.X to "https://x.com/nasa/status/12/video/1/",
            LiveWebSite.X to "https://x.com/nasa/photo",
            LiveWebSite.X to "https://x.com/nasa/header_photo/",
            LiveWebSite.Instagram to "https://www.instagram.com/create/style/",
            LiveWebSite.Instagram to "https://www.instagram.com/p/ABC/liked_by/",
            LiveWebSite.Instagram to "https://www.instagram.com/natgeo/p/ABC/comments/",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/post/new/",
            LiveWebSite.Reddit to "https://www.reddit.com/r/Kotlin/submit",
            LiveWebSite.Reddit to "https://www.reddit.com/submit",
        ).forEach { (site, url) -> assertFalse(url, LiveWebSessionState.isRestorableUrl(site, url)) }
        listOf(
            LiveWebSite.X to "https://x.com/photos",
            LiveWebSite.X to "https://x.com/nasa/media",
            LiveWebSite.X to "https://x.com/nasa/status/12/analytics",
            LiveWebSite.Reddit to "https://www.reddit.com/r/submit/",
            LiveWebSite.Instagram to "https://www.instagram.com/p/ABC/",
        ).forEach { (site, url) -> assertTrue(url, LiveWebSessionState.isRestorableUrl(site, url)) }
    }

    @Test
    fun mediaViewersAndLikerListsAreSavedAsThePostUnderThem() {
        assertEquals(
            "https://x.com/nasa/status/12",
            LiveWebSessionState.restorableUrlFor(LiveWebSite.X, "https://x.com/nasa/status/12/photo/1"),
        )
        assertEquals(
            "https://x.com/i/web/status/12",
            LiveWebSessionState.restorableUrlFor(LiveWebSite.X, "https://x.com/i/web/status/12/video/1"),
        )
        assertEquals(
            "https://www.instagram.com/p/ABC/",
            LiveWebSessionState.restorableUrlFor(LiveWebSite.Instagram, "https://www.instagram.com/natgeo/p/ABC/liked_by/"),
        )
        // Composers have no page under them: unchanged, and then refused.
        assertEquals("https://x.com/compose/post", LiveWebSessionState.restorableUrlFor(LiveWebSite.X, "https://x.com/compose/post"))
        assertEquals("https://x.com/home", LiveWebSessionState.restorableUrlFor(LiveWebSite.X, "https://x.com/home"))
        assertEquals(null, LiveWebSessionState.restorableUrlFor(LiveWebSite.X, null))
        val mapped = LiveWebSessionState.restorableUrlFor(LiveWebSite.X, "https://x.com/nasa/status/12/photo/2")
        assertTrue(LiveWebSessionState.isRestorableUrl(LiveWebSite.X, mapped))
        // A profile's avatar or header viewer is saved as the profile.
        assertEquals("https://x.com/nasa", LiveWebSessionState.restorableUrlFor(LiveWebSite.X, "https://x.com/nasa/photo"))
        assertEquals("https://x.com/nasa", LiveWebSessionState.restorableUrlFor(LiveWebSite.X, "https://x.com/nasa/header_photo"))
        assertTrue(LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "https://x.com/nasa"))
    }

    @Test
    fun onlyComposersKeepThePageSavedUnderThem() {
        listOf(
            LiveWebSite.X to "https://x.com/compose/post",
            LiveWebSite.X to "https://x.com/intent/post?text=hi",
            LiveWebSite.Instagram to "https://www.instagram.com/create/style/",
            LiveWebSite.LinkedIn to "https://www.linkedin.com/post/new/",
            LiveWebSite.Reddit to "https://www.reddit.com/r/Kotlin/submit",
        ).forEach { (site, url) -> assertTrue(url, LiveWebSessionState.isOnlyTransient(site, url)) }
        listOf(
            LiveWebSite.X to "https://x.com/i/chat/123",
            LiveWebSite.X to "https://x.com/messages",
            LiveWebSite.X to "https://x.com/i/flow/login",
            LiveWebSite.X to "https://x.com/nasa/status/12",
            LiveWebSite.Instagram to "https://www.instagram.com/direct/inbox/",
            LiveWebSite.Reddit to "https://www.reddit.com/login/",
            LiveWebSite.Reddit to "https://example.com/submit",
        ).forEach { (site, url) -> assertFalse(url, LiveWebSessionState.isOnlyTransient(site, url)) }
        assertFalse(LiveWebSessionState.isOnlyTransient(LiveWebSite.X, null))
    }
}
