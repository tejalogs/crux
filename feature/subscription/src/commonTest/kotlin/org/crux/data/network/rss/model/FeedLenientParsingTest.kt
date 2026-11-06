package org.crux.data.network.rss.model

import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.network.rss.findFeedLinks
import org.crux.data.network.rss.parseFeedText
import org.crux.data.network.rss.resolveAgainst
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.mapper.render
import org.crux.ui.model.mapper.resolvedLink
import org.crux.ui.model.mapper.title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedLenientParsingTest {
    @Test
    fun rss20ItemWithoutLinkFallsBackToPermalinkGuid() {
        val feed =
            parseFeedText(
                """
                <rss version="2.0"><channel><title>T</title><link>https://example.com/</link>
                  <item><title>No link</title><guid isPermaLink="true">https://example.com/post-1</guid></item>
                  <item><title>Opaque guid</title><guid isPermaLink="false">abc-123</guid></item>
                </channel></rss>
                """.trimIndent(),
            )

        assertTrue(feed is Feed.Rss20)
        assertEquals("https://example.com/post-1", feed.channel.items[0].resolvedLink())
        assertNull(feed.channel.items[1].resolvedLink())
    }

    @Test
    fun rss20ChannelWithoutTitleOrLinkIsAccepted() {
        val feed =
            parseFeedText(
                """
                <rss><channel><description>d</description>
                  <item><title>One</title><link>https://example.com/1</link></item>
                </channel></rss>
                """.trimIndent(),
            )

        assertTrue(feed is Feed.Rss20)
        assertNull(feed.version)
        assertEquals("", feed.title)
        assertEquals(1, feed.channel.items.size)
    }

    @Test
    fun enclosureAttributesAreReadAndABadLengthDoesNotRejectTheFeed() {
        val feed =
            parseFeedText(
                """
                <rss version="2.0"><channel><title>T</title><link>https://example.com/</link>
                  <item><title>Pic</title><link>https://example.com/1</link>
                    <enclosure url="https://cdn.example.com/a.jpg" length="" type="image/jpeg"/></item>
                  <item><title>Pic2</title><link>https://example.com/2</link>
                    <enclosure url="https://cdn.example.com/b.jpg" length="12.5k"/></item>
                </channel></rss>
                """.trimIndent(),
            ) as Feed.Rss20

        val first = feed.channel.items[0].enclosure
        assertEquals("https://cdn.example.com/a.jpg", first?.url)
        assertEquals("image/jpeg", first?.type)
        assertNotNull(feed.channel.items[1].enclosure)
        assertNull(
            feed.channel.items[1]
                .enclosure
                ?.type,
        )
    }

    @Test
    fun rdfItemWithoutTitleAndAtomEntryWithoutIdAreAccepted() {
        val rdf =
            parseFeedText(
                """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/">
                  <channel><title>R</title><link>https://example.com/</link></channel>
                  <item><link>https://example.com/a</link></item>
                </rdf:RDF>
                """.trimIndent(),
            )
        assertTrue(rdf is Feed.RDF)
        assertNull(rdf.items.single().title)
        assertEquals("https://example.com/a", rdf.items.single().resolvedLink())

        val atom =
            parseFeedText(
                """
                <feed><title>A</title>
                  <entry><title>E</title><link href="https://example.com/e"/></entry>
                </feed>
                """.trimIndent(),
            )
        assertTrue(atom is Feed.Atom)
        assertNull(atom.id)
        assertEquals("https://example.com/e", atom.entries.single().resolvedLink())
    }

    @Test
    fun relativeAndTagLinksDoNotThrowWhenBuildingTheRefererHeader() {
        listOf("/relative/post", "tag:example.com,2026:1", "not a url").forEach { link ->
            val item =
                Feed.Rss20.Item(
                    title = "T",
                    link = link,
                    description = """<p><img src="https://img.example.com/a.png"></p>""",
                )
            val feed = item.render("Src", null, RssDisplayMode.FULL_CONTENT) as UiTimelineV2.Feed
            val image = feed.media as UiMedia.Image
            assertEquals("https://img.example.com/a.png", image.url)
            assertNull(image.customHeaders?.get("Referer"))
        }
    }

    @Test
    fun absoluteLinksStillGetAReferer() {
        val item =
            Feed.Rss20.Item(
                title = "T",
                link = "https://example.com/post",
                description = """<img src="https://img.example.com/a.png">""",
            )
        val image = (item.render("Src", null, RssDisplayMode.FULL_CONTENT) as UiTimelineV2.Feed).media as UiMedia.Image
        assertEquals("https://example.com/", image.customHeaders?.get("Referer"))
    }

    @Test
    fun relativeAndProtocolRelativeImagesResolveAgainstTheArticle() {
        val relative =
            Feed.Rss20.Item(
                link = "https://example.com/blog/post",
                description = """<img src="/img/a.png">""",
            )
        val protocolRelative =
            Feed.Rss20.Item(
                link = "https://example.com/blog/post",
                description = """<img src="//cdn.example.com/b.png">""",
            )
        val sibling =
            Feed.Rss20.Item(
                link = "https://example.com/blog/post",
                description = """<img src="c.png">""",
            )

        fun imageOf(item: Feed.Rss20.Item) =
            ((item.render("S", null, RssDisplayMode.FULL_CONTENT) as UiTimelineV2.Feed).media as UiMedia.Image).url

        assertEquals("https://example.com/img/a.png", imageOf(relative))
        assertEquals("https://cdn.example.com/b.png", imageOf(protocolRelative))
        assertEquals("https://example.com/blog/c.png", imageOf(sibling))
    }

    @Test
    fun feedLinksResolveAgainstThePageIncludingPortAndPath() {
        val html =
            """
            <html><head>
              <link rel="alternate" type="application/rss+xml" href="/feed.xml">
              <link rel="alternate" type="application/atom+xml" href="atom.xml">
              <link rel="alternate" type="text/xml" href="//cdn.example.com/plain.xml">
              <link rel="stylesheet" type="text/css" href="/a.css">
              <link rel="alternate" type="application/rss+xml" href="https://other.example.org/rss">
            </head></html>
            """.trimIndent()

        val links = findFeedLinks(html, "https://example.com:8443/blog/index.html")

        assertEquals(
            listOf(
                "https://example.com:8443/feed.xml",
                "https://example.com:8443/blog/atom.xml",
                "https://cdn.example.com/plain.xml",
                "https://other.example.org/rss",
            ),
            links,
        )
    }

    @Test
    fun resolveAgainstRejectsNonWebResults() {
        assertEquals("https://example.com/x", resolveAgainst("https://example.com/a/b", "/x"))
        assertNull(resolveAgainst("https://example.com/a", "mailto:a@example.com"))
    }
}
