package org.crux.data.repository

import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.database.app.model.SubscriptionType
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
                name = "Artificial Intelligence & Frontier Research",
                description = "LLMs, reasoning systems, autonomous agents, and AI safety breakthroughs",
                feeds =
                    listOf(
                        FeedItem(
                            title = "OpenAI Research & News",
                            url = "https://openai.com/news/rss.xml",
                            description = "Official announcements, GPT releases, and frontier alignment",
                        ),
                        FeedItem(
                            title = "Google DeepMind",
                            url = "https://deepmind.google/blog/rss.xml",
                            description = "Breakthroughs in Gemini, AlphaFold, agentic systems, and science",
                        ),
                        FeedItem(
                            title = "MIT Technology Review - AI",
                            url = "https://www.technologyreview.com/topic/artificial-intelligence/feed",
                            description = "Authoritative journalism on machine learning and emerging technologies",
                        ),
                        FeedItem(
                            title = "Import AI",
                            url = "https://importai.substack.com/feed",
                            description = "Jack Clark’s definitive weekly newsletter on AI research and compute",
                        ),
                        FeedItem(
                            title = "Latent Space",
                            url = "https://www.latent.space/feed",
                            description = "The AI engineer newsletter covering foundations, agents, and open models",
                        ),
                        FeedItem(
                            title = "The Gradient",
                            url = "https://thegradient.pub/rss/",
                            description = "Deep dives into AI research written by practicing researchers",
                        ),
                        FeedItem(
                            title = "Simon Willison’s Weblog",
                            url = "https://simonwillison.net/atom/entries/",
                            description = "AI engineering, local LLMs, prompt experiments, and Python",
                        ),
                    ),
            ),
            Category(
                name = "Tech & Engineering",
                description = "Computing breakthroughs, startup technology, and digital architecture",
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
                            title = "TechCrunch",
                            url = "https://techcrunch.com/feed/",
                            description = "Breaking startup funding, venture, and software innovation news",
                        ),
                        FeedItem(
                            title = "Wired",
                            url = "https://www.wired.com/feed/rss",
                            description = "Essential reporting on technology, science, and culture",
                        ),
                        FeedItem(
                            title = "404 Media",
                            url = "https://www.404media.co/rss/",
                            description = "Hard-hitting investigative tech reporting on cybersecurity and AI",
                        ),
                        FeedItem(
                            title = "Stratechery",
                            url = "https://stratechery.com/feed/",
                            description = "Ben Thompson’s macro analysis of tech strategy and AI platform dynamics",
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
                        FeedItem(
                            title = "Paul Graham Essays",
                            url = "https://paulgraham.com/rss.html",
                            description = "Timeless essays on software startups, thinking, and agency",
                        ),
                        FeedItem(
                            title = "Benedict Evans",
                            url = "https://www.ben-evans.com/benedictevans?format=rss",
                            description = "Macro tech industry analysis, AI trajectories, and mobile computing",
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
