package org.crux.data.platform

import org.crux.model.MicroBlogKey
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveWebPlatformLinksTest {
    private val key = MicroBlogKey("me", "example.com")

    @Test
    fun instagramAndLinkedInClaimNoNativeDeepLinks() {
        // Their pages open in the live web screen; native Status/Profile screens have no data.
        assertTrue(InstagramPlatformSpec.deepLinks(key).isEmpty())
        assertTrue(LinkedInPlatformSpec.deepLinks(key).isEmpty())
    }

    @Test
    fun cruxComposeDeeplinkRoundTripsItsText() {
        val route = DeeplinkRoute.Compose.Crux(initialText = "Shared title\nhttps://example.com/a?b=c d")
        assertEquals(route, DeeplinkRoute.parse(route.toUri()))
        assertEquals(DeeplinkRoute.Compose.Crux(), DeeplinkRoute.parse(DeeplinkRoute.Compose.Crux().toUri()))
    }
}
