package org.crux.ui.component

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssLinkDestinationTest {
    private val account = MicroBlogKey("alice", "example.social")
    private val user = MicroBlogKey("bob", "example.social")

    private fun anchor(
        href: String,
        baseUri: String = "https://blog.example.com/posts/1",
    ): Element = Ksoup.parse("<a href=\"$href\">link</a>", baseUri).selectFirst("a")!!

    @Test
    fun privilegedAppRouteInArticleIsNotALink() {
        assertNull(rssLinkDestination(anchor(DeeplinkRoute.BlockUser(account, user).toUri())))
    }

    @Test
    fun postEventLinkInArticleIsNotALink() {
        assertNull(rssLinkDestination(anchor("flare-event://0a0b0c")))
    }

    @Test
    fun openLinkDirectlyWrapperInArticleIsNotALink() {
        assertNull(rssLinkDestination(anchor(DeeplinkRoute.OpenLinkDirectly("https://evil.example").toUri())))
    }

    @Test
    fun webLinkIsKept() {
        assertEquals("https://example.com/a", rssLinkDestination(anchor("https://example.com/a")))
    }

    @Test
    fun relativeLinkIsResolvedAgainstTheArticle() {
        assertEquals("https://blog.example.com/about", rssLinkDestination(anchor("/about")))
    }

    @Test
    fun contentSafeAppRouteIsKept() {
        val search = DeeplinkRoute.Search(accountType = AccountType.Specific(account), query = "kotlin").toUri()
        assertEquals(search, rssLinkDestination(anchor(search)))
    }

    @Test
    fun emptyHrefIsNotALink() {
        assertNull(rssLinkDestination(anchor("")))
    }
}
