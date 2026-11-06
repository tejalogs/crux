package org.crux.ui.screen.status.action

import org.crux.model.MicroBlogKey
import org.crux.ui.screen.home.LiveWebSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossPostPlanTest {
    private val key1 = MicroBlogKey(id = "user1", host = "example.com")
    private val key2 = MicroBlogKey(id = "user2", host = "bsky.social")

    @Test
    fun planWithApiAccountsOnly() {
        val plan =
            CrossPostPlan(
                apiAccountKeys = listOf(key1, key2),
                webSites = emptyList<LiveWebSite>(),
                attachCarousel = true,
            )
        assertTrue(plan.hasApiTargets)
        assertFalse(plan.hasWebTargets)
        assertEquals(2, plan.totalTargetCount)
        assertEquals("Broadcast to 2 Accounts", plan.actionButtonLabel)
    }

    @Test
    fun planWithWebSitesOnly() {
        val plan =
            CrossPostPlan(
                apiAccountKeys = emptyList<MicroBlogKey>(),
                webSites = listOf(LiveWebSite.LinkedIn, LiveWebSite.Instagram),
                attachCarousel = true,
            )
        assertFalse(plan.hasApiTargets)
        assertTrue(plan.hasWebTargets)
        assertEquals(2, plan.totalTargetCount)
        assertEquals("Stage in 2 Web Sessions", plan.actionButtonLabel)
    }

    @Test
    fun planWithBothApiAndWebSites() {
        val plan =
            CrossPostPlan(
                apiAccountKeys = listOf(key1),
                webSites = listOf(LiveWebSite.LinkedIn),
                attachCarousel = false,
            )
        assertTrue(plan.hasApiTargets)
        assertTrue(plan.hasWebTargets)
        assertEquals(2, plan.totalTargetCount)
        assertEquals("Broadcast & Cross-Post (2)", plan.actionButtonLabel)
    }

    @Test
    fun planWithSingleApiAccount() {
        val plan =
            CrossPostPlan(
                apiAccountKeys = listOf(key1),
                webSites = emptyList<LiveWebSite>(),
                attachCarousel = false,
            )
        assertEquals("Broadcast to 1 Account", plan.actionButtonLabel)
    }

    @Test
    fun planWithSingleWebSession() {
        val plan =
            CrossPostPlan(
                apiAccountKeys = emptyList<MicroBlogKey>(),
                webSites = listOf(LiveWebSite.LinkedIn),
                attachCarousel = false,
            )
        assertEquals("Stage in 1 Web Session", plan.actionButtonLabel)
    }
}
