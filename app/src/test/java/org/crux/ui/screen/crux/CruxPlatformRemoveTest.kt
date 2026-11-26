package org.crux.ui.screen.crux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CruxPlatformRemoveTest {
    @Test
    fun signedInSiteAsksBeforeRemoving() {
        assertEquals(RemoveAction.AskHideOrSignOut, removeAction(CruxPlatform.Instagram, signedIn = true))
    }

    @Test
    fun siteWithoutSessionClearsWithoutUndo() {
        val action = removeAction(CruxPlatform.Instagram, signedIn = false)
        assertEquals(RemoveAction.ClearWithoutUndo, action)
        // Undo only re-enables the tab; it cannot bring back the cleared cookies, so it is not offered.
        assertFalse(action.offersUndo)
    }

    @Test
    fun feedsHideOffersUndo() {
        assertTrue(removeAction(CruxPlatform.Rss, signedIn = false).offersUndo)
    }

    @Test
    fun hideTabStaySignedInOffersUndo() {
        assertTrue(HIDE_TAB_OFFERS_UNDO)
    }

    @Test
    fun askingFirstOffersNoUndoByItself() {
        assertFalse(RemoveAction.AskHideOrSignOut.offersUndo)
    }

    @Test
    fun feedsOnlyHidesAndOffersUndoWhateverTheSignInFlag() {
        assertEquals(RemoveAction.HideAndOfferUndo, removeAction(CruxPlatform.Rss, signedIn = false))
        assertEquals(RemoveAction.HideAndOfferUndo, removeAction(CruxPlatform.Rss, signedIn = true))
    }

    @Test
    fun everySitePlatformFollowsTheSameRules() {
        CruxPlatform.entries.filter { it.site != null }.forEach { platform ->
            assertEquals(platform.name, RemoveAction.AskHideOrSignOut, removeAction(platform, signedIn = true))
            assertEquals(platform.name, RemoveAction.ClearWithoutUndo, removeAction(platform, signedIn = false))
        }
    }

    @Test
    fun signOutIsNeverOfferedForFeeds() {
        assertFalse(offersSignOut(CruxPlatform.Rss, signedIn = true))
        assertFalse(offersSignOut(CruxPlatform.Rss, signedIn = false))
    }

    @Test
    fun signOutIsOfferedOnlyForASignedInSite() {
        assertTrue(offersSignOut(CruxPlatform.Instagram, signedIn = true))
        assertFalse(offersSignOut(CruxPlatform.Instagram, signedIn = false))
    }
}
