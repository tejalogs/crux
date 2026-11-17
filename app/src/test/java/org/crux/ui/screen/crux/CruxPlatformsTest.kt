package org.crux.ui.screen.crux

import org.crux.data.datastore.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CruxPlatformsTest {
    @Test
    fun defaultPlatformsStartWithRssAndMapToKnownEntries() {
        assertEquals("Rss", AppSettings.DEFAULT_CRUX_PLATFORMS.first())
        assertEquals(CruxPlatform.Rss, CruxPlatform.entries.first())
        val known = AppSettings.DEFAULT_CRUX_PLATFORMS.mapNotNull { CruxPlatform.fromName(it) }
        assertEquals(AppSettings.DEFAULT_CRUX_PLATFORMS.size, known.size)
    }

    @Test
    fun nextUnsignedPlatformAdvancesThroughSocialPlatformsInOrder() {
        val order = listOf(CruxPlatform.X, CruxPlatform.Instagram, CruxPlatform.LinkedIn, CruxPlatform.Reddit)
        assertEquals(CruxPlatform.X, nextUnsignedPlatform(order, emptySet()))
        assertEquals(CruxPlatform.Instagram, nextUnsignedPlatform(order, setOf(CruxPlatform.X)))
        assertEquals(CruxPlatform.LinkedIn, nextUnsignedPlatform(order, setOf(CruxPlatform.X, CruxPlatform.Instagram)))
        // Reddit works signed out, so after X/IG/LI sign in it is still offered next if selected
        assertEquals(
            CruxPlatform.Reddit,
            nextUnsignedPlatform(order, setOf(CruxPlatform.X, CruxPlatform.Instagram, CruxPlatform.LinkedIn)),
        )
    }

    @Test
    fun everySocialPlatformCanPostAndRssCannot() {
        CruxPlatform.entries.filter { it.site != null }.forEach { assertTrue(it.name, it.canPost) }
        assertEquals(false, CruxPlatform.Rss.canPost)
    }
}
