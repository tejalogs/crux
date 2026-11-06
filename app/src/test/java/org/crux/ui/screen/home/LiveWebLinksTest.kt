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
}
