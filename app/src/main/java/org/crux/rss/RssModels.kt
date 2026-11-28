package org.crux.rss

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
internal enum class RssFeedDisplayMode {
    FULL_CONTENT,
    DESCRIPTION_ONLY,
    OPEN_IN_BROWSER,
    ;

    companion object {
        fun fromStored(raw: String?): RssFeedDisplayMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: FULL_CONTENT
    }
}

@Immutable
internal data class RssFeed(
    val id: Long,
    val url: String,
    val title: String,
    val siteUrl: String? = null,
    val iconUrl: String? = null,
    val category: String? = null,
    val displayMode: RssFeedDisplayMode = RssFeedDisplayMode.FULL_CONTENT,
    val etag: String? = null,
    val lastModified: String? = null,
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null,
    val unreadCount: Int = 0,
) {
    val host: String
        get() =
            runCatching { java.net.URI(siteUrl ?: url).host?.removePrefix("www.") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: url
}

@Immutable
internal data class RssArticle(
    val id: String,
    val feedId: Long,
    val feedTitle: String,
    val feedIconUrl: String?,
    val feedDisplayMode: RssFeedDisplayMode,
    val title: String,
    val link: String,
    val author: String? = null,
    val summaryHtml: String? = null,
    val snippet: String = "",
    val imageUrl: String? = null,
    val publishedAt: Long = 0L,
    val isRead: Boolean = false,
    val cachedFullHtml: String? = null,
    val cachedByline: String? = null,
    val cachedSiteName: String? = null,
)

internal data class ParsedArticle(
    val externalId: String,
    val title: String,
    val link: String,
    val author: String?,
    val summaryHtml: String?,
    val snippet: String,
    val imageUrl: String?,
    val publishedAt: Long,
)

internal data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val iconUrl: String?,
    val articles: List<ParsedArticle>,
)

@Immutable
internal data class DiscoveredFeed(
    val url: String,
    val title: String,
    val siteUrl: String? = null,
    val iconUrl: String? = null,
    val recommendedMode: RssFeedDisplayMode = RssFeedDisplayMode.FULL_CONTENT,
)

internal data class OpmlFeedEntry(
    val url: String,
    val title: String,
    val category: String? = null,
    val siteUrl: String? = null,
)

@Immutable
internal data class RssSyncStatus(
    val syncing: Boolean = false,
    val syncedFeeds: Int = 0,
    val totalFeeds: Int = 0,
    val lastSyncedAt: Long = 0L,
    val errorCount: Int = 0,
)

internal enum class RssDocumentSource {
    Full,
    FeedSummary,
    FeedText,
    OfflineCache,
}

@Immutable
@Serializable
internal data class RssDocument(
    val title: String,
    val contentHtml: String,
    val textContent: String,
    val byline: String? = null,
    val siteName: String? = null,
    val publishedAt: Long? = null,
    @Transient val source: RssDocumentSource = RssDocumentSource.Full,
)

internal enum class RssArticleFailureReason {
    Unreachable,
    TimedOut,
    NoArticle,
    HttpError,
    Other,
}

internal object CuratedRssDirectory {
    data class Category(
        val name: String,
        val description: String,
        val feeds: List<CuratedFeed>,
    )

    data class CuratedFeed(
        val title: String,
        val url: String,
        val description: String,
        val category: String,
        val displayMode: RssFeedDisplayMode = RssFeedDisplayMode.FULL_CONTENT,
    )

    val categories: List<Category> =
        listOf(
            Category(
                name = "AI & Frontier Research",
                description = "LLMs, reasoning systems, autonomous agents, and AI safety",
                feeds =
                    listOf(
                        CuratedFeed(
                            title = "OpenAI Research & News",
                            url = "https://openai.com/news/rss.xml",
                            description = "Official announcements, model releases, and frontier research",
                            category = "AI & Frontier Research",
                        ),
                        CuratedFeed(
                            title = "Google DeepMind",
                            url = "https://deepmind.google/blog/rss.xml",
                            description = "Breakthroughs in Gemini, AlphaFold, and AI science",
                            category = "AI & Frontier Research",
                        ),
                        CuratedFeed(
                            title = "Simon Willison's Weblog",
                            url = "https://simonwillison.net/atom/entries/",
                            description = "AI engineering, local LLMs, and practical tooling",
                            category = "AI & Frontier Research",
                        ),
                        CuratedFeed(
                            title = "MIT Technology Review - AI",
                            url = "https://www.technologyreview.com/topic/artificial-intelligence/feed",
                            description = "Authoritative journalism on machine learning and emerging tech",
                            category = "AI & Frontier Research",
                        ),
                        CuratedFeed(
                            title = "Import AI",
                            url = "https://importai.substack.com/feed",
                            description = "Weekly newsletter on AI research, policy, and compute",
                            category = "AI & Frontier Research",
                        ),
                        CuratedFeed(
                            title = "Latent Space",
                            url = "https://www.latent.space/feed",
                            description = "AI engineering newsletter covering foundations and open models",
                            category = "AI & Frontier Research",
                        ),
                    ),
            ),
            Category(
                name = "Tech & Engineering",
                description = "Computing breakthroughs, startups, and software architecture",
                feeds =
                    listOf(
                        CuratedFeed(
                            title = "Hacker News",
                            url = "https://news.ycombinator.com/rss",
                            description = "Top discussions in computing, programming, and startups",
                            category = "Tech & Engineering",
                        ),
                        CuratedFeed(
                            title = "Ars Technica",
                            url = "https://feeds.arstechnica.com/arstechnica/index",
                            description = "Deep technology analysis, science, and digital policy",
                            category = "Tech & Engineering",
                        ),
                        CuratedFeed(
                            title = "The Verge",
                            url = "https://www.theverge.com/rss/index.xml",
                            description = "Technology reporting, creative culture, and platforms",
                            category = "Tech & Engineering",
                        ),
                        CuratedFeed(
                            title = "404 Media",
                            url = "https://www.404media.co/rss/",
                            description = "Investigative tech reporting on cybersecurity and AI",
                            category = "Tech & Engineering",
                        ),
                        CuratedFeed(
                            title = "Stratechery",
                            url = "https://stratechery.com/feed/",
                            description = "Macro analysis of tech strategy and platform dynamics",
                            category = "Tech & Engineering",
                        ),
                        CuratedFeed(
                            title = "LWN.net",
                            url = "https://lwn.net/headlines/rss",
                            description = "Authoritative news from Linux and free software development",
                            category = "Tech & Engineering",
                        ),
                    ),
            ),
            Category(
                name = "Privacy & Security",
                description = "Digital rights, cryptography, and defensive security",
                feeds =
                    listOf(
                        CuratedFeed(
                            title = "Electronic Frontier Foundation",
                            url = "https://www.eff.org/rss/updates.xml",
                            description = "Defending civil liberties and user rights in the digital world",
                            category = "Privacy & Security",
                        ),
                        CuratedFeed(
                            title = "Privacy Guides",
                            url = "https://www.privacyguides.org/en/feed_rss_created.xml",
                            description = "Practical privacy recommendations and open-source tools",
                            category = "Privacy & Security",
                        ),
                        CuratedFeed(
                            title = "Krebs on Security",
                            url = "https://krebsonsecurity.com/feed/",
                            description = "Investigative security and cybercrime journalism",
                            category = "Privacy & Security",
                        ),
                        CuratedFeed(
                            title = "Schneier on Security",
                            url = "https://www.schneier.com/feed/atom/",
                            description = "Security, cryptography, and privacy analysis",
                            category = "Privacy & Security",
                        ),
                        CuratedFeed(
                            title = "Tor Project Blog",
                            url = "https://blog.torproject.org/rss.xml",
                            description = "Anonymity networks and anti-censorship engineering",
                            category = "Privacy & Security",
                        ),
                    ),
            ),
            Category(
                name = "Creative & Strategy",
                description = "Essays, mental models, and independent writing",
                feeds =
                    listOf(
                        CuratedFeed(
                            title = "Farnam Street",
                            url = "https://fs.blog/feed/",
                            description = "Mental models and decision-making clarity",
                            category = "Creative & Strategy",
                        ),
                        CuratedFeed(
                            title = "Paul Graham Essays",
                            url = "https://paulgraham.com/rss.html",
                            description = "Essays on startups, software, and independent thinking",
                            category = "Creative & Strategy",
                        ),
                        CuratedFeed(
                            title = "Benedict Evans",
                            url = "https://www.ben-evans.com/benedictevans?format=rss",
                            description = "Macro tech industry analysis and mobile computing",
                            category = "Creative & Strategy",
                        ),
                        CuratedFeed(
                            title = "Seth Godin's Blog",
                            url = "https://seths.blog/feed/",
                            description = "Daily notes on creative work and shipping ideas",
                            category = "Creative & Strategy",
                        ),
                        CuratedFeed(
                            title = "First Round Review",
                            url = "https://review.firstround.com/feed.xml",
                            description = "Playbooks from founders and product builders",
                            category = "Creative & Strategy",
                        ),
                    ),
            ),
        )

    val allFeeds: List<CuratedFeed> = categories.flatMap { it.feeds }

    /** Clean starter pack seeded on brand-new installs when no legacy RSS feeds exist. */
    val starterFeeds: List<CuratedFeed> =
        listOf(
            allFeeds.first { it.url == "https://feeds.arstechnica.com/arstechnica/index" },
            allFeeds.first { it.url == "https://news.ycombinator.com/rss" },
            allFeeds.first { it.url == "https://www.eff.org/rss/updates.xml" },
            allFeeds.first { it.url == "https://simonwillison.net/atom/entries/" },
        )
}
