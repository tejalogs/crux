package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWebPrivacyCountersTest {
    @Test
    fun unchangedLinkRemovesNothing() {
        assertEquals(0, linkCleaningCount("https://example.com/a?id=7", "https://example.com/a?id=7"))
    }

    @Test
    fun countsDroppedParameters() {
        val original = "https://example.com/a?utm_source=x&id=7&fbclid=y"
        assertEquals(2, linkCleaningCount(original, cleanOutgoingLink(original)))
    }

    @Test
    fun countsAnUnwrappedTrackingRedirect() {
        val original = "https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fpage&e=abc"
        val cleaned = cleanOutgoingLink(original)
        assertEquals("https://example.com/page", cleaned)
        assertEquals(1, linkCleaningCount(original, cleaned))
    }
}
