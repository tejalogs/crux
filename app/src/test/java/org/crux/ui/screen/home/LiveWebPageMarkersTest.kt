package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * The page engine must not leave anything a site could match to Crux: no global, attribute, id,
 * CSS selector, comment or console string that says crux or cx, and no marker name a site could
 * recognise by its shape or recompute from public code.
 */
class LiveWebPageMarkersTest {
    /** Any "cx" at all, which also covers crux-free spellings like a bare cx attribute or window.cx. */
    private val forbidden = listOf("crux", "cx")

    private val bridgeTokens = listOf("", "0123456789abcdef0123456789abcdef")

    /** Every script a supported site can get: all tweaks on, one at a time, none, plus the read-only scripts. */
    private fun everyScript(): Map<String, String> =
        buildMap {
            LiveWebSite.entries.forEach { site ->
                val all = SiteTweak.forSite(site).toSet()
                bridgeTokens.forEach { token ->
                    listOf(true, false).forEach { counts ->
                        buildSiteScript(site, all, hideEngagementCounts = counts, bridgeToken = token)
                            ?.let { put("$site all counts=$counts token=$token", it) }
                        buildSiteScript(site, emptySet(), hideEngagementCounts = counts, bridgeToken = token)
                            ?.let { put("$site none counts=$counts token=$token", it) }
                    }
                }
                SiteTweak.forSite(site).forEach { tweak ->
                    buildSiteScript(site, setOf(tweak))?.let { put("$site $tweak", it) }
                }
                LiveWebConfig.defaultsFor(site).documentStartScripts(site, bridgeTokens.last()).forEachIndexed { i, script ->
                    put("$site document start $i", script)
                }
            }
            put("bookmark", EXTRACT_BOOKMARK_POST_SCRIPT)
            put("smart reply", READ_POST_SCRIPT)
        }

    private fun allScript(site: LiveWebSite): String =
        requireNotNull(buildSiteScript(site, SiteTweak.forSite(site).toSet(), bridgeToken = "t")) { "no script for $site" }

    private val attrMarks: List<String>
        get() =
            listOf(
                EngineMarks.auth,
                EngineMarks.explore,
                EngineMarks.reels,
                EngineMarks.nsfwDismissed,
                EngineMarks.nsfwContent,
                EngineMarks.unblurred,
                REDDIT_SCROLL_FREE_ATTR,
                REDDIT_NSFW_EMPTY_ATTR,
            )

    private val propMarks: List<String>
        get() = listOf(PageMarkers.consolePrefix, EngineMarks.guard, EngineMarks.tapEvent)

    @Test
    fun noScriptNamesCrux() {
        val scripts = everyScript()
        assertTrue("every site has an engine script", LiveWebSite.entries.all { site -> scripts.keys.any { it.startsWith("$site all") } })
        scripts.forEach { (name, js) ->
            forbidden.forEach { word ->
                val at = js.indexOf(word, ignoreCase = true)
                assertTrue("$name contains '$word' near: ${if (at < 0) "" else js.substring(maxOf(0, at - 40), minOf(js.length, at + 40))}", at < 0)
            }
        }
    }

    @Test
    fun bridgeMessagesAndMarkersNeverNameCrux() {
        val names =
            attrMarks + propMarks +
                bridgeTokens.flatMap { token ->
                    listOf(
                        bridgeBookmarkMessage(token),
                        bridgePtrMessage(allow = true, bridgeToken = token),
                        bridgePtrMessage(allow = false, bridgeToken = token),
                        bridgeRedditNsfwNoteMessage(token),
                    )
                }
        names.forEach { name ->
            forbidden.forEach { assertFalse("$name contains $it", name.contains(it, ignoreCase = true)) }
        }
        assertEquals("every marker is distinct", names.size, names.toSet().size)
    }

    @Test
    fun markerNamesShareNoTokenAndCarryNoHash() {
        val words = attrMarks.map { it.removePrefix("data-") } + propMarks
        words.forEach { assertFalse("$it has hex digits", it.any(Char::isDigit)) }
        // Each name is drawn on its own: no common token links them (all eleven sharing even three
        // leading letters by chance is about 1 in 10^35).
        assertTrue("names share a prefix: $words", words.map { it.take(3) }.toSet().size > 1)
        val firstParts = attrMarks.map { it.removePrefix("data-").substringBefore('-') }
        assertTrue("attributes share a first part: $firstParts", firstParts.toSet().size > 1)
        assertNotEquals(PageMarkers.attr("a"), PageMarkers.attr("b"))
        assertNotEquals(PageMarkers.prop("a"), PageMarkers.prop("b"))
        assertNotEquals("attr and prop names are separate", PageMarkers.attr("same").removePrefix("data-"), PageMarkers.prop("same"))
    }

    @Test
    fun markerShapesAreValidAndVaried() {
        val attr = Regex("^data-[a-z]{4,8}(-[a-z]{4,8})?$")
        val prop = Regex("^[a-z]{6,12}$")
        (attrMarks + PageMarkers.attr("anything")).forEach { assertTrue(it, attr.matches(it)) }
        (propMarks + PageMarkers.prop("anything")).forEach { assertTrue(it, prop.matches(it)) }

        val random = Random(42)
        val attrs = (0 until 3000).map { PageMarkers.newAttrName(random) }
        attrs.forEach { assertTrue(it, attr.matches(it)) }
        assertEquals("one and two part names both occur", setOf(1, 2), attrs.map { it.removePrefix("data-").split('-').size }.toSet())
        assertEquals("every part length occurs", (4..8).toSet(), attrs.flatMap { it.removePrefix("data-").split('-') }.map { it.length }.toSet())
        val props = (0 until 3000).map { PageMarkers.newPropName(random) }
        props.forEach { assertTrue(it, prop.matches(it)) }
        assertEquals("every length occurs", (6..12).toSet(), props.map { it.length }.toSet())
        assertEquals("the whole alphabet is used", ('a'..'z').toSet(), props.flatMap { it.toList() }.toSet())
    }

    @Test
    fun uncleanNamesAreRejected() {
        listOf("abcxde", "data-qcxa", "data-akrux-crux", "xabcde", "data-xenon", "abdec", "data-tolic", "CXabde").forEach {
            assertFalse(it, PageMarkers.isClean(it))
        }
        listOf("abxdef", "data-tibmo-akrusen", "qorvel").forEach { assertTrue(it, PageMarkers.isClean(it)) }
    }

    @Test
    fun markersStayTheSameWithinAProcess() {
        assertEquals(PageMarkers.attr("auth"), PageMarkers.attr("auth"))
        assertEquals(EngineMarks.auth, PageMarkers.attr("auth"))
        assertEquals(PageMarkers.prop("tap"), EngineMarks.tapEvent)
        assertEquals(bridgeBookmarkMessage("t"), bridgeBookmarkMessage("t"))
        LiveWebSite.entries.forEach { site ->
            val all = SiteTweak.forSite(site).toSet()
            assertEquals(
                "$site builds the same script twice",
                buildSiteScript(site, all, hideEngagementCounts = true, bridgeToken = "t"),
                buildSiteScript(site, all, hideEngagementCounts = true, bridgeToken = "t"),
            )
        }
        val first = everyScript()
        assertEquals(first, everyScript())
    }

    @Test
    fun bridgeMessagesAreTokenScopedAndDistinct() {
        val kinds =
            listOf(
                bridgeBookmarkMessage("t"),
                bridgePtrMessage(allow = true, bridgeToken = "t"),
                bridgePtrMessage(allow = false, bridgeToken = "t"),
                bridgeRedditNsfwNoteMessage("t"),
            )
        assertEquals(kinds.size, kinds.toSet().size)
        kinds.forEach { assertTrue(it, it.startsWith(PageMarkers.consolePrefix + ":t:")) }
        assertNotEquals(bridgeBookmarkMessage("a"), bridgeBookmarkMessage("b"))
        assertNotEquals(bridgeBookmarkMessage(""), bridgeBookmarkMessage("a"))
    }

    @Test
    fun engineKeepsItsStateOffWindowAndUsesTheNativeConsole() {
        LiveWebSite.entries.forEach { site ->
            val js = allScript(site)
            assertFalse("$site writes no window.__ global", js.contains("window.__"))
            assertTrue(js.contains("var guard=Symbol.for('${EngineMarks.guard}');"))
            assertTrue(
                "$site sets the guard only when it runs after load",
                js.contains("if(document.readyState!=='loading'){try{Object.defineProperty(window,guard,{value:true})}catch(e){}}"),
            )
            assertEquals("$site defines exactly one window property", 1, Regex("defineProperty\\(window").findAll(js).count())
            assertFalse("$site sets no named window property", Regex("window\\[['\"]|window\\.[a-z]+=[^=]").containsMatchIn(js))
            assertEquals("$site reads console.debug only once, at start", 1, Regex("console\\.debug").findAll(js).count())
            val capture = js.indexOf("var nDebug=console.debug")
            assertTrue(capture in 0 until js.indexOf("var css="))
            assertFalse("$site posts bridge messages only through the captured console", js.contains("console.debug("))
            assertFalse("$site timers are the captured ones", Regex("(?<![A-Za-z.])setTimeout\\(").containsMatchIn(js))
            assertTrue(js.contains("new NObserver(schedule)"))
            assertFalse("$site ships no line comments", js.lines().any { it.trimStart().startsWith("//") })
            assertFalse("$site writes no tap timestamps", js.contains("tapped"))
        }
    }

    @Test
    fun onlyMarksSomethingReadsAreWrittenIntoThePage() {
        val allowed = attrMarks.toSet() + "inert" + "aria-hidden"
        val setAttr = Regex("setAttribute\\('([^']+)'")
        everyScript().forEach { (name, js) ->
            setAttr.findAll(js).map { it.groupValues[1] }.forEach {
                assertTrue("$name writes attribute $it", it in allowed || !it.startsWith("data-"))
            }
            assertFalse("$name gives an element an id", Regex("\\.id\\s*=\\s*'").containsMatchIn(js))
            assertFalse("$name writes an off flag instead of removing it", js.contains("?'1':'0'"))
        }
        LiveWebSite.entries.forEach { site ->
            val js = allScript(site)
            assertTrue("$site tracks hidden elements in the closure", js.contains("var hiddenEls=new WeakSet();"))
            assertTrue(js.contains("shadowStyles.set(sr,s)"))
        }
    }

    @Test
    fun signInMarkIsSetOnlyWhereItIsRead() {
        listOf(LiveWebSite.X, LiveWebSite.LinkedIn, LiveWebSite.Mastodon).forEach { site ->
            assertTrue("$site never sets the sign-in mark", allScript(site).contains("var authAttr=false;"))
        }
        listOf(LiveWebSite.Instagram, LiveWebSite.Reddit).forEach { site ->
            val js = allScript(site)
            assertTrue("$site reads the sign-in mark in CSS", js.contains("html:not([${EngineMarks.auth}="))
            assertTrue("$site sets it", js.contains("var authAttr=true;"))
        }
        assertTrue("the mark is skipped before it is written", allScript(LiveWebSite.X).contains("if(!authAttr)return;"))
    }

    @Test
    fun cssAndJsUseTheSameMarkers() {
        val reddit = allScript(LiveWebSite.Reddit)
        assertTrue(reddit.contains("setAttribute('${EngineMarks.auth}',"))
        assertTrue(reddit.contains("html:not([${EngineMarks.auth}=\\\"1\\\"])"))
        assertTrue(reddit.contains("e.setAttribute('${EngineMarks.nsfwDismissed}','1')"))
        assertTrue(reddit.contains("#blocking-modal[${EngineMarks.nsfwDismissed}]{display:none!important}"))
        assertTrue(reddit.contains("p.setAttribute('${EngineMarks.unblurred}','1')"))
        assertTrue(reddit.contains("shreddit-blurred-container[${EngineMarks.unblurred}]"))
        assertTrue(reddit.contains("if(hiddenEls.has(e)||nag(e))continue;"))
        val ig = allScript(LiveWebSite.Instagram)
        assertTrue(ig.contains("setAttribute('${EngineMarks.reels}',flag)"))
        assertTrue(ig.contains("html[${EngineMarks.reels}=\\\"1\\\"]"))
        assertTrue(ig.contains("setAttribute('${EngineMarks.explore}',"))
        assertTrue(ig.contains("html[${EngineMarks.explore}=\\\"1\\\"]"))
    }

    @Test
    fun linkedInSignInStateLivesInTheClosure() {
        val js = requireNotNull(buildSiteScript(LiveWebSite.LinkedIn, emptySet()))
        assertTrue(js.contains("var liSubmitted=false;document.addEventListener('submit',function(){liSubmitted=true},true);"))
        assertTrue(js.contains("if(!liSubmitted&&/^\\/(uas\\/)?login/.test(location.pathname)){"))
    }

    @Test
    fun bookmarkScriptAsksTheEngineForTheTappedPost() {
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains("document.dispatchEvent(new CustomEvent('${EngineMarks.tapEvent}', { detail: tapAsk }))"))
        assertTrue(
            "only a real post element is taken",
            EXTRACT_BOOKMARK_POST_SCRIPT.contains("tapped.isConnected && tapped.matches && tapped.matches(sel)) best = tapped;"),
        )
        val x = requireNotNull(buildSiteScript(LiveWebSite.X, SiteWebSettings.defaultsFor(LiveWebSite.X).tweaks))
        assertTrue("the engine only answers within a few seconds of the tap", x.contains("Date.now()-tapAt<6000)d.el=tapPost;"))
        assertTrue("the engine answers first, on window, in the capture phase", x.contains("nListen.call(window,'${EngineMarks.tapEvent}',function(ev){"))
        assertTrue("no page listener sees the request", x.contains("ev.stopImmediatePropagation();"))
    }

    @Test
    fun clickListenerNeverThrowsIntoThePage() {
        val x = allScript(LiveWebSite.X)
        assertTrue(
            "the click handler is wrapped in try",
            Regex("nListen\\.call\\(window,'click',function\\(ev\\)\\{\\s*try\\{").containsMatchIn(x),
        )
        assertTrue("support for :has() is checked first", x.contains("CSS.supports('selector(:has(a))')"))
        assertFalse("the click selector has no unconditional :has()", x.contains("t.closest('[data-testid=\"bookmark\"]"))
    }

    /**
     * Every script parses as JavaScript. Runs where node is installed (it is on CI runners and dev
     * machines); skipped otherwise.
     */
    @Test
    fun everyScriptIsValidJavaScript() {
        val node = listOf("node", "/usr/local/bin/node", "/opt/homebrew/bin/node").firstOrNull { canRun(it) }
        assumeTrue("node is not installed", node != null)
        val dir = createTempDir()
        try {
            everyScript().entries.forEachIndexed { i, (name, js) ->
                val file = File(dir, "script$i.js").apply { writeText(js) }
                val process = ProcessBuilder(node, "--check", file.path).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertTrue("$name did not finish", process.waitFor(30, TimeUnit.SECONDS))
                assertEquals("$name does not parse:\n$output", 0, process.exitValue())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun canRun(command: String): Boolean =
        runCatching {
            val p = ProcessBuilder(command, "--version").redirectErrorStream(true).start()
            p.inputStream.readBytes()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

    private fun createTempDir(): File = kotlin.io.path.createTempDirectory("pagescripts").toFile()
}
