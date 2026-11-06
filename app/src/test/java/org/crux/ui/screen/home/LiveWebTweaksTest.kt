package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class LiveWebTweaksTest {
    private fun script(
        site: LiveWebSite,
        tweaks: Set<SiteTweak>,
    ): String = requireNotNull(buildSiteScript(site, tweaks)) { "no script for $site" }

    private fun defaultScript(site: LiveWebSite) = script(site, SiteWebSettings.defaultsFor(site).tweaks)

    // Defaults

    @Test
    fun signedInAccountsDefaultToStandardAndRedditToStrict() {
        assertEquals(ProtectionLevel.Standard, SiteWebSettings.defaultsFor(LiveWebSite.Instagram).protection)
        assertEquals(ProtectionLevel.Standard, SiteWebSettings.defaultsFor(LiveWebSite.X).protection)
        assertEquals(ProtectionLevel.Standard, SiteWebSettings.defaultsFor(LiveWebSite.LinkedIn).protection)
        assertEquals(ProtectionLevel.Strict, SiteWebSettings.defaultsFor(LiveWebSite.Reddit).protection)
    }

    @Test
    fun everyTweakIsOnByDefaultExceptHidingForYou() {
        val off = SiteTweak.entries.filterNot { it in SiteWebSettings.defaultsFor(it.site).tweaks }
        assertEquals(listOf(SiteTweak.XHideForYou), off)
    }

    @Test
    fun everySiteHasTweaksAndBlocksAutoplayByDefault() {
        LiveWebSite.entries.forEach { site ->
            assertTrue("$site has no tweaks", SiteTweak.forSite(site).isNotEmpty())
            assertTrue(SiteWebSettings.defaultsFor(site).blockAutoplay)
        }
    }

    @Test
    fun globalDefaults() {
        val defaults = WebPrivacySettings.Default
        assertTrue(defaults.cleanOutgoingLinks)
        assertFalse(defaults.secureWindow)
        assertFalse(defaults.sendGlobalPrivacyControl)
    }

    @Test
    fun updatingASiteLeavesTheOriginalAndOtherSitesAlone() {
        val before = WebPrivacySettings.Default
        val after =
            before.updateSite(LiveWebSite.X) { it.withProtection(ProtectionLevel.Strict).withTweak(SiteTweak.XTrends, false) }
        assertEquals(ProtectionLevel.Standard, before.site(LiveWebSite.X).protection)
        assertTrue(before.site(LiveWebSite.X).isOn(SiteTweak.XTrends))
        assertEquals(ProtectionLevel.Strict, after.site(LiveWebSite.X).protection)
        assertFalse(after.site(LiveWebSite.X).isOn(SiteTweak.XTrends))
        assertEquals(before.site(LiveWebSite.Instagram), after.site(LiveWebSite.Instagram))
    }

    // Script generation

    @Test
    fun instagramScriptFollowsTheToggles() {
        val all = defaultScript(LiveWebSite.Instagram)
        assertTrue(all.contains("a[href=\\\"/reels/\\\"]"))
        assertTrue(all.contains("data-cx-explore"))
        assertTrue(all.contains("Sponsored"))
        assertTrue(all.contains("Suggested for you"))
        assertTrue(all.contains("play.google.com"))

        val noReels = script(LiveWebSite.Instagram, SiteWebSettings.defaultsFor(LiveWebSite.Instagram).tweaks - SiteTweak.InstagramReels)
        assertFalse(noReels.contains("/reels/"))
        assertTrue(noReels.contains("Sponsored"))

        val onlyAds = script(LiveWebSite.Instagram, setOf(SiteTweak.InstagramAds))
        assertTrue(onlyAds.contains("Sponsored"))
        assertFalse(onlyAds.contains("Suggested for you"))
        assertFalse(onlyAds.contains("data-cx-explore"))
        assertFalse(onlyAds.contains("play.google.com"))
    }

    @Test
    fun xScriptFollowsTheToggles() {
        val all = defaultScript(LiveWebSite.X)
        assertTrue(all.contains("placementTracking"))
        assertTrue(all.contains("Who to follow"))
        assertTrue(all.contains("data-testid=\\\"trend\\\""))
        assertTrue(all.contains("Grok"))
        assertTrue(all.contains("premium_sign_up"))
        assertFalse("For you tab is off by default", all.contains("For you"))

        val withForYou = script(LiveWebSite.X, SiteWebSettings.defaultsFor(LiveWebSite.X).tweaks + SiteTweak.XHideForYou)
        assertTrue(withForYou.contains("For you"))

        val noGrok = script(LiveWebSite.X, SiteWebSettings.defaultsFor(LiveWebSite.X).tweaks - SiteTweak.XPremiumGrok)
        assertFalse(noGrok.contains("Grok"))
        assertFalse(noGrok.contains("premium_sign_up"))
        assertTrue(noGrok.contains("placementTracking"))
    }

    @Test
    fun linkedInScriptFollowsTheToggles() {
        val all = defaultScript(LiveWebSite.LinkedIn)
        assertTrue(all.contains("Promoted"))
        assertTrue(all.contains("Add to your feed"))
        assertTrue(all.contains("mobile_app_upsell"))

        val noNews = script(LiveWebSite.LinkedIn, SiteWebSettings.defaultsFor(LiveWebSite.LinkedIn).tweaks - SiteTweak.LinkedInNews)
        assertFalse(noNews.contains("Add to your feed"))
        assertFalse(noNews.contains("LinkedIn News"))
        assertTrue(noNews.contains("Promoted"))
    }

    @Test
    fun redditScriptFollowsTheToggles() {
        val all = defaultScript(LiveWebSite.Reddit)
        assertTrue(all.contains("shreddit-ad-post"))
        assertTrue(all.contains("xpromo"))
        assertTrue(all.contains("recommendation"))

        val noRecommended =
            script(LiveWebSite.Reddit, SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks - SiteTweak.RedditRecommended)
        assertFalse(noRecommended.contains("recommendation"))
        assertTrue(noRecommended.contains("shreddit-ad-post"))
    }

    @Test
    fun xHasNoScriptWhenEveryTweakIsOff() {
        assertNull(buildSiteScript(LiveWebSite.X, emptySet()))
    }

    @Test
    fun loggedInSiteScriptsOnlyHideAndNeverTouchRequestsOrClick() {
        listOf(LiveWebSite.Instagram, LiveWebSite.X, LiveWebSite.LinkedIn).forEach { site ->
            val js = script(site, SiteTweak.forSite(site).toSet())
            listOf("fetch(", "XMLHttpRequest", "sendBeacon", ".click()", "WebSocket").forEach {
                assertFalse("$site script contains $it", js.contains(it))
            }
        }
    }

    @Test
    fun noRuleUsesAnInvalidSelectorThatWouldDropTheWholeStylesheet() {
        SiteTweak.entries.forEach { tweak ->
            tweak.rule.css.forEach { selector ->
                assertFalse("$tweak: $selector", selector.contains(":has-text") || selector.contains(":contains("))
                assertFalse("$tweak: selector lists belong in separate entries", selector.contains("),"))
            }
        }
    }

    @Test
    fun eachSelectorBecomesItsOwnRule() {
        val js = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditPromoted))
        assertEquals(
            4,
            Regex
                .escape("{display:none!important}")
                .toRegex()
                .findAll(js)
                .count(),
        )
    }

    @Test
    fun labelPatternMatchesWholeTextOnlyAndEitherApostrophe() {
        val pattern = Regex(labelPattern(listOf("What's happening", "Because you visited*")), RegexOption.IGNORE_CASE)
        assertTrue(pattern.containsMatchIn("What's happening"))
        assertTrue(pattern.containsMatchIn("What’s happening"))
        assertTrue(pattern.containsMatchIn("  · What's happening "))
        assertFalse(pattern.containsMatchIn("What's happening in the world today"))
        assertTrue(pattern.containsMatchIn("Because you visited r/android"))
        assertEquals("", labelPattern(emptyList()))
    }

    // Request blocking

    private fun hostOf(url: String): String = URI(url).host

    @Test
    fun standardNeverBlocksAnything() {
        LiveWebSite.entries.forEach { site ->
            assertTrue(blockedHostsFor(site, ProtectionLevel.Standard).isEmpty())
            val hosts = listOf(hostOf(site.homeUrl), "doubleclick.net", "scribe.twitter.com", "connect.facebook.net") + site.trackerHosts
            hosts.forEach { host ->
                assertFalse("$site Standard blocked $host", shouldBlockHost(site, ProtectionLevel.Standard, host))
            }
        }
    }

    @Test
    fun strictBlocksTheGlobalListAndTheSitesOwnTrackers() {
        LiveWebSite.entries.forEach { site ->
            assertTrue(shouldBlockHost(site, ProtectionLevel.Strict, "doubleclick.net"))
            assertTrue(shouldBlockHost(site, ProtectionLevel.Strict, "ad.doubleclick.net"))
            site.trackerHosts.forEach { assertTrue("$site $it", shouldBlockHost(site, ProtectionLevel.Strict, it)) }
        }
        assertTrue(shouldBlockHost(LiveWebSite.Reddit, ProtectionLevel.Strict, "alb.reddit.com"))
        assertTrue(shouldBlockHost(LiveWebSite.X, ProtectionLevel.Strict, "scribe.twitter.com"))
    }

    @Test
    fun strictStillLoadsTheSiteItself() {
        listOf(
            LiveWebSite.Instagram to listOf("www.instagram.com", "i.instagram.com", "scontent.cdninstagram.com", "graph.instagram.com"),
            LiveWebSite.X to listOf("x.com", "api.x.com", "abs.twimg.com", "pbs.twimg.com", "www.google.com", "www.gstatic.com"),
            LiveWebSite.LinkedIn to listOf("www.linkedin.com", "media.licdn.com", "static.licdn.com"),
            LiveWebSite.Reddit to listOf("www.reddit.com", "oauth.reddit.com", "i.redd.it", "preview.redd.it", "www.redditstatic.com"),
        ).forEach { (site, hosts) ->
            hosts.forEach { assertFalse("$site Strict blocked $it", shouldBlockHost(site, ProtectionLevel.Strict, it)) }
        }
    }

    @Test
    fun hostMatchingIsBySuffixOnLabelBoundaries() {
        assertTrue(hostMatchesAny("a.b.example.com", setOf("example.com")))
        assertFalse(hostMatchesAny("notexample.com", setOf("example.com")))
        assertFalse(hostMatchesAny(null, setOf("example.com")))
    }

    // Config

    @Test
    fun configFollowsSettingsAndDiffersWhenASettingChanges() {
        val base = WebPrivacySettings.Default
        val x = base.configFor(LiveWebSite.X)
        assertEquals(x, base.configFor(LiveWebSite.X))
        assertNotEquals(x, base.updateSite(LiveWebSite.X) { it.withTweak(SiteTweak.XTrends, false) }.configFor(LiveWebSite.X))
        assertNotEquals(x, base.updateSite(LiveWebSite.X) { it.withBlockAutoplay(false) }.configFor(LiveWebSite.X))
        assertNotEquals(x, base.copy(cleanOutgoingLinks = false).configFor(LiveWebSite.X))
        assertFalse(x.blocks(LiveWebSite.X, "doubleclick.net"))
    }

    @Test
    fun globalPrivacyControlIsOnlyInjectedOnStrictSites() {
        val on = WebPrivacySettings.Default.copy(sendGlobalPrivacyControl = true)
        val standard = on.configFor(LiveWebSite.Instagram).documentStartScripts(LiveWebSite.Instagram)
        assertFalse(standard.any { it.contains("globalPrivacyControl") })
        val strict = on.configFor(LiveWebSite.Reddit).documentStartScripts(LiveWebSite.Reddit)
        assertTrue(strict.any { it.contains("globalPrivacyControl") })
        val off = WebPrivacySettings.Default.configFor(LiveWebSite.Reddit).documentStartScripts(LiveWebSite.Reddit)
        assertFalse(off.any { it.contains("globalPrivacyControl") })
        val strictInstagram =
            on
                .updateSite(LiveWebSite.Instagram) { it.withProtection(ProtectionLevel.Strict) }
                .configFor(LiveWebSite.Instagram)
                .documentStartScripts(LiveWebSite.Instagram)
        assertTrue(strictInstagram.any { it.contains("globalPrivacyControl") })
    }

    @Test
    fun everySiteGetsATweakScriptByDefault() {
        LiveWebSite.entries.forEach {
            assertNotNull(
                WebPrivacySettings.Default
                    .configFor(it)
                    .documentStartScripts(it)
                    .firstOrNull(),
            )
        }
    }

    // Per-site data clearing

    @Test
    fun expiryCookiesCoverHostOnlyAndDomainCookies() {
        val cookies = expiryCookiesFor("x.com", "auth_token")
        assertEquals(2, cookies.size)
        assertTrue(cookies.all { it.startsWith("auth_token=;") && it.contains("Max-Age=0") })
        assertTrue(cookies.any { it.endsWith("Domain=.x.com") })
        assertTrue(cookies.any { !it.contains("Domain=") })
    }

    @Test
    fun clearingOneSiteNeverTargetsAnotherSitesDomains() {
        val domainsBySite = LiveWebSite.entries.associateWith { it.cookieDomains }
        LiveWebSite.entries.forEach { site ->
            val others = domainsBySite.filterKeys { it != site }.values.flatten()
            site.cookieDomains.forEach { domain ->
                others.forEach { other ->
                    assertFalse("$site domain $domain overlaps $other", domain == other || domain.endsWith(".$other"))
                }
            }
        }
        assertEquals(listOf("https://x.com", "https://www.x.com"), storageOriginsFor("x.com"))
    }

    @Test
    fun promptHidingNeverClimbsIntoASiteHeader() {
        val script = buildSiteScript(LiveWebSite.Reddit, SiteTweak.forSite(LiveWebSite.Reddit).toSet()).orEmpty()
        // The header around Reddit's "Open App" button also holds the menu and search.
        assertTrue(script.contains("querySelectorAll(clickable).length<=1"))
        assertTrue(script.contains("HEADER|NAV"))
    }
}
