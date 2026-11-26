package org.crux.ui.screen.home

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class LiveWebLinkOpenerAnchorTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun nextStartedIntent(): Intent? = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity

    @Test
    fun aTcoLinkWhoseAddressXPrintedOpensWithNoLookup() {
        val lookedUp = mutableListOf<String>()
        val lookups =
            PendingShortLinks(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { url ->
                lookedUp += url
                error("must not look up a link X already expanded")
            }

        openLinkOutsideWebView(
            context,
            "https://t.co/AbC123xyz",
            clean = true,
            openInReader = false,
            anchorExpansion = "https://example.com/news/full-story?utm_source=twitter…",
            shortLinkLookups = lookups,
        )

        assertTrue(lookedUp.isEmpty())
        val intent = nextStartedIntent()
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent!!.action)
        assertEquals("https://example.com/news/full-story", intent.dataString)
    }

    @Test
    fun withoutAUsableAnchorTheLinkIsLookedUpAndItsTargetOpens() {
        val lookedUp = mutableListOf<String>()
        val lookups =
            PendingShortLinks(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { url ->
                lookedUp += url
                "https://example.com/news/looked-up"
            }

        openLinkOutsideWebView(
            context,
            "https://t.co/AbC123xyz",
            clean = true,
            openInReader = false,
            anchorExpansion = "http://192.168.1.1/a-b",
            shortLinkLookups = lookups,
        )

        // A private address in the anchor is never trusted: the cookie-less lookup runs instead.
        assertEquals(listOf("https://t.co/AbC123xyz"), lookedUp)
        assertNull(lookups.pendingLink)
    }
}
