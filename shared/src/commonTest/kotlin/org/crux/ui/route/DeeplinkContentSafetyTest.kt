package org.crux.ui.route

import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertFalse
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
}
