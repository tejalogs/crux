package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWebLinksTest {
    @Test
    fun unwrapsInstagramRedirect() {
        assertEquals(
            "https://example.com/page",
            cleanOutgoingLink("https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fpage%3Futm_source%3Dig&e=abc"),
        )
    }

    @Test
    fun malformedRedirectDoesNotCrash() {
        val link = "https://l.instagram.com/?u=https%3A%2F%2Fexample.com%zz"
        assertEquals(link, cleanOutgoingLink(link))
    }

    @Test
    fun unwrapsLinkedInRedirect() {
        assertEquals(
            "https://example.com/a",
            cleanOutgoingLink("https://www.linkedin.com/safety/go?url=https%3A%2F%2Fexample.com%2Fa&trk=flagship"),
        )
    }

    @Test
    fun dropsOnlyTrackingParameters() {
        assertEquals(
            "https://example.com/a?id=7#top",
            cleanOutgoingLink("https://example.com/a?utm_source=x&id=7&fbclid=y#top"),
        )
        assertEquals(
            "https://x.com/user/status/123",
            cleanOutgoingLink("https://x.com/user/status/123?s=20&t=abcdef123"),
        )
        assertEquals(
            "https://www.instagram.com/p/123/",
            cleanOutgoingLink("https://www.instagram.com/p/123/?igsh=xyz987"),
        )
        assertEquals(
            "https://www.linkedin.com/posts/123",
            cleanOutgoingLink("https://www.linkedin.com/posts/123?trackingId=abc"),
        )
        assertEquals(
            "https://www.reddit.com/r/tech/123",
            cleanOutgoingLink("https://www.reddit.com/r/tech/123?rdt=456"),
        )
    }

    @Test
    fun searchAndTimestampParametersSurviveOutsideX() {
        assertEquals(
            "https://example.com/?s=kotlin",
            cleanOutgoingLink("https://example.com/?s=kotlin"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=abc&t=30",
            cleanOutgoingLink("https://www.youtube.com/watch?v=abc&t=30&utm_source=x"),
        )
        assertEquals(
            "https://twitter.com/user/status/1",
            cleanOutgoingLink("https://twitter.com/user/status/1?s=20&t=zz"),
        )
    }

    @Test
    fun leavesCleanLinksAlone() {
        assertEquals("https://example.com/a?id=7", cleanOutgoingLink("https://example.com/a?id=7"))
        assertEquals("not a url", cleanOutgoingLink("not a url"))
    }

    @Test
    fun readsThePostHandedBackByThePage() {
        val post = parseLiveWebPost("\"{\\\"text\\\":\\\"Hello\\\",\\\"author\\\":\\\"Asha\\\"}\"")
        assertEquals(LiveWebPost("Hello", "Asha"), post)
    }

    @Test
    fun nothingInViewGivesNoPost() {
        assertEquals(null, parseLiveWebPost("\"\""))
        assertEquals(null, parseLiveWebPost("null"))
        assertEquals(null, parseLiveWebPost(null))
    }

    @Test
    fun readerEligibleUrlsIncludeArticlesAndExcludeAppsMediaAndAuth() {
        assertEquals(true, isReaderEligibleUrl("https://arstechnica.com/tech-policy/2026/11/example/"))
        assertEquals(true, isReaderEligibleUrl("http://example.org/blog/post"))
        assertEquals(false, isReaderEligibleUrl("https://play.google.com/store/apps/details?id=org.crux"))
        assertEquals(false, isReaderEligibleUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(false, isReaderEligibleUrl("https://accounts.google.com/o/oauth2/auth"))
        assertEquals(false, isReaderEligibleUrl("https://example.com/report.pdf"))
        assertEquals(false, isReaderEligibleUrl("https://pbs.twimg.com/media/sample.jpg"))
        assertEquals(false, isReaderEligibleUrl("mailto:team@example.com"))
    }

    @Test
    fun readerEligibleUrlsExcludeBioLinksAndStorefronts() {
        assertEquals(false, isReaderEligibleUrl("https://linktr.ee/creator"))
        assertEquals(false, isReaderEligibleUrl("https://beacons.ai/musician"))
        assertEquals(false, isReaderEligibleUrl("https://bento.me/designer"))
        assertEquals(false, isReaderEligibleUrl("https://mybrand.carrd.co"))
        assertEquals(false, isReaderEligibleUrl("https://stan.store/creator"))
        assertEquals(false, isReaderEligibleUrl("https://bio.link/creator"))
        assertEquals(false, isReaderEligibleUrl("https://open.spotify.com/album/12345"))
        assertEquals(false, isReaderEligibleUrl("https://mybrand.com/"))
        assertEquals(false, isReaderEligibleUrl("https://mybrand.com/shop"))
        assertEquals(false, isReaderEligibleUrl("https://mybrand.com/about"))
        assertEquals(false, isReaderEligibleUrl("https://mybrand.com/profile"))
        assertEquals(false, isReaderEligibleUrl("https://mybrand.com/johndoe"))
        assertEquals(false, isReaderEligibleUrl("https://github.com/torvalds/linux"))
        assertEquals(true, isReaderEligibleUrl("https://theverge.com/2026/11/12/iphone-review.html"))
        assertEquals(true, isReaderEligibleUrl("https://medium.com/@author/how-to-build-great-apps-12345"))
    }

    @Test
    fun unwrapsRedditAndFacebookRedirects() {
        assertEquals(
            "https://example.com/post",
            cleanOutgoingLink("https://out.reddit.com/?url=https%3A%2F%2Fexample.com%2Fpost"),
        )
        assertEquals(
            "https://example.com/article",
            cleanOutgoingLink("https://l.facebook.com/?u=https%3A%2F%2Fexample.com%2Farticle"),
        )
    }

    @Test
    fun sessionStateAcceptsOnlySameSiteHttpsUrls() {
        assertEquals(true, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "https://x.com/home"))
        assertEquals(true, LiveWebSessionState.isRestorableUrl(LiveWebSite.Reddit, "https://www.reddit.com/r/Kotlin/"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "https://x.com/"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "https://x.com/i/flow/login"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.Instagram, "https://www.instagram.com/accounts/login/"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.LinkedIn, "https://www.linkedin.com/uas/login"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "http://x.com/home"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "https://reddit.com/r/Kotlin"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "about:blank"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, null))
    }
}
