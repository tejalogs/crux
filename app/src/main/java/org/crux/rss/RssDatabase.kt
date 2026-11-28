package org.crux.rss

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.content.edit

/**
 * Lean pure-Android SQLite store for RSS feeds and articles (uses Android's built-in SQLite,
 * avoiding KMP's bundled 1 MB native libsqliteJni.so).
 */
internal class RssDatabase(
    context: Context,
    dbName: String = DB_NAME,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, DB_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rss_feeds (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                site_url TEXT,
                icon_url TEXT,
                category TEXT,
                display_mode TEXT NOT NULL DEFAULT 'FULL_CONTENT',
                etag TEXT,
                last_modified TEXT,
                last_synced_at INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rss_articles (
                id TEXT PRIMARY KEY,
                feed_id INTEGER NOT NULL REFERENCES rss_feeds(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                link TEXT NOT NULL,
                author TEXT,
                summary_html TEXT,
                snippet TEXT NOT NULL,
                image_url TEXT,
                published_at INTEGER NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0,
                cached_full_html TEXT,
                cached_byline TEXT,
                cached_site_name TEXT,
                fetched_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rss_articles_published ON rss_articles(published_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rss_articles_feed_pub ON rss_articles(feed_id, published_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rss_articles_link ON rss_articles(link)")
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        // Initial version 1.
    }

    fun getAllFeeds(): List<RssFeed> {
        val db = readableDatabase
        val out = mutableListOf<RssFeed>()
        val sql =
            """
            SELECT f.id, f.url, f.title, f.site_url, f.icon_url, f.category,
                   f.display_mode, f.etag, f.last_modified, f.last_synced_at, f.last_error,
                   COALESCE((SELECT COUNT(*) FROM rss_articles a WHERE a.feed_id = f.id AND a.is_read = 0), 0) AS unread_count
            FROM rss_feeds f
            ORDER BY LOWER(f.title) ASC, f.id ASC
            """.trimIndent()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out +=
                    RssFeed(
                        id = c.getLong(0),
                        url = c.getString(1),
                        title = c.getString(2),
                        siteUrl = c.getString(3),
                        iconUrl = c.getString(4),
                        category = c.getString(5),
                        displayMode = RssFeedDisplayMode.fromStored(c.getString(6)),
                        etag = c.getString(7),
                        lastModified = c.getString(8),
                        lastSyncedAt = c.getLong(9),
                        lastError = c.getString(10),
                        unreadCount = c.getInt(11),
                    )
            }
        }
        return out
    }

    fun getFeedById(id: Long): RssFeed? = getAllFeeds().firstOrNull { it.id == id }

    fun getFeedByUrl(url: String): RssFeed? =
        getAllFeeds().firstOrNull { it.url.equals(url.trim(), ignoreCase = true) }

    fun upsertFeed(
        url: String,
        title: String,
        siteUrl: String? = null,
        iconUrl: String? = null,
        category: String? = null,
        displayMode: RssFeedDisplayMode = RssFeedDisplayMode.FULL_CONTENT,
    ): Long {
        val cleanUrl = url.trim()
        val db = writableDatabase
        val existing = getFeedByUrl(cleanUrl)
        if (existing != null) {
            val cv =
                ContentValues().apply {
                    put("title", title.trim().ifEmpty { existing.title })
                    if (!siteUrl.isNullOrBlank()) put("site_url", siteUrl)
                    if (!iconUrl.isNullOrBlank()) put("icon_url", iconUrl)
                    if (!category.isNullOrBlank()) put("category", category)
                    put("display_mode", displayMode.name)
                }
            db.update("rss_feeds", cv, "id = ?", arrayOf(existing.id.toString()))
            return existing.id
        }
        val cv =
            ContentValues().apply {
                put("url", cleanUrl)
                put("title", title.trim().ifEmpty { cleanUrl })
                put("site_url", siteUrl)
                put("icon_url", iconUrl)
                put("category", category)
                put("display_mode", displayMode.name)
                put("created_at", System.currentTimeMillis())
            }
        return db.insertWithOnConflict("rss_feeds", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun updateFeedDetails(
        id: Long,
        url: String,
        title: String,
        category: String?,
        displayMode: RssFeedDisplayMode,
    ) {
        val cv =
            ContentValues().apply {
                put("url", url.trim())
                put("title", title.trim().ifEmpty { url.trim() })
                put("category", category?.trim()?.takeIf { it.isNotEmpty() })
                put("display_mode", displayMode.name)
            }
        writableDatabase.update("rss_feeds", cv, "id = ?", arrayOf(id.toString()))
    }

    fun updateFeedSyncMeta(
        id: Long,
        title: String? = null,
        siteUrl: String? = null,
        iconUrl: String? = null,
        etag: String? = null,
        lastModified: String? = null,
        syncedAt: Long = System.currentTimeMillis(),
        error: String? = null,
    ) {
        val cv =
            ContentValues().apply {
                if (!title.isNullOrBlank()) put("title", title)
                if (!siteUrl.isNullOrBlank()) put("site_url", siteUrl)
                if (!iconUrl.isNullOrBlank()) put("icon_url", iconUrl)
                if (etag != null) put("etag", etag)
                if (lastModified != null) put("last_modified", lastModified)
                put("last_synced_at", syncedAt)
                put("last_error", error)
            }
        writableDatabase.update("rss_feeds", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteFeed(id: Long) {
        writableDatabase.delete("rss_feeds", "id = ?", arrayOf(id.toString()))
    }

    fun upsertArticles(
        feedId: Long,
        articles: List<ParsedArticle>,
        fetchedAt: Long = System.currentTimeMillis(),
    ) {
        if (articles.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sql =
                """
                INSERT INTO rss_articles (
                    id, feed_id, title, link, author, summary_html, snippet, image_url, published_at, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    link = excluded.link,
                    author = COALESCE(excluded.author, rss_articles.author),
                    summary_html = COALESCE(excluded.summary_html, rss_articles.summary_html),
                    snippet = CASE WHEN length(excluded.snippet) > 0 THEN excluded.snippet ELSE rss_articles.snippet END,
                    image_url = COALESCE(excluded.image_url, rss_articles.image_url),
                    published_at = CASE WHEN excluded.published_at > 0 THEN excluded.published_at ELSE rss_articles.published_at END,
                    fetched_at = excluded.fetched_at
                """.trimIndent()
            val stmt = db.compileStatement(sql)
            for (article in articles) {
                stmt.clearBindings()
                stmt.bindString(1, article.externalId)
                stmt.bindLong(2, feedId)
                stmt.bindString(3, article.title)
                stmt.bindString(4, article.link)
                if (article.author != null) stmt.bindString(5, article.author) else stmt.bindNull(5)
                if (article.summaryHtml != null) stmt.bindString(6, article.summaryHtml) else stmt.bindNull(6)
                stmt.bindString(7, article.snippet)
                if (article.imageUrl != null) stmt.bindString(8, article.imageUrl) else stmt.bindNull(8)
                stmt.bindLong(9, article.publishedAt)
                stmt.bindLong(10, fetchedAt)
                stmt.execute()
            }
            // Keep the newest MAX_ARTICLES_PER_FEED per feed so the database stays fast indefinitely.
            db.execSQL(
                """
                DELETE FROM rss_articles
                WHERE feed_id = ? AND id NOT IN (
                    SELECT id FROM rss_articles WHERE feed_id = ?
                    ORDER BY published_at DESC LIMIT $MAX_ARTICLES_PER_FEED
                )
                """.trimIndent(),
                arrayOf(feedId.toString(), feedId.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getRecentArticles(
        feedId: Long? = null,
        limit: Int = 400,
    ): List<RssArticle> {
        val db = readableDatabase
        val out = mutableListOf<RssArticle>()
        val where = if (feedId != null) "WHERE a.feed_id = ?" else ""
        val args = if (feedId != null) arrayOf(feedId.toString(), limit.toString()) else arrayOf(limit.toString())
        val sql =
            """
            SELECT a.id, a.feed_id, f.title, f.icon_url, f.display_mode,
                   a.title, a.link, a.author, a.summary_html, a.snippet,
                   a.image_url, a.published_at, a.is_read,
                   a.cached_full_html, a.cached_byline, a.cached_site_name
            FROM rss_articles a
            INNER JOIN rss_feeds f ON f.id = a.feed_id
            $where
            ORDER BY a.published_at DESC, a.fetched_at DESC
            LIMIT ?
            """.trimIndent()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out +=
                    RssArticle(
                        id = c.getString(0),
                        feedId = c.getLong(1),
                        feedTitle = c.getString(2),
                        feedIconUrl = c.getString(3),
                        feedDisplayMode = RssFeedDisplayMode.fromStored(c.getString(4)),
                        title = c.getString(5),
                        link = c.getString(6),
                        author = c.getString(7),
                        summaryHtml = c.getString(8),
                        snippet = c.getString(9),
                        imageUrl = c.getString(10),
                        publishedAt = c.getLong(11),
                        isRead = c.getInt(12) != 0,
                        cachedFullHtml = c.getString(13),
                        cachedByline = c.getString(14),
                        cachedSiteName = c.getString(15),
                    )
            }
        }
        return out
    }

    fun getCachedArticleByLink(link: String): RssArticle? {
        val db = readableDatabase
        val sql =
            """
            SELECT a.id, a.feed_id, f.title, f.icon_url, f.display_mode,
                   a.title, a.link, a.author, a.summary_html, a.snippet,
                   a.image_url, a.published_at, a.is_read,
                   a.cached_full_html, a.cached_byline, a.cached_site_name
            FROM rss_articles a
            INNER JOIN rss_feeds f ON f.id = a.feed_id
            WHERE a.link = ?
            LIMIT 1
            """.trimIndent()
        db.rawQuery(sql, arrayOf(link)).use { c ->
            if (!c.moveToNext()) return null
            return RssArticle(
                id = c.getString(0),
                feedId = c.getLong(1),
                feedTitle = c.getString(2),
                feedIconUrl = c.getString(3),
                feedDisplayMode = RssFeedDisplayMode.fromStored(c.getString(4)),
                title = c.getString(5),
                link = c.getString(6),
                author = c.getString(7),
                summaryHtml = c.getString(8),
                snippet = c.getString(9),
                imageUrl = c.getString(10),
                publishedAt = c.getLong(11),
                isRead = c.getInt(12) != 0,
                cachedFullHtml = c.getString(13),
                cachedByline = c.getString(14),
                cachedSiteName = c.getString(15),
            )
        }
    }

    fun markArticleRead(
        articleId: String,
        isRead: Boolean,
    ) {
        val cv = ContentValues().apply { put("is_read", if (isRead) 1 else 0) }
        writableDatabase.update("rss_articles", cv, "id = ?", arrayOf(articleId))
    }

    fun markLinkRead(link: String) {
        val cv = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update("rss_articles", cv, "link = ?", arrayOf(link))
    }

    fun markAllRead(feedId: Long? = null) {
        val cv = ContentValues().apply { put("is_read", 1) }
        if (feedId != null) {
            writableDatabase.update("rss_articles", cv, "feed_id = ?", arrayOf(feedId.toString()))
        } else {
            writableDatabase.update("rss_articles", cv, null, null)
        }
    }

    fun cacheFullArticle(
        link: String,
        fullHtml: String,
        byline: String?,
        siteName: String?,
    ) {
        val cv =
            ContentValues().apply {
                put("cached_full_html", fullHtml)
                if (!byline.isNullOrBlank()) put("cached_byline", byline)
                if (!siteName.isNullOrBlank()) put("cached_site_name", siteName)
            }
        writableDatabase.update("rss_articles", cv, "link = ?", arrayOf(link))
    }

    fun clearArticleCache() {
        writableDatabase.delete("rss_articles", null, null)
        val cv =
            ContentValues().apply {
                putNull("etag")
                putNull("last_modified")
                put("last_synced_at", 0L)
            }
        writableDatabase.update("rss_feeds", cv, null, null)
    }

    fun pruneArticlesOlderThan(cutoffEpochMs: Long): Int =
        writableDatabase.delete(
            "rss_articles",
            "fetched_at < ? AND is_read = 1",
            arrayOf(cutoffEpochMs.toString()),
        )

    companion object {
        internal const val DB_NAME = "crux_rss.db"
        private const val DB_VERSION = 1
        private const val MAX_ARTICLES_PER_FEED = 150
    }
}

/**
 * One-time migration from Flare's legacy Room3 `app.db` (`DbRssSources`) into [RssDatabase],
 * so existing users upgrading from v1.0.16 keep every subscribed RSS feed automatically.
 * Brand-new installs with no legacy feeds receive [CuratedRssDirectory.starterFeeds].
 */
internal object LegacyRssMigrator {
    private const val PREFS_NAME = "crux_rss_meta"
    private const val KEY_INITIALIZED = "rss_v2_initialized"
    private const val TAG = "LegacyRssMigrator"

    fun ensureInitialized(
        context: Context,
        db: RssDatabase,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return

        val imported = importLegacyFeeds(context, db)
        if (imported == 0 && db.getAllFeeds().isEmpty()) {
            CuratedRssDirectory.starterFeeds.forEach { feed ->
                db.upsertFeed(
                    url = feed.url,
                    title = feed.title,
                    category = feed.category,
                    displayMode = feed.displayMode,
                )
            }
        }
        prefs.edit { putBoolean(KEY_INITIALIZED, true) }
    }

    internal fun importLegacyFeeds(
        context: Context,
        targetDb: RssDatabase,
        legacyDbFile: java.io.File = context.applicationContext.getDatabasePath("app.db"),
    ): Int {
        if (!legacyDbFile.exists() || !legacyDbFile.canRead()) return 0
        var count = 0
        runCatching {
            SQLiteDatabase.openDatabase(legacyDbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
                legacy.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='DbRssSources'",
                    null,
                ).use { check ->
                    if (!check.moveToFirst()) return 0
                }
                legacy.rawQuery(
                    "SELECT url, title, icon, displayMode, type FROM DbRssSources",
                    null,
                ).use { c ->
                    while (c.moveToNext()) {
                        val url = c.getString(0)?.trim().orEmpty()
                        val title = c.getString(1)?.trim().orEmpty()
                        val icon = c.getString(2)?.trim()?.takeIf { it.isNotEmpty() }
                        val mode = RssFeedDisplayMode.fromStored(c.getString(3))
                        val type = c.getString(4)?.trim().orEmpty()
                        if (url.isNotEmpty() && (type.isEmpty() || type.equals("RSS", ignoreCase = true))) {
                            targetDb.upsertFeed(
                                url = url,
                                title = title.ifEmpty { url },
                                iconUrl = icon,
                                displayMode = mode,
                            )
                            count++
                        }
                    }
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Skipped legacy DbRssSources import: ${err.message}")
        }
        return count
    }
}
