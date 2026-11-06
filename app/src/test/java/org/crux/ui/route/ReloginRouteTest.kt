package org.crux.ui.route

import org.crux.data.model.tab.TimelineSpecIds
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.liveWebSiteForTabId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReloginRouteTest {
    @Test
    fun instagramAndLinkedInGoToTheirWebPages() {
        assertEquals(Route.InstagramWeb(), reloginRoute("instagram"))
        assertEquals(Route.LinkedInWeb(), reloginRoute("linkedin"))
    }

    @Test
    fun anythingElseGoesToThePlatformList() {
        assertEquals(Route.Settings.Accounts, reloginRoute("mastodon"))
        assertEquals(Route.Settings.Accounts, reloginRoute("bluesky"))
    }

    @Test
    fun legacyLoginDeeplinksMapToUsableRoutes() {
        assertEquals(Route.Settings.Accounts, Route.from(DeeplinkRoute.Login))
        assertEquals(Route.CruxCompose(), Route.from(DeeplinkRoute.Compose.New))
        assertEquals(Route.CruxCompose("hello"), Route.from(DeeplinkRoute.Compose.Crux("hello")))
    }

    @Test
    fun deeplinksWithoutADataSourceMapToNothing() {
        val key = org.crux.model.MicroBlogKey("1", "example.com")
        assertEquals(null, Route.from(DeeplinkRoute.AllLists(key)))
        assertEquals(null, Route.from(DeeplinkRoute.AllDirectMessages(key)))
        assertEquals(null, Route.from(DeeplinkRoute.EditLocalFollow(key, key)))
        assertEquals(null, Route.from(DeeplinkRoute.Compose.Reply(key, key)))
    }

    @Test
    fun staticPillIdsMatchTheSpecIds() {
        assertEquals(LiveWebSite.Instagram, liveWebSiteForTabId("${TimelineSpecIds.WEB_INSTAGRAM}:instagram"))
    }
}
