package org.crux.ui.screen.home

import android.content.res.Configuration
import org.crux.common.downloadToFile
import org.crux.ui.common.InternalDeeplinkToken
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import org.crux.ui.screen.settings.normalizeHardenedBrowserInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun dropsAdClickAndNewsletterIdentifiers() {
        assertEquals(
            "https://example.com/a?id=7",
            cleanOutgoingLink(
                "https://example.com/a?msclkid=1&dclid=2&id=7&yclid=3&ttclid=4&mc_eid=5&mc_cid=6&_hsenc=7&_hsmi=8&gbraid=9&wbraid=10",
            ),
        )
    }

    @Test
    fun dropsSharerIdentifiersOnlyOnTheirOwnSites() {
        assertEquals("https://youtu.be/abc123?t=42", cleanOutgoingLink("https://youtu.be/abc123?si=sharer&t=42"))
        assertEquals(
            "https://www.youtube.com/watch?v=abc123",
            cleanOutgoingLink("https://www.youtube.com/watch?v=abc123&si=sharer"),
        )
        assertEquals(
            "https://music.youtube.com/watch?v=abc123",
            cleanOutgoingLink("https://music.youtube.com/watch?v=abc123&si=sharer"),
        )
        assertEquals("https://open.spotify.com/track/1", cleanOutgoingLink("https://open.spotify.com/track/1?si=sharer"))
        assertEquals(
            "https://www.linkedin.com/posts/123",
            cleanOutgoingLink("https://www.linkedin.com/posts/123?rcm=memberToken"),
        )
        // si and rcm are ordinary parameters on other sites and must survive there.
        assertEquals("https://example.com/search?si=1&rcm=2", cleanOutgoingLink("https://example.com/search?si=1&rcm=2"))
        // A look-alike host is not YouTube.
        assertEquals("https://notyoutube.com/v?si=1", cleanOutgoingLink("https://notyoutube.com/v?si=1"))
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
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.LinkedIn, "https://accounts.google.com/v3/signin/identifier"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "http://x.com/home"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "https://reddit.com/r/Kotlin"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, "about:blank"))
        assertEquals(false, LiveWebSessionState.isRestorableUrl(LiveWebSite.X, null))
    }

    @Test
    fun oauthBackNavigationSkipsGoogleAndOAuthRedirectHops() {
        val history =
            listOf(
                "https://www.linkedin.com/login",
                "https://www.linkedin.com/uas/connect/google",
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=1",
                "https://accounts.google.com/v3/signin/identifier",
            )
        assertEquals(
            BackNavigationAction.GoSteps(-3),
            computeBackNavigationAction(
                currentUrl = "https://accounts.google.com/v3/signin/identifier",
                historyUrls = history,
                currentIndex = 3,
                site = LiveWebSite.LinkedIn,
            ),
        )
        assertEquals(true, computeWebViewCanGoBack(webViewCanGoBack = false, currentUrl = "https://accounts.google.com/v3/signin/identifier"))
    }

    @Test
    fun oauthBackNavigationFallsBackToSiteLoginWhenNoCleanHistoryExists() {
        assertEquals(
            BackNavigationAction.LoadUrl(LiveWebSite.LinkedIn.loginUrl),
            computeBackNavigationAction(
                currentUrl = "https://accounts.google.com/v3/signin/identifier",
                historyUrls = listOf("https://accounts.google.com/o/oauth2/auth", "https://accounts.google.com/v3/signin/identifier"),
                currentIndex = 1,
                site = LiveWebSite.LinkedIn,
            ),
        )
        assertEquals(
            BackNavigationAction.NativeGoBack,
            computeBackNavigationAction(
                currentUrl = "https://www.linkedin.com/feed/",
                historyUrls = listOf("https://www.linkedin.com/login", "https://www.linkedin.com/feed/"),
                currentIndex = 1,
                site = LiveWebSite.LinkedIn,
            ),
        )
    }

    @Test
    fun restoresNightModeMaskWhenWebViewStripsUiMode() {
        val baseOtherBits = Configuration.UI_MODE_TYPE_NORMAL
        val lightUiMode = baseOtherBits or Configuration.UI_MODE_NIGHT_NO
        val darkUiMode = baseOtherBits or Configuration.UI_MODE_NIGHT_YES

        assertEquals(
            darkUiMode,
            computeRestoredNightUiMode(
                currentUiMode = lightUiMode,
                expectedNightMask = Configuration.UI_MODE_NIGHT_YES,
            ),
        )
        assertEquals(
            lightUiMode,
            computeRestoredNightUiMode(
                currentUiMode = darkUiMode,
                expectedNightMask = Configuration.UI_MODE_NIGHT_NO,
            ),
        )
        assertNull(
            computeRestoredNightUiMode(
                currentUiMode = darkUiMode,
                expectedNightMask = Configuration.UI_MODE_NIGHT_YES,
            ),
        )
        assertNull(
            computeRestoredNightUiMode(
                currentUiMode = lightUiMode,
                expectedNightMask = Configuration.UI_MODE_NIGHT_UNDEFINED,
            ),
        )
    }

    @Test
    fun mainFrameUrlRoutingProtectsAllFourPlatformsAgainstAppBouncesAndSubframeHijacks() {
        // Subframes are never hijacked
        assertEquals(
            MainFrameUrlAction.AllowInWebView,
            decideMainFrameUrlAction(
                rawUrl = "https://external.example.com/embed",
                site = LiveWebSite.Instagram,
                cleanLinks = true,
                hasGesture = false,
                isForMainFrame = false,
            ),
        )

        // Same-site navigation stays inside WebView across all 4 platforms
        assertEquals(
            MainFrameUrlAction.AllowInWebView,
            decideMainFrameUrlAction("https://www.instagram.com/natgeo/reel/C1abc/", LiveWebSite.Instagram, true, true),
        )
        assertEquals(
            MainFrameUrlAction.AllowInWebView,
            decideMainFrameUrlAction("https://x.com/i/flow/login", LiveWebSite.X, true, true),
        )
        assertEquals(
            MainFrameUrlAction.AllowInWebView,
            decideMainFrameUrlAction("https://www.linkedin.com/feed/", LiveWebSite.LinkedIn, true, true),
        )
        assertEquals(
            MainFrameUrlAction.AllowInWebView,
            decideMainFrameUrlAction("https://www.reddit.com/r/android/comments/abc/post/", LiveWebSite.Reddit, true, true),
        )

        // Same-site HTTP links upgrade in-place to HTTPS instead of spawning duplicate screens
        assertEquals(
            MainFrameUrlAction.LoadInWebView("https://www.reddit.com/r/android/"),
            decideMainFrameUrlAction("http://www.reddit.com/r/android/", LiveWebSite.Reddit, true, true),
        )
        assertEquals(
            MainFrameUrlAction.LoadInWebView("https://www.instagram.com/natgeo/"),
            decideMainFrameUrlAction("http://www.instagram.com/natgeo/", LiveWebSite.Instagram, true, true),
        )

        // Signed-out X root recovers to direct login flow
        assertEquals(
            MainFrameUrlAction.LoadInWebView(LiveWebSite.X.loginUrl),
            decideMainFrameUrlAction("https://x.com/", LiveWebSite.X, true, false),
        )

        // Same-site tracking redirects unwrap cleanly in-place
        assertEquals(
            MainFrameUrlAction.LoadInWebView("https://www.instagram.com/p/abc123/"),
            decideMainFrameUrlAction(
                "https://l.instagram.com/?u=https%3A%2F%2Fwww.instagram.com%2Fp%2Fabc123%2F%3Figsh%3Dxyz",
                LiveWebSite.Instagram,
                true,
                true,
            ),
        )
        assertEquals(
            MainFrameUrlAction.LoadInWebView("https://www.linkedin.com/posts/abc"),
            decideMainFrameUrlAction(
                "https://www.linkedin.com/redir/redirect?url=https%3A%2F%2Fwww.linkedin.com%2Fposts%2Fabc%3Ftrk%3D1",
                LiveWebSite.LinkedIn,
                true,
                true,
            ),
        )

        // External http(s) links hand off outside WebView
        assertEquals(
            MainFrameUrlAction.OpenOutside("https://example.com/news"),
            decideMainFrameUrlAction("https://example.com/news", LiveWebSite.Reddit, true, true),
        )

        // Native app / Play Store bounce schemes stay blocked even on tap
        listOf(
            LiveWebSite.Instagram to "instagram://reel?id=123",
            LiveWebSite.LinkedIn to "linkedin://feed",
            LiveWebSite.Reddit to "reddit://r/android",
            LiveWebSite.X to "twitter://timeline",
            LiveWebSite.Instagram to "market://details?id=com.instagram.android",
            LiveWebSite.LinkedIn to "intent://linkedin.com/#Intent;scheme=https;end",
        ).forEach { (site, uri) ->
            assertEquals(
                "$site must block $uri",
                MainFrameUrlAction.Block,
                decideMainFrameUrlAction(uri, site, true, hasGesture = true),
            )
        }

        // mailto/tel require explicit user gesture
        assertEquals(
            MainFrameUrlAction.OpenDialOrMail("mailto:hi@example.com"),
            decideMainFrameUrlAction("mailto:hi@example.com", LiveWebSite.LinkedIn, true, hasGesture = true),
        )
        assertEquals(
            MainFrameUrlAction.Block,
            decideMainFrameUrlAction("mailto:hi@example.com", LiveWebSite.LinkedIn, true, hasGesture = false),
        )
    }

    @Test
    fun compactHostPathFormatsCleanTopBarSubtitles() {
        assertEquals("instagram.com/natgeo", formatCompactHostPath("https://www.instagram.com/natgeo/?igsh=1"))
        assertEquals("accounts.google.com/v3/signin", formatCompactHostPath("https://accounts.google.com/v3/signin?hl=en"))
        assertEquals("x.com", formatCompactHostPath("https://x.com/"))
        assertNull(formatCompactHostPath("about:blank"))
        assertNull(formatCompactHostPath(""))
        assertNull(formatCompactHostPath(null))
    }

    @Test
    fun cleanOutgoingLinkBlocksSchemeSmugglingAndSpoofedLinkedInSuffixes() {
        val smuggledCrux = "https://l.instagram.com/?u=crux%3A%2F%2Fabcdef"
        assertEquals(smuggledCrux, cleanOutgoingLink(smuggledCrux))

        val smuggledFile = "https://out.reddit.com/?url=file%3A%2F%2F%2Fetc%2Fpasswd"
        assertEquals(smuggledFile, cleanOutgoingLink(smuggledFile))

        val spoofedLinkedIn = "https://evillinkedin.com/safety/go?url=https%3A%2F%2Fexample.com"
        assertEquals(spoofedLinkedIn, cleanOutgoingLink(spoofedLinkedIn))
    }

    @Test
    fun sanitizeSiteInitialUrlEnforcesSameSiteHttpsOnly() {
        assertEquals(
            "https://www.instagram.com/natgeo/",
            sanitizeSiteInitialUrl(LiveWebSite.Instagram, "http://www.instagram.com/natgeo/"),
        )
        assertEquals(
            LiveWebSite.Instagram.startUrlForSession(),
            sanitizeSiteInitialUrl(LiveWebSite.Instagram, "https://evil.example.com/phish"),
        )
        assertEquals(
            LiveWebSite.X.startUrlForSession(),
            sanitizeSiteInitialUrl(LiveWebSite.X, "javascript:alert(1)"),
        )
        assertEquals(
            LiveWebSite.LinkedIn.startUrlForSession(),
            sanitizeSiteInitialUrl(LiveWebSite.LinkedIn, "file:///etc/hosts"),
        )
    }

    @Test
    fun hardenedBrowserInputUpgradesHttpAndRejectsNonWebSchemes() {
        assertEquals("https://example.com/path", normalizeHardenedBrowserInput("http://example.com/path"))
        assertEquals("https://example.com", normalizeHardenedBrowserInput("example.com"))
        assertNull(normalizeHardenedBrowserInput("file:///data/local/tmp"))
        assertNull(normalizeHardenedBrowserInput("javascript://alert(1)"))
        assertNull(normalizeHardenedBrowserInput("crux://abcdef"))
    }

    @Test
    fun externalDeeplinksAllowOnlySafeEntryRoutesAndBlockPrivilegedInternalRoutes() {
        val composeUri = DeeplinkRoute.Compose.Crux(initialText = "hello").toUri()
        val loginUri = DeeplinkRoute.Login.toUri()
        val mediaFileUri = DeeplinkRoute.Media.Image(uri = "file:///data/data/org.crux.social/files/x", previewUrl = null).toUri()
        val rssInjectUri = DeeplinkRoute.Rss.Detail(url = "http://127.0.0.1/", descriptionHtml = "<b>spoof</b>").toUri()
        val openDirectUri = DeeplinkRoute.OpenLinkDirectly(url = "https://evil.example").toUri()

        assertTrue(InternalDeeplinkToken.isAllowedDeeplink(composeUri, isTrustedInternal = false))
        assertTrue(InternalDeeplinkToken.isAllowedDeeplink(loginUri, isTrustedInternal = false))
        assertFalse(InternalDeeplinkToken.isAllowedDeeplink(mediaFileUri, isTrustedInternal = false))
        assertFalse(InternalDeeplinkToken.isAllowedDeeplink(rssInjectUri, isTrustedInternal = false))
        assertFalse(InternalDeeplinkToken.isAllowedDeeplink(openDirectUri, isTrustedInternal = false))

        // Trusted in-process stamped intents continue to work for internal navigation.
        assertTrue(InternalDeeplinkToken.isAllowedDeeplink(mediaFileUri, isTrustedInternal = true))
        assertTrue(InternalDeeplinkToken.isAllowedDeeplink(rssInjectUri, isTrustedInternal = true))
        assertTrue(InternalDeeplinkToken.isAllowedDeeplink(openDirectUri, isTrustedInternal = true))
    }

    @Test
    fun bookmarkExtractionStripsNonHttpSchemesFromUrlAvatarAndMedia() {
        val raw =
            "\"{\\\"title\\\":\\\"Post\\\",\\\"authorName\\\":\\\"Alice\\\",\\\"authorHandle\\\":\\\"@alice\\\"," +
                "\\\"avatar\\\":\\\"file:///etc/passwd\\\",\\\"text\\\":\\\"Body\\\"," +
                "\\\"url\\\":\\\"javascript:alert(1)\\\",\\\"media\\\":[\\\"file:///a.png\\\",\\\"https://pbs.twimg.com/img.jpg\\\"]}\""
        val parsed = parseExtractedSocialPost(LiveWebSite.X, raw, "https://x.com/alice/status/1")
        assertEquals("https://x.com/alice/status/1", parsed.sourceUrl)
        assertNull(parsed.authorAvatarUrl)
        assertEquals(listOf("https://pbs.twimg.com/img.jpg"), parsed.mediaUrls)
    }

    @Test
    fun mediaShareDownloaderRefusesFileAndLoopbackUrls() {
        val out = File.createTempFile("crux-share-test", ".tmp")
        try {
            assertFalse(downloadToFile("file:///etc/hosts", out, null))
            assertFalse(downloadToFile("http://127.0.0.1:8080/secret.png", out, null))
            assertFalse(downloadToFile("http://169.254.169.254/latest/meta-data/", out, null))
        } finally {
            out.delete()
        }
    }
}
