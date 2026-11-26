package org.crux.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWebLinkedInExtractTest {
    private fun jsResult(json: String): String =
        "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun stripsFeedPostLabel() {
        assertEquals("Asha Rao", stripLinkedInFeedPostPrefix("Feed post Asha Rao"))
        assertEquals("Asha Rao", stripLinkedInFeedPostPrefix("  feed post\nAsha Rao "))
        assertEquals("Asha Rao", stripLinkedInFeedPostPrefix("Asha Rao"))
        assertEquals("Feed postman", stripLinkedInFeedPostPrefix("Feed postman"))
        assertEquals("", stripLinkedInFeedPostPrefix("Feed post"))
    }

    @Test
    fun smartReplyAuthorDropsLabelOnLinkedInOnly() {
        val raw = jsResult("""{"text":"Hiring now","author":"Feed post Asha Rao"}""")
        assertEquals("Asha Rao", parseLiveWebPost(raw, LiveWebSite.LinkedIn)?.author)
        assertEquals("Feed post Asha Rao", parseLiveWebPost(raw, LiveWebSite.X)?.author)
        assertEquals("Feed post Asha Rao", parseLiveWebPost(raw)?.author)
    }

    @Test
    fun bookmarkAuthorDropsLabelAndFallsBackWhenEmpty() {
        val named =
            parseExtractedSocialPost(
                LiveWebSite.LinkedIn,
                jsResult("""{"authorName":"Feed post Asha Rao","text":"Hiring now","url":"https://www.linkedin.com/feed/update/urn:li:activity:1/"}"""),
                "https://www.linkedin.com/feed/",
            )
        assertEquals("Asha Rao", named.authorName)
        assertEquals("https://www.linkedin.com/feed/update/urn:li:activity:1/", named.id)

        val unnamed =
            parseExtractedSocialPost(
                LiveWebSite.LinkedIn,
                jsResult("""{"authorName":"Feed post","text":"Hiring now","url":"https://www.linkedin.com/feed/"}"""),
                "https://www.linkedin.com/feed/",
            )
        assertEquals("LinkedIn", unnamed.authorName)
    }

    @Test
    fun feedUrlFallbackGetsPerPostIds() {
        val feed = "https://www.linkedin.com/feed/"
        val a = bookmarkPostId(LiveWebSite.LinkedIn, feed, "First post")
        val b = bookmarkPostId(LiveWebSite.LinkedIn, feed, "Second post")
        assertNotEquals(a, b)
        assertTrue(a.startsWith("$feed#post-"))
        assertEquals(a, bookmarkPostId(LiveWebSite.LinkedIn, feed, "  First post "))
    }

    @Test
    fun permalinksAndOtherSitesKeepTheirUrl() {
        val update = "https://www.linkedin.com/feed/update/urn:li:activity:9/"
        val posts = "https://www.linkedin.com/posts/asha_hiring-activity-9"
        assertEquals(update, bookmarkPostId(LiveWebSite.LinkedIn, update, "x"))
        assertEquals(posts, bookmarkPostId(LiveWebSite.LinkedIn, posts, "x"))
        assertEquals("https://x.com/home", bookmarkPostId(LiveWebSite.X, "https://x.com/home", "x"))
    }

    @Test
    fun readPostScriptKnowsTheMobileAnchors() {
        assertTrue(READ_POST_SCRIPT.contains("div[role=listitem][componentkey^=\"update-card\"]"))
        assertTrue(READ_POST_SCRIPT.contains("[data-testid=\"expandable-text-box\"]"))
        assertTrue(READ_POST_SCRIPT.contains("Feed post"))
        // Desktop selectors stay as fallbacks.
        assertTrue(READ_POST_SCRIPT.contains("div.feed-shared-update-v2"))
        assertTrue(READ_POST_SCRIPT.contains(".update-components-text"))
    }

    @Test
    fun socialContextPatternFlagsReactorsNotAuthors() {
        val social = Regex(LINKEDIN_SOCIAL_CONTEXT_PATTERN, RegexOption.IGNORE_CASE)
        listOf(
            "Jane Doe likes this",
            "Jane Doe loves this",
            "Jane Doe celebrates this",
            "Jane Doe supports this",
            "Jane Doe finds this insightful",
            "Jane Doe is curious about this",
            "Jane Doe commented on this",
            "Acme reposted this",
            "Jane Doe follows Acme",
        ).forEach { assertTrue(it, social.containsMatchIn(it)) }
        listOf("Asha Rao", "Asha Rao \u2022 2nd", "Feed post Asha Rao", "Acme Corp", "Asha Likesworth").forEach {
            assertFalse(it, social.containsMatchIn(it))
        }
    }

    @Test
    fun bothScriptsSkipSocialContextHeaders() {
        val literal = "/$LINKEDIN_SOCIAL_CONTEXT_PATTERN/i"
        assertTrue(READ_POST_SCRIPT.contains(literal))
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains(literal))
        assertTrue(READ_POST_SCRIPT.contains("inSocialLine(heads[i],t)"))
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains("liInSocialLine(heads[hd], htx)"))
    }

    @Test
    fun bookmarkScriptKnowsTheMobileAnchors() {
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains("div[role=\"listitem\"][componentkey^=\"update-card\"]"))
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains("[data-testid=\"expandable-text-box\"]"))
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains("a[href*=\"/feed/update/\"]"))
        assertTrue(EXTRACT_BOOKMARK_POST_SCRIPT.contains("div.feed-shared-update-v2"))
    }
}
