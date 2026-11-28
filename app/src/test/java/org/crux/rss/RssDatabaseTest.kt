package org.crux.rss

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.crux.ui.screen.rss.NativeRssTabTarget
import org.crux.ui.screen.rss.nativeRssTabTargetForId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RssDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: RssDatabase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = RssDatabase(context, dbName = "test_rss_${System.nanoTime()}.db")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun feedCrudAndArticleUpsertUnreadCountsAndOfflineCache() {
        val feedId =
            db.upsertFeed(
                url = "https://example.org/rss.xml",
                title = "Example Blog",
                siteUrl = "https://example.org",
                category = "Tech",
            )
        assertTrue(feedId > 0L)

        val parsed =
            listOf(
                ParsedArticle(
                    externalId = "a1",
                    title = "First Story",
                    link = "https://example.org/1",
                    author = "Alice",
                    summaryHtml = "<p>Summary 1</p>",
                    snippet = "Summary 1",
                    imageUrl = null,
                    publishedAt = 1_000L,
                ),
                ParsedArticle(
                    externalId = "a2",
                    title = "Second Story",
                    link = "https://example.org/2",
                    author = "Bob",
                    summaryHtml = "<p>Summary 2</p>",
                    snippet = "Summary 2",
                    imageUrl = "https://example.org/img.jpg",
                    publishedAt = 2_000L,
                ),
            )
        db.upsertArticles(feedId, parsed, fetchedAt = 3_000L)

        val feeds = db.getAllFeeds()
        assertEquals(1, feeds.size)
        assertEquals(2, feeds.first().unreadCount)

        val articles = db.getRecentArticles(limit = 10)
        assertEquals(2, articles.size)
        assertEquals("Second Story", articles.first().title)

        // Mark one read and confirm unread count updates and survives re-upsert.
        db.markArticleRead(articles.first().id, true)
        assertEquals(1, db.getAllFeeds().first().unreadCount)
        db.upsertArticles(feedId, parsed, fetchedAt = 4_000L)
        assertEquals(1, db.getAllFeeds().first().unreadCount)

        // Offline article cache round-trip.
        db.cacheFullArticle(
            link = "https://example.org/1",
            fullHtml = "<article><p>Full Offline Body</p></article>",
            byline = "Alice Smith",
            siteName = "Example Blog",
        )
        val cached = db.getCachedArticleByLink("https://example.org/1")
        assertNotNull(cached)
        assertEquals("<article><p>Full Offline Body</p></article>", cached?.cachedFullHtml)
        assertEquals("Alice Smith", cached?.cachedByline)

        // Cascade delete feed removes articles.
        db.deleteFeed(feedId)
        assertTrue(db.getAllFeeds().isEmpty())
        assertTrue(db.getRecentArticles().isEmpty())
    }

    @Test
    fun prunesOldArticlesBeyondPerFeedLimit() {
        val feedId = db.upsertFeed(url = "https://many.example.org/feed", title = "High Volume")
        val items =
            (1..175).map { idx ->
                ParsedArticle(
                    externalId = "item-$idx",
                    title = "Post #$idx",
                    link = "https://many.example.org/post/$idx",
                    author = null,
                    summaryHtml = null,
                    snippet = "Snippet $idx",
                    imageUrl = null,
                    publishedAt = idx.toLong() * 1000L,
                )
            }
        db.upsertArticles(feedId, items, fetchedAt = 999_999L)
        val stored = db.getRecentArticles(limit = 500, feedId = feedId)
        assertEquals(150, stored.size)
        assertEquals("Post #175", stored.first().title)
        assertEquals("Post #26", stored.last().title)
    }

    @Test
    fun legacyMigratorImportsRssRowsFromLegacyAppDb() {
        val tempFile = File(context.cacheDir, "legacy_app_${System.nanoTime()}.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(tempFile, null).use { legacy ->
                legacy.execSQL(
                    """
                    CREATE TABLE DbRssSources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        url TEXT NOT NULL,
                        title TEXT NOT NULL,
                        icon TEXT,
                        displayMode TEXT,
                        type TEXT
                    )
                    """.trimIndent(),
                )
                legacy.execSQL(
                    "INSERT INTO DbRssSources (url, title, icon, displayMode, type) VALUES " +
                        "('https://lwn.net/headlines/rss', 'LWN', NULL, 'DESCRIPTION_ONLY', 'RSS')," +
                        "('https://podcast.example.org/feed', 'Podcast', NULL, 'FULL_CONTENT', 'PODCAST')",
                )
            }
            val imported = LegacyRssMigrator.importLegacyFeeds(context, db, legacyDbFile = tempFile)
            assertEquals(1, imported)
            val feeds = db.getAllFeeds()
            assertEquals(1, feeds.size)
            assertEquals("https://lwn.net/headlines/rss", feeds.first().url)
            assertEquals(RssFeedDisplayMode.DESCRIPTION_ONLY, feeds.first().displayMode)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun matchesNativeRssTabIdsCorrectly() {
        assertEquals(NativeRssTabTarget.AllFeeds, nativeRssTabTargetForId("rss.all:all"))
        assertEquals(
            NativeRssTabTarget.SingleFeed("https://lwn.net/headlines/rss"),
            nativeRssTabTargetForId("rss.feed:https://lwn.net/headlines/rss"),
        )
        assertEquals(
            NativeRssTabTarget.SingleFeed("https://example.org/rss"),
            nativeRssTabTargetForId("rss.subscription:RSS:https://example.org/rss"),
        )
        assertNull(nativeRssTabTargetForId("web.x:home"))
    }
}
