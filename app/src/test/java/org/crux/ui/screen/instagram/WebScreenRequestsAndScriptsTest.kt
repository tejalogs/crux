package org.crux.ui.screen.instagram

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import org.crux.ui.screen.home.ContactedHost
import org.crux.ui.screen.home.LiveWebConfig
import org.crux.ui.screen.home.LiveWebContactLog
import org.crux.ui.screen.home.LiveWebPrivacyCounters
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.ProtectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WebScreenRequestsAndScriptsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val counters get() = LiveWebPrivacyCounters.get(context)

    @Before
    fun clearLog() {
        LiveWebContactLog.clear()
    }

    private fun request(url: String): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame(): Boolean = false

            override fun isRedirect(): Boolean = false

            override fun hasGesture(): Boolean = false

            override fun getMethod(): String = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

    private fun hostsOf(site: LiveWebSite) = LiveWebContactLog.snapshot().firstOrNull { it.site == site }?.hosts.orEmpty()

    @Test
    fun instagramScreenRequestsReachTheContactLogAndAreNeverBlocked() {
        val config = LiveWebConfig.defaultsFor(LiveWebSite.Instagram)
        assertEquals(ProtectionLevel.Standard, config.protection)
        val before = counters.blockedRequestsBySite.value[LiveWebSite.Instagram] ?: 0L

        assertNull(screenRequestResponse(LiveWebSite.Instagram, config, request("https://connect.facebook.net/en_US/fbevents.js"), counters))
        assertNull(screenRequestResponse(LiveWebSite.Instagram, config, request("https://www.instagram.com/api/v1/feed/"), counters))

        val hosts = hostsOf(LiveWebSite.Instagram)
        assertEquals(setOf("connect.facebook.net", "www.instagram.com"), hosts.map { it.host }.toSet())
        assertTrue(hosts.all { it.blocked == 0 })
        assertEquals(before, counters.blockedRequestsBySite.value[LiveWebSite.Instagram] ?: 0L)
    }

    @Test
    fun linkedInScreenRequestsReachTheContactLog() {
        val config = LiveWebConfig.defaultsFor(LiveWebSite.LinkedIn)
        screenRequestResponse(LiveWebSite.LinkedIn, config, request("https://www.linkedin.com/feed/"), counters)
        assertEquals(
            listOf(ContactedHost("www.linkedin.com", thirdParty = false, requests = 1, blocked = 0)),
            hostsOf(LiveWebSite.LinkedIn),
        )
    }

    @Test
    fun noRequestRecordsNothing() {
        assertNull(screenRequestResponse(LiveWebSite.Instagram, LiveWebConfig.defaultsFor(LiveWebSite.Instagram), null, counters))
        assertTrue(hostsOf(LiveWebSite.Instagram).isEmpty())
    }
}

class ReplaceableScriptsTest {
    private class FakeView

    private class Installer {
        val live = mutableListOf<Pair<FakeView, String>>()

        fun install(
            view: FakeView,
            script: String,
        ): (() -> Unit)? {
            if (script == "refused") return null
            val entry = view to script
            live += entry
            return { live.remove(entry) }
        }
    }

    @Test
    fun writingNewScriptsRemovesTheEarlierOnesFirst() {
        val installer = Installer()
        val scripts = ReplaceableScripts(installer::install)
        val view = FakeView()

        scripts.replace(view, listOf("a", "b"))
        scripts.replace(view, listOf("c"))

        assertEquals(listOf(view to "c"), installer.live)
        assertEquals(1, scripts.installedCount)
    }

    @Test
    fun applyingTheSameSetTwiceNeverStacksIt() {
        val installer = Installer()
        val scripts = ReplaceableScripts(installer::install)
        val view = FakeView()

        repeat(3) { scripts.replace(view, listOf("a", "b")) }

        assertEquals(listOf(view to "a", view to "b"), installer.live)
    }

    @Test
    fun aRebuiltViewStartsAFreshSetAndLeavesTheOldViewAlone() {
        val installer = Installer()
        val scripts = ReplaceableScripts(installer::install)
        val old = FakeView()
        val rebuilt = FakeView()

        scripts.replace(old, listOf("a"))
        scripts.replace(rebuilt, listOf("b"))

        assertEquals(listOf(old to "a", rebuilt to "b"), installer.live)
        assertEquals(1, scripts.installedCount)
    }

    @Test
    fun scriptsThatCouldNotBeAddedAreNotTracked() {
        val installer = Installer()
        val scripts = ReplaceableScripts(installer::install)
        val view = FakeView()

        scripts.replace(view, listOf("refused", "a"))
        assertEquals(1, scripts.installedCount)
        scripts.replace(view, emptyList())
        assertTrue(installer.live.isEmpty())
        assertEquals(0, scripts.installedCount)
    }
}
