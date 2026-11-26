package org.crux.data.network.rss

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.crux.ui.model.mapper.title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RssServiceStatusTest {
    @Test
    fun errorStatusIsReportedAsHttpFailureNotAParseError() =
        runTest {
            for (status in listOf(404, 410, 500, 503)) {
                val client = mockClient { respond("<html><body>Not here</body></html>", HttpStatusCode.fromValue(status)) }
                try {
                    val error = assertFailsWith<FeedHttpStatusException> { fetchFeed(client, "https://example.com/feed.xml") }
                    assertEquals(status, error.response.status.value)
                    assertTrue(error.message.contains("HTTP $status"), error.message)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun successfulFeedIsParsed() =
        runTest {
            val client = mockClient { respond(RSS, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml")) }
            try {
                assertEquals("Example feed", fetchFeed(client, "https://example.com/feed.xml").title)
            } finally {
                client.close()
            }
        }

    @Test
    fun redirectsAreStillFollowedAndChecked() =
        runTest {
            val client =
                mockClient { request ->
                    when (request.url.encodedPath) {
                        "/old" -> respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/feed.xml"))
                        "/to-lan" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://192.168.1.1/feed.xml"))
                        else -> respond(RSS, HttpStatusCode.OK)
                    }
                }
            try {
                assertEquals("Example feed", fetchFeed(client, "https://example.com/old").title)
                assertFailsWith<IllegalArgumentException> { fetchFeed(client, "https://example.com/to-lan") }
            } finally {
                client.close()
            }
        }

    private fun TestScope.mockClient(handler: MockRequestHandler): HttpClient =
        HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler(handler)
                },
            ),
        ) {
            // Like publicWebClient: getPublic follows and re-checks each redirect itself.
            followRedirects = false
        }

    private companion object {
        val RSS =
            """
            <?xml version="1.0"?>
            <rss version="2.0"><channel><title>Example feed</title><link>https://example.com</link>
            <item><title>One</title><link>https://example.com/one</link></item>
            </channel></rss>
            """.trimIndent()
    }
}
