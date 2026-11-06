package org.crux.ui.screen.home

import org.crux.ui.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveWebRoutesTest {
    @Test
    fun instagramLinksOpenInTheLiveWebScreen() {
        assertEquals(
            Route.InstagramWeb(initialUrl = "https://www.instagram.com/p/abc123/"),
            liveWebLinkRoute("https://www.instagram.com/p/abc123/"),
        )
        assertEquals(
            Route.InstagramWeb(initialUrl = "https://instagram.com/natgeo"),
            liveWebLinkRoute("  https://instagram.com/natgeo "),
        )
        assertEquals(
            Route.InstagramWeb(initialUrl = "http://m.instagram.com/reel/xyz/"),
            liveWebLinkRoute("http://m.instagram.com/reel/xyz/"),
        )
    }

    @Test
    fun linkedInLinksOpenInTheLiveWebScreen() {
        assertEquals(
            Route.LinkedInWeb(initialUrl = "https://www.linkedin.com/posts/someone_123"),
            liveWebLinkRoute("https://www.linkedin.com/posts/someone_123"),
        )
        assertEquals(
            Route.LinkedInWeb(initialUrl = "https://linkedin.com/in/someone"),
            liveWebLinkRoute("https://linkedin.com/in/someone"),
        )
    }

    @Test
    fun otherLinksAreNotClaimed() {
        assertNull(liveWebLinkRoute("https://example.com/instagram.com"))
        assertNull(liveWebLinkRoute("https://instagram.com.evil.example/p/1"))
        assertNull(liveWebLinkRoute("https://l.instagram.com/?u=https%3A%2F%2Fexample.com"))
        assertNull(liveWebLinkRoute("not a url"))
        assertNull(liveWebLinkRoute("crux://abcdef"))
    }

    @Test
    fun staticAndLegacyTabIdsResolveToTheirSite() {
        assertEquals(LiveWebSite.X, liveWebSiteForTabId("web.x:x"))
        assertEquals(LiveWebSite.Reddit, liveWebSiteForTabId("web.reddit:reddit"))
        assertEquals(LiveWebSite.Instagram, liveWebSiteForTabId("web.instagram:instagram"))
        assertEquals(LiveWebSite.LinkedIn, liveWebSiteForTabId("web.linkedin:linkedin"))
        // Pills saved before the static ones existed.
        assertEquals(LiveWebSite.Instagram, liveWebSiteForTabId("common.home:12345@instagram.com"))
        assertEquals(LiveWebSite.LinkedIn, liveWebSiteForTabId("common.home:me@linkedin.com"))
        assertNull(liveWebSiteForTabId("common.home:me@example.social"))
        assertNull(liveWebSiteForTabId("rss.all:all"))
    }

    @Test
    fun xAndRedditLinksOpenInTheirLiveWebScreens() {
        assertEquals(
            Route.LiveWeb(site = "X", initialUrl = "https://x.com/user/status/1"),
            liveWebLinkRoute("https://x.com/user/status/1"),
        )
        assertEquals(
            Route.LiveWeb(site = "X", initialUrl = "https://mobile.twitter.com/user"),
            liveWebLinkRoute("https://mobile.twitter.com/user"),
        )
        assertEquals(
            Route.LiveWeb(site = "Reddit", initialUrl = "https://old.reddit.com/r/android/comments/abc"),
            liveWebLinkRoute("https://old.reddit.com/r/android/comments/abc"),
        )
        assertEquals(
            Route.LiveWeb(site = "Reddit", initialUrl = "https://redd.it/abc"),
            liveWebLinkRoute("https://redd.it/abc"),
        )
        assertNull(liveWebLinkRoute("https://x.com.evil.example/a"))
        assertNull(liveWebLinkRoute("https://notreddit.com/r/a"))
    }

    @Test
    fun everySocialSiteRoutesToItsLiveScreen() {
        val cases =
            mapOf(
                "https://www.instagram.com/p/abc/" to Route.InstagramWeb("https://www.instagram.com/p/abc/"),
                "https://www.linkedin.com/in/someone" to Route.LinkedInWeb("https://www.linkedin.com/in/someone"),
                "https://x.com/jack" to Route.LiveWeb("X", "https://x.com/jack"),
                "https://www.reddit.com/r/kotlin" to Route.LiveWeb("Reddit", "https://www.reddit.com/r/kotlin"),
            )
        cases.forEach { (url, route) -> assertEquals(url, LinkTarget.LiveWebScreen(route), resolveLinkTarget(url)) }
    }

    @Test
    fun otherWebLinksGoToTheSystemBrowserWithTrackingRemoved() {
        assertEquals(
            LinkTarget.SystemBrowser("https://example.com/post?id=7"),
            resolveLinkTarget("  https://example.com/post?utm_source=rss&id=7&fbclid=x "),
        )
        assertEquals(
            LinkTarget.SystemBrowser("https://example.com/a"),
            resolveLinkTarget("https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fa%3Futm_medium%3Dz"),
        )
        assertEquals(
            LinkTarget.SystemBrowser("https://example.com/search?s=kotlin&t=30"),
            resolveLinkTarget("https://example.com/search?s=kotlin&t=30"),
        )
    }

    @Test
    fun aRedirectThatLandsOnASocialSiteOpensItsLiveScreen() {
        assertEquals(
            LinkTarget.LiveWebScreen(Route.LiveWeb("Reddit", "https://www.reddit.com/r/a")),
            resolveLinkTarget("https://l.instagram.com/?u=https%3A%2F%2Fwww.reddit.com%2Fr%2Fa%3Futm_source%3Dig"),
        )
        assertEquals(
            LinkTarget.LiveWebScreen(Route.LiveWeb("X", "https://x.com/user/status/1")),
            resolveLinkTarget("https://x.com/user/status/1?s=20&t=abc"),
        )
    }

    @Test
    fun cleaningCanBeSwitchedOff() {
        assertEquals(
            LinkTarget.SystemBrowser("https://example.com/post?utm_source=rss"),
            resolveLinkTarget("https://example.com/post?utm_source=rss", clean = false),
        )
    }

    @Test
    fun appLinksAreInternalAndOtherSchemesPassThrough() {
        assertEquals(LinkTarget.InternalDeepLink("crux://abcdef"), resolveLinkTarget("crux://abcdef"))
        assertEquals(LinkTarget.SystemBrowser("mailto:a@example.com"), resolveLinkTarget("mailto:a@example.com"))
        assertEquals(LinkTarget.SystemBrowser("tel:+441234"), resolveLinkTarget("tel:+441234"))
    }

    @Test
    fun emptyRelativeAndHostlessLinksAreInvalid() {
        listOf("", "  ", "/posts/1", "#frag", "example.com/post", "https://", "https:///x").forEach {
            assertEquals(it, LinkTarget.Invalid, resolveLinkTarget(it))
        }
    }

    @Test
    fun linksWithUnescapedCharactersStillReachTheBrowser() {
        assertEquals(
            LinkTarget.SystemBrowser("https://example.com/a b|c"),
            resolveLinkTarget("https://example.com/a b|c"),
        )
    }
}
