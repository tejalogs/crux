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
    fun everyTweakFollowsOptInDefaults() {
        val off = SiteTweak.entries.filterNot { it in SiteWebSettings.defaultsFor(it.site).tweaks }.toSet()
        assertEquals(
            setOf(
                SiteTweak.InstagramSuggested,
                SiteTweak.InstagramStories,
                SiteTweak.XHideForYou,
                SiteTweak.LinkedInSuggested,
            ),
            off,
        )
    }

    @Test
    fun everySiteHasTweaksAndAllowsPlaybackByDefault() {
        LiveWebSite.entries.forEach { site ->
            assertTrue("$site has no tweaks", SiteTweak.forSite(site).isNotEmpty())
            assertFalse(SiteWebSettings.defaultsFor(site).blockAutoplay)
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
        val igAll = script(LiveWebSite.Instagram, SiteTweak.forSite(LiveWebSite.Instagram).toSet())
        assertTrue(igAll.contains("a[href=\\\"/reels/\\\"]"))
        assertTrue(igAll.contains("data-cx-explore"))
        assertTrue(igAll.contains("Sponsored"))
        assertTrue(igAll.contains("Suggested for you"))
        assertTrue(igAll.contains("play.google.com"))
        // Default Instagram script keeps Suggested posts off to avoid hiding feed content during scroll.
        assertFalse(all.contains("Suggested for you"))

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
        assertFalse("BottomBar must not be hidden so X login works", all.contains("data-testid=\\\"BottomBar\\\""))
        assertFalse("Don't miss what's happening must not be in prompts to avoid hiding login bar", all.contains("Don['’]t miss what['’]s happening"))

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
        assertNotEquals(x, base.updateSite(LiveWebSite.X) { it.withBlockAutoplay(true) }.configFor(LiveWebSite.X))
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
        assertTrue(script.contains("View in Reddit App"))
        assertTrue(script.contains("shadowRoot"))
    }

    @Test
    fun noSiteScriptRewritesBrowserFunctions() {
        // Rewriting page APIs on a signed-in site is an automation tell; tweaks only hide things.
        LiveWebSite.entries.forEach { site ->
            val script = buildSiteScript(site, SiteTweak.forSite(site).toSet()).orEmpty()
            assertTrue("${site.name} patches a prototype", ".prototype." !in script)
            assertTrue("${site.name} fakes storage access", "requestStorageAccess" !in script)
            assertTrue("${site.name} fakes permissions", "permissions.query" !in script)
        }
    }

    @Test
    fun openLinksInReaderDefaultsOffAndFollowsToggle() {
        assertFalse(WebPrivacySettings.Default.openLinksInReader)
        assertFalse(WebPrivacySettings.Default.configFor(LiveWebSite.X).openInReader)
        val enabled = WebPrivacySettings.Default.copy(openLinksInReader = true)
        assertTrue(enabled.configFor(LiveWebSite.X).openInReader)
    }

    @Test
    fun batchAPrivacyDefaultsAndConfigPropagation() {
        val defaults = WebPrivacySettings.Default
        assertFalse(defaults.blockThirdPartyCookies)
        assertTrue(defaults.rememberTabPosition)
        assertFalse(defaults.configFor(LiveWebSite.Reddit).blockThirdPartyCookies)
        assertTrue(defaults.configFor(LiveWebSite.Reddit).rememberTabPosition)

        val hardened =
            defaults.copy(
                blockThirdPartyCookies = true,
                rememberTabPosition = false,
            )
        assertTrue(hardened.configFor(LiveWebSite.X).blockThirdPartyCookies)
        assertFalse(hardened.configFor(LiveWebSite.X).rememberTabPosition)
    }

    @Test
    fun redditAiTrendingAndNsfwFollowToggles() {
        val defaults = defaultScript(LiveWebSite.Reddit)
        assertTrue(defaults.contains("reddit-answers"))
        assertTrue(defaults.contains("trending-carousel"))
        assertTrue("NSFW unblur is on by default", defaults.contains("removeAttribute('nsfw')"))
        assertTrue(defaults.contains("over18=1"))

        val withoutUnblur =
            script(LiveWebSite.Reddit, SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks - SiteTweak.RedditUnblurNsfw)
        assertFalse(withoutUnblur.contains("removeAttribute('nsfw')"))
        assertFalse(withoutUnblur.contains("over18=1"))
    }

    @Test
    fun aiTweaksCoverInstagramXAndLinkedInWithoutBreakingSafeRules() {
        assertTrue(defaultScript(LiveWebSite.Instagram).contains("Meta AI"))
        assertFalse(defaultScript(LiveWebSite.Instagram).contains("story_tray"))
        assertTrue(defaultScript(LiveWebSite.X).contains("spacesBar"))
        assertTrue(defaultScript(LiveWebSite.LinkedIn).contains("Rewrite with AI"))
        assertTrue(defaultScript(LiveWebSite.LinkedIn).contains("/games/"))
    }

    @Test
    fun calmMetricsHidesEngagementCountsOnlyWhenEnabled() {
        assertFalse(WebPrivacySettings.Default.hideEngagementCounts)
        val xDefault = WebPrivacySettings.Default.configFor(LiveWebSite.X).documentStartScripts(LiveWebSite.X).joinToString()
        assertFalse(xDefault.contains("app-text-transition-container"))

        val calm = WebPrivacySettings.Default.copy(hideEngagementCounts = true)
        assertTrue(calm.configFor(LiveWebSite.X).documentStartScripts(LiveWebSite.X).joinToString().contains("app-text-transition-container"))
        assertTrue(calm.configFor(LiveWebSite.LinkedIn).documentStartScripts(LiveWebSite.LinkedIn).joinToString().contains("social-details-social-counts"))
        assertTrue(calm.configFor(LiveWebSite.Reddit).documentStartScripts(LiveWebSite.Reddit).joinToString().contains("vote-count"))
        assertTrue(calm.configFor(LiveWebSite.Instagram).documentStartScripts(LiveWebSite.Instagram).joinToString().contains("/liked_by/"))
    }

    @Test
    fun aiPrivacyUrlsStayWithinSiteAllowedHosts() {
        LiveWebSite.entries.forEach { site ->
            val uri = URI(site.aiPrivacyUrl)
            assertEquals("https", uri.scheme)
            assertTrue("${site.name} aiPrivacyUrl ${site.aiPrivacyUrl} must stay allowed", site.allows(uri.host))
        }
    }

    @Test
    fun strictBlocksExpandedTelemetryHosts() {
        assertTrue(shouldBlockHost(LiveWebSite.Reddit, ProtectionLevel.Strict, "segment.io"))
        assertTrue(shouldBlockHost(LiveWebSite.Reddit, ProtectionLevel.Strict, "w3-reporting-nel.reddit.com"))
        assertTrue(shouldBlockHost(LiveWebSite.X, ProtectionLevel.Strict, "ads-api.twitter.com"))
    }

    @Test
    fun sessionCookiesExcludeGuestTokensAndStartUrlsUseLoginFlowWhenSignedOut() {
        assertFalse("ct0 is set on guest X visits and must not mark X signed in", "ct0" in LiveWebSite.X.sessionCookies)
        assertTrue("auth_token" in LiveWebSite.X.sessionCookies)
        assertFalse("token_v2 is set on anonymous Reddit visits and must not mark Reddit signed in", "token_v2" in LiveWebSite.Reddit.sessionCookies)
        assertTrue("reddit_session" in LiveWebSite.Reddit.sessionCookies)

        assertEquals("https://x.com/i/flow/login", LiveWebSite.X.startUrlForSession())
        assertEquals("https://www.instagram.com/accounts/login/", LiveWebSite.Instagram.startUrlForSession())
        assertEquals("https://www.linkedin.com/login", LiveWebSite.LinkedIn.startUrlForSession())
        assertEquals("https://www.reddit.com/new/", LiveWebSite.Reddit.startUrlForSession())
        assertTrue(LiveWebSite.X.isSignedOutXRoot("https://x.com/"))
        assertTrue(LiveWebSite.X.isSignedOutXRoot("https://x.com/?mx=2"))
        assertFalse(LiveWebSite.X.isSignedOutXRoot("https://x.com/i/flow/login"))
        assertFalse(LiveWebSite.X.isSignedOutXRoot("https://x.com/jack"))
    }

    @Test
    fun xAllowsFeedHostsAndExcludesDocsAndMarketingSubdomains() {
        assertTrue(LiveWebSite.X.allows("x.com"))
        assertTrue(LiveWebSite.X.allows("mobile.x.com"))
        assertTrue(LiveWebSite.X.allows("api.x.com"))
        assertFalse(LiveWebSite.X.allows("developer.x.com"))
        assertFalse(LiveWebSite.X.allows("help.x.com"))
        assertFalse(LiveWebSite.X.allows("business.x.com"))
        assertFalse(LiveWebSite.X.allows("about.x.com"))
    }

    @Test
    fun engineGuardsAuthAndLoginPathsAgainstDomHiding() {
        val xJs = defaultScript(LiveWebSite.X)
        assertTrue(xJs.contains("isAuthPage"))
        assertTrue(xJs.contains("i\\/flow|login|accounts\\/login|uas\\/login|checkpoint|challenge"))
    }

    @Test
    fun engineIncludesNativeBookmarkTapHookWithoutGlobalWindowLeak() {
        val xJs = defaultScript(LiveWebSite.X)
        assertTrue(xJs.contains("data-cx-tapped"))
        assertTrue(xJs.contains("__crux_bm_tap__"))
        assertTrue(xJs.contains("__crux_ptr:0"))
        assertTrue(xJs.contains("canHide"))
        assertFalse(xJs.contains("window.__cruxLastTappedPost"))
    }

    @Test
    fun redditAllowsVideoAndMediaHostsAndUnwrapsLinkedInRedirects() {
        assertTrue(LiveWebSite.Reddit.allows("v.redd.it"))
        assertTrue(LiveWebSite.Reddit.allows("i.redd.it"))
        assertTrue(LiveWebSite.Reddit.allows("www.redditstatic.com"))
        assertEquals(
            "https://example.com/article",
            cleanOutgoingLink("https://www.linkedin.com/redir/redirect?url=https%3A%2F%2Fexample.com%2Farticle&trk=abc"),
        )
    }

    @Test
    fun redditScriptPreservesNativeScrollSearchInputAndNsfwPostDescriptions() {
        val redditJs = defaultScript(LiveWebSite.Reddit)
        assertFalse("Nested overflow-y:auto breaks root compositor scrolling", redditJs.contains("overflow-y:auto!important"))
        assertTrue(redditJs.contains("overflow-x:clip!important"))
        assertTrue("Scans must pause while typing in shadow-DOM search inputs", redditJs.contains("isEditableFocused"))
        assertTrue(redditJs.contains("__cxRedditSearchGuard"))
        assertTrue("Post detail pages must not be hidden by feed labels", redditJs.contains("isPostDetail"))
        assertTrue(redditJs.contains("shreddit-blurred-container[cx-unblurred]"))
        assertFalse(
            "Blanket nsfw async-loader hiding can drop NSFW post bodies",
            redditJs.contains("shreddit-async-loader[bundlename*=\\\"nsfw\\\"]{"),
        )
        assertFalse(
            "Unconditional blurred hiding before reveal drops text descriptions",
            redditJs.contains("shreddit-blurred-container [slot=\\\"blurred\\\"]"),
        )
        assertTrue("Reddit search guard handles IME compositionend", redditJs.contains("compositionend"))
        assertTrue("Reddit NSFW reveal removes hidden attribute", redditJs.contains("removeAttribute('hidden')"))
    }

    @Test
    fun instagramReelsLocksHorizontalScrollAndAllSitesClipHorizontalOverflow() {
        val igJs = defaultScript(LiveWebSite.Instagram)
        assertTrue(igJs.contains("overflow-x:clip!important"))
        assertTrue(igJs.contains("overscroll-behavior-x:none!important"))
        assertTrue(igJs.contains("data-cx-reels"))
        assertTrue(igJs.contains("touch-action:pan-y!important"))
        assertTrue(igJs.contains("overflow-wrap:anywhere!important"))
        assertTrue(igJs.contains("__cxIgReelLock"))
        assertTrue(igJs.contains("scrollLeft=0"))

        val xJs = defaultScript(LiveWebSite.X)
        assertTrue(xJs.contains("overflow-x:clip!important"))
        assertTrue(xJs.contains("overscroll-behavior-x:none!important"))
        assertTrue(xJs.contains("fleetline"))
        assertTrue(xJs.contains("/i/broadcasts/"))

        val liJs = defaultScript(LiveWebSite.LinkedIn)
        assertTrue(liJs.contains("overflow-x:clip!important"))
        assertTrue(liJs.contains("overscroll-behavior-x:none!important"))
    }

    @Test
    fun oauthAndChallengeHostsStayAllowedAcrossSocialPlatforms() {
        listOf(LiveWebSite.X, LiveWebSite.LinkedIn, LiveWebSite.Reddit).forEach { site ->
            assertTrue("$site should allow accounts.google.com", site.allows("accounts.google.com"))
            assertTrue("$site should allow gds.google.com", site.allows("gds.google.com"))
            assertTrue("$site should allow accounts.youtube.com", site.allows("accounts.youtube.com"))
            assertTrue("$site should allow appleid.apple.com", site.allows("appleid.apple.com"))
        }
        assertTrue(LiveWebSite.Instagram.allows("www.instagram.com"))
        assertTrue(LiveWebSite.Instagram.allows("m.facebook.com"))
    }
}
