package org.crux.ui.screen.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.crux.data.network.PublicUrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebShortLinksTest {
    /** Answers from a fixed redirect table and records every request it was asked to make. */
    private class FakeFetcher(
        private val answers: Map<Pair<String, Boolean>, ShortLinkHop?>,
        private val failures: Set<Pair<String, Boolean>> = emptySet(),
    ) : ShortLinkFetcher {
        val requests = mutableListOf<Pair<String, Boolean>>()

        /** Requests for which the page body was asked for. */
        val pageRequests = mutableListOf<Pair<String, Boolean>>()

        override suspend fun fetch(
            url: String,
            head: Boolean,
            readPage: Boolean,
        ): ShortLinkHop? {
            requests += url to head
            if (readPage) pageRequests += url to head
            if ((url to head) in failures) error("network down")
            val answer = answers[url to head]
            // Like the real fetcher: the page is handed back only when it was asked for.
            return if (readPage) answer else answer?.copy(page = null)
        }
    }

    private fun redirect(location: String) = ShortLinkHop(301, location)

    /** Public for every shortener and example host, so tests never do DNS. */
    private val allowAll: (String) -> Boolean = { true }

    private fun resolve(
        url: String,
        fetcher: ShortLinkFetcher,
        isSafe: (String) -> Boolean = allowAll,
    ): String? =
        runBlocking {
            resolveShortLink(url, fetcher = fetcher, isSafe = isSafe, dispatcher = Dispatchers.Unconfined)
        }

    // Host matching

    @Test
    fun matchesEachShortenerHost() {
        listOf(
            "https://t.co/abc123",
            "http://t.co/abc123",
            "https://lnkd.in/eXyZ",
            "https://bit.ly/3abc",
            "https://buff.ly/4def",
            "https://ow.ly/xyz",
            "https://www.bit.ly/3abc",
            "https://T.CO/abc",
            "  https://t.co/abc  ",
        ).forEach { assertTrue(it, isShortLinkUrl(it)) }
    }

    @Test
    fun doesNotMatchLookAlikeOrUnrelatedHosts() {
        listOf(
            "https://microsoft.co/abc",
            "https://at.co/abc",
            "https://t.co.evil.com/abc",
            "https://evil.t.co/abc",
            "https://notbit.ly/abc",
            "https://x.com/someone/status/1",
            "https://linkedin.com/in/someone",
            "ftp://t.co/abc",
            "javascript:alert(1)",
            "not a url",
            "",
        ).forEach { assertFalse(it, isShortLinkUrl(it)) }
    }

    @Test
    fun nonShortenerIsNeverFetched() {
        val fetcher = FakeFetcher(emptyMap())
        assertNull(resolve("https://example.com/a", fetcher))
        assertTrue(fetcher.requests.isEmpty())
    }

    // Resolution

    @Test
    fun resolvesSingleRedirectWithHeadOnly() {
        val fetcher = FakeFetcher(mapOf(("https://t.co/abc" to true) to redirect("https://example.com/news/2026/11/story")))
        assertEquals("https://example.com/news/2026/11/story", resolve("https://t.co/abc", fetcher))
        assertEquals(listOf("https://t.co/abc" to true), fetcher.requests)
    }

    @Test
    fun fallsBackToGetWhenHeadIsRefused() {
        val fetcher =
            FakeFetcher(
                mapOf(
                    ("https://lnkd.in/x" to true) to ShortLinkHop(405, null),
                    ("https://lnkd.in/x" to false) to ShortLinkHop(302, "https://example.com/post/one"),
                ),
            )
        assertEquals("https://example.com/post/one", resolve("https://lnkd.in/x", fetcher))
        assertEquals(listOf("https://lnkd.in/x" to true, "https://lnkd.in/x" to false), fetcher.requests)
    }

    @Test
    fun fallsBackToGetWhenHeadThrows() {
        val fetcher =
            FakeFetcher(
                answers = mapOf(("https://bit.ly/a" to false) to redirect("https://example.com/a-b")),
                failures = setOf("https://bit.ly/a" to true),
            )
        assertEquals("https://example.com/a-b", resolve("https://bit.ly/a", fetcher))
    }

    @Test
    fun resolvesRelativeLocation() {
        val fetcher =
            FakeFetcher(
                mapOf(
                    ("https://bit.ly/a" to true) to redirect("/b"),
                    ("https://bit.ly/b" to true) to redirect("https://example.com/final-page"),
                ),
            )
        assertEquals("https://example.com/final-page", resolve("https://bit.ly/a", fetcher))
    }

    @Test
    fun stopsAtFirstNonShortenerWithoutFetchingIt() {
        val fetcher = FakeFetcher(mapOf(("https://t.co/abc" to true) to redirect("https://example.com/a-b")))
        resolve("https://t.co/abc", fetcher)
        assertTrue(fetcher.requests.none { it.first.startsWith("https://example.com") })
    }

    // Hop cap

    @Test
    fun followsChainUpToHopCap() {
        val fetcher =
            FakeFetcher(
                mapOf(
                    ("https://bit.ly/1" to true) to redirect("https://ow.ly/2"),
                    ("https://ow.ly/2" to true) to redirect("https://buff.ly/3"),
                    ("https://buff.ly/3" to true) to redirect("https://example.com/real-article"),
                ),
            )
        assertEquals("https://example.com/real-article", resolve("https://bit.ly/1", fetcher))
        assertEquals(3, fetcher.requests.size)
    }

    @Test
    fun givesUpWhenChainExceedsHopCap() {
        val fetcher =
            FakeFetcher(
                mapOf(
                    ("https://bit.ly/1" to true) to redirect("https://ow.ly/2"),
                    ("https://ow.ly/2" to true) to redirect("https://buff.ly/3"),
                    ("https://buff.ly/3" to true) to redirect("https://t.co/4"),
                    ("https://t.co/4" to true) to redirect("https://example.com/real-article"),
                ),
            )
        assertNull(resolve("https://bit.ly/1", fetcher))
        assertTrue(fetcher.requests.none { it.first == "https://t.co/4" })
    }

    @Test
    fun redirectLoopEndsAtHopCap() {
        val fetcher =
            FakeFetcher(
                mapOf(
                    ("https://t.co/a" to true) to redirect("https://t.co/b"),
                    ("https://t.co/b" to true) to redirect("https://t.co/a"),
                ),
            )
        assertNull(resolve("https://t.co/a", fetcher))
        assertEquals(SHORT_LINK_MAX_HOPS, fetcher.requests.size)
    }

    // Refusals

    @Test
    fun refusesRedirectIntoPrivateNetwork() {
        // Real validator for everything off the shortener host, so private literals are judged as in production.
        val validator: (String) -> Boolean = { url ->
            isShortLinkUrl(url) || PublicUrlValidator.isSafePublicHttpUrl(url)
        }
        listOf(
            "http://127.0.0.1/admin",
            "http://192.168.1.1/",
            "http://10.0.0.5:8080/x",
            "http://169.254.169.254/latest/meta-data",
            "http://localhost/x",
            "http://[::1]/x",
        ).forEach { privateTarget ->
            val fetcher = FakeFetcher(mapOf(("https://t.co/abc" to true) to redirect(privateTarget)))
            assertNull(privateTarget, resolve("https://t.co/abc", fetcher, validator))
            assertEquals(privateTarget, 1, fetcher.requests.size)
        }
    }

    @Test
    fun refusesWhenShortenerItselfFailsValidation() {
        val fetcher = FakeFetcher(mapOf(("https://t.co/abc" to true) to redirect("https://example.com/a-b")))
        assertNull(resolve("https://t.co/abc", fetcher) { false })
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun neverFollowsNonWebSchemes() {
        listOf("crux://open", "intent://x#Intent;end", "javascript:alert(1)", "file:///etc/hosts", "mailto:a@b.c").forEach { target ->
            val fetcher = FakeFetcher(mapOf(("https://t.co/abc" to true) to redirect(target)))
            assertNull(target, resolve("https://t.co/abc", fetcher))
        }
    }

    @Test
    fun returnsNullWhenNoRedirect() {
        val fetcher =
            FakeFetcher(
                mapOf(
                    ("https://t.co/abc" to true) to ShortLinkHop(200, null),
                    ("https://t.co/abc" to false) to ShortLinkHop(200, null),
                ),
            )
        assertNull(resolve("https://t.co/abc", fetcher))
    }

    @Test
    fun returnsNullWhenRedirectHasNoLocation() {
        val fetcher = FakeFetcher(mapOf(("https://t.co/abc" to true) to ShortLinkHop(301, null)))
        assertNull(resolve("https://t.co/abc", fetcher))
    }

    @Test
    fun returnsNullWhenEveryRequestFails() {
        val fetcher = FakeFetcher(emptyMap(), failures = setOf("https://t.co/abc" to true, "https://t.co/abc" to false))
        assertNull(resolve("https://t.co/abc", fetcher))
    }

    @Test
    fun returnsNullAfterTimeout() {
        val slow =
            object : ShortLinkFetcher {
                override suspend fun fetch(
                    url: String,
                    head: Boolean,
                    readPage: Boolean,
                ): ShortLinkHop? {
                    delay(10_000)
                    return redirect("https://example.com/a-b")
                }
            }
        val result =
            runBlocking {
                resolveShortLink("https://t.co/abc", fetcher = slow, isSafe = allowAll, timeoutMs = 50, dispatcher = Dispatchers.Default)
            }
        assertNull(result)
    }

    // Opener glue: expand, clean, or fall back

    @Test
    fun expandedLinkIsCleaned() {
        val result =
            runBlocking {
                expandOutboundLink("https://t.co/abc", clean = true) { "https://example.com/news/story?utm_source=twitter&id=7" }
            }
        assertEquals("https://example.com/news/story?id=7", result)
    }

    @Test
    fun expandedLinkKeptAsIsWhenCleaningOff() {
        val result =
            runBlocking {
                expandOutboundLink("https://t.co/abc", clean = false) { "https://example.com/news/story?utm_source=twitter" }
            }
        assertEquals("https://example.com/news/story?utm_source=twitter", result)
    }

    @Test
    fun expandedLinkedInSafetyRedirectIsUnwrapped() {
        val result =
            runBlocking {
                expandOutboundLink("https://lnkd.in/x", clean = true) {
                    "https://www.linkedin.com/safety/go?url=https%3A%2F%2Fexample.com%2Fblog%2Fpost-one%3Futm_medium%3Dsocial"
                }
            }
        assertEquals("https://example.com/blog/post-one", result)
    }

    @Test
    fun fallsBackToOriginalWhenLookupFails() {
        val original = "https://t.co/abc"
        assertEquals(original, runBlocking { expandOutboundLink(original, clean = true) { null } })
        assertEquals(original, runBlocking { expandOutboundLink(original, clean = true) { error("boom") } })
    }

    @Test
    fun nonShortenerIsPassedThroughWithoutLookup() {
        var called = false
        val original = "https://example.com/a?utm_source=x"
        val result =
            runBlocking {
                expandOutboundLink(original, clean = true) {
                    called = true
                    "https://elsewhere.example/"
                }
            }
        assertEquals(original, result)
        assertFalse(called)
    }

    // Reader eligibility after expansion

    @Test
    fun expandedArticleIsReaderEligibleButShortLinkIsNot() {
        assertFalse(isReaderEligibleUrl("https://t.co/abc123"))
        assertFalse(isReaderEligibleUrl("https://lnkd.in/eXyZ"))
        assertTrue(isReaderEligibleUrl("https://example.com/news/2026/11/story"))
    }

    @Test
    fun resolveRedirectTargetRejectsHostless() {
        assertNull(resolveRedirectTarget("https://t.co/a", "https:///nohost"))
        assertEquals("https://t.co/b", resolveRedirectTarget("https://t.co/a", "b"))
    }

    // Hard time cap, even when the work cannot be cancelled

    @Test
    fun returnsOnTimeWhenValidatorBlocks() {
        // PublicUrlValidator does a blocking DNS lookup; a hung resolver must not hold the tap.
        val blockingValidator: (String) -> Boolean = {
            Thread.sleep(2_000)
            true
        }
        val fetcher = FakeFetcher(mapOf(("https://t.co/abc" to true) to redirect("https://example.com/a-b")))
        val started = System.nanoTime()
        val result =
            runBlocking {
                resolveShortLink(
                    "https://t.co/abc",
                    fetcher = fetcher,
                    isSafe = blockingValidator,
                    timeoutMs = 50,
                    dispatcher = Dispatchers.IO,
                )
            }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(result)
        assertTrue("took $elapsedMs ms", elapsedMs < 500)
    }

    @Test
    fun returnsOnTimeWhenFetcherBlocks() {
        val blocking =
            object : ShortLinkFetcher {
                override suspend fun fetch(
                    url: String,
                    head: Boolean,
                    readPage: Boolean,
                ): ShortLinkHop? {
                    Thread.sleep(2_000)
                    return redirect("https://example.com/a-b")
                }
            }
        val started = System.nanoTime()
        val result =
            runBlocking {
                resolveShortLink("https://t.co/abc", fetcher = blocking, isSafe = allowAll, timeoutMs = 50, dispatcher = Dispatchers.IO)
            }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(result)
        assertTrue("took $elapsedMs ms", elapsedMs < 500)
    }

    // lnkd.in notice page ("this link will take you to a page that's not on LinkedIn")

    private fun linkedInNotice(vararg hrefs: String): String =
        buildString {
            append("<html><head><meta name=\"pageKey\" content=\"d_shortlink_frontend_external_link_redirect_interstitial\">")
            append("</head><body>")
            append("<h1>Leaving LinkedIn</h1>")
            hrefs.forEach {
                append("<a class=\"artdeco-button\" data-tracking-control-name=\"external_url_click\" ")
                append("data-tracking-will-navigate href=\"$it\">go</a>")
            }
            append("<a data-tracking-control-name=\"learn_more_click\" ")
            append("href=\"https://www.linkedin.com/help/linkedin/answer/a1341680\">Learn more</a>")
            append("</body></html>")
        }

    private fun noticeAnswer(page: String) =
        mapOf(
            ("https://lnkd.in/dAbc123" to true) to ShortLinkHop(200, null),
            ("https://lnkd.in/dAbc123" to false) to ShortLinkHop(200, null, page),
        )

    @Test
    fun readsTargetFromLinkedInNoticePage() {
        val fetcher = FakeFetcher(noticeAnswer(linkedInNotice("https://www.youtube.com/watch?v=abc&amp;feature=emb")))
        assertEquals("https://www.youtube.com/watch?v=abc&feature=emb", resolve("https://lnkd.in/dAbc123", fetcher))
        // HEAD carries no body; only the lnkd.in GET asks for the page.
        assertEquals(listOf("https://lnkd.in/dAbc123" to false), fetcher.pageRequests)
        assertTrue(fetcher.requests.none { it.first.contains("youtube") })
    }

    @Test
    fun linkedInRedirectStillWorks() {
        val fetcher = FakeFetcher(mapOf(("https://lnkd.in/x" to true) to redirect("https://www.linkedin.com/learning/some-course")))
        assertEquals("https://www.linkedin.com/learning/some-course", resolve("https://lnkd.in/x", fetcher))
        assertTrue(fetcher.pageRequests.isEmpty())
    }

    @Test
    fun linkedInPageWithoutNoticeLinkKeepsOriginal() {
        val fetcher = FakeFetcher(noticeAnswer("<html><body>Something else</body></html>"))
        assertNull(resolve("https://lnkd.in/dAbc123", fetcher))
        val original = "https://lnkd.in/dAbc123"
        val opened =
            runBlocking {
                expandOutboundLink(original, clean = true) {
                    resolveShortLink(it, fetcher = fetcher, isSafe = allowAll, dispatcher = Dispatchers.Unconfined)
                }
            }
        assertEquals(original, opened)
    }

    @Test
    fun linkedInPageWithTwoNoticeLinksIsNotTrusted() {
        val fetcher = FakeFetcher(noticeAnswer(linkedInNotice("https://example.com/a-b", "https://example.org/c-d")))
        assertNull(resolve("https://lnkd.in/dAbc123", fetcher))
    }

    @Test
    fun linkedInNoticeIntoPrivateNetworkIsRefused() {
        val validator: (String) -> Boolean = { url -> isShortLinkUrl(url) || PublicUrlValidator.isSafePublicHttpUrl(url) }
        val fetcher = FakeFetcher(noticeAnswer(linkedInNotice("http://192.168.1.1/admin")))
        assertNull(resolve("https://lnkd.in/dAbc123", fetcher, validator))
    }

    @Test
    fun linkedInNoticeWithNonWebSchemeIsRefused() {
        val fetcher = FakeFetcher(noticeAnswer(linkedInNotice("javascript:alert(1)")))
        assertNull(resolve("https://lnkd.in/dAbc123", fetcher))
    }

    @Test
    fun otherShortenersNeverAskForThePage() {
        val fetcher =
            FakeFetcher(
                mapOf(
                    ("https://t.co/abc" to true) to ShortLinkHop(200, null),
                    ("https://t.co/abc" to false) to ShortLinkHop(200, null, linkedInNotice("https://example.com/a-b")),
                ),
            )
        assertNull(resolve("https://t.co/abc", fetcher))
        assertTrue(fetcher.pageRequests.isEmpty())
    }

    @Test
    fun noticeTargetDecodesEntities() {
        assertEquals(
            "https://example.com/a?x=1&y='2'",
            linkedInNoticeTarget(linkedInNotice("https://example.com/a?x=1&amp;y=&#39;2&#x27;")),
        )
        assertNull(linkedInNoticeTarget(linkedInNotice("")))
    }

    // Opener: a late answer opens only while Crux is on screen

    @Test
    fun foregroundImportanceGate() {
        assertTrue(isForegroundImportance(100)) // IMPORTANCE_FOREGROUND
        assertTrue(isForegroundImportance(200)) // IMPORTANCE_VISIBLE
        assertFalse(isForegroundImportance(300)) // IMPORTANCE_SERVICE
        assertFalse(isForegroundImportance(400)) // IMPORTANCE_CACHED
    }
}
