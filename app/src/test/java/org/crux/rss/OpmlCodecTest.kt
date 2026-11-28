package org.crux.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpmlCodecTest {
    @Test
    fun importsNestedFoldersDeduplicatesAndRejectsUnsafeUrls() {
        val opml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Export</title></head>
              <body>
                <outline text="Tech">
                  <outline type="rss" text="Hacker News" xmlUrl="https://news.ycombinator.com/rss" htmlUrl="https://news.ycombinator.com" />
                  <outline type="rss" text="Duplicate HN" xmlUrl="https://news.ycombinator.com/rss" />
                  <outline type="rss" text="Local SSRF" xmlUrl="http://127.0.0.1:8080/secret" />
                  <outline type="rss" text="File Scheme" xmlUrl="file:///etc/passwd" />
                </outline>
                <outline type="rss" title="EFF Updates" xmlUrl="https://www.eff.org/rss/updates.xml" />
              </body>
            </opml>
            """.trimIndent()

        val entries = OpmlCodec.parse(opml)
        assertEquals(2, entries.size)
        assertEquals("https://news.ycombinator.com/rss", entries[0].url)
        assertEquals("Hacker News", entries[0].title)
        assertEquals("Tech", entries[0].category)
        assertEquals("https://www.eff.org/rss/updates.xml", entries[1].url)
        assertEquals("EFF Updates", entries[1].title)
        assertEquals(null, entries[1].category)
        assertEquals(2, OpmlCodec.countImportableFeeds(opml))
    }

    @Test
    fun exportAndReimportRoundTripPreservesCategoriesAndEscapesXml() {
        val feeds =
            listOf(
                RssFeed(
                    id = 1,
                    url = "https://example.org/rss?a=1&b=2",
                    title = "R&D <News>",
                    siteUrl = "https://example.org",
                    category = "AI & Research",
                ),
                RssFeed(
                    id = 2,
                    url = "https://blog.example.com/feed",
                    title = "Uncategorized Blog",
                    siteUrl = "https://blog.example.com",
                    category = null,
                ),
            )

        val xml = OpmlCodec.export(feeds)
        assertTrue(xml.contains("<opml version=\"2.0\">"))
        assertTrue(xml.contains("R&amp;D &lt;News&gt;"))
        assertFalse(xml.contains("a=1&b=2"))

        val parsed = OpmlCodec.parse(xml)
        assertEquals(2, parsed.size)
        val first = parsed.first { it.url == "https://example.org/rss?a=1&b=2" }
        assertEquals("R&D <News>", first.title)
        assertEquals("AI & Research", first.category)
    }
}
