package org.crux.rss

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure-Android reactive RSS repository with bounded parallel ETag/304 syncing, offline full-text
 * caching, unread state, OPML 2.0 import/export, and automatic legacy feed migration.
 */
internal class RssRepository private constructor(
    private val appContext: Context,
    private val db: RssDatabase = RssDatabase(appContext),
    private val network: RssNetworkClient = RssNetworkClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _feeds = MutableStateFlow<List<RssFeed>>(emptyList())
    val feeds: StateFlow<List<RssFeed>> = _feeds.asStateFlow()

    private val _articles = MutableStateFlow<List<RssArticle>>(emptyList())
    val articles: StateFlow<List<RssArticle>> = _articles.asStateFlow()

    private val _syncStatus = MutableStateFlow(RssSyncStatus())
    val syncStatus: StateFlow<RssSyncStatus> = _syncStatus.asStateFlow()

    @Volatile
    private var initialized = false

    init {
        scope.launch {
            ensureLoaded()
            // Kick off a gentle initial refresh when articles are empty or stale (> 15 min).
            val newestSync = _feeds.value.maxOfOrNull { it.lastSyncedAt } ?: 0L
            if (_articles.value.isEmpty() || System.currentTimeMillis() - newestSync > STALE_SYNC_MS) {
                syncAll(force = false)
            }
        }
    }

    suspend fun ensureLoaded() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            if (!initialized) {
                LegacyRssMigrator.ensureInitialized(appContext, db)
                reloadStateFromDb()
                initialized = true
            }
        }
    }

    private fun reloadStateFromDb() {
        _feeds.value = db.getAllFeeds()
        _articles.value = db.getRecentArticles(limit = 500)
    }

    suspend fun syncAll(force: Boolean = true) {
        ensureLoaded()
        if (!syncMutex.tryLock()) return
        try {
            val allFeeds = withContext(Dispatchers.IO) { db.getAllFeeds() }
            if (allFeeds.isEmpty()) return
            val now = System.currentTimeMillis()
            val toSync =
                if (force) {
                    allFeeds
                } else {
                    allFeeds.filter { now - it.lastSyncedAt >= MIN_AUTO_SYNC_GAP_MS }
                }
            if (toSync.isEmpty()) return

            val completed = AtomicInteger(0)
            val errors = AtomicInteger(0)
            _syncStatus.value = RssSyncStatus(syncing = true, syncedFeeds = 0, totalFeeds = toSync.size)
            val semaphore = Semaphore(MAX_CONCURRENT_SYNCS)

            coroutineScope {
                toSync
                    .map { feed ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val ok = syncOneInternal(feed, force = force)
                                if (!ok) errors.incrementAndGet()
                                val done = completed.incrementAndGet()
                                _syncStatus.value =
                                    _syncStatus.value.copy(
                                        syncedFeeds = done,
                                        errorCount = errors.get(),
                                    )
                            }
                        }
                    }.awaitAll()
            }

            withContext(Dispatchers.IO) { reloadStateFromDb() }
            _syncStatus.value =
                RssSyncStatus(
                    syncing = false,
                    syncedFeeds = toSync.size,
                    totalFeeds = toSync.size,
                    lastSyncedAt = System.currentTimeMillis(),
                    errorCount = errors.get(),
                )
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun syncFeed(feedId: Long) {
        ensureLoaded()
        val feed = withContext(Dispatchers.IO) { db.getFeedById(feedId) } ?: return
        _syncStatus.value = RssSyncStatus(syncing = true, syncedFeeds = 0, totalFeeds = 1)
        val ok = withContext(Dispatchers.IO) { syncOneInternal(feed, force = true) }
        withContext(Dispatchers.IO) { reloadStateFromDb() }
        _syncStatus.value =
            RssSyncStatus(
                syncing = false,
                syncedFeeds = 1,
                totalFeeds = 1,
                lastSyncedAt = System.currentTimeMillis(),
                errorCount = if (ok) 0 else 1,
            )
    }

    private suspend fun syncOneInternal(
        feed: RssFeed,
        force: Boolean,
    ): Boolean =
        try {
            val result =
                network.fetchFeed(
                    url = feed.url,
                    etag = if (force) null else feed.etag,
                    lastModified = if (force) null else feed.lastModified,
                )
            val now = System.currentTimeMillis()
            when (result) {
                FeedFetchResult.NotModified -> {
                    db.updateFeedSyncMeta(id = feed.id, syncedAt = now, error = null)
                }

                is FeedFetchResult.Updated -> {
                    val resolvedIcon =
                        result.feed.iconUrl
                            ?: feed.iconUrl
                            ?: RssNetworkClient.defaultFaviconFor(result.feed.siteUrl ?: feed.url)
                    db.updateFeedSyncMeta(
                        id = feed.id,
                        title = feed.title.takeIf { it.isNotBlank() && it != feed.url } ?: result.feed.title,
                        siteUrl = result.feed.siteUrl ?: feed.siteUrl,
                        iconUrl = resolvedIcon,
                        etag = result.etag,
                        lastModified = result.lastModified,
                        syncedAt = now,
                        error = null,
                    )
                    db.upsertArticles(feed.id, result.feed.articles, fetchedAt = now)
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            db.updateFeedSyncMeta(
                id = feed.id,
                syncedAt = System.currentTimeMillis(),
                error = e.message ?: e::class.simpleName,
            )
            false
        }

    suspend fun discoverFeeds(rawInput: String): List<DiscoveredFeed> = network.discoverFeeds(rawInput)

    suspend fun addFeed(
        url: String,
        title: String,
        siteUrl: String? = null,
        iconUrl: String? = null,
        category: String? = null,
        displayMode: RssFeedDisplayMode = RssFeedDisplayMode.FULL_CONTENT,
    ): Long {
        ensureLoaded()
        val id =
            withContext(Dispatchers.IO) {
                val createdId =
                    db.upsertFeed(
                        url = url,
                        title = title,
                        siteUrl = siteUrl,
                        iconUrl = iconUrl ?: RssNetworkClient.defaultFaviconFor(siteUrl ?: url),
                        category = category,
                        displayMode = displayMode,
                    )
                reloadStateFromDb()
                createdId
            }
        if (id > 0L) {
            scope.launch { syncFeed(id) }
        }
        return id
    }

    suspend fun updateFeed(
        id: Long,
        url: String,
        title: String,
        category: String?,
        displayMode: RssFeedDisplayMode,
    ) {
        ensureLoaded()
        withContext(Dispatchers.IO) {
            db.updateFeedDetails(id = id, url = url, title = title, category = category, displayMode = displayMode)
            reloadStateFromDb()
        }
    }

    suspend fun deleteFeed(id: Long) {
        ensureLoaded()
        withContext(Dispatchers.IO) {
            db.deleteFeed(id)
            reloadStateFromDb()
        }
    }

    suspend fun markRead(
        articleId: String,
        isRead: Boolean = true,
    ) {
        withContext(Dispatchers.IO) {
            db.markArticleRead(articleId, isRead)
            reloadStateFromDb()
        }
    }

    suspend fun markLinkRead(link: String) {
        withContext(Dispatchers.IO) {
            db.markLinkRead(link)
            reloadStateFromDb()
        }
    }

    suspend fun markAllRead(feedId: Long? = null) {
        withContext(Dispatchers.IO) {
            db.markAllRead(feedId)
            reloadStateFromDb()
        }
    }

    suspend fun importOpml(
        opmlXml: String,
        onProgress: ((imported: Int, total: Int, currentTitle: String) -> Unit)? = null,
    ): Int {
        ensureLoaded()
        val entries = withContext(Dispatchers.Default) { OpmlCodec.parse(opmlXml) }
        if (entries.isEmpty()) return 0
        var imported = 0
        withContext(Dispatchers.IO) {
            entries.forEach { entry ->
                db.upsertFeed(
                    url = entry.url,
                    title = entry.title,
                    siteUrl = entry.siteUrl,
                    iconUrl = RssNetworkClient.defaultFaviconFor(entry.siteUrl ?: entry.url),
                    category = entry.category,
                )
                imported++
                onProgress?.invoke(imported, entries.size, entry.title)
            }
            reloadStateFromDb()
        }
        scope.launch { syncAll(force = false) }
        return imported
    }

    suspend fun exportOpml(): String {
        ensureLoaded()
        val currentFeeds = withContext(Dispatchers.IO) { db.getAllFeeds() }
        return OpmlCodec.export(currentFeeds)
    }

    /**
     * Loads an article for the Paged Reader: returns cached offline full-article HTML immediately
     * when available (unless [forceRefresh] is true), otherwise fetches + extracts via Readability
     * and caches the result for offline re-reading.
     */
    suspend fun loadArticleDocument(
        url: String,
        descriptionHtml: String? = null,
        title: String? = null,
        fallbackHtml: String? = null,
        forceRefresh: Boolean = false,
    ): RssDocument {
        ensureLoaded()
        val cached = withContext(Dispatchers.IO) { db.getCachedArticleByLink(url) }
        if (cached != null && !cached.isRead) {
            scope.launch { markRead(cached.id, true) }
        }
        if (descriptionHtml != null) {
            return network.extractArticle(url = url, descriptionHtml = descriptionHtml, title = title)
        }
        val cachedHtml = cached?.cachedFullHtml
        if (!forceRefresh && !cachedHtml.isNullOrBlank()) {
            val html = cachedHtml
            val text =
                withContext(Dispatchers.Default) {
                    com.fleeksoft.ksoup.Ksoup
                        .parse(html)
                        .apply { select("script,style,template").remove() }
                        .text()
                }
            return RssDocument(
                title = title?.takeIf { it.isNotBlank() } ?: cached.title,
                contentHtml = html,
                textContent = text,
                byline = cached.cachedByline ?: cached.author,
                siteName = cached.cachedSiteName ?: cached.feedTitle,
                publishedAt = cached.publishedAt.takeIf { it > 0L },
                source = RssDocumentSource.OfflineCache,
            )
        }
        val effectiveFallback = fallbackHtml?.takeIf { it.isNotBlank() } ?: cached?.summaryHtml
        val doc =
            network.extractArticle(
                url = url,
                descriptionHtml = null,
                title = title?.takeIf { it.isNotBlank() } ?: cached?.title,
                fallbackHtml = effectiveFallback,
            )
        if (doc.source == RssDocumentSource.Full && doc.contentHtml.isNotBlank()) {
            withContext(Dispatchers.IO) {
                db.cacheFullArticle(
                    link = url,
                    fullHtml = doc.contentHtml,
                    byline = doc.byline,
                    siteName = doc.siteName,
                )
            }
        }
        return doc
    }

    suspend fun clearArticleCache() {
        ensureLoaded()
        withContext(Dispatchers.IO) {
            db.clearArticleCache()
            reloadStateFromDb()
        }
    }

    suspend fun pruneArticlesOlderThan(cutoffEpochMs: Long): Int {
        ensureLoaded()
        return withContext(Dispatchers.IO) {
            val deleted = db.pruneArticlesOlderThan(cutoffEpochMs)
            if (deleted > 0) reloadStateFromDb()
            deleted
        }
    }

    companion object {
        private const val MAX_CONCURRENT_SYNCS = 6
        private const val STALE_SYNC_MS = 15 * 60 * 1000L
        private const val MIN_AUTO_SYNC_GAP_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: RssRepository? = null

        fun get(context: Context): RssRepository =
            instance ?: synchronized(this) {
                instance ?: RssRepository(context.applicationContext).also { instance = it }
            }
    }
}
