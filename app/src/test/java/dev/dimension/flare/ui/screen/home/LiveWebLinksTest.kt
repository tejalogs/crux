package dev.dimension.flare.ui.screen.home

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
