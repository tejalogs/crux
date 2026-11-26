package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebPostExtractTest {
    private fun jsResult(json: String): String = "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun smartReplyKeepsTheXHandleThePageFound() {
        val post = parseLiveWebPost(jsResult("""{"text":"Launch day","author":"NASA","handle":"@NASA"}"""), LiveWebSite.X)
        assertEquals(LiveWebPost("Launch day", "NASA", "@NASA"), post)
        assertEquals("@NASA", smartReplyHandle(requireNotNull(post)))
    }

    @Test
    fun smartReplyHandleFallsBackAndRejectsNonHandles() {
        assertEquals("@old", smartReplyHandle(LiveWebPost("t", "@old")))
        assertEquals("", smartReplyHandle(LiveWebPost("t", "Display Name")))
        assertEquals("", smartReplyHandle(LiveWebPost("t", "n", "@a b")))
        assertEquals("", smartReplyHandle(LiveWebPost("t", "n", "@")))
        assertEquals("", smartReplyHandle(LiveWebPost("t", "n", "u/someone")))
        assertEquals("", smartReplyHandle(LiveWebPost("t", "n", "@" + "a".repeat(80))))
        assertEquals("@me@mastodon.social", smartReplyHandle(LiveWebPost("t", "Me", " @me@mastodon.social ")))
        // Older results without a handle field still parse.
        assertEquals("", parseLiveWebPost(jsResult("""{"text":"Hi","author":"Asha"}"""))?.handle)
    }

    @Test
    fun xPhotosAreAskedForAtALargerSize() {
        assertEquals(
            "https://pbs.twimg.com/media/AAA?format=jpg&name=large",
            upgradeXImageUrl("https://pbs.twimg.com/media/AAA?format=jpg&name=small"),
        )
        assertEquals(
            "https://pbs.twimg.com/media/AAA?format=webp&name=orig",
            upgradeXImageUrl("https://pbs.twimg.com/media/AAA?format=webp&name=360x360", size = "orig"),
        )
        assertEquals("https://pbs.twimg.com/media/AAA?format=jpg&name=large", upgradeXImageUrl("https://pbs.twimg.com/media/AAA?format=jpg"))
        assertEquals("https://pbs.twimg.com/media/AAA.jpg:large", upgradeXImageUrl("https://pbs.twimg.com/media/AAA.jpg:small"))
        assertEquals("https://pbs.twimg.com/media/AAA.jpg?name=orig", upgradeXImageUrl("https://pbs.twimg.com/media/AAA.jpg", size = "orig"))
    }

    @Test
    fun xImageUpgradeLeavesEverythingElseAlone() {
        listOf(
            // Already large enough.
            "https://pbs.twimg.com/media/AAA?format=jpg&name=orig",
            "https://pbs.twimg.com/media/AAA?format=jpg&name=4096x4096",
            // No format: the CDN could not tell which file to send.
            "https://pbs.twimg.com/media/AAA",
            // Avatars, video thumbnails, other hosts, plain http.
            "https://pbs.twimg.com/profile_images/1/a_normal.jpg",
            "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/a.jpg?name=small",
            "https://evil.example/media/AAA?format=jpg&name=small",
            "http://pbs.twimg.com/media/AAA?format=jpg&name=small",
            "not a url %%",
        ).forEach { assertEquals(it, upgradeXImageUrl(it)) }
        assertEquals(
            "https://pbs.twimg.com/media/AAA?format=jpg&name=small",
            upgradeXImageUrl("https://pbs.twimg.com/media/AAA?format=jpg&name=small", size = "huge"),
        )
    }

    @Test
    fun xBookmarksKeepTheLargePhoto() {
        val raw =
            jsResult(
                """{"title":"Post","authorName":"NASA","authorHandle":"@NASA","text":"Body","url":"https://x.com/NASA/status/1",""" +
                    """"media":["https://pbs.twimg.com/media/AAA?format=jpg&name=small","https://pbs.twimg.com/media/AAA?format=jpg&name=medium"]}""",
            )
        val parsed = parseExtractedSocialPost(LiveWebSite.X, raw, "https://x.com/home")
        assertEquals(listOf("https://pbs.twimg.com/media/AAA?format=jpg&name=large"), parsed.mediaUrls)
        val long = parseExtractedSocialPost(LiveWebSite.X, raw, "https://x.com/home", overrideImageUrl = "https://pbs.twimg.com/media/BBB?format=png&name=small")
        assertEquals("https://pbs.twimg.com/media/BBB?format=png&name=large", long.mediaUrls.first())
        // Other sites keep their images as they are.
        val reddit = parseExtractedSocialPost(LiveWebSite.Reddit, raw, "https://www.reddit.com/")
        assertEquals("https://pbs.twimg.com/media/AAA?format=jpg&name=small", reddit.mediaUrls.first())
    }

    @Test
    fun instagramLinksBecomeThePostItself() {
        assertEquals("https://www.instagram.com/p/ABC/", canonicalInstagramPostUrl("https://www.instagram.com/p/ABC/liked_by/"))
        assertEquals("https://www.instagram.com/p/ABC/", canonicalInstagramPostUrl("https://www.instagram.com/p/ABC/comments/"))
        assertEquals("https://www.instagram.com/p/ABC/", canonicalInstagramPostUrl("https://www.instagram.com/p/ABC/c/179/"))
        assertEquals("https://www.instagram.com/p/ABC/", canonicalInstagramPostUrl("https://www.instagram.com/natgeo/p/ABC/"))
        assertEquals("https://www.instagram.com/reel/XY_-1/", canonicalInstagramPostUrl("https://instagram.com/natgeo/reel/XY_-1/?igsh=1"))
        assertEquals("https://www.instagram.com/reel/XY/", canonicalInstagramPostUrl("https://www.instagram.com/reels/XY/"))
        assertEquals("/p/ABC/", canonicalInstagramPostPath("/natgeo/p/ABC/liked_by/"))
    }

    @Test
    fun otherInstagramPagesAndOtherHostsAreUnchanged() {
        listOf(
            "https://www.instagram.com/",
            "https://www.instagram.com/natgeo/",
            "https://www.instagram.com/reels/audio/123/",
            "https://www.instagram.com/explore/",
            "https://evil.example/p/ABC/liked_by/",
        ).forEach { assertEquals(it, canonicalInstagramPostUrl(it)) }
        assertEquals(null, canonicalInstagramPostPath("/natgeo/"))
    }

    @Test
    fun instagramBookmarksSaveThePostNotItsLikersList() {
        val raw = jsResult("""{"authorName":"natgeo","text":"Caption","url":"https://www.instagram.com/p/ABC/liked_by/"}""")
        val parsed = parseExtractedSocialPost(LiveWebSite.Instagram, raw, "https://www.instagram.com/")
        assertEquals("https://www.instagram.com/p/ABC/", parsed.sourceUrl)
        assertEquals("https://www.instagram.com/p/ABC/", parsed.id)
    }

    @Test
    fun aFailedInstagramExtractStillSavesThePostNotItsLikersList() {
        listOf(null, "null", "\"not json\"").forEach { raw ->
            val parsed = parseExtractedSocialPost(LiveWebSite.Instagram, raw, "https://www.instagram.com/natgeo/p/ABC/liked_by/")
            assertEquals("https://www.instagram.com/p/ABC/", parsed.sourceUrl)
            assertEquals("https://www.instagram.com/p/ABC/", parsed.id)
        }
        // Other sites keep the page they were on.
        val x = parseExtractedSocialPost(LiveWebSite.X, null, "https://x.com/nasa/status/12")
        assertEquals("https://x.com/nasa/status/12", x.sourceUrl)
    }

    @Test
    fun theOpenedPostRuleOnlyRunsOnItsOwnSite() {
        assertTrue(POST_PARTS_JS.contains("var im=onHost('instagram.com')?"))
        assertTrue(POST_PARTS_JS.contains("var lm=onHost('linkedin.com')?"))
        assertTrue(POST_PARTS_JS.contains("(onHost('x.com')||onHost('twitter.com'))?"))
    }

    @Test
    fun bothScriptsPreferTheTappedThenTheOpenedPost() {
        listOf(READ_POST_SCRIPT, EXTRACT_BOOKMARK_POST_SCRIPT).forEach { js ->
            assertTrue(js.contains("new CustomEvent('${EngineMarks.tapEvent}'"))
            assertTrue(js.contains("best=focalPost(") || js.contains("best = focalPost("))
            assertTrue(js.contains("var focalPost=function(list)"))
            assertTrue(js.contains("var xPostParts=function(root)"))
        }
        assertTrue(READ_POST_SCRIPT.contains("tapped.matches(postSel+',[role=article]'))best=tapped"))
        // The opened post is found by its own time link on X, outside a quoted post.
        assertTrue(POST_PARTS_JS.contains("as[i].querySelector('time')&&!xQuoteOf(as[i],el)"))
        // Older WebViews reject :has(); a selector error would lose the whole result.
        assertFalse(EXTRACT_BOOKMARK_POST_SCRIPT.contains(":has("))
        assertFalse(READ_POST_SCRIPT.contains(":has("))
    }

    @Test
    fun scriptsReadTheHandleAndSkipQuotedText() {
        assertTrue(READ_POST_SCRIPT.contains("handle:xp.handle"))
        assertTrue(POST_PARTS_JS.contains("if(!q){if(!own)own=tx}"))
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains("video[poster^=\"https://\"]"))
        assertTrue(POST_PARTS_JS.contains("a[href*=\"/liked_by/\"],a[href*=\"/comments/\"]"))
    }
}
