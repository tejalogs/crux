package org.crux.ui.screen.crux

import org.crux.data.datastore.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CruxPlatformsTest {
    @Test
    fun defaultPlatformsMapToKnownEntries() {
        val known = AppSettings.DEFAULT_CRUX_PLATFORMS.mapNotNull { CruxPlatform.fromName(it) }
        assertEquals(AppSettings.DEFAULT_CRUX_PLATFORMS.size, known.size)
    }

    @Test
    fun everySocialPlatformCanPostAndRssCannot() {
        CruxPlatform.entries.filter { it.site != null }.forEach { assertTrue(it.name, it.canPost) }
        assertEquals(false, CruxPlatform.Rss.canPost)
    }
}
