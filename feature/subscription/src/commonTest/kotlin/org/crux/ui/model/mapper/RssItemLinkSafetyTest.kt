package org.crux.ui.model.mapper

import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.network.rss.model.Feed
import org.crux.model.MicroBlogKey
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** A feed author must not be able to turn a card tap into an in-app crux:// route. */
class RssItemLinkSafetyTest {
    private val privilegedRoute =
        DeeplinkRoute
            .BlockUser(
                accountKey = MicroBlogKey("alice", "example.social"),
                userKey = MicroBlogKey("bob", "example.social"),
            ).toUri()

    @Test
    fun rss20ItemWithAppLinkHasNoLink() {
        assertNull(Feed.Rss20.Item(title = "x", link = privilegedRoute).resolvedLink())
        assertNull(Feed.Rss20.Item(title = "x", link = "  CRUX://abcd ").resolvedLink())
        assertNull(Feed.Rss20.Item(title = "x", link = "flare-event://abcd").resolvedLink())
    }

    @Test
    fun rss20GuidWithAppLinkIsRefused() {
        val item =
            Feed.Rss20.Item(
                title = "x",
                guid = Feed.Rss20.Guid(isPermaLink = true, value = privilegedRoute),
            )
        assertNull(item.resolvedLink())
    }

    @Test
    fun rss20AppLinkFallsBackToWebGuid() {
        val item =
            Feed.Rss20.Item(
                title = "x",
                link = privilegedRoute,
                guid = Feed.Rss20.Guid(isPermaLink = true, value = "https://example.com/post"),
            )
        assertEquals("https://example.com/post", item.resolvedLink())
    }

    @Test
    fun atomAppLinkFallsBackToId() {
        val entry =
            Feed.Atom.Entry(
                id = "https://example.com/entry",
                links = listOf(Feed.Atom.Link(href = privilegedRoute)),
            )
        assertEquals("https://example.com/entry", entry.resolvedLink())
        assertNull(Feed.Atom.Entry(id = privilegedRoute).resolvedLink())
    }

    @Test
    fun rdfItemWithAppLinkHasNoLink() {
        assertNull(Feed.RDF.Item(title = "x", link = privilegedRoute).resolvedLink())
    }

    @Test
    fun webLinksAreKept() {
        assertEquals("https://example.com/a", Feed.Rss20.Item(link = " https://example.com/a ").resolvedLink())
        assertEquals("tag:example.com,2024:1", Feed.Atom.Entry(id = "tag:example.com,2024:1").resolvedLink())
    }

    @Test
    fun openInBrowserCardNeverWrapsAnAppLink() {
        val feed =
            Feed.Rss20
                .Item(title = "x", link = privilegedRoute, description = "body")
                .render(sourceName = "Feed", sourceIcon = null, displayMode = RssDisplayMode.OPEN_IN_BROWSER)
        val event = assertIs<ClickEvent.Deeplink>(assertIs<UiTimelineV2.Feed>(feed).clickEvent)
        // No usable web link: the card opens in the reader, never hands the in-app link to the opener.
        val route = assertIs<DeeplinkRoute.Rss.Detail>(DeeplinkRoute.parse(event.url))
        assertFalse(route.url.startsWith("crux://", ignoreCase = true), "Reader must not get the in-app link")
    }

    @Test
    fun openInBrowserCardKeepsWebLink() {
        val feed =
            Feed.Rss20
                .Item(title = "x", link = "https://example.com/a", description = "body")
                .render(sourceName = "Feed", sourceIcon = null, displayMode = RssDisplayMode.OPEN_IN_BROWSER)
        val event = assertIs<ClickEvent.Deeplink>(assertIs<UiTimelineV2.Feed>(feed).clickEvent)
        assertEquals(DeeplinkRoute.OpenLinkDirectly("https://example.com/a").toUri(), event.url)
    }
}
