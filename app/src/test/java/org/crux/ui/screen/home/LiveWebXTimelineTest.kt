package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** X's timeline tweaks: ads by label only, module rows, Discover more, the For you tab. */
class LiveWebXTimelineTest {
    private fun script(tweaks: Set<SiteTweak>): String = requireNotNull(buildSiteScript(LiveWebSite.X, tweaks))

    private val all = script(SiteTweak.forSite(LiveWebSite.X).toSet())

    private fun path(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    // Promoted posts

    @Test
    fun placementTrackingIsNeverAnAdMarker() {
        SiteTweak.forSite(LiveWebSite.X).forEach { tweak ->
            tweak.rule.css.forEach { assertFalse("$tweak: $it", it.contains("placementTracking")) }
        }
        assertFalse(all.contains("placementTracking"))
        assertEquals(listOf("Ad", "Promoted"), SiteTweak.XPromoted.rule.postLabels)
    }

    @Test
    fun aVideoPreRollBadgeNeverHidesTheOrganicPostAroundIt() {
        val exclude = LiveWebSite.X.scriptProfile().labelExcludeSelector
        assertEquals(X_LABEL_EXCLUDE_SELECTOR, exclude)
        listOf(
            "[data-testid=\"tweetText\"]",
            "[data-testid=\"User-Name\"] a",
            "[data-testid=\"videoPlayer\"]",
            "[data-testid=\"videoComponent\"]",
            "[data-testid=\"tweetPhoto\"]",
            "[data-testid=\"card.wrapper\"]",
        ).forEach { assertTrue("$it is never read as a label", exclude.split(",").contains(it)) }
        // X may put the "Ad" label in the author line: only the name, handle and time links there are skipped.
        assertFalse("the author line itself is still read", exclude.split(",").contains("[data-testid=\"User-Name\"]"))
        assertTrue(all.contains("[data-testid=\\\"videoPlayer\\\"],[data-testid=\\\"videoComponent\\\"]"))
        assertTrue("a label is read only outside the excluded parts", all.contains("!(excludeSel&&el.closest(excludeSel))"))
    }

    @Test
    fun theOpenedPostIsNeverHiddenButItsRepliesAreStillChecked() {
        val detail = path(X_POST_DETAIL_PATTERN)
        assertEquals("1234", detail.find("/jack/status/1234")?.groupValues?.get(1))
        assertEquals("1234", detail.find("/jack/status/1234/photo/1")?.groupValues?.get(1))
        assertEquals("99", detail.find("/i/web/status/99")?.groupValues?.get(1))
        listOf(
            "/home",
            "/jack",
            "/jack/followers",
            "/explore",
            "/search",
            "/i/bookmarks",
            "/jack/status/1234/retweets",
            "/jack/status/1234/likes",
            "/jack/status/1234/quotes",
        ).forEach { assertNull("$it is not a post's page", detail.find(it)) }
        assertEquals(X_POST_DETAIL_PATTERN, LiveWebSite.X.scriptProfile().postDetailPattern)
        assertTrue(all.contains("var postDetailId=function(){var m=detailRx&&detailRx.exec(location.pathname||'');return m?(m[1]||''):null};"))
        assertTrue(all.contains("if(postLabels&&postSel&&!immersive&&detailId!==''){"))
        assertTrue("only the opened post is skipped", all.contains("if(detailId&&isOpenedPost(p,detailId))return;"))
        assertTrue("the opened post links to its own id", all.contains("a[href$=\"/'+id+'\"],a[href*=\"/'+id+'/\"],a[href*=\"/'+id+'?\"]"))
        assertTrue("X renders the opened post with tabindex -1", all.contains("p.getAttribute('tabindex')==='-1'"))
    }

    @Test
    fun anAdThePersonOpenedIsNotHiddenByItsImpressionPixel() {
        assertEquals(listOf(X_IMPRESSION_PIXEL_CELL), SiteTweak.XPromoted.rule.css)
        assertTrue(X_IMPRESSION_PIXEL_CELL.contains(":has([data-testid=\"top-impression-pixel\"])"))
        assertTrue("the opened post's cell is left", X_IMPRESSION_PIXEL_CELL.endsWith(":not(:has(article[tabindex=\"-1\"]))"))
        val js = requireNotNull(buildSiteScript(LiveWebSite.X, setOf(SiteTweak.XPromoted)))
        assertTrue(
            "virtualized X cells and their inner wrapper collapse to 1px with overflow-anchor:none to prevent VirtualScroller scroll jumps",
            js.contains("overflow-anchor:none!important") &&
                js.contains("> *{height:1px!important") &&
                js.contains("> * > *{display:none!important}"),
        )
    }

    @Test
    fun instagramNeverLabelChecksThePostOrReelThePersonOpened() {
        val ig = LiveWebSite.Instagram.scriptProfile().postDetailPattern
        assertEquals(IG_POST_DETAIL_PATTERN, ig)
        listOf("/p/abc/", "/p/abc/comments/", "/p/abc", "/jack/reel/abc/", "/reel/abc/", "/jack/p/abc/").forEach {
            val match = path(ig).find(it)
            assertTrue("$it is an opened post", match != null)
            assertEquals("$it: no group, so no post there is label-checked", 1, match!!.groupValues.size)
        }
        listOf("/", "/explore/", "/jack/", "/reels/", "/direct/inbox/", "/stories/jack/1/").forEach {
            assertNull("$it is not one post's page", path(ig).find(it))
        }
        val js = requireNotNull(buildSiteScript(LiveWebSite.Instagram, SiteTweak.forSite(LiveWebSite.Instagram).toSet()))
        assertTrue(js.contains("var detailRx=rx(\"$IG_POST_DETAIL_PATTERN\");"))
    }

    @Test
    fun redditCommentsPagesStayUnscannedAndOtherSitesHaveNoDetailPage() {
        val reddit = LiveWebSite.Reddit.scriptProfile().postDetailPattern
        val match = path(reddit).find("/r/android/comments/abc/title/")
        assertTrue(match != null)
        assertEquals("no group: no post on a comments page is label-checked", 1, match!!.groupValues.size)
        listOf(LiveWebSite.LinkedIn, LiveWebSite.Mastodon).forEach {
            assertEquals("$it", "", it.scriptProfile().postDetailPattern)
            assertTrue(requireNotNull(buildSiteScript(it, SiteTweak.forSite(it).toSet())).contains("var detailRx=rx(\"\");"))
        }
    }

    // Who to follow

    @Test
    fun whoToFollowRowsAreWalkedOnlyOnHomeAndPostPages() {
        val pages = path(X_MODULE_WALK_PATTERN)
        listOf("/home", "/home/", "/jack/status/1", "/jack/status/1/", "/jack/status/1/photo/1", "/i/web/status/1").forEach {
            assertTrue("$it walks", pages.containsMatchIn(it))
        }
        listOf(
            "/jack/followers",
            "/jack/following",
            "/jack/verified_followers",
            "/jack",
            "/search",
            "/explore",
            "/i/connect_people",
            "/jack/lists",
            "/jack/status/1/retweets",
            "/jack/status/1/likes",
            "/jack/status/1/quotes",
            "/jack/status/1/retweets/with_comments",
        ).forEach { assertFalse("$it keeps its account rows", pages.containsMatchIn(it)) }
    }

    @Test
    fun whoToFollowHidesTheRowsAfterItsHiddenHeadingAndStopsAtAPostOrSeparator() {
        val js = script(setOf(SiteTweak.XWhoToFollow))
        assertTrue(js.contains("Who to follow"))
        assertTrue("walks only after the engine hid the heading's cell", js.contains("if(!c||!hiddenEls.has(c))return;"))
        assertTrue("only later cells, never earlier ones", js.contains("n=c.nextElementSibling;n&&n.matches&&n.matches(cs);n=n.nextElementSibling"))
        assertFalse(js.contains("previousElementSibling"))
        assertTrue(js.contains("if(n.querySelector('$X_MODULE_END_SELECTOR'))break;"))
        assertTrue(js.contains("if(!n.querySelector('$X_SUGGESTION_ROW_SELECTOR'))break;"))
        assertTrue(js.contains("if(hide(n,false))hiddenEls.add(n);else break;"))
        listOf("article[data-testid=\"tweet\"]", "[role=\"separator\"]", "input", "form").forEach {
            assertTrue("$it ends the module", X_MODULE_END_SELECTOR.split(",").contains(it))
        }
        listOf("[data-testid=\"UserCell\"]", "a[href^=\"/i/connect_people\"]", "a[href*=\"/i/topics/\"]").forEach {
            assertTrue("$it is a suggestion row", X_SUGGESTION_ROW_SELECTOR.split(",").contains(it))
        }
        assertEquals(X_WHO_TO_FOLLOW_LABELS, SiteTweak.XWhoToFollow.rule.headingLabels)
        assertFalse("the walk is part of the tweak", script(setOf(SiteTweak.XTrends)).contains("UserCell"))
    }

    // Discover more

    @Test
    fun discoverMoreIsANewDefaultOnXTweakAppendedAtTheEnd() {
        assertEquals(SiteTweak.XDiscoverMore, SiteTweak.entries.last())
        assertEquals(LiveWebSite.X, SiteTweak.XDiscoverMore.site)
        assertTrue(SiteTweak.XDiscoverMore.defaultOn)
        assertTrue(SiteTweak.XDiscoverMore in SiteWebSettings.defaultsFor(LiveWebSite.X).tweaks)
        assertTrue(SiteTweak.XDiscoverMore in SiteTweak.forSite(LiveWebSite.X))
        // The tweaks that were there before keep their positions.
        assertEquals(0, SiteTweak.InstagramAds.ordinal)
        assertEquals(SiteTweak.entries.size - 2, SiteTweak.MastodonTrending.ordinal)
    }

    @Test
    fun discoverMoreMatchesItsHeadingOnPostPagesOnly() {
        val heading = Regex(labelPattern(X_DISCOVER_MORE_LABELS), RegexOption.IGNORE_CASE)
        listOf("Discover more", "Discover moreSourced from across X", "Sourced from across X").forEach {
            assertTrue("$it is the heading", heading.containsMatchIn(it))
        }
        assertFalse(heading.containsMatchIn("Who to follow"))
        val pages = path(X_POST_PAGE_PATTERN)
        assertTrue(pages.containsMatchIn("/jack/status/20"))
        assertTrue(pages.containsMatchIn("/jack/status/20/video/1"))
        listOf("/home", "/explore", "/jack", "/search", "/jack/status/20/likes", "/jack/status/20/retweets", "/jack/status/20/quotes").forEach { assertFalse("$it has no Discover more run", pages.containsMatchIn(it)) }
    }

    @Test
    fun discoverMoreHidesTheHeadingAndOnlyTheCellsAfterIt() {
        val js = script(setOf(SiteTweak.XDiscoverMore))
        assertTrue(js.contains("Discover more"))
        assertTrue("never a heading inside a post", js.contains("if(h.closest('article'))continue;"))
        assertTrue("the heading's cell goes first", js.contains("if(hc.querySelector('input,form,textarea')||!hide(hc,false))continue;"))
        assertTrue(js.contains("for(var n=c.nextElementSibling;n&&n.matches&&n.matches(cs);n=n.nextElementSibling){"))
        assertFalse("the replies above stay", js.contains("previousElementSibling"))
        assertTrue("one post per cell, never a cell with two posts", js.contains("}else if(posts>1)break;"))
        assertTrue(
            "a field, a separator or a button outside a post ends the run",
            js.contains("if(n.querySelector('input,form,textarea,[role=\"separator\"]')||buttonOutsidePost(n))break;"),
        )
        assertTrue("a cell without a post goes only when it is X's spinner", js.contains("if(!n.querySelector('[role=\"progressbar\"]')){"))
        assertTrue("the engine's own guard still applies", js.contains("if(!canHide(n,true))break;"))
        assertFalse("display only", js.contains(".click("))
        assertFalse(script(SiteWebSettings.defaultsFor(LiveWebSite.X).tweaks - SiteTweak.XDiscoverMore).contains("Sourced from across X"))
    }

    @Test
    fun discoverMoreCarriesOnOnceXUnmountsTheHeadingCell() {
        val js = script(setOf(SiteTweak.XDiscoverMore))
        assertTrue("the run's state is kept per post path", js.contains("if(!last||last.path!==path)last=keep.xDiscoverLast={path:path,cell:null,collapsed:0};"))
        assertTrue(js.contains("last.cell=reached;"))
        assertTrue(
            "only from a cell still on the page that the run hid",
            js.contains("if(!c&&last.cell&&last.cell.isConnected&&hiddenEls.has(last.cell))c=last.cell;"),
        )
        assertTrue("cells hidden earlier move the anchor on", js.contains("if(hiddenEls.has(n)){reached=n;continue;}"))
    }

    @Test
    fun discoverMoreCollapsesOnlyABoundedRunSoXNeverFetchesPageAfterPage() {
        val js = script(setOf(SiteTweak.XDiscoverMore))
        assertEquals(10, X_DISCOVER_MORE_MAX_COLLAPSED)
        assertTrue(js.contains("if(last.collapsed<$X_DISCOVER_MORE_MAX_COLLAPSED){"))
        assertTrue("later cells keep their height", js.contains("n.style.setProperty('visibility','hidden','important');"))
        assertTrue(js.contains("n.style.setProperty('opacity','0','important');"))
        assertFalse("nothing is loaded or clicked", js.contains(".click(") || js.contains("scrollIntoView") || js.contains("fetch("))
    }

    // For you

    @Test
    fun forYouIsHiddenOnlyOnHomeAndNeverWhileSelected() {
        assertEquals(X_TAB_PATH_PATTERN, LiveWebSite.X.scriptProfile().tabPathPattern)
        val home = path(X_TAB_PATH_PATTERN)
        assertTrue(home.containsMatchIn("/home"))
        listOf("/explore", "/explore/tabs/for-you", "/jack", "/i/lists").forEach { assertFalse("$it keeps its tabs", home.containsMatchIn(it)) }
        val js = script(setOf(SiteTweak.XHideForYou))
        assertTrue(js.contains("var tabsHere=!tabPathRx||tabPathRx.test(location.pathname||'');"))
        assertTrue(js.contains("var keepTab=!tabsHere||t.getAttribute('aria-selected')==='true';"))
        assertTrue("a tab hidden earlier comes back once selected", js.contains("w.style.removeProperty('display');"))
        assertTrue(js.contains("hiddenEls.delete(t);"))
        listOf(LiveWebSite.Instagram, LiveWebSite.LinkedIn, LiveWebSite.Reddit, LiveWebSite.Mastodon).forEach {
            assertEquals("$it", "", it.scriptProfile().tabPathPattern)
        }
    }

    // Work per pass

    @Test
    fun noXTweakWalksEveryButtonWithoutACache() {
        assertFalse(all.contains("document.querySelectorAll('a,button,[role=button]').forEach"))
        assertTrue(all.contains("if(checkedButtons.has(b)||hiddenEls.has(b))return;"))
        assertTrue("module walks only read headings inside timeline cells", all.contains("document.querySelectorAll(cs+' h2,'+cs+' [role=\"heading\"]')"))
        assertTrue("and return at once off their pages", all.contains("if(!wx.test(location.pathname||''))return;"))
        assertTrue(all.contains("if(!px.test(path))return;"))
        assertTrue("their patterns are compiled once per page", all.contains("keep.xWalkRx||(keep.xWalkRx=new RegExp("))
        assertTrue(all.contains("keep.xDiscoverRx||(keep.xDiscoverRx=new RegExp("))
    }

    @Test
    fun appPromptLayersTestTheirChildLinkBeforeWalkingForAField() {
        val css = SiteTweak.XAppPrompts.rule.css.filter { it.startsWith("#layers") || it.contains("sheetDialog") }
        assertEquals(4, css.size)
        css.forEach {
            assertTrue("$it keeps its field guard", it.endsWith(X_NO_FIELD))
            assertTrue("$it tests the link first", it.indexOf(":has(") < it.indexOf(X_NO_FIELD))
        }
        assertEquals(":not(:has(input,form))", X_NO_FIELD)
        assertTrue(css.contains("#layers div:has(> a[href*=\"app.link\"]):not(:has(input,form))"))
    }
}
