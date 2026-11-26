package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebContactLogTest {
    private var now = 1_000L
    private val midnight = 10_000L

    private fun book(max: Int = MAX_CONTACTED_HOSTS_PER_SITE) =
        ContactLogBook(maxHostsPerSite = max, clock = { now }, nextMidnight = { t -> if (t < midnight) midnight else t + 86_400_000L })

    private fun ContactLogBook.hostsOf(site: LiveWebSite): List<ContactedHost> = snapshot().firstOrNull { it.site == site }?.hosts.orEmpty()

    @Test
    fun registrableDomainKeepsTheOwnerOfAHost() {
        assertEquals("doubleclick.net", registrableDomain("stats.g.doubleclick.net"))
        assertEquals("instagram.com", registrableDomain("www.instagram.com"))
        assertEquals("x.com", registrableDomain("x.com"))
        assertEquals("google.co.in", registrableDomain("www.google.co.in"))
        assertEquals("bbc.co.uk", registrableDomain("static.files.bbc.co.uk."))
        assertEquals("10.0.0.1", registrableDomain("10.0.0.1"))
    }

    @Test
    fun sitesOwnHostsAreFirstPartyAndEverythingElseIsThird() {
        assertFalse(isThirdPartyHost(LiveWebSite.Instagram, "scontent.cdninstagram.com"))
        assertFalse(isThirdPartyHost(LiveWebSite.Instagram, "static.xx.fbcdn.net"))
        assertFalse(isThirdPartyHost(LiveWebSite.X, "pbs.twimg.com"))
        assertFalse(isThirdPartyHost(LiveWebSite.LinkedIn, "static.licdn.com"))
        assertFalse(isThirdPartyHost(LiveWebSite.Reddit, "alb.reddit.com"))
        assertTrue(isThirdPartyHost(LiveWebSite.X, "www.googletagmanager.com"))
        assertTrue(isThirdPartyHost(LiveWebSite.LinkedIn, "bat.bing.com"))
        // Sign-in and captcha services the site allows are still someone else's.
        assertTrue(isThirdPartyHost(LiveWebSite.Reddit, "accounts.google.com"))
        assertTrue(isThirdPartyHost(LiveWebSite.X, "client-api.arkoselabs.com"))
        assertTrue(isThirdPartyHost(LiveWebSite.Reddit, "www.google.co.in"))
    }

    @Test
    fun countsRequestsAndBlocksPerHost() {
        val log = book()
        repeat(3) { log.record(LiveWebSite.Reddit, "www.reddit.com", blocked = false) }
        log.record(LiveWebSite.Reddit, "stats.g.doubleclick.net", blocked = true)
        log.record(LiveWebSite.Reddit, "stats.g.doubleclick.net", blocked = true)
        val hosts = log.hostsOf(LiveWebSite.Reddit)
        assertEquals(ContactedHost("stats.g.doubleclick.net", thirdParty = true, requests = 2, blocked = 2), hosts[0])
        assertEquals(ContactedHost("www.reddit.com", thirdParty = false, requests = 3, blocked = 0), hosts[1])
    }

    @Test
    fun sitesAreKeptApartAndHostsAreNormalised() {
        val log = book()
        log.record(LiveWebSite.X, "X.COM.", blocked = false)
        log.record(LiveWebSite.X, "x.com", blocked = false)
        log.record(LiveWebSite.LinkedIn, "x.com", blocked = false)
        assertEquals(listOf(ContactedHost("x.com", thirdParty = false, requests = 2, blocked = 0)), log.hostsOf(LiveWebSite.X))
        assertEquals(listOf(ContactedHost("x.com", thirdParty = true, requests = 1, blocked = 0)), log.hostsOf(LiveWebSite.LinkedIn))
    }

    @Test
    fun requestsWithoutAHostAreIgnored() {
        val log = book()
        log.record(LiveWebSite.X, null, blocked = false)
        log.record(LiveWebSite.X, "", blocked = true)
        assertTrue(log.snapshot().isEmpty())
    }

    @Test
    fun thirdPartiesComeFirstThenTheBusiestHosts() {
        val log = book()
        log.record(LiveWebSite.X, "x.com", blocked = false)
        repeat(5) { log.record(LiveWebSite.X, "abs.twimg.com", blocked = false) }
        log.record(LiveWebSite.X, "www.google-analytics.com", blocked = false)
        repeat(2) { log.record(LiveWebSite.X, "ads.adsrvr.org", blocked = false) }
        assertEquals(
            listOf("ads.adsrvr.org", "www.google-analytics.com", "abs.twimg.com", "x.com"),
            log.hostsOf(LiveWebSite.X).map { it.host },
        )
    }

    @Test
    fun hostsPastTheLimitAreOnlyCounted() {
        val log = book(max = 2)
        log.record(LiveWebSite.X, "a.example", blocked = false)
        log.record(LiveWebSite.X, "b.example", blocked = false)
        log.record(LiveWebSite.X, "c.example", blocked = false)
        log.record(LiveWebSite.X, "d.example", blocked = true)
        // A host already listed keeps counting.
        log.record(LiveWebSite.X, "a.example", blocked = false)
        val contacts = log.snapshot().single()
        assertEquals(listOf("a.example", "b.example"), contacts.hosts.map { it.host })
        assertEquals(2, contacts.hosts.first { it.host == "a.example" }.requests)
        assertEquals(2, contacts.droppedRequests)
    }

    @Test
    fun onlyNewHostsAndClearsBumpTheChangeCounter() {
        val log = book()
        val start = log.changes.value
        log.record(LiveWebSite.X, "x.com", blocked = false)
        val afterNewHost = log.changes.value
        log.record(LiveWebSite.X, "x.com", blocked = false)
        assertEquals(start + 1, afterNewHost)
        assertEquals(afterNewHost, log.changes.value)
        log.clear()
        assertEquals(afterNewHost + 1, log.changes.value)
    }

    @Test
    fun clearEmptiesEverySite() {
        val log = book()
        log.record(LiveWebSite.X, "x.com", blocked = false)
        log.record(LiveWebSite.Reddit, "www.reddit.com", blocked = false)
        log.clear()
        assertTrue(log.snapshot().isEmpty())
    }

    @Test
    fun theTallyStartsOverAtMidnight() {
        val log = book()
        log.record(LiveWebSite.X, "x.com", blocked = false)
        now = midnight + 5
        assertNull(log.snapshot().firstOrNull { it.site == LiveWebSite.X })
        log.record(LiveWebSite.X, "abs.twimg.com", blocked = false)
        assertEquals(listOf("abs.twimg.com"), log.hostsOf(LiveWebSite.X).map { it.host })
    }

    @Test
    fun theMidnightRolloverWipesOnlyOnce() {
        val log = book()
        log.record(LiveWebSite.X, "x.com", blocked = false)
        val beforeMidnight = log.changes.value
        now = midnight + 5
        log.snapshot()
        log.snapshot()
        log.record(LiveWebSite.X, "x.com", blocked = false)
        log.record(LiveWebSite.X, "x.com", blocked = true)
        // One bump for the rollover, one for x.com appearing again in the new day.
        assertEquals(beforeMidnight + 2, log.changes.value)
        assertEquals(listOf(ContactedHost("x.com", thirdParty = false, requests = 2, blocked = 1)), log.hostsOf(LiveWebSite.X))
    }

    @Test
    fun manyThreadsRecordingTheSameHostLoseNoCounts() {
        val log = book()
        val threads =
            (1..8).map {
                Thread { repeat(1_000) { log.record(LiveWebSite.Reddit, "www.reddit.com", blocked = false) } }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(8_000, log.hostsOf(LiveWebSite.Reddit).single().requests)
    }

    @Test
    fun nextLocalMidnightIsWithinADayAhead() {
        val t = 1_764_000_000_000L
        val next = nextLocalMidnight(t)
        assertTrue(next > t)
        assertTrue(next - t <= 25L * 60 * 60 * 1000)
    }
}
