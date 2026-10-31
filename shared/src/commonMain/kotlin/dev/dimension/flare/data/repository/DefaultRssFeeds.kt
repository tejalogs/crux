package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public object DefaultRssFeeds {
    public data class Category(
        val name: String,
        val description: String,
        val feeds: List<FeedItem>,
    )

    public data class FeedItem(
        val title: String,
        val url: String,
        val description: String,
        val icon: String? = null,
        val displayMode: RssDisplayMode = RssDisplayMode.FULL_CONTENT,
    ) {
        public fun toSubscriptionSourceInput(): SubscriptionSourceInput =
            SubscriptionSourceInput(
                url = url,
                title = title,
                icon = icon,
                displayMode = displayMode,
                type = SubscriptionType.RSS,
            )
    }

    public val categories: List<Category> =
        listOf(
            Category(
                name = "Tech & Open Source",
                description = "Computing breakthroughs, software engineering, and open infrastructure",
                feeds =
                    listOf(
                        FeedItem(
                            title = "Hacker News",
                            url = "https://news.ycombinator.com/rss",
                            description = "Top discussions in computing, programming, and startups",
                        ),
                        FeedItem(
                            title = "Ars Technica",
                            url = "https://feeds.arstechnica.com/arstechnica/index",
                            description = "Deep technology analysis, science, and digital policy",
                        ),
                        FeedItem(
                            title = "The Verge",
                            url = "https://www.theverge.com/rss/index.xml",
                            description = "Technology reporting, creative culture, and computing platforms",
                        ),
                        FeedItem(
                            title = "LWN.net",
                            url = "https://lwn.net/headlines/rss",
                            description = "Authoritative news from Linux and free software development",
                        ),
                    ),
            ),
            Category(
                name = "Digital Privacy & Security",
                description = "Cryptographic integrity, defensive security, and privacy tooling",
                feeds =
                    listOf(
                        FeedItem(
                            title = "Electronic Frontier Foundation",
                            url = "https://www.eff.org/rss/updates.xml",
                            description = "Defending civil liberties and user rights in the digital world",
                        ),
                        FeedItem(
                            title = "Privacy Guides",
                            url = "https://www.privacyguides.org/en/feed_rss_created.xml",
                            description = "Practical privacy recommendations and security tools",
                        ),
                        FeedItem(
                            title = "Krebs on Security",
                            url = "https://krebsonsecurity.com/feed/",
                            description = "In-depth investigative security and cybercrime journalism",
                        ),
                        FeedItem(
                            title = "Schneier on Security",
                            url = "https://www.schneier.com/feed/atom/",
                            description = "Security, cryptography, and privacy analysis by Bruce Schneier",
                        ),
                    ),
            ),
            Category(
                name = "Internet Censorship, Rights & Safety",
                description = "Circumvention research, surveillance resistance, and press freedom",
                feeds =
                    listOf(
                        FeedItem(
                            title = "Tor Project Blog",
                            url = "https://blog.torproject.org/rss.xml",
                            description = "Anonymity network developments and anti-censorship tools",
                        ),
                        FeedItem(
                            title = "Citizen Lab",
                            url = "https://citizenlab.ca/feed/",
                            description = "Investigating digital espionage and targeted surveillance",
                        ),
                        FeedItem(
                            title = "Freedom of the Press Foundation",
                            url = "https://freedom.press/feed/",
                            description = "Protecting public-interest journalism and digital safety",
                        ),
                        FeedItem(
                            title = "NetBlocks",
                            url = "https://netblocks.org/feed",
                            description = "Mapping internet freedom and tracking connectivity shutdowns",
                        ),
                    ),
            ),
            Category(
                name = "Creative Thinking",
                description = "Mental models, ideation habits, and high-agency creative practice",
                feeds =
                    listOf(
                        FeedItem(
                            title = "Farnam Street (Brain Food)",
                            url = "https://fs.blog/feed/",
                            description = "Timeless insights, mental models, and decision-making clarity",
                        ),
                        FeedItem(
                            title = "Seth Godin's Blog",
                            url = "https://seths.blog/feed/",
                            description = "Daily provocations on art, shipping ideas, and authentic work",
                        ),
                        FeedItem(
                            title = "Austin Kleon",
                            url = "https://austinkleon.com/feed/",
                            description = "Reflections on art, notebooks, reading, and living creatively",
                        ),
                    ),
            ),
            Category(
                name = "Marketing & Strategy",
                description = "User psychology, retention design, and organic product distribution",
                feeds =
                    listOf(
                        FeedItem(
                            title = "Growth.Design",
                            url = "https://growth.design/feed.xml",
                            description = "Interactive UX case studies and behavioral product psychology",
                        ),
                        FeedItem(
                            title = "Moz Blog",
                            url = "https://moz.com/posts/rss/blog",
                            description = "Search optimization, inbound discovery, and content marketing",
                        ),
                        FeedItem(
                            title = "First Round Review",
                            url = "https://review.firstround.com/feed.xml",
                            description = "Actionable playbooks from founders and product operators",
                        ),
                    ),
            ),
        )

    public val allFeeds: List<FeedItem> = categories.flatMap { it.feeds }
}
