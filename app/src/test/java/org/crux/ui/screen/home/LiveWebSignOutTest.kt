package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebSignOutTest {
    @Test
    fun expiryCookiesAreSecureSoHostAndSecurePrefixedCookiesGoToo() {
        val cookies = expiryCookiesFor("x.com", "__Host-session")
        assertTrue(cookies.all { it.contains("; Secure;") && it.contains("Path=/") })
    }

    @Test
    fun signOutCoversTheSitesOwnPageSubdomains() {
        val origins = signOutOriginsFor("reddit.com")
        assertTrue(origins.containsAll(listOf("https://reddit.com", "https://www.reddit.com")))
        assertTrue(origins.containsAll(listOf("https://old.reddit.com", "https://sh.reddit.com", "https://new.reddit.com")))
        assertTrue(signOutOriginsFor("x.com").contains("https://mobile.x.com"))
        assertTrue(signOutOriginsFor("instagram.com").contains("https://m.instagram.com"))
    }

    @Test
    fun identityProviderCookiesGoWhenNoOtherSignedInSiteUsesThem() {
        val cleared = identityProviderHostsToClear(LiveWebSite.X, stillSignedIn = emptyList())
        assertTrue(cleared.containsAll(listOf("accounts.google.com", "accounts.youtube.com", "appleid.apple.com")))
        // X never signs in through Meta, so it leaves Instagram's provider alone.
        assertTrue("auth.meta.com" !in cleared)
    }

    @Test
    fun identityProviderCookiesStayWhileAnotherSignedInSiteUsesThem() {
        // LinkedIn also signs in with Google and Apple: signing out of X must not end that sign-in.
        assertEquals(emptyList<String>(), identityProviderHostsToClear(LiveWebSite.X, stillSignedIn = listOf(LiveWebSite.LinkedIn)))
        // Mastodon uses no identity provider, so it does not keep Google's cookies alive.
        assertTrue(
            identityProviderHostsToClear(LiveWebSite.X, stillSignedIn = listOf(LiveWebSite.Mastodon)).contains("accounts.google.com"),
        )
    }

    @Test
    fun instagramSignOutClearsMetaSignInOnly() {
        assertEquals(listOf("auth.meta.com"), identityProviderHostsToClear(LiveWebSite.Instagram, stillSignedIn = emptyList()))
    }

    @Test
    fun mastodonUsesNoIdentityProvider() {
        assertEquals(emptyList<String>(), identityProviderHostsToClear(LiveWebSite.Mastodon, stillSignedIn = emptyList()))
    }
}
