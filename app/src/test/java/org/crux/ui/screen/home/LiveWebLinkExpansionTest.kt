package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebLinkExpansionTest {
    private val tco = "https://t.co/AbC123xyz"

    @Test
    fun xLinkTextGivesTheFullAddressWithNoLookup() {
        // X's anchor: hidden "https://", the visible part, the hidden rest, then an ellipsis.
        val text = "https://example.com/news/2026/11/a-long-story-slug?id=7…"
        assertEquals("https://example.com/news/2026/11/a-long-story-slug?id=7", expandedShortLinkFromAnchor(tco, text))
    }

    @Test
    fun shortTextWithNoEllipsisIsUsedAsIs() {
        assertEquals("https://github.com/org/repo", expandedShortLinkFromAnchor(tco, "  https://github.com/org/repo  "))
        assertEquals("http://example.org/a", expandedShortLinkFromAnchor(tco, "http://example.org/a..."))
    }

    @Test
    fun truncatedOrDomainOnlyTextFallsBackToTheLookup() {
        // Visible-only text (innerText) has no scheme; card links show a domain only.
        assertNull(expandedShortLinkFromAnchor(tco, "example.com/news/sto…"))
        assertNull(expandedShortLinkFromAnchor(tco, "example.com"))
        assertNull(expandedShortLinkFromAnchor(tco, "From example.com"))
        assertNull(expandedShortLinkFromAnchor(tco, ""))
        assertNull(expandedShortLinkFromAnchor(tco, null))
        assertNull(expandedShortLinkFromAnchor(tco))
    }

    @Test
    fun onlyWholeHttpUrlsAreAccepted() {
        assertNull(expandedShortLinkFromAnchor(tco, "javascript:alert(1)"))
        assertNull(expandedShortLinkFromAnchor(tco, "intent://scan/#Intent;scheme=zxing;end"))
        assertNull(expandedShortLinkFromAnchor(tco, "ftp://example.com/file"))
        assertNull(expandedShortLinkFromAnchor(tco, "https:///no-host"))
        assertNull(expandedShortLinkFromAnchor(tco, "https://example.com/a b"))
        assertNull(expandedShortLinkFromAnchor(tco, "mailto:someone@example.com"))
    }

    @Test
    fun anotherShortenerInTheTextStillNeedsTheLookup() {
        assertNull(expandedShortLinkFromAnchor(tco, "https://bit.ly/3xyz"))
        assertNull(expandedShortLinkFromAnchor(tco, "https://t.co/other"))
    }

    @Test
    fun laterCandidatesAreTriedWhenTheTextIsNotAUrl() {
        val title = "https://example.com/news/full-story"
        assertEquals(title, expandedShortLinkFromAnchor(tco, "example.com/news/fu…", title))
        assertEquals(title, expandedShortLinkFromAnchor(tco, null, null, title))
    }

    @Test
    fun onlyTcoAnchorsAreTrusted() {
        // lnkd.in and bit.ly anchors show the short address itself; never expand from their text.
        assertNull(expandedShortLinkFromAnchor("https://lnkd.in/abc", "https://example.com/story"))
        assertNull(expandedShortLinkFromAnchor("https://bit.ly/abc", "https://example.com/story"))
        assertNull(expandedShortLinkFromAnchor("https://example.com/x", "https://example.com/story"))
        assertNull(expandedShortLinkFromAnchor("https://microsoft.co/abc", "https://example.com/story"))
        assertEquals("https://example.com/story", expandedShortLinkFromAnchor("https://www.t.co/abc", "https://example.com/story"))
    }

    @Test
    fun anchorTextFromAnEarlierTapIsIgnored() {
        assertEquals("https://example.com/a", anchorTextForTappedLink(tco, tco, "https://example.com/a"))
        assertEquals("https://example.com/a", anchorTextForTappedLink(" $tco", "$tco ", "https://example.com/a"))
        assertNull(anchorTextForTappedLink(tco, "https://t.co/someOtherLink", "https://example.com/b"))
        assertNull(anchorTextForTappedLink(tco, null, "https://example.com/b"))
        assertNull(anchorTextForTappedLink(tco, tco, null))
    }

    @Test
    fun lookupIsCappedAtThreeSecondsAndFeedbackComesFirst() {
        assertTrue(SHORT_LINK_TIMEOUT_MS <= 3_000L)
        assertTrue(SHORT_LINK_FEEDBACK_DELAY_MS in 1 until SHORT_LINK_TIMEOUT_MS)
        assertTrue("feedback must feel immediate", SHORT_LINK_FEEDBACK_DELAY_MS <= 300L)
    }

    @Test
    fun privateOrLocalAddressesInTheAnchorAreRefused() {
        // The reader would fetch these; the lookup path refuses them, so the anchor path must too.
        assertNull(expandedShortLinkFromAnchor(tco, "http://192.168.1.1/a-b"))
        assertNull(expandedShortLinkFromAnchor(tco, "http://10.0.0.5/admin/page-one"))
        assertNull(expandedShortLinkFromAnchor(tco, "http://127.0.0.1:8080/x"))
        assertNull(expandedShortLinkFromAnchor(tco, "http://localhost/story-one"))
        assertNull(expandedShortLinkFromAnchor(tco, "http://[::1]/story-one"))
        assertNull(expandedShortLinkFromAnchor(tco, "http://router.local/story-one"))
        assertNull(expandedShortLinkFromAnchor(tco, "http://2130706433/story-one"))
        assertNull(expandedShortLinkFromAnchor(tco, "https://user:pass@example.com/story"))
        // A private candidate does not stop a later public one.
        assertEquals(
            "https://example.com/story",
            expandedShortLinkFromAnchor(tco, "http://192.168.1.1/a-b", "https://example.com/story"),
        )
    }

    @Test
    fun anchorTextIsTrustedOnlyOnXsOwnPages() {
        assertTrue(anchorExpansionTrustedOn("https://x.com/home"))
        assertTrue(anchorExpansionTrustedOn("https://x.com/someone/status/1"))
        assertTrue(anchorExpansionTrustedOn("https://mobile.twitter.com/someone/status/1"))
        // Other sites (and the Hardened Browser) write their own anchor text.
        assertFalse(anchorExpansionTrustedOn("https://www.reddit.com/r/android/comments/abc/"))
        assertFalse(anchorExpansionTrustedOn("https://www.linkedin.com/feed/"))
        assertFalse(anchorExpansionTrustedOn("https://other.example/"))
        // X's tab allows shared sign-in pages, but they are not X's.
        assertFalse(anchorExpansionTrustedOn("https://accounts.google.com/signin"))
        // Look-alikes, excluded X subdomains, plain http and no page at all.
        assertFalse(anchorExpansionTrustedOn("https://notx.com/home"))
        assertFalse(anchorExpansionTrustedOn("https://x.com.evil.example/home"))
        assertFalse(anchorExpansionTrustedOn("https://developer.x.com/en"))
        assertFalse(anchorExpansionTrustedOn("http://x.com/home"))
        assertFalse(anchorExpansionTrustedOn(null))
        assertFalse(anchorExpansionTrustedOn("about:blank"))
    }

    @Test
    fun aTcoLinkWithItsAddressInTheAnchorOpensWithNoLookup() {
        val plan = planOutboundOpen(tco, clean = true, anchorExpansion = "https://example.com/news/full-story?utm_source=twitter")
        assertEquals(OutboundOpen.Now(url = "https://example.com/news/full-story", countedFrom = tco), plan)
    }

    @Test
    fun aShortLinkWithoutAUsableAnchorNeedsTheLookup() {
        assertEquals(OutboundOpen.AfterLookup(tco), planOutboundOpen(" $tco ", clean = true, anchorExpansion = null))
        assertEquals(OutboundOpen.AfterLookup(tco), planOutboundOpen(tco, clean = true, anchorExpansion = "example.com/sto…"))
        assertEquals(
            OutboundOpen.AfterLookup("https://lnkd.in/g-Ab12Cd"),
            planOutboundOpen("https://lnkd.in/g-Ab12Cd", clean = true, anchorExpansion = "https://example.com/story"),
        )
    }

    @Test
    fun withCleaningOffOrForOrdinaryLinksNothingIsLookedUp() {
        assertEquals(OutboundOpen.Now(tco, tco), planOutboundOpen(tco, clean = false, anchorExpansion = "https://example.com/story"))
        val plain = "https://example.com/news/story-one"
        assertEquals(OutboundOpen.Now(plain, plain), planOutboundOpen(plain, clean = true, anchorExpansion = null))
    }

    @Test
    fun shortenerLinksNeverOpenInTheReaderUnexpanded() {
        // lnkd.in codes often hold '-' or '_', which a one-segment slug check would accept.
        assertFalse(isReaderEligibleUrl("https://lnkd.in/g-Ab12Cd"))
        assertFalse(isReaderEligibleUrl("https://lnkd.in/g_Ab12Cd"))
        assertFalse(isReaderEligibleUrl("https://www.lnkd.in/g-Ab12Cd"))
        assertFalse(isReaderEligibleUrl("https://t.co/a-b_c"))
        assertFalse(isReaderEligibleUrl("https://bit.ly/my-launch-post"))
        // Their expanded targets still can.
        assertTrue(isReaderEligibleUrl("https://example.com/news/2026/11/a-long-story"))
    }
}
