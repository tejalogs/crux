package org.crux.data.database.app.dao

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FeedUrlNormalizationTest {
    @Test
    fun hostCaseDefaultPortFragmentAndTrailingSlashAreIgnored() {
        val key = normalizeFeedUrl("https://example.com/feed")
        assertEquals(key, normalizeFeedUrl("  HTTPS://Example.COM:443/feed/  "))
        assertEquals(key, normalizeFeedUrl("https://example.com/feed#top"))
        assertEquals("http://example.com", normalizeFeedUrl("http://example.com:80/"))
    }

    @Test
    fun differentPathsQueriesSchemesAndPortsStayDistinct() {
        assertNotEquals(normalizeFeedUrl("https://example.com/feed"), normalizeFeedUrl("https://example.com/Feed"))
        assertNotEquals(normalizeFeedUrl("https://example.com/feed?a=1"), normalizeFeedUrl("https://example.com/feed?a=2"))
        assertNotEquals(normalizeFeedUrl("https://example.com/feed"), normalizeFeedUrl("http://example.com/feed"))
        assertNotEquals(normalizeFeedUrl("https://example.com:8443/feed"), normalizeFeedUrl("https://example.com/feed"))
    }

    @Test
    fun nonUrlsAreOnlyTrimmed() {
        assertEquals("rsshub://x/y", normalizeFeedUrl(" rsshub://x/y "))
        assertEquals("not a url", normalizeFeedUrl(" not a url "))
    }
}
