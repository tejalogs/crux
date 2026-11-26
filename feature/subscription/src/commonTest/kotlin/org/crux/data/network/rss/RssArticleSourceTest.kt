package org.crux.data.network.rss

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.crux.common.JSON_WITH_ENCODE_DEFAULT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RssArticleSourceTest {
    @Test
    fun extractedArticleIsMarkedFull() =
        runTest {
            val client = mockClient { respond(ARTICLE) }
            try {
                val doc = reader(client).load("https://example.com/a", fallbackHtml = "<p>Summary</p>")
                assertEquals(DocumentSource.Full, doc.source)
            } finally {
                client.close()
            }
        }

    @Test
    fun fallbackToTheFeedSummaryIsMarked() =
        runTest {
            val client = mockClient { respond("<h1>Gone</h1>", HttpStatusCode.NotFound) }
            try {
                val doc = reader(client).load("https://example.com/a", fallbackHtml = "<p>Summary</p>")
                assertEquals(DocumentSource.FeedSummary, doc.source)
                assertEquals("Summary", doc.textContent)
            } finally {
                client.close()
            }
        }

    @Test
    fun feedTextModeIsNotMistakenForAFallback() =
        runTest {
            val client = mockClient { error("Description mode must not fetch") }
            try {
                val doc = reader(client).load("https://example.com/a", descriptionHtml = "<p>Feed text</p>")
                assertEquals(DocumentSource.FeedText, doc.source)
            } finally {
                client.close()
            }
        }

    @Test
    fun failuresWithoutAFallbackCarryAReason() =
        runTest {
            suspend fun reasonFor(handler: MockRequestHandler): Pair<ArticleFailureReason, Int?> {
                val client = mockClient(handler)
                try {
                    val error = assertFails { reader(client).load("https://example.com/a") }
                    return error.articleFailureReason() to error.articleHttpStatus()
                } finally {
                    client.close()
                }
            }

            assertEquals(ArticleFailureReason.HttpError to 503, reasonFor { respond("busy", HttpStatusCode.ServiceUnavailable) })
            assertEquals(ArticleFailureReason.NoArticle to null, reasonFor { respond("<html><body></body></html>") })
            assertEquals(ArticleFailureReason.Unreachable to null, reasonFor { throw IOException("Unable to resolve host") })
            assertEquals(
                ArticleFailureReason.TimedOut to null,
                reasonFor {
                    delay(20_000)
                    respond(ARTICLE)
                },
            )
        }

    @Test
    fun unknownFailuresAreOtherAndCausesAreFollowed() {
        assertEquals(ArticleFailureReason.Other, IllegalArgumentException("Refused").articleFailureReason())
        assertEquals(ArticleFailureReason.Unreachable, IllegalStateException("wrapped", IOException("down")).articleFailureReason())
        assertNull(IllegalStateException("no status").articleHttpStatus())
    }

    @Test
    fun sourceIsNotPartOfTheJsonHandedToSummaries() {
        val doc =
            DocumentData("T", "<p>C</p>", "C", null, null, null, null, null, null, null, source = DocumentSource.FeedSummary)
        // Even a Json that writes default values leaves it out.
        val json = JSON_WITH_ENCODE_DEFAULT.encodeToString(DocumentData.serializer(), doc)
        assertFalse(json.contains("source"), json)
    }

    private fun TestScope.reader(client: HttpClient) = Readability(client, StandardTestDispatcher(testScheduler))

    private fun TestScope.mockClient(handler: MockRequestHandler): HttpClient =
        HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler(handler)
                },
            ),
        )
}
