package org.crux.ui.presenter.home.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CountImportableOpmlFeedsTest {
    @Test
    fun countsNestedFeedsAndSkipsFoldersWithoutUrl() {
        val opml =
            """
            <opml version="2.0">
                <head><title>Feeds</title></head>
                <body>
                    <outline text="Tech" title="Tech">
                        <outline type="rss" text="A" xmlUrl="https://a.example/feed"/>
                        <outline type="rss" text="Blank" xmlUrl="  "/>
                    </outline>
                    <outline type="rss" text="B" xmlUrl="https://b.example/feed"/>
                </body>
            </opml>
            """.trimIndent()

        assertEquals(2, countImportableOpmlFeeds(opml))
    }

    @Test
    fun emptyBodyHasNoFeeds() {
        val opml =
            """
            <opml version="2.0">
                <head><title>Feeds</title></head>
                <body>
                    <outline text="Empty folder"/>
                </body>
            </opml>
            """.trimIndent()

        assertEquals(0, countImportableOpmlFeeds(opml))
    }

    @Test
    fun invalidOpmlThrows() {
        assertFails { countImportableOpmlFeeds("not xml at all") }
    }
}
