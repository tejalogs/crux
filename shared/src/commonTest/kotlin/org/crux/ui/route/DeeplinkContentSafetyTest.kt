package org.crux.ui.route

import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeeplinkContentSafetyTest {
    private val account = MicroBlogKey("alice", "example.social")
    private val user = MicroBlogKey("bob", "example.social")

    @Test
    fun webLinksAreAllowed() {
        assertTrue(DeeplinkRoute.isSafeFromContent("https://example.com/post"))
    }

    @Test
    fun plainNavigationFromContentIsAllowed() {
        assertTrue(DeeplinkRoute.isSafeFromContent(DeeplinkRoute.Search(accountType = AccountType.Specific(account), query = "kotlin").toUri()))
        assertTrue(DeeplinkRoute.isSafeFromContent(DeeplinkRoute.Login.toUri()))
    }

    @Test
    fun privilegedRoutesFromContentAreBlocked() {
        assertFalse(DeeplinkRoute.isSafeFromContent(DeeplinkRoute.BlockUser(account, user).toUri()))
        assertFalse(DeeplinkRoute.isSafeFromContent(DeeplinkRoute.OpenLinkDirectly("https://evil.example").toUri()))
    }

    @Test
    fun malformedAppLinkIsBlocked() {
        assertFalse(DeeplinkRoute.isSafeFromContent("crux://zz-not-hex"))
    }

    @Test
    fun postActionsAndOddAppSpellingsFromContentAreBlocked() {
        assertFalse(DeeplinkRoute.isSafeFromContent("flare-event://0a0b0c"))
        assertFalse(DeeplinkRoute.isSafeFromContent("FLARE-EVENT://0a0b0c"))
        assertFalse(DeeplinkRoute.isSafeFromContent(" CRUX://" + DeeplinkRoute.Login.toUri().removePrefix("crux://")))
        assertFalse(DeeplinkRoute.isSafeFromContent("crux:abcd"))
    }

    @Test
    fun otherSchemesAreLeftToTheLinkOpener() {
        assertTrue(DeeplinkRoute.isSafeFromContent("mailto:someone@example.com"))
        assertTrue(DeeplinkRoute.isSafeFromContent("/relative/path"))
        assertTrue(DeeplinkRoute.isSafeFromContent("https://example.com/crux://not-a-scheme"))
    }

    @Test
    fun internalLinkDetection() {
        assertTrue(DeeplinkRoute.isInternalLink(DeeplinkRoute.Login.toUri()))
        assertTrue(DeeplinkRoute.isInternalLink("  Crux://x"))
        assertTrue(DeeplinkRoute.isInternalLink("flare-event://x"))
        assertFalse(DeeplinkRoute.isInternalLink("https://example.com"))
        assertFalse(DeeplinkRoute.isInternalLink("cruxy://x"))
        assertFalse(DeeplinkRoute.isInternalLink(""))
    }

    @Test
    fun openLinkDirectlyToWebLinkStillParses() {
        val uri = DeeplinkRoute.OpenLinkDirectly("https://example.com/a").toUri()
        assertEquals(DeeplinkRoute.OpenLinkDirectly("https://example.com/a"), DeeplinkRoute.parse(uri))
    }

    @Test
    fun openLinkDirectlyCannotWrapAnInAppRoute() {
        // A feed card in "open in browser" mode wraps its item link like this; the inner link
        // would otherwise be re-opened as a trusted in-app intent and skip isSafeFromContent.
        val blockUser = DeeplinkRoute.BlockUser(account, user).toUri()
        assertNull(DeeplinkRoute.parse(DeeplinkRoute.OpenLinkDirectly(blockUser).toUri()))
        assertNull(DeeplinkRoute.parse(DeeplinkRoute.OpenLinkDirectly(" CRUX://abcd").toUri()))
        assertNull(DeeplinkRoute.parse(DeeplinkRoute.OpenLinkDirectly("flare-event://abcd").toUri()))
        // Even a route that content may open is refused when wrapped: there is no reason to.
        assertNull(DeeplinkRoute.parse(DeeplinkRoute.OpenLinkDirectly(DeeplinkRoute.Login.toUri()).toUri()))
    }

    @Test
    fun nestedOpenLinkDirectlyIsRefused() {
        val inner = DeeplinkRoute.OpenLinkDirectly(DeeplinkRoute.BlockUser(account, user).toUri()).toUri()
        assertNull(DeeplinkRoute.parse(DeeplinkRoute.OpenLinkDirectly(inner).toUri()))
    }
}
