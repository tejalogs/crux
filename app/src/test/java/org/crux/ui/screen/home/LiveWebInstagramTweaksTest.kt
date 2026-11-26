package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Instagram's Suggested carousel, Explore flag and Meta AI rules. */
class LiveWebInstagramTweaksTest {
    private fun script(tweaks: Set<SiteTweak>): String = requireNotNull(buildSiteScript(LiveWebSite.Instagram, tweaks))

    // Suggested for you

    @Test
    fun suggestedCarouselBetweenPostsIsFoundByItsTitleOrSeeAllLink() {
        val js = script(setOf(SiteTweak.InstagramSuggested))
        assertTrue(js.contains("main.querySelectorAll('a[href^=\"/explore/people\"]')"))
        val title = Regex(labelPattern(IG_SUGGESTED_BLOCK_LABELS), RegexOption.IGNORE_CASE)
        listOf("Suggested for you", "Suggested reels", "Suggested Reels").forEach { assertTrue(it, title.containsMatchIn(it)) }
        assertFalse(title.containsMatchIn("Suggested for you based on your activity today"))
        assertFalse("off by default, so the default script has none of it", script(SiteWebSettings.defaultsFor(LiveWebSite.Instagram).tweaks).contains("igSuggestSeen"))
    }

    @Test
    fun suggestedCarouselClimbsOnlyToTheWrapperBesideTheFeedsPosts() {
        val js = script(setOf(SiteTweak.InstagramSuggested))
        assertTrue("never from inside a post, dialog or nav", js.contains("el.closest('article,[role=\"dialog\"],nav,header,form')"))
        assertTrue(
            "climbs while the parent holds no post",
            js.contains("while(box.parentElement&&box.parentElement!==main&&!box.parentElement.querySelector('article'))box=box.parentElement;"),
        )
        assertTrue("only when the parent holds the feed's posts", js.contains("if(!p||hiddenEls.has(box)||!p.querySelector('article'))return;"))
        assertTrue(
            "never a wrapper with a post, field, dialog or main",
            js.contains("if(box.querySelector('article,input,textarea,form,main,nav,[role=\"dialog\"],[role=\"main\"]'))return;"),
        )
        assertTrue("never taller than a screen and a half", js.contains("if(vh&&(box.offsetHeight||0)>vh*1.5)return;"))
        assertTrue("never on the people page, a sign-in page or a Reel", js.contains("if(isImmersive()||isAuthPage()||/^\\/explore\\/people/i.test(location.pathname||''))return;"))
        assertTrue("checked text is cached", js.contains("var seen=keep.igSuggestSeen||(keep.igSuggestSeen=new WeakSet());"))
    }

    // Explore

    @Test
    fun exploreFlagFollowsNavigationAtOnce() {
        val js = script(setOf(SiteTweak.InstagramExplore))
        assertTrue("registered by the tweak", js.contains("if(!keep.igRouteHook)keep.igRouteHook=function(){"))
        assertTrue(
            "called the moment the path changes",
            Regex("igLastPath=location\\.pathname;\\s*igSyncReel\\(\\);\\s*if\\(keep\\.igRouteHook\\)keep\\.igRouteHook\\(\\);").containsMatchIn(js),
        )
        assertTrue("a tap is re-read after Instagram pushes the new path", js.contains("nLater(igAfterTap,0);nLater(igAfterTap,250);"))
        assertTrue(js.contains("window.addEventListener('popstate',igClampReel,{passive:true});"))
        assertTrue("the flag is removed, never set to an off value", js.contains("r.removeAttribute('${EngineMarks.explore}')"))
    }

    @Test
    fun exploreHidesEveryPostAndReelLinkShapeOnlyOnExploreItself() {
        val css = SiteTweak.InstagramExplore.rule.css
        assertEquals(IG_EXPLORE_GRID_CSS, css)
        css.forEach { assertTrue("$it is scoped to the flag", it.startsWith("html[${EngineMarks.explore}=\"1\"] ")) }
        listOf("a[href^=\"/p/\"]", "a[href^=\"/reel/\"]", "main a[href*=\"/p/\"]", "main a[href*=\"/reel/\"]").forEach { shape ->
            assertTrue("missing $shape", css.any { it.endsWith(" $shape") })
        }
        val js = script(setOf(SiteTweak.InstagramExplore))
        assertTrue("only /explore itself, never tags or search", js.contains("/^\\/explore\\/?$/i.test(location.pathname||'')"))
        val off = script(setOf(SiteTweak.InstagramAds))
        assertFalse("the flag's name never reaches a page whose tweak is off", off.contains(EngineMarks.explore))
    }

    // Meta AI

    @Test
    fun metaAiNeverHidesAFieldFormOrSearch() {
        val css = SiteTweak.InstagramAi.rule.css
        css.filter { it.contains("aria-label*=\"Meta AI\"") }.forEach {
            assertTrue("$it must skip fields", it.endsWith(IG_NOT_A_FIELD))
        }
        assertFalse("no bare aria-label rule", css.contains("[aria-label*=\"Meta AI\"]"))
        listOf(
            ":not(input)",
            ":not(textarea)",
            ":not(form)",
            ":not([role=\"search\"])",
            ":not([role=\"searchbox\"])",
            ":not([contenteditable])",
            ":not(:has(input,textarea,form,",
        ).forEach { assertTrue("missing $it", IG_NOT_A_FIELD.contains(it)) }
        assertFalse("one selector, not a list", IG_NOT_A_FIELD.contains("),"))
        val js = script(setOf(SiteTweak.InstagramAi))
        assertTrue(js.contains("[aria-label*=\\\"Meta AI\\\"]:not(input):not(textarea)"))
    }

    @Test
    fun metaAiPromptBarsNeverClimbPastAField() {
        val js = script(setOf(SiteTweak.InstagramAi))
        // bar() stops at any wrapper holding more than one clickable, and a field counts as one.
        assertTrue(js.contains("var clickable='a,button,input,select,textarea,[role=button],[role=search],[role=navigation]';"))
        assertTrue(js.contains("if(d.querySelector('input,form,textarea,"))
    }
}
