package org.crux.ui.screen.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebXPremiumTest {
    private val script = requireNotNull(buildSiteScript(LiveWebSite.X, setOf(SiteTweak.XPremiumGrok)))

    @Test
    fun hidesTheHeaderSubscribeLinkBySelector() {
        listOf(
            "a[href*=\\\"/i/premium\\\"]",
            "a[href*=\\\"premium_sign_up\\\"]",
            "a[aria-label=\\\"Subscribe\\\"]",
            "a[aria-label=\\\"Get Premium\\\"]",
        ).forEach { assertTrue("missing $it", script.contains(it)) }
    }

    @Test
    fun hidesTheSubscribeButtonByItsOwnText() {
        assertTrue(script.contains("Subscribe"))
        assertTrue(script.contains("Get Premium"))
        // Only the element itself is hidden, never a wrapper that could be the header.
        assertTrue(script.contains("b.style.setProperty('display','none','important')"))
    }

    @Test
    fun theHeaderGuardStaysInTheEngine() {
        assertTrue(script.contains("HEADER|NAV"))
    }

    @Test
    fun nothingIsHiddenWhenTheTweakIsOff() {
        val off = requireNotNull(buildSiteScript(LiveWebSite.X, setOf(SiteTweak.XTrends)))
        assertFalse(off.contains("premium_sign_up"))
    }
}
