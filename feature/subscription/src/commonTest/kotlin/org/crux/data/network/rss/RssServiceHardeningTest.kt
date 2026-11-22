package org.crux.data.network.rss

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import nl.adaptivity.xmlutil.XmlException
import org.crux.data.network.rss.model.Feed
import org.crux.data.network.rss.model.decodeOpml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RssServiceHardeningTest {
    private val billionLaughsDtd =
        buildString {
            append("<!DOCTYPE lolz [\n")
            append("<!ENTITY l0 \"lol\">\n")
            for (level in 1..9) {
                val previous = "&l${level - 1};"
                append("<!ENTITY l$level \"${previous.repeat(10)}\">\n")
            }
            append("]>\n")
        }

    @Test
    fun feedWithNestedEntityDeclarationsIsRefusedBeforeExpansion() {
        val feed =
            """<?xml version="1.0"?>
            |$billionLaughsDtd<rss version="2.0"><channel><title>&l9;</title></channel></rss>
            """.trimMargin()

        assertFailsWith<XmlException> { parseFeedText(feed) }
    }

    @Test
    fun opmlWithNestedEntityDeclarationsIsRefusedBeforeExpansion() {
        val opml =
            """<?xml version="1.0"?>
            |$billionLaughsDtd<opml version="2.0"><head><title>&l9;</title></head><body/></opml>
            """.trimMargin()

        assertFailsWith<XmlException> { decodeOpml(opml) }
    }

    @Test
    fun entityDeclarationsAreRefusedWhateverTheCase() {
        assertFailsWith<XmlException> {
            rejectEntityDeclarations("<!doctype rss [<!entity x \"y\">]><rss/>")
        }
    }

    @Test
    fun publicDoctypeWithoutEntitiesIsAllowed() {
        // RSS 0.91 feeds still ship this external DOCTYPE; it declares no entities.
        rejectEntityDeclarations(
            """
            <?xml version="1.0"?>
            <!DOCTYPE rss PUBLIC "-//Netscape Communications//DTD RSS 0.91//EN" "http://my.netscape.com/publish/formats/rss-0.91.dtd">
            <rss version="0.91"><channel><title>Old feed</title></channel></rss>
            """.trimIndent(),
        )
    }

    @Test
    fun escapedEntityTextInContentIsNotMistakenForADtd() {
        val feed =
            parseFeedText(
                """
                <rss version="2.0"><channel><title>XML tips</title><link>https://example.com/</link>
                  <item><title>About &lt;!DOCTYPE&gt; and &lt;!ENTITY&gt;</title></item>
                </channel></rss>
                """.trimIndent(),
            )

        assertTrue(feed is Feed.Rss20)
        assertEquals("About <!DOCTYPE> and <!ENTITY>", feed.channel.items.first().title)
    }

    @Test
    fun atomIconPointingAtPrivateNetworkIsNotUsed() =
        runTest {
            val feed =
                parseFeedText(
                    """
                    <feed><title>A</title><icon>http://192.168.1.1/apply.cgi?action=reboot</icon>
                      <link href="http://10.0.0.1/"/>
                    </feed>
                    """.trimIndent(),
                )

            // Both the feed's own icon and the favicon fallback point at private addresses, so
            // nothing is returned and no request is made.
            assertNull(RssService.iconFor(feed, "http://192.168.1.1/feed.xml"))
        }

    @Test
    fun atomIconOnPublicAddressIsKeptAndRelativeIconIsResolved() =
        runTest {
            val absolute = parseFeedText("<feed><title>A</title><icon>https://93.184.216.34/icon.png</icon></feed>")
            assertEquals("https://93.184.216.34/icon.png", RssService.iconFor(absolute, "https://93.184.216.34/feed.xml"))

            val relative = parseFeedText("<feed><title>A</title><icon>/favicon.png</icon></feed>")
            assertEquals("https://93.184.216.34/favicon.png", RssService.iconFor(relative, "https://93.184.216.34/feed.xml"))
        }

    @Test
    fun boundedParallelMapKeepsOrderAndLimitsConcurrency() =
        runTest {
            var running = 0
            var peak = 0
            val result =
                boundedParallelMap((1..10).toList(), maxConcurrent = 3) { value ->
                    running++
                    peak = maxOf(peak, running)
                    delay(10)
                    running--
                    value * 2
                }

            assertEquals((1..10).map { it * 2 }, result)
            assertTrue(peak <= 3, "peak concurrency was $peak")
        }

    @Test
    fun discoveryLimitsAreSmall() {
        assertTrue(MAX_DISCOVERED_FEED_LINKS in 1..10)
        assertTrue(MAX_CONCURRENT_DISCOVERY_FETCHES in 1..MAX_DISCOVERED_FEED_LINKS)
    }
}
