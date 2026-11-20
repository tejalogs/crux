package org.crux.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class PublicHttpTest {
    @Test
    fun resolvesRelativeAndAbsoluteRedirects() {
        assertEquals("https://example.com/feed.xml", resolveRedirect("https://example.com/blog/rss", "/feed.xml"))
        assertEquals("https://cdn.example.org/a.xml", resolveRedirect("https://example.com/rss", "https://cdn.example.org/a.xml"))
    }
}
