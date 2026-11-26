package org.crux.ui.screen.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingShortLinksTest {
    private class RecordingFeedback(
        val name: String,
        val events: MutableList<String>,
    ) : ShortLinkFeedback {
        override fun show() {
            events += "show $name"
        }

        override fun dismiss() {
            events += "dismiss $name"
        }
    }

    private class Harness(
        test: TestScope,
    ) {
        val lookups = mutableMapOf<String, CompletableDeferred<String>>()
        val lookedUp = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val events = mutableListOf<String>()
        var foreground = true
        val links =
            PendingShortLinks(
                scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler)),
                feedbackDelayMs = 250L,
                lookup = { url ->
                    lookedUp += url
                    lookups.getOrPut(url) { CompletableDeferred() }.await()
                },
            )

        fun tap(
            url: String,
            feedback: String = url,
        ) = links.open(url, RecordingFeedback(feedback, events), isForeground = { foreground }) { opened += it }

        fun answer(
            url: String,
            target: String,
        ) {
            lookups.getOrPut(url) { CompletableDeferred() }.complete(target)
        }
    }

    private val a = "https://t.co/aaa"
    private val b = "https://t.co/bbb"

    @Test
    fun aQuickAnswerOpensWithNoNote() =
        runTest {
            val h = Harness(this)
            h.tap(a)
            runCurrent()
            h.answer(a, "https://example.com/a")
            advanceTimeBy(100L)
            runCurrent()
            assertEquals(listOf("https://example.com/a"), h.opened)
            assertTrue(h.events.isEmpty())
            assertNull(h.links.pendingLink)
        }

    @Test
    fun aSlowLookupShowsTheNoteAndDismissesItWhenTheLinkOpens() =
        runTest {
            val h = Harness(this)
            h.tap(a)
            runCurrent()
            assertEquals(a, h.links.pendingLink)
            advanceTimeBy(251L)
            runCurrent()
            assertEquals(listOf("show $a"), h.events)
            h.answer(a, "https://example.com/a")
            runCurrent()
            assertEquals(listOf("show $a", "dismiss $a"), h.events)
            assertEquals(listOf("https://example.com/a"), h.opened)
        }

    @Test
    fun aSecondTapOnTheSameLinkShowsTheNoteAgainWithoutASecondLookup() =
        runTest {
            val h = Harness(this)
            h.tap(a, feedback = "first")
            runCurrent()
            h.tap(" $a ", feedback = "second")
            runCurrent()
            assertEquals(listOf(a), h.lookedUp)
            assertEquals(listOf("show second"), h.events)
            h.answer(a, "https://example.com/a")
            runCurrent()
            assertEquals(listOf("show second", "dismiss second"), h.events)
            assertEquals(listOf("https://example.com/a"), h.opened)
        }

    @Test
    fun theLatestTapWins() =
        runTest {
            val h = Harness(this)
            h.tap(a)
            runCurrent()
            advanceTimeBy(300L)
            runCurrent()
            h.tap(b)
            runCurrent()
            // The first link's note goes with it.
            assertEquals(listOf("show $a", "dismiss $a"), h.events)
            h.answer(a, "https://example.com/a")
            h.answer(b, "https://example.com/b")
            runCurrent()
            assertEquals(listOf("https://example.com/b"), h.opened)
        }

    @Test
    fun openingAnyOtherLinkDropsTheWaitingLookup() =
        runTest {
            val h = Harness(this)
            h.tap(a)
            runCurrent()
            advanceTimeBy(300L)
            runCurrent()
            h.links.cancel()
            assertNull(h.links.pendingLink)
            h.answer(a, "https://example.com/a")
            runCurrent()
            assertTrue(h.opened.isEmpty())
            assertEquals(listOf("show $a", "dismiss $a"), h.events)
        }

    @Test
    fun anAnswerThatArrivesInTheBackgroundIsDropped() =
        runTest {
            val h = Harness(this)
            h.tap(a)
            runCurrent()
            h.foreground = false
            h.answer(a, "https://example.com/a")
            runCurrent()
            assertTrue(h.opened.isEmpty())
            assertNull(h.links.pendingLink)
        }

    @Test
    fun aFailedLookupOpensTheOriginalLink() =
        runTest {
            val h = Harness(this)
            h.tap(a)
            runCurrent()
            h.lookups.getValue(a).completeExceptionally(IllegalStateException("network"))
            runCurrent()
            assertEquals(listOf(a), h.opened)
            assertNull(h.links.pendingLink)
        }
}
