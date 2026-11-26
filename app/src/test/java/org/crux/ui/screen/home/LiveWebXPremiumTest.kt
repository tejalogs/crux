package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebXPremiumTest {
    private val script = requireNotNull(buildSiteScript(LiveWebSite.X, setOf(SiteTweak.XPremiumGrok)))

    @Test
    fun premiumLinksInTheMenusGoAtOnce() {
        listOf(
            "nav a[href*=\\\"/i/premium\\\"]",
            "[role=\\\"navigation\\\"] a[href*=\\\"/i/premium\\\"]",
            "nav a[aria-label=\\\"Get Premium\\\"]",
            "nav [data-testid=\\\"premium-signup-tab\\\"]",
        ).forEach { assertTrue("missing $it", script.contains(it)) }
    }

    @Test
    fun noPremiumOrSubscribeRuleSkipsTheAccountNoticeGuard() {
        val css = SiteTweak.XPremiumGrok.rule.css
        listOf(
            "a[href*=\"/i/premium\"]",
            "a[href=\"/i/premium_sign_up\"]",
            "a[href*=\"premium_sign_up\"]",
            "a[href*=\"/i/verified\"]",
            "a[aria-label=\"Subscribe\"]",
            "button[aria-label=\"Subscribe\"]",
            "[data-testid=\"subscribe\"]",
            "a[aria-label=\"Get Premium\"]",
            "[data-testid=\"premium-signup-tab\"]",
            "a[href=\"/i/verified-choose\"]",
            "a[href*=\"/verified-orgs-signup\"]",
        ).forEach { assertFalse("$it hides a notice's action or a creator's button unconditionally", it in css) }
        val premium = css.filter { rule -> listOf("/i/premium", "/i/verified", "verified-", "Get Premium", "premium-signup", "premium_sign_up").any { it in rule } }
        assertEquals("the menu rules are all still there", 10, premium.size)
        premium.forEach {
            assertTrue("$it is only for the menus", it.startsWith("nav ") || it.startsWith("[role=\"navigation\"] "))
        }
    }

    @Test
    fun subscribeButtonsGoThroughTheGuardedUpsellScan() {
        assertEquals(X_PREMIUM_BUTTON_LABELS, SiteTweak.XPremiumGrok.rule.buttonLabels)
        assertTrue(script.contains("if(buttons&&!auth)scanButtons();"))
        assertTrue("each button is read once", script.contains("if(checkedButtons.has(b)||hiddenEls.has(b))return;"))
        assertTrue("never next to a limit, payment or billing notice", script.contains("if(accountNotice&&ctx&&accountNotice.test(clean(ctx.textContent)))return;"))
        assertTrue("only the button itself is hidden", script.contains("if(hide(b,false))hiddenEls.add(b);"))
        assertFalse("the old unguarded text loop is gone", script.contains("b.style.setProperty('display','none','important')"))
        assertFalse(script.contains("t.length<=24&&rx.test(t)"))
    }

    @Test
    fun premiumLinksOutsideTheMenusKeepTheNoticeGuard() {
        assertTrue(script.contains("document.querySelectorAll('$X_PREMIUM_LINK_SELECTOR')"))
        listOf(
            "a[href*=\"/i/premium\"]",
            "a[href*=\"premium_sign_up\"]",
            "a[href*=\"/i/verified\"]",
            "a[href*=\"/verified-orgs-signup\"]",
            "a[aria-label=\"Get Premium\"]",
            "[data-testid=\"premium-signup-tab\"]",
        ).forEach { assertTrue("$it outside the menus goes through the guard", X_PREMIUM_LINK_SELECTOR.split(",").contains(it)) }
        assertTrue(script.contains("if(a.closest('article')){seen.add(a);return;}"))
        assertTrue(script.contains("var ctx=noticeCtx(a);"))
        assertTrue(script.contains("if(ctx&&accountNotice.test(clean(ctx.textContent)))return;"))
        assertTrue("settings, Premium and monetization pages are left alone", script.contains("settings|i\\/premium|i\\/verified|i\\/monetization"))
    }

    @Test
    fun creatorSubscribeButtonsAndPostsStay() {
        val keep = X_PROFILE_KEEP_JS
        assertTrue(script.contains("var keepUpsell=function(b){try{return !!($keep)}catch(e){return true}};"))
        assertTrue("checked after the label, before hiding, and not cached", script.contains("if(keepUpsell(b))return;"))
        assertTrue("never inside a post", keep.startsWith("b.closest('article')"))
        assertTrue("creator subscriptions pages", keep.contains("creator-subscriptions"))
        assertTrue("a profile header's own Subscribe", keep.contains("document.querySelector('[data-testid=\"UserName\"]')"))
        assertTrue("menus on a profile page still lose the upsell", keep.contains("!b.closest('nav,[role=\"navigation\"]')"))
        assertTrue("Premium links are never a creator's button", keep.contains("premium_sign_up"))
    }

    @Test
    fun otherSitesKeepNoUpsellExceptions() {
        val linkedIn = requireNotNull(buildSiteScript(LiveWebSite.LinkedIn, setOf(SiteTweak.LinkedInPremium)))
        assertTrue(linkedIn.contains("var keepUpsell=function(b){try{return !!(false)}catch(e){return true}};"))
    }

    @Test
    fun theHeaderGuardStaysInTheEngine() {
        assertTrue(script.contains("HEADER|NAV"))
    }

    @Test
    fun nothingIsHiddenWhenTheTweakIsOff() {
        val off = requireNotNull(buildSiteScript(LiveWebSite.X, setOf(SiteTweak.XTrends)))
        assertFalse(off.contains("premium_sign_up"))
        assertTrue("no labels, no scan", off.contains("buttons=rx(\"\")"))
        assertTrue("and no keep test either", off.contains("var keepUpsell=function(b){try{return !!(false)}catch(e){return true}};"))
        assertFalse(off.contains("creator-subscriptions"))
    }

    private companion object {
        val X_PROFILE_KEEP_JS: String = LiveWebSite.X.scriptProfile().upsellKeepJs
    }
}
