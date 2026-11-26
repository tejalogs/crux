package org.crux.ui.screen.home

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class LiveWebRequestCoverageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val counters get() = LiveWebPrivacyCounters.get(context)

    @Before
    fun clearLog() {
        LiveWebContactLog.clear()
    }

    private fun hostsOf(site: LiveWebSite) = LiveWebContactLog.snapshot().firstOrNull { it.site == site }?.hosts.orEmpty()

    @Test
    fun standardSitesAreObservedButNeverBlocked() {
        val config = LiveWebConfig.defaultsFor(LiveWebSite.X)
        assertEquals(ProtectionLevel.Standard, config.protection)
        assertNull(siteRequestResponse(LiveWebSite.X, config, "www.google-analytics.com", counters))
        assertEquals(
            listOf(ContactedHost("www.google-analytics.com", thirdParty = true, requests = 1, blocked = 0)),
            hostsOf(LiveWebSite.X),
        )
    }

    @Test
    fun strictSitesRefuseTrackersAndRecordTheBlock() {
        val config = LiveWebConfig.defaultsFor(LiveWebSite.Reddit)
        assertEquals(ProtectionLevel.Strict, config.protection)
        val before = counters.blockedRequestsBySite.value[LiveWebSite.Reddit] ?: 0L
        val response = siteRequestResponse(LiveWebSite.Reddit, config, "stats.g.doubleclick.net", counters)
        assertNotNull(response)
        assertEquals(204, response!!.statusCode)
        assertEquals(before + 1, counters.blockedRequestsBySite.value[LiveWebSite.Reddit])
        assertNull(siteRequestResponse(LiveWebSite.Reddit, config, "www.reddit.com", counters))
        val hosts = hostsOf(LiveWebSite.Reddit)
        assertEquals(1, hosts.first { it.host == "stats.g.doubleclick.net" }.blocked)
        assertEquals(0, hosts.first { it.host == "www.reddit.com" }.blocked)
    }

    @Test
    fun sharedServiceWorkerRequestsAreMatchedToTheirSite() {
        assertEquals(
            LiveWebSite.Reddit,
            siteForSharedServiceWorkerRequest("https://stats.g.doubleclick.net/x", "https://www.reddit.com"),
        )
        assertEquals(LiveWebSite.X, siteForSharedServiceWorkerRequest("https://abs.twimg.com/sw.js", null))
        assertNull(siteForSharedServiceWorkerRequest("https://example.org/sw.js", "https://example.org/"))
    }

    @Test
    fun sharedServiceWorkerHostsAreNeverCreditedToTheFirstSiteInLine() {
        // Google sign-in is allowed by several sites: without an Origin it belongs to none of them.
        assertNull(siteForSharedServiceWorkerRequest("https://accounts.google.com/gsi/client", null))
        // Nor is a worker of the sign-in page itself any one site's.
        assertNull(siteForSharedServiceWorkerRequest("https://www.gstatic.com/x.js", "https://accounts.google.com"))
        // A non-site page's worker is left alone even when it fetches a site's host.
        assertNull(siteForSharedServiceWorkerRequest("https://abs.twimg.com/x.js", "https://example.org"))
    }

    @Test
    fun aSiteOnlyTrackerWithoutOriginStillFindsItsSite() {
        assertEquals(LiveWebSite.Instagram, siteForSharedServiceWorkerRequest("https://connect.facebook.net/en_US/fbevents.js", null))
        assertEquals(LiveWebSite.LinkedIn, siteForSharedServiceWorkerRequest("https://snap.licdn.com/li.lms-analytics/insight.min.js", null))
    }

    @Test
    fun concurrentBlocksFromPageAndServiceWorkerAreAllCounted() {
        val config = LiveWebConfig.defaultsFor(LiveWebSite.Reddit)
        val before = counters.blockedRequestsBySite.value[LiveWebSite.Reddit] ?: 0L
        val beforeTotal = counters.blockedRequests.value
        val threads =
            (1..4).map {
                Thread { repeat(250) { siteRequestResponse(LiveWebSite.Reddit, config, "stats.g.doubleclick.net", counters) } }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(before + 1_000, counters.blockedRequestsBySite.value[LiveWebSite.Reddit])
        assertEquals(beforeTotal + 1_000, counters.blockedRequests.value)
    }

    @Test
    fun webViewsNotBuiltForASiteHaveNoSiteAndNoPage() {
        val plain = android.webkit.WebView(context)
        assertNull(plain.siteOfWebView())
        assertNull(liveWebPageOf(plain))
    }

    @Test
    fun mediaMuteMarkerNeverNamesCrux() {
        val scripts = mediaFreezeScripts()
        val marker = PageMarkers.attr("media-muted")
        scripts.forEach { script ->
            assertTrue(script.contains(marker))
            assertFalse(script.contains("crux", ignoreCase = true))
            assertFalse(script.contains("cx", ignoreCase = true))
        }
    }
}
