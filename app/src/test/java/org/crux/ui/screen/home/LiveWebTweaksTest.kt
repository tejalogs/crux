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
                SiteTweak.RedditUnblurNsfw,
            ),
            off,
        )
    }

    @Test
    fun everySiteHasTweaksAndOnlyRedditAndLinkedInBlockAutoplayByDefault() {
        val autoplayBlocked = setOf(LiveWebSite.Reddit, LiveWebSite.LinkedIn)
        LiveWebSite.entries.forEach { site ->
            assertTrue("$site has no tweaks", SiteTweak.forSite(site).isNotEmpty())
            assertEquals("$site blockAutoplay default", site in autoplayBlocked, SiteWebSettings.defaultsFor(site).blockAutoplay)
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
        assertTrue(igAll.contains("${EngineMarks.explore}"))
        assertTrue("the Explore flag is set from Instagram's own route changes", igAll.contains("if(keep.igRouteHook)keep.igRouteHook();"))
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
        assertFalse(onlyAds.contains("${EngineMarks.explore}"))
        assertFalse(onlyAds.contains("play.google.com"))
    }

    @Test
    fun xScriptFollowsTheToggles() {
        val all = defaultScript(LiveWebSite.X)
        assertFalse("placementTracking is structural (videos, profile buttons), never an ad marker", all.contains("placementTracking"))
        assertTrue(all.contains("top-impression-pixel"))
        assertTrue(all.contains("Who to follow"))
        assertTrue("Discover more is on by default", all.contains("Discover more"))
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
        assertTrue(noGrok.contains("top-impression-pixel"))

        val noDiscover = script(LiveWebSite.X, SiteWebSettings.defaultsFor(LiveWebSite.X).tweaks - SiteTweak.XDiscoverMore)
        assertFalse(noDiscover.contains("Discover more"))
        assertTrue(noDiscover.contains("Who to follow"))
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
    fun siteScriptsOnlyHideAndNeverTouchRequestsOrClick() {
        LiveWebSite.entries.forEach { site ->
            val js = script(site, SiteTweak.forSite(site).toSet())
            listOf(
                "fetch(",
                "XMLHttpRequest",
                "sendBeacon",
                "WebSocket",
                ".click(",
                "dispatchEvent(",
                "new MouseEvent",
                "new PointerEvent",
                ".submit(",
                "requestSubmit",
                // Calling a site component's own reveal method is a scripted action too.
                "unblur(",
            ).forEach {
                assertFalse("$site script contains $it", js.contains(it))
            }
        }
    }

    @Test
    fun redditNsfwUnblurNeverPressesRedditsButtonsOrWritesItsPreferences() {
        val js = script(LiveWebSite.Reddit, SiteTweak.forSite(LiveWebSite.Reddit).toSet())
        assertTrue("unblur still reveals loaded posts", js.contains("removeAttribute('nsfw')"))
        assertFalse(js.contains(".click("))
        assertFalse(js.contains("dispatchEvent("))
        assertFalse("never calls Reddit's own unblur handler", js.contains("unblur("))
        // Writing a component's reactive properties is the same reveal path as calling its methods.
        listOf(".blurred=", "__blurred", "isBlurred=", "_blur=").forEach {
            assertFalse("never writes Reddit component state ($it)", js.contains(it))
        }
        assertFalse("no automation helper", js.contains("qNsfwClick"))
        assertFalse("no generic Continue/Yes/18+ button matching", js.contains("Continue in browser"))
        assertFalse(js.contains("I am over 18"))
        assertFalse("Reddit's age preference is left to the person and the app shell", js.contains("document.cookie="))
        assertFalse(js.contains("localStorage.setItem"))
        assertFalse(js.contains("cookieStore.set"))
        assertTrue("only NSFW community barriers are dismissed", js.contains("if(!qIsNsfwBarrier(h))return;"))
    }

    @Test
    fun redditNsfwUnblurLeavesBarriersAndDialogsUntilPostsHaveLoaded() {
        val js = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditUnblurNsfw))
        assertTrue("checks for posts Reddit already loaded", js.contains("var qLoaded=qHasLoadedPosts();"))
        assertTrue(js.contains("document.querySelectorAll('shreddit-post,[slot=\"text-body\"],shreddit-post-text-body')"))
        assertTrue("barrier and dialog handling runs only with loaded posts", js.contains("if(qLoaded)document.querySelectorAll('community-barrier,"))
        assertTrue(js.contains("if(qLoaded)document.documentElement.setAttribute('${EngineMarks.nsfwContent}','1');"))
        val blockers = listOf("#blocking-modal", "#nsfw-qr-dialog", "#configured-xpromo", "dialog", "body > div", "xpromo-nsfw-blocking-container")
        SiteTweak.RedditUnblurNsfw.rule.css.filter { css -> blockers.any { it in css } }.also { assertTrue(it.isNotEmpty()) }.forEach {
            assertTrue("$it must target only overlays the script marked as NSFW", it.contains("[${EngineMarks.nsfwDismissed}]"))
        }
        SiteTweak.RedditUnblurNsfw.rule.css.forEach {
            assertFalse("$it hides any backdrop", it.contains("backdrop-filter") || it.contains("blur(4px)"))
            assertFalse("$it hides by class alone", it.contains(" .prompt") || it.contains(" .overlay") || it.contains(" .viewInApp"))
        }
    }

    @Test
    fun redditNsfwUnblurOnlyDismissesOverlaysWithAnExplicitNsfwSignal() {
        val js = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditUnblurNsfw))
        assertTrue("#blocking-modal is also used for quarantine notices", js.contains("if(!qIsNsfwGate(h)&&!qIsNsfwGate(dlg))return;"))
        assertTrue("generic barrier loaders need an NSFW signal", js.contains("}else if(qIsNsfwGate(h)&&!h.querySelector("))
        assertTrue("a backdrop goes only with an NSFW gate", js.contains("if(!qDismissed)return;"))
        assertTrue("and only next to a gate dismissed in this scan", js.contains("if(!qNextToNew(d))return;"))
        assertTrue("a backdrop holding another open modal stays", js.contains("for(var k=0;k<inner.length;k++){if(qIsOpenModal(inner[k]))return;}"))
        assertTrue(js.contains("var qOpenModalSel='dialog[open],[role=\"dialog\"],[role=\"alertdialog\"],[aria-modal=\"true\"],faceplate-dialog,faceplate-modal[open],#blocking-modal,#nsfw-qr-dialog';"))
        assertTrue(js.contains("var ownGate=qIsNsfwGate(d);"))
        assertTrue("the gate check reuses the explicit barrier signals", js.contains("if(qIsNsfwBarrier(h))return true;"))
    }

    @Test
    fun anAlreadyDismissedGateNeverLetsALaterBackdropGo() {
        val js = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditUnblurNsfw))
        assertFalse("an older dismissed gate must not set qDismissed", js.contains("{qDismissed=true;return;}"))
        assertTrue(js.contains("if(h.getAttribute('${EngineMarks.nsfwDismissed}'))return;"))
        assertTrue("only a newly marked gate counts", js.contains("var qMark=function(e){e.setAttribute('${EngineMarks.nsfwDismissed}','1');qNew.push(e);qDismissed=true;};"))
        val dismissals = Regex("qDismissed=true").findAll(js).count()
        assertEquals("qDismissed is set only inside qMark", 1, dismissals)
    }

    @Test
    fun redditKeepsTheScrollLockWhileItsGateOrQuarantineNoticeIsOpen() {
        listOf(SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks, SiteTweak.forSite(LiveWebSite.Reddit).toSet()).forEach { tweaks ->
            val js = script(LiveWebSite.Reddit, tweaks)
            assertFalse("the gate and quarantine modal are no longer ignored", js.contains(":not(#blocking-modal)"))
            assertFalse(js.contains(":not(#nsfw-qr-dialog)"))
            assertTrue(js.contains("var hasOpenOverlay=isEditableFocused()||$REDDIT_OPEN_OVERLAY_JS;"))
        }
        assertTrue(REDDIT_OPEN_OVERLAY_JS.contains("#blocking-modal:not([${EngineMarks.nsfwDismissed}]),#nsfw-qr-dialog:not([${EngineMarks.nsfwDismissed}])"))
        assertTrue("a hidden placeholder modal does not count", REDDIT_OPEN_OVERLAY_JS.contains("if(e.tagName!=='REDDIT-SEARCH-LARGE'&&(!vis(e)"))
        assertTrue(REDDIT_OPEN_OVERLAY_JS.contains("var vis=function(e){if(e.getClientRects().length)return true;"))
        val nags = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditNags))
        assertTrue(nags.contains("if(!isEditableFocused()&&!$REDDIT_OPEN_OVERLAY_JS&&document.body"))
    }

    @Test
    fun appPromptNoticeCheckCrossesShadowRoots() {
        val js = script(LiveWebSite.Reddit, SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks)
        assertTrue("checks the button and its host", js.contains("var chain=[b,host];"))
        assertTrue(js.contains("if(n.closest&&n.closest(noticeSel))return true;"))
        assertTrue("walks out through each shadow root", js.contains("n=r&&r.host;"))
    }

    @Test
    fun defaultRedditTweaksLeaveTheNsfwGateAlone() {
        val defaults = SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks
        assertFalse(SiteTweak.RedditUnblurNsfw in defaults)
        val js = script(LiveWebSite.Reddit, defaults)
        assertFalse("no slot is added to open the gate", js.contains("createElement('slot')"))
        assertFalse("the engine never touches the gate's overlay", js.contains("XPROMO-NSFW-BLOCKING-CONTAINER"))
        assertFalse(js.contains("${EngineMarks.nsfwDismissed}','1'"))
        assertFalse(js.contains("${EngineMarks.nsfwContent}"))
        val nagCss = SiteTweak.RedditNags.rule.css
        assertFalse("the bare prefix also matches the NSFW gate's ids", nagCss.contains("[id^=configured-xpromo]"))
        assertTrue(nagCss.contains("[id^=configured-xpromo]:not([id*=nsfw])"))
        assertTrue(nagCss.contains("configured-xpromo-modal:not([id*=nsfw])"))
        assertFalse(nagCss.contains("configured-xpromo-modal"))
        assertTrue(nagCss.contains("shreddit-async-loader[bundlename*=\"xpromo\"]:not([bundlename*=\"nsfw\"])"))
        nagCss.forEach { assertFalse("$it targets the NSFW gate", it.contains("xpromo-nsfw-blocking-container") || it.contains("blocking-modal")) }
        assertTrue("an app prompt inside the gate hides only its button", js.contains("if(inNotice(b,host,d)){done=hide(b,false)}"))
        assertTrue(js.contains("xpromo-nsfw-blocking-container,#blocking-modal,#nsfw-qr-dialog"))
    }

    @Test
    fun nsfwBarrierCheckUsesExplicitSignalsNotCommunityNamesOrDescriptions() {
        val js = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditUnblurNsfw))
        assertFalse("the whole barrier text includes the community description", js.contains("var t=(h.textContent"))
        assertTrue(js.contains("querySelectorAll('h1,h2,h3,[slot=\"title\"],[slot=\"heading\"],button,[role=\"button\"]')"))

        val text = Regex(NSFW_BARRIER_TEXT_PATTERN, RegexOption.IGNORE_CASE)
        listOf("You must be 18+ to view this community", "Yes, I'm over 18", "This community contains mature content").forEach {
            assertTrue("$it is an NSFW barrier heading or button", text.containsMatchIn(it))
        }
        // A private community whose name and description mention "mature", "nsfw" or "18".
        listOf("r/maturegamers is a private community", "r/nsfwart has been banned", "r/over18club", "Request to join", "Learn more").forEach {
            assertFalse("$it is not an NSFW signal", text.containsMatchIn(it))
        }

        val attr = Regex(NSFW_BARRIER_ATTR_PATTERN, RegexOption.IGNORE_CASE)
        listOf("nsfw", "over18", "over-18", "is-nsfw").forEach { assertTrue(attr.containsMatchIn(it)) }
        listOf("private", "banned", "quarantined", "gated").forEach { assertFalse("$it barrier stays", attr.containsMatchIn(it)) }
        assertTrue("attribute values are read only for type-like attributes", js.contains("if(/^(type|reason|barrier-type|variant|kind)$/i.test(an))return /$NSFW_BARRIER_ATTR_PATTERN/i.test(av);"))
        assertTrue(js.contains("if(/^(source|data-testid)$/i.test(an))return /$NSFW_ID_VALUE_PATTERN/i.test(av);"))
        assertTrue("an nsfw-named flag must be on", js.contains("if(/$NSFW_BARRIER_ATTR_PATTERN/i.test(an))return /$NSFW_FLAG_ON_PATTERN/i.test(av);"))
        assertTrue(js.contains("if(/$NON_NSFW_BARRIER_PATTERN/i.test(av))return false;"))

        val flagOn = Regex(NSFW_FLAG_ON_PATTERN, RegexOption.IGNORE_CASE)
        listOf("", "true", "TRUE", "1").forEach { assertTrue("nsfw=\"$it\" is on", flagOn.containsMatchIn(it)) }
        listOf("false", "0", "no", "off").forEach { assertFalse("nsfw=\"$it\" is not an NSFW gate", flagOn.containsMatchIn(it)) }
        val idValue = Regex(NSFW_ID_VALUE_PATTERN, RegexOption.IGNORE_CASE)
        assertTrue(idValue.containsMatchIn("nsfw-blocking"))
        assertFalse("a bare 'mature' testid is not enough", idValue.containsMatchIn("mature-content-gate"))
        val keep = Regex(NON_NSFW_BARRIER_PATTERN, RegexOption.IGNORE_CASE)
        listOf("mature-content-quarantine", "quarantined", "private", "banned").forEach { assertTrue("$it keeps its explanation", keep.containsMatchIn(it)) }
        assertFalse(keep.containsMatchIn("nsfw"))
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
        // The profile's own complete rules (the collapsed NSFW media box) count once each too.
        val baseHides = LiveWebSite.Reddit.scriptProfile().baseRules.count { it.endsWith("{display:none!important}") }
        assertEquals(
            SiteTweak.RedditPromoted.rule.css.size + baseHides,
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
        assertFalse("NSFW unblur is opt-in", defaults.contains("removeAttribute('nsfw')"))

        val withUnblur =
            script(LiveWebSite.Reddit, SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks + SiteTweak.RedditUnblurNsfw)
        assertTrue(withUnblur.contains("removeAttribute('nsfw')"))
        assertTrue(withUnblur.contains("shreddit-blurred-container[${EngineMarks.unblurred}]"))
        assertFalse(withUnblur.contains("over18=1"))
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
        LiveWebSite.entries.forEach { site ->
            val js = script(site, SiteTweak.forSite(site).toSet())
            assertTrue(js.contains("isAuthPage"))
            assertTrue("$site engine builds isAuthPage from the shared list", js.contains("new RegExp(\"$AUTH_PATH_PATTERN\",'i')"))
        }
    }

    @Test
    fun sharedAuthPathPatternIsPlainAndCoversEverySitesSignInPaths() {
        // Must stay valid in both Kotlin and JavaScript, and survive being quoted into the page script.
        listOf("(?<", "(?=", "(?!", "\\", "\"").forEach {
            assertFalse("AUTH_PATH_PATTERN must not use $it", AUTH_PATH_PATTERN.contains(it))
        }
        val rx = Regex(AUTH_PATH_PATTERN, RegexOption.IGNORE_CASE)
        listOf(
            "/login",
            "/signin",
            "/sign-in",
            "/logout",
            "/signup",
            "/register",
            "/authwall",
            "/checkpoint/challenge/abc",
            "/challenge",
            "/uas/login",
            "/uas/login-submit",
            "/uas/request-password-reset",
            "/accounts/login/",
            "/accounts/onetap/",
            "/accounts/password/reset/",
            "/accounts/emailsignup/",
            "/accounts/suspended/",
            "/auth_platform/codeentry/",
            "/i/flow/login",
            "/two_step",
            "/account/access",
            "/account/login_verification",
            "/LOGIN",
            "/auth/sign_in",
            "/auth/sign_up",
            "/auth/sign_out",
            "/auth/password/new",
            "/auth/confirmation",
            "/oauth/authorize",
        ).forEach { assertTrue("$it should be an auth path", rx.containsMatchIn(it)) }
        listOf("/feed/", "/r/android/comments/abc/title/", "/loginsomething", "/jack", "/explore/", "/in/someone/", "/jobs/").forEach {
            assertFalse("$it is not an auth path", rx.containsMatchIn(it))
        }
    }

    @Test
    fun scansPauseInTheBackgroundAndCatchUpWhenShown() {
        val js = defaultScript(LiveWebSite.X)
        assertTrue(js.contains("if(document.hidden)return;"))
        assertTrue(js.contains("addEventListener('visibilitychange',function(){if(!document.hidden)schedule();}"))
    }

    @Test
    fun onlyElementsThatWereActuallyHiddenAreMarkedHidden() {
        val js = script(LiveWebSite.Reddit, SiteTweak.forSite(LiveWebSite.Reddit).toSet())
        assertTrue(js.contains("if(!canHide(e,allowPost))return false;"))
        assertTrue(js.contains("if(hide(cell,true)){"))
        assertFalse("a refused post must not be marked and skipped forever", js.contains("hide(cell,true);"))
        assertFalse(
            "a refused post keeps its length throttle, so it is re-walked only when its content changes",
            js.contains("scannedPosts.delete(p);"),
        )
        assertTrue(js.contains("&&hide(m,false)){"))
        assertTrue(js.contains("if(hide(w,false)){"))
        assertTrue(js.contains("if(done)hiddenEls.add(b);"))
    }

    @Test
    fun engineIncludesNativeBookmarkTapHookWithoutGlobalWindowLeak() {
        val xJs = defaultScript(LiveWebSite.X)
        assertTrue(xJs.contains(bridgeBookmarkMessage()))
        assertTrue(xJs.contains(bridgePtrMessage(allow = false)))
        assertTrue(xJs.contains("canHide"))
        assertTrue("the tapped post stays in the closure", xJs.contains("if(p){tapPost=p;tapAt=Date.now();}"))
        assertTrue("the bookmark script asks for it by event", xJs.contains("nListen.call(window,'${EngineMarks.tapEvent}',function(ev){"))
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains("new CustomEvent('${EngineMarks.tapEvent}', { detail: tapAsk })"))
        assertFalse("no tap timestamp is written into the page", xJs.contains("String(Date.now())"))
        assertFalse(xJs.contains("LastTappedPost"))
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
        val redditJs = script(LiveWebSite.Reddit, SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks + SiteTweak.RedditUnblurNsfw)
        assertFalse("Nested overflow-y:auto breaks root compositor scrolling", redditJs.contains("overflow-y:auto!important"))
        assertTrue(redditJs.contains("overflow-x:clip!important"))
        assertTrue("Scans must pause while typing in shadow-DOM search inputs", redditJs.contains("isEditableFocused"))
        assertTrue(redditJs.contains("var rsSearchInput=function(ev){"))
        assertTrue("Post detail pages must not be hidden by feed labels", redditJs.contains("var detailRx=rx(\"/comments/\");"))
        assertTrue("no post on a comments page is label-checked", redditJs.contains("if(postLabels&&postSel&&!immersive&&detailId!==''){"))
        assertTrue(redditJs.contains("shreddit-blurred-container[${EngineMarks.unblurred}]"))
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
        assertTrue(igJs.contains("${EngineMarks.reels}"))
        assertTrue(igJs.contains("touch-action:pan-y!important"))
        assertTrue(igJs.contains("overflow-wrap:anywhere!important"))
        assertTrue("the reel lock keeps its state in the engine's closure", igJs.contains("var igSyncReel=function(){"))
        assertTrue(igJs.contains("if(igSyncReel)igSyncReel();"))
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
        assertTrue(LiveWebSite.Instagram.allows("auth.meta.com"))
        assertFalse(LiveWebSite.Instagram.allows("meta.ai"))
    }

    @Test
    fun edgeCaseGuardsPreserveSearchAppStoreLinksSpoilersAndLoginFlows() {
        val igJs = defaultScript(LiveWebSite.Instagram)
        assertTrue("Instagram Explore must only hide /explore root, not /explore/tags or /explore/search", igJs.contains("^\\/explore\\/?$"))
        assertTrue("Instagram Reels must lock mount_0_0 root", igJs.contains("mount_0_0"))

        val xJs = defaultScript(LiveWebSite.X)
        assertTrue("X Play Store prompt selector must be scoped to #layers so tweet links stay visible", xJs.contains("#layers div:has(> a[href*=\\\"play.google.com\\\"]):not(:has(input,form))"))
        assertTrue("X 2FA and unlock paths must be guarded as auth pages", xJs.contains("account/login_verification"))

        val redditJs = defaultScript(LiveWebSite.Reddit)
        assertFalse("RedditNags must not hide arbitrary Play Store links in posts/comments", redditJs.contains("a[href*=\\\"play.google.com\\\"]{"))
        assertTrue("RedditNags still hides Reddit's own Play Store app links", redditJs.contains("play.google.com/store/apps/details?id=com.reddit"))
        assertFalse("Reddit's auth modal keeps its Log In / Sign Up toggles", redditJs.contains("auth-flow-link"))
        assertTrue(
            "Reddit's header Log In button is hidden only outside dialogs",
            redditJs.contains("html:not([${EngineMarks.auth}=\\\"1\\\"]) #login-button:not(dialog *):not([role=\\\"dialog\\\"] *)"),
        )
        val unblurJs = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditUnblurNsfw))
        assertTrue("NSFW unblur must not unblur movie/game spoiler containers", unblurJs.contains("shreddit-blurred-container:not([reason=\"spoiler\"])"))
    }

    @Test
    fun redditNagsLeavesNoticesBarriersAndAuthModalsAlone() {
        val js = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditNags))
        assertFalse("info banners (removed, locked, archived) stay", js.contains("faceplate-banner{display"))
        assertTrue(js.contains("faceplate-banner:has(a[href*=\\\"app.link\\\"]){display"))
        assertTrue(js.contains("faceplate-banner:has(a[href*=\\\"onelink\\\"]){display"))
        assertTrue(js.contains("faceplate-banner:has(a[href^=\\\"intent:\\\"]){display"))
        assertFalse("private, banned and quarantined barriers stay", js.contains("community-barrier{display"))
        assertFalse(SiteTweak.RedditNags.rule.css.contains("community-barrier"))
        assertTrue("an app prompt inside a barrier hides only the button", js.contains("var noticeSel='community-barrier,"))
        assertTrue(js.contains("if(inNotice(b,host,d)){done=hide(b,false)}"))
        SiteTweak.RedditNags.rule.css.filter { "login" in it || "signup" in it || "register" in it }.forEach {
            assertTrue("$it must skip dialogs", it.contains(":not([role=\"dialog\"] *)"))
            assertTrue("$it must skip sign-in pages", it.startsWith("html:not([${EngineMarks.auth}=\"1\"])"))
        }
        assertTrue(Regex(AUTH_PATH_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn("/register"))
    }

    @Test
    fun instagramAppPromptsNeverHideSignInOrCodeEntryDialogs() {
        val js = script(LiveWebSite.Instagram, setOf(SiteTweak.InstagramAppPrompts))
        SiteTweak.InstagramAppPrompts.rule.css.filter { it.contains("dialog") }.forEach {
            assertTrue("$it must skip dialogs with fields", it.contains(":not(:has(input,form,textarea))"))
            assertTrue("$it must skip sign-in pages", it.startsWith("html:not([${EngineMarks.auth}=\"1\"])"))
        }
        assertFalse("no unguarded dialog rule", js.contains(" div[role=\\\"dialog\\\"]:has("))
        assertTrue(js.contains("if(!isAuthPage())document.querySelectorAll('div[role=dialog]')"))
        assertTrue(js.contains("if(d.querySelector('input,form,textarea'))return;"))
    }

    @Test
    fun linkedInTweaksCoverJobsSuggestionsAndPremiumAndStayOnLinkedInsOwnLinks() {
        val js = script(LiveWebSite.LinkedIn, SiteTweak.forSite(LiveWebSite.LinkedIn).toSet())
        assertTrue(js.contains("Jobs recommended for you"))
        assertTrue(js.contains("aside:has(a[href^=\\\"/jobs/collections/\\\"])"))
        assertTrue(js.contains("People you may know"))
        assertTrue(js.contains("More suggestions for you"))
        assertTrue(js.contains("Promoted by.*"))
        assertTrue(js.contains("Try Premium.*"))
        assertTrue(js.contains("a[href^=\\\"/games/\\\"]"))
        assertTrue(js.contains("a[href^=\\\"https://www.linkedin.com/games/\\\"]"))
        assertTrue(js.contains("a[href^=\\\"/advice/\\\"]"))
        assertFalse("external article cards with /games/ in the URL stay", js.contains("a[href*=\\\"/games/\\\"]"))
        assertFalse("external article cards with /advice/ in the URL stay", js.contains("a[href*=\\\"/advice/\\\"]"))

        val premium = SiteTweak.LinkedInPremium.rule
        assertTrue("Premium upsells are buttons, not whole posts", premium.postLabels.isEmpty())
        assertTrue("Premium labels never hide a dialog or bar", premium.promptLabels.isEmpty())
        assertTrue("no card or banner selectors", premium.css.isEmpty())
        val buttons = Regex(labelPattern(premium.buttonLabels), RegexOption.IGNORE_CASE)
        assertTrue(buttons.containsMatchIn("Try Premium for ₹0"))
        assertTrue(buttons.containsMatchIn("Try Premium for $0"))
        assertFalse("win-back notices stay", buttons.containsMatchIn("Retry Premium for $0"))
        assertFalse(buttons.containsMatchIn("Reactivate Premium"))
        assertFalse(buttons.containsMatchIn("Why I stopped paying for Premium"))

        val promoted = Regex(labelPattern(SiteTweak.LinkedInPromoted.rule.postLabels), RegexOption.IGNORE_CASE)
        assertTrue(promoted.containsMatchIn("Promoted by Acme Corp"))
        assertTrue(promoted.containsMatchIn("Promoted"))
        assertFalse(promoted.containsMatchIn("I was promoted by my manager today"))
    }

    @Test
    fun linkedInPremiumHidesOnlyTheButtonAndNeverNextToAnAccountNotice() {
        val js = script(LiveWebSite.LinkedIn, setOf(SiteTweak.LinkedInPremium))
        assertTrue(js.contains("if(buttons&&!auth)scanButtons();"))
        assertTrue("only the button itself is hidden", js.contains("if(hide(b,false))hiddenEls.add(b);"))
        assertTrue(js.contains("if(accountNotice&&ctx&&accountNotice.test(clean(ctx.textContent)))return;"))
        assertTrue("the notice check looks at the whole card, not just the parent", js.contains("var ctx=noticeCtx(b);"))
        assertFalse(js.contains("||b.parentElement;"))
        assertTrue(js.contains("var cardSel='section,article,aside,li,[role=article],[class*=card],[class*=paywall],[class*=upsell],[class*=banner]';"))
        assertTrue("climbs at most four levels", js.contains("for(var i=0;e&&i<4;i++){"))
        assertTrue("never climbs into the feed or main", js.contains("if(role==='main'||role==='feed'||e.id==='main-content')break;"))
        assertTrue(js.contains("if(/^(MAIN|BODY|HTML)$/.test(e.tagName))break;"))
        val appPrompts = SiteTweak.forSite(LiveWebSite.LinkedIn).flatMap { it.rule.promptLabels }
        assertFalse("Premium labels never hide a dialog or bar", Regex(labelPattern(appPrompts), RegexOption.IGNORE_CASE).containsMatchIn("Try Premium for $0"))

        val notice = Regex(ACCOUNT_NOTICE_PATTERN, RegexOption.IGNORE_CASE)
        listOf(
            "You've reached the monthly limit for profile searches",
            "Your payment didn't go through",
            "Update your billing details",
            "Your subscription ends soon",
            "Download your invoice",
            "Your card was declined",
            "You have 0 InMail credits left",
        ).forEach { assertTrue("$it is an account notice", notice.containsMatchIn(it)) }
        listOf("Try Premium for $0", "See who viewed your profile").forEach {
            assertFalse("$it is a plain upsell", notice.containsMatchIn(it))
        }
        assertTrue("the notice words are plain syntax", ACCOUNT_NOTICE_PATTERN.none { it == '\\' || it == '"' || it == '\'' })
    }

    @Test
    fun buttonLabelsAloneStillProduceAScript() {
        assertNotNull(buildSiteScript(LiveWebSite.LinkedIn, setOf(SiteTweak.LinkedInPremium)))
    }

    // Live device fixes (1.0.13): Reddit NSFW media box, Reddit scroll lock, LinkedIn mobile feed, pull-to-refresh.

    @Test
    fun redditNsfwNoteBridgeMirrorsTheBookmarkBridge() {
        assertTrue(bridgeRedditNsfwNoteMessage().startsWith(PageMarkers.consolePrefix))
        assertTrue(bridgeRedditNsfwNoteMessage("abc").startsWith(PageMarkers.consolePrefix + ":abc:"))
        assertNotEquals(bridgeRedditNsfwNoteMessage("abc"), bridgeBookmarkMessage("abc"))
        listOf(SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks, SiteTweak.forSite(LiveWebSite.Reddit).toSet()).forEach { tweaks ->
            val js = requireNotNull(buildSiteScript(LiveWebSite.Reddit, tweaks, bridgeToken = "abc"))
            assertTrue("the note is posted with or without the NSFW tweak", js.contains("nPost(\"${bridgeRedditNsfwNoteMessage("abc")}\");"))
            assertFalse(js.contains("__BRIDGE_NSFW_NOTE__"))
            assertTrue("once per URL", js.contains("if(qCollapsed&&keep.nsfwNoteUrl!==location.href){"))
            assertTrue("only on Reddit's own pages", js.contains("if(hasShadowDom){"))
        }
        LiveWebSite.entries.filter { it != LiveWebSite.Reddit }.forEach { site ->
            val js = script(site, SiteTweak.forSite(site).toSet())
            assertFalse("$site never posts the Reddit note", js.contains("nsfw_note"))
            assertFalse(js.contains("__BRIDGE_"))
        }
    }

    @Test
    fun redditNeverHidesTheNsfwGateHostOrTheMediaItHolds() {
        val unblur = SiteTweak.RedditUnblurNsfw.rule
        assertFalse("the host's default slot holds the media", unblur.css.contains("xpromo-nsfw-blocking-container[${EngineMarks.nsfwDismissed}]"))
        assertTrue(unblur.css.contains("xpromo-nsfw-blocking-container [${EngineMarks.nsfwDismissed}]"))
        val js = script(LiveWebSite.Reddit, SiteTweak.forSite(LiveWebSite.Reddit).toSet())
        assertFalse("the gate host is never marked", js.contains("if(!h.querySelector('shreddit-post,[slot=\"text-body\"]'))qMark(h);"))
        assertTrue("generic NSFW wrappers around media stay", js.contains("&&!qWrapsMedia(h)){"))
        assertTrue(js.contains("var qWrapsMedia=function(h){"))
        assertTrue("a shadow wrapper around the default slot stays", js.contains("if(o.querySelector('slot:not([name])'))return;"))
        assertTrue("the shadow prompt still goes", js.contains("h.shadowRoot.querySelectorAll('.prompt,.overlay,.bg-scrim,.viewInApp')"))
    }

    @Test
    fun anEmptyNsfwMediaBoxCollapsesOnlyThePostsMediaContainer() {
        val rules = LiveWebSite.Reddit.scriptProfile().baseRules
        assertTrue(rules.contains("[slot=\"post-media-container\"][$REDDIT_NSFW_EMPTY_ATTR]{display:none!important}"))
        listOf(SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks, SiteTweak.forSite(LiveWebSite.Reddit).toSet()).forEach { tweaks ->
            val js = script(LiveWebSite.Reddit, tweaks)
            assertTrue(js.contains("var box=c.closest('[slot=\"post-media-container\"]');"))
            assertTrue("a player that arrives later is shown again", js.contains("box.removeAttribute('$REDDIT_NSFW_EMPTY_ATTR')"))
            assertTrue("a still-loading player is not collapsed", js.contains("if(qNow-first<$REDDIT_NSFW_EMPTY_GRACE_MS){qWait=true;return;}"))
            assertTrue("media is looked for in the embed's shadow root too", js.contains("if(e.shadowRoot&&d<3&&qDeepMedia(e.shadowRoot,d+1,collapsed))return true;"))
            assertTrue("named-slot children (the app button) are not media", js.contains("s.getAttribute('slot'))continue;"))
        }
    }

    @Test
    fun redditNeverRendersTheEmbedItWithholds() {
        listOf(SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks, SiteTweak.forSite(LiveWebSite.Reddit).toSet()).forEach { tweaks ->
            val js = script(LiveWebSite.Reddit, tweaks)
            listOf(
                "createElement('iframe",
                "createElement(\"iframe",
                "createElement('video",
                "innerHTML",
                "outerHTML",
                "insertAdjacentHTML",
                "getAttribute('html')",
                "srcdoc",
                ".click(",
                "dispatchEvent(",
                "reddit.com/settings",
            ).forEach { assertFalse("Reddit script contains $it", js.contains(it)) }
        }
    }

    @Test
    fun redditFreesTheScrollLockOnlyWhileNoGenuineModalIsOpen() {
        val rules = LiveWebSite.Reddit.scriptProfile().baseRules
        assertTrue(rules.contains("html[$REDDIT_SCROLL_FREE_ATTR=\"1\"] body.rpl-scroll-lock{overflow:visible!important}"))
        listOf(SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks, SiteTweak.forSite(LiveWebSite.Reddit).toSet()).forEach { tweaks ->
            val js = script(LiveWebSite.Reddit, tweaks)
            assertTrue(js.contains("if(hasOpenOverlay){if(qFree)qRoot.removeAttribute('$REDDIT_SCROLL_FREE_ATTR');}"))
            assertTrue(js.contains("else if(!qFree)qRoot.setAttribute('$REDDIT_SCROLL_FREE_ATTR','1');"))
            assertFalse("Nested overflow-y:auto breaks root compositor scrolling", js.contains("overflow-y:auto!important"))
        }
        assertTrue("app nags are never a genuine modal", REDDIT_OPEN_OVERLAY_JS.contains("if(hiddenEls.has(e)||nag(e))continue;"))
        assertTrue("the check crosses shadow roots", REDDIT_OPEN_OVERLAY_JS.contains("var r=n.getRootNode&&n.getRootNode();n=r&&r.host;"))
        assertTrue(REDDIT_OPEN_OVERLAY_JS.contains(REDDIT_APP_LINK_SELECTOR))
        listOf("#xpromo-bottom-sheet", "[id=\"xpromo-bottom-sheet\"]", "xpromo-nsfw-blocking-container", "[${EngineMarks.nsfwDismissed}]").forEach {
            assertTrue("$it is not a genuine modal", REDDIT_NAG_SHEET_SELECTOR.split(',').contains(it))
        }
        assertFalse("login, report and quarantine dialogs stay genuine", REDDIT_NAG_SHEET_SELECTOR.contains("dialog"))
        assertFalse(REDDIT_NAG_SHEET_SELECTOR.contains("blocking-modal"))
    }

    @Test
    fun redditNagsHideTheGenericAppSheetDiv() {
        val css = SiteTweak.RedditNags.rule.css
        assertTrue(css.contains("div#xpromo-bottom-sheet"))
        assertTrue(css.contains("[id=\"xpromo-bottom-sheet\"]"))
        assertTrue(css.contains("$REDDIT_APP_SHEET_GUARD:has(a[href*=\"onelink.me\"])"))
        assertTrue(css.contains("$REDDIT_APP_SHEET_GUARD:has(a[href*=\"app.link\"])"))
        assertFalse("an unguarded app-link sheet rule is gone", css.contains(".rpl-bottom-sheet:has(a[href*=\"onelink.me\"])"))
        assertFalse("other bottom sheets (share, sort) stay", css.contains(".rpl-bottom-sheet"))
        assertTrue(css.contains("xpromo-bottom-sheet"))
        val js = script(LiveWebSite.Reddit, setOf(SiteTweak.RedditNags))
        assertTrue(js.contains("div#xpromo-bottom-sheet{display:none!important}"))
    }

    @Test
    fun linkedInFindsPostsInTheSignedInMobileFeed() {
        val profile = LiveWebSite.LinkedIn.scriptProfile()
        val posts = profile.postSelector.split(',')
        assertTrue(posts.contains(LINKEDIN_MOBILE_POST_SELECTOR))
        assertEquals("div[role=\"listitem\"][componentkey^=\"update-card\"]", LINKEDIN_MOBILE_POST_SELECTOR)
        listOf("div.feed-shared-update-v2", "div[data-id*=\"activity\"]", "div[data-urn*=\"activity\"]", "article").forEach {
            assertTrue("desktop and guest selector $it stays", posts.contains(it))
        }
        assertTrue("the post's own words are never a label", profile.labelExcludeSelector.split(',').contains("[data-testid=\"expandable-text-box\"]"))
        assertTrue(profile.labelExcludeSelector.contains(".update-components-text"))
        assertTrue(profile.moduleSelector.split(',').contains(LINKEDIN_MOBILE_POST_SELECTOR))

        val js = defaultScript(LiveWebSite.LinkedIn)
        assertTrue(js.contains("div[role=\\\"listitem\\\"][componentkey^=\\\"update-card\\\"]"))
        assertTrue(js.contains("[data-testid=\\\"expandable-text-box\\\"]"))

        val promoted = Regex(labelPattern(SiteTweak.LinkedInPromoted.rule.postLabels), RegexOption.IGNORE_CASE)
        listOf("Promoted", "Promoted by Acme", " Promoted ").forEach { assertTrue("$it is a header label", promoted.containsMatchIn(it)) }
        assertFalse("the post's hidden heading is not a label", promoted.containsMatchIn("Feed post Acme"))
        val suggested = Regex(labelPattern(SiteTweak.LinkedInSuggested.rule.postLabels), RegexOption.IGNORE_CASE)
        assertTrue(suggested.containsMatchIn("Suggested"))
    }

    @Test
    fun pullToRefreshFindsTheRealScrollerWithNoDepthCap() {
        LiveWebSite.entries.forEach { site ->
            val js = script(site, SiteWebSettings.defaultsFor(site).tweaks + SiteTweak.forSite(site).take(1))
            assertFalse("$site: no depth cap", js.contains("depth<8"))
            assertTrue(js.contains("var path=ev.composedPath?ev.composedPath():[];"))
            assertTrue("walks out through shadow roots without composedPath", js.contains("n=n.assignedSlot||n.parentNode||n.host;"))
            assertTrue(js.contains("var oy=getComputedStyle(n).overflowY;"))
            assertTrue(js.contains("return oy==='auto'||oy==='scroll'||oy==='overlay';"))
            assertTrue("every scroller under the touch must be at top", js.contains("if((ptrScrollers[i].scrollTop||0)>1)return false;"))
            assertTrue(js.contains("if(docTop()>1)return false;"))
            assertTrue("element scrolls are heard in the capture phase", js.contains("document.addEventListener('scroll',function(ev){"))
            assertTrue("leaving the top turns refresh off at once", js.contains("ptrRemember(t);postPtr(false);return;}"))
            assertTrue("returning to top is checked once a frame", js.contains("ptrRaf=nFrame(function(){"))
            assertTrue("only posts on change", js.contains("if(lastPtrState===allow)return;"))
            assertTrue(js.contains(bridgePtrMessage(allow = false)))
            assertTrue(js.contains(bridgePtrMessage(allow = true)))
        }
        val tokenJs = requireNotNull(buildSiteScript(LiveWebSite.LinkedIn, SiteWebSettings.defaultsFor(LiveWebSite.LinkedIn).tweaks, bridgeToken = "t1"))
        assertTrue(
            tokenJs.contains("\"${bridgePtrMessage(allow = true, bridgeToken = "t1")}\":\"${bridgePtrMessage(allow = false, bridgeToken = "t1")}\""),
        )
    }

    @Test
    fun anNsfwBoxCountsAsGatedOnlyWhileRedditsPromptIsUpAndOnScreen() {
        listOf(SiteWebSettings.defaultsFor(LiveWebSite.Reddit).tweaks, SiteTweak.forSite(LiveWebSite.Reddit).toSet()).forEach { tweaks ->
            val js = script(LiveWebSite.Reddit, tweaks)
            assertTrue("the gate is read from the prompt's hidden class", js.contains("if(!p[i].classList.contains('hidden'))return true;"))
            assertTrue(js.contains("var p=c.shadowRoot?c.shadowRoot.querySelectorAll('.prompt'):[];"))
            assertTrue("an ungated container is never collapsed", js.contains("if(!gated||qDeepMedia(c,0,was)){"))
            assertTrue("the grace period starts on screen", js.contains("if(!qInView(box)){qOff=true;return;}"))
            assertTrue("an off-screen box is checked again on scroll", js.contains("nListen.call(window,'scroll',function(){schedule();},{capture:true,passive:true});"))
            assertTrue("the note goes only when a box was collapsed", js.contains("if(qCollapsed&&keep.nsfwNoteUrl!==location.href){"))
            assertFalse(js.contains("qGated&&"))
            assertTrue("a 1x1 pixel or hidden poster is not media", js.contains("var qSized=function(e){var r=e.getBoundingClientRect();return r.width>2&&r.height>2;};"))
            assertTrue(js.contains("if(t==='IMG')return !collapsed&&qSized(e)&&!(e.complete&&(e.naturalWidth||0)<=2);"))
            assertTrue("a post with no media container collapses the wrapper", js.contains("var w=c.closest('div[id\$=\"-aspect-ratio\"],shreddit-aspect-ratio')||c.parentElement;"))
            assertTrue("never a wrapper that holds the post or its text", js.contains("if(w.querySelector('shreddit-post,[slot=\"text-body\"],[slot=\"title\"],shreddit-post-text-body'))return null;"))
            assertTrue("a box that healed with a player stays", js.contains("if(!box||qHealed.has(c))return;"))
        }
        val rules = LiveWebSite.Reddit.scriptProfile().baseRules
        assertTrue(rules.contains("[$REDDIT_NSFW_EMPTY_ATTR]:not(xpromo-nsfw-blocking-container){display:none!important}"))
        assertFalse("the gate host is never collapsed", rules.any { it.startsWith("xpromo-nsfw-blocking-container[$REDDIT_NSFW_EMPTY_ATTR]") })
    }

    @Test
    fun redditCountsAlertDialogsAriaModalsAndOtherSheetsAsGenuine() {
        listOf(
            "[role=\"alertdialog\"]:not([${EngineMarks.nsfwDismissed}])",
            "[aria-modal=\"true\"]:not([${EngineMarks.nsfwDismissed}])",
            "faceplate-dialog:not([${EngineMarks.nsfwDismissed}])",
            ".rpl-bottom-sheet:not([${EngineMarks.nsfwDismissed}])",
        ).forEach { assertTrue("$it is a genuine modal", REDDIT_OPEN_OVERLAY_JS.contains(it)) }
        assertTrue(
            "a sheet that asks for input is never a nag",
            REDDIT_OPEN_OVERLAY_JS.contains("b.querySelector('$REDDIT_APP_LINK_SELECTOR')&&!b.querySelector('$REDDIT_SHEET_FORM_SELECTOR')"),
        )
        listOf("input", "form", "faceplate-text-input", "[type=\"password\"]").forEach {
            assertTrue(REDDIT_SHEET_FORM_SELECTOR.split(',').contains(it))
        }
        listOf("input", "form", "faceplate-text-input").forEach {
            assertTrue("hidden sheets never hold $it", REDDIT_APP_SHEET_GUARD.contains(":not(:has($it))"))
        }
        assertTrue("display:contents sheets are judged by their children", REDDIT_OPEN_OVERLAY_JS.contains("getComputedStyle(e).display!=='contents'"))
    }

    @Test
    fun pullToRefreshRemembersAFeedScrollerOutsideTheTouch() {
        LiveWebSite.entries.forEach { site ->
            val js = script(site, SiteWebSettings.defaultsFor(site).tweaks + SiteTweak.forSite(site).take(1))
            assertTrue(js.contains("var ptrScrollers=[],ptrKnown=[],ptrBase=null,ptrRaf=0;"))
            assertTrue("a scroller that leaves the top is remembered", js.contains("if(top>1){if(!isDocScroller(t)&&t&&t.nodeType===1)ptrRemember(t);postPtr(false);return;}"))
            assertTrue(js.contains("if(isVScroller(n)){scrollers.push(n);if((n.scrollTop||0)>1)ptrRemember(n);}"))
            assertTrue("remembered scrollers must be at top too", js.contains("for(var j=0;j<ptrKnown.length;j++){if((ptrKnown[j].scrollTop||0)>1)return false;}"))
            assertTrue("gone or off-screen scrollers are dropped", js.contains("ptrKnown=ptrKnown.filter(ptrIsMain);"))
            assertTrue(js.contains("if(!n.isConnected)return false;"))
            assertTrue("only a feed-sized scroller is remembered", js.contains("return r.height>=vh*0.4&&r.bottom>0&&r.top<vh;"))
            assertTrue("a touch outside the feed looks up the scroller mid-screen", js.contains("var mid=document.elementFromPoint((window.innerWidth||0)/2,(window.innerHeight||0)/2);"))
            assertTrue(js.contains("if(mid.nodeType===1&&isVScroller(mid))ptrRemember(mid);"))
            assertTrue("it climbs out of shadow roots", js.contains("mid=mid.nodeType===11?mid.host:(mid.assignedSlot||mid.parentNode);"))
        }
    }
}
