package org.crux.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RssFeedParserTest {
    @Test
    fun parsesRss20FeedWithMediaThumbnailAndContentEncoded() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Crux Engineering</title>
                <link>https://example.org/blog</link>
                <image><url>/icon.png</url></image>
                <item>
                  <guid>post-1</guid>
                  <title>Building a Lean RSS Engine</title>
                  <link>/posts/lean-rss</link>
                  <author>Jane Doe</author>
                  <pubDate>Tue, 24 Nov 2026 10:30:00 GMT</pubDate>
                  <media:thumbnail url="https://cdn.example.org/thumb.jpg" />
                  <description>Short summary</description>
                  <content:encoded><![CDATA[<p>Full <b>HTML</b> body with details.</p>]]></content:encoded>
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val feed = RssFeedParser.parse(xml, "https://example.org/feed.xml")
        assertEquals("Crux Engineering", feed.title)
        assertEquals("https://example.org/blog", feed.siteUrl)
        assertEquals("https://example.org/icon.png", feed.iconUrl)
        assertEquals(1, feed.articles.size)

        val article = feed.articles.first()
        assertTrue(article.externalId.isNotBlank())
        assertEquals(article.externalId, RssFeedParser.parse(xml, "https://example.org/feed.xml").articles.first().externalId)
        assertEquals("Building a Lean RSS Engine", article.title)
        assertEquals("https://example.org/posts/lean-rss", article.link)
        assertEquals("Jane Doe", article.author)
        assertEquals("https://cdn.example.org/thumb.jpg", article.imageUrl)
        assertTrue(article.summaryHtml.orEmpty().contains("<b>HTML</b>"))
        assertEquals("Short summary", article.snippet)
        assertTrue(article.publishedAt > 0L)
    }

    @Test
    fun parsesAtom10FeedWithRelativeLinksAndIsoDate() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Daily</title>
              <link rel="alternate" href="https://atom.example.com/" />
              <icon>https://atom.example.com/favicon.png</icon>
              <entry>
                <id>tag:atom.example.com,2026:1</id>
                <title>Atom Story</title>
                <link rel="alternate" href="/articles/atom-story" />
                <author><name>Alex</name></author>
                <updated>2026-11-24T12:00:00Z</updated>
                <content type="html">&lt;p&gt;Hello &lt;img src=&quot;/hero.webp&quot; width=&quot;400&quot; height=&quot;300&quot;/&gt; world&lt;/p&gt;</content>
              </entry>
            </feed>
            """.trimIndent()

        val feed = RssFeedParser.parse(xml, "https://atom.example.com/atom.xml")
        assertEquals("Atom Daily", feed.title)
        assertEquals("https://atom.example.com/favicon.png", feed.iconUrl)
        assertEquals(1, feed.articles.size)

        val item = feed.articles.first()
        assertEquals("https://atom.example.com/articles/atom-story", item.link)
        assertEquals("Alex", item.author)
        assertEquals("https://atom.example.com/hero.webp", item.imageUrl)
        assertTrue(item.publishedAt > 0L)
    }

    @Test
    fun parsesJsonFeed11() {
        val json =
            """
            {
              "version": "https://jsonfeed.org/version/1.1",
              "title": "JSON Tech",
              "home_page_url": "https://json.example.net",
              "favicon": "https://json.example.net/fav.ico",
              "items": [
                {
                  "id": "j-1",
                  "url": "https://json.example.net/post-1",
                  "title": "Fast JSON Feed",
                  "content_html": "<p>Clean and fast.</p>",
                  "image": "https://json.example.net/cover.png",
                  "date_published": "2026-11-20T09:00:00Z",
                  "authors": [{ "name": "Sam" }]
                }
              ]
            }
            """.trimIndent()

        val feed = RssFeedParser.parse(json, "https://json.example.net/feed.json")
        assertEquals("JSON Tech", feed.title)
        assertEquals("https://json.example.net/fav.ico", feed.iconUrl)
        assertEquals(1, feed.articles.size)
        val item = feed.articles.first()
        assertEquals("Fast JSON Feed", item.title)
        assertEquals("Sam", item.author)
        assertEquals("https://json.example.net/cover.png", item.imageUrl)
        assertEquals("Clean and fast.", item.snippet)
    }

    @Test
    fun ignoresTrackingPixelsAndHandlesInvalidXmlControlChars() {
        val dirtyXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Dirty${'\u0007'}Feed</title>
                <item>
                  <title>Post With Pixel</title>
                  <link>https://example.org/p1</link>
                  <description><![CDATA[<img src="https://tracker.example.org/px.gif" width="1" height="1"/><p>Real text</p>]]></description>
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val feed = RssFeedParser.parse(dirtyXml, "https://example.org/rss")
        assertEquals("DirtyFeed", feed.title)
        assertEquals(1, feed.articles.size)
        assertNull(feed.articles.first().imageUrl)
        assertEquals("Real text", feed.articles.first().snippet)
    }

    @Test
    fun parsesCommonDateFormats() {
        assertNotNull(RssFeedParser.parseFeedDate("Tue, 24 Nov 2026 10:30:00 GMT"))
        assertNotNull(RssFeedParser.parseFeedDate("2026-11-24T10:30:00Z"))
        assertNotNull(RssFeedParser.parseFeedDate("2026-11-24T10:30:00+05:30"))
        assertNull(RssFeedParser.parseFeedDate("not-a-date"))
    }
}
