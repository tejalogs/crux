package org.crux.ui.model.mapper

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.network.rss.hasRssContent
import org.crux.data.network.rss.model.Feed
import org.crux.model.AccountType
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.render.toUi
import org.crux.ui.route.DeeplinkRoute
import kotlin.time.Instant

// internal fun StatusContent.Rss.RssContent.render(): UiTimelineV2 =
//    when (this) {
//        is StatusContent.Rss.RssContent.Atom -> this.render()
//        is StatusContent.Rss.RssContent.Rss20 -> this.render()
//        is StatusContent.Rss.RssContent.RDF -> this.render()
//    }

internal val Feed.title: String
    get() =
        when (this) {
            is Feed.Atom -> {
                this.title?.value?.takeIf { it.isNotBlank() } ?: this.links
                    .firstOrNull()
                    ?.href
                    .orEmpty()
            }

            is Feed.Rss20 -> {
                this.channel.title?.takeIf { it.isNotBlank() } ?: this.channel.link.orEmpty()
            }

            is Feed.RDF -> {
                this.channel.title?.takeIf { it.isNotBlank() } ?: this.channel.link.orEmpty()
            }
        }

internal val Feed.link: String?
    get() =
        when (this) {
            is Feed.Atom -> this.links.firstOrNull()?.href
            is Feed.Rss20 -> this.channel.link
            is Feed.RDF -> this.channel.link
        }

/** The article address: the alternate link, falling back to the entry id. Null when neither exists. */
internal fun Feed.Atom.Entry.resolvedLink(): String? =
    (links.firstOrNull { it.rel == null || it.rel == "alternate" }?.href ?: id)?.trim()?.takeIf { it.isNotEmpty() }

/** The article address: the link, falling back to a guid that is a permalink or looks like a web address. */
internal fun Feed.Rss20.Item.resolvedLink(): String? =
    link?.trim()?.takeIf { it.isNotEmpty() }
        ?: guid
            ?.takeIf { it.isPermaLink || it.value.trim().startsWith("http", ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

internal fun Feed.RDF.Item.resolvedLink(): String? = link?.trim()?.takeIf { it.isNotEmpty() }

/** Turns a relative or protocol-relative address into an absolute one against [base]; leaves it as is if that fails. */
internal fun resolveFeedUrl(
    base: String?,
    ref: String,
): String {
    val trimmed = ref.trim()
    if (base.isNullOrBlank() || trimmed.isEmpty()) return trimmed
    return runCatching { StringUtil.resolve(base, trimmed) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: trimmed
}

private val WEB_LINK = Regex("^https?://[^/?#\\s]+", RegexOption.IGNORE_CASE)

/** The Referer some image hosts demand. Relative or `tag:` links have no host, so they get none. */
private fun refererHeaders(link: String?): ImmutableMap<String, String>? {
    val host =
        link
            ?.takeIf { WEB_LINK.containsMatchIn(it) }
            ?.let { runCatching { Url(it).host }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: return null
    return persistentMapOf("Referer" to "https://$host/")
}

private fun firstImageSrc(
    doc: Document?,
    base: String?,
): String? =
    doc
        ?.select("img")
        ?.firstOrNull()
        ?.attr("src")
        ?.takeIf { it.isNotBlank() }
        ?.let { resolveFeedUrl(base, it) }

private fun imageMedia(
    url: String?,
    link: String?,
): UiMedia.Image? =
    url?.let {
        UiMedia.Image(
            url = it,
            previewUrl = it,
            description = null,
            height = 0f,
            width = 0f,
            sensitive = false,
            customHeaders = refererHeaders(link),
        )
    }

internal fun Feed.Atom.Entry.render(
    sourceName: String,
    sourceIcon: String?,
    displayMode: RssDisplayMode,
    sourceLanguage: String? = null,
): UiTimelineV2 {
    val link = resolvedLink().orEmpty()
    val rawHtml = content?.value?.takeIf { it.hasRssContent() } ?: summary?.value
    val descHtml = rawHtml?.let { Ksoup.parse(it, link) }
    val img =
        firstImageSrc(descHtml, link)
            ?: (media?.thumbnail?.url ?: links.firstOrNull { it.rel == "enclosure" && it.type?.startsWith("image/") == true }?.href)
                ?.let { resolveFeedUrl(link, it) }
    return UiTimelineV2.Feed(
        title = title?.value?.decodeFeedTitle()?.takeIf { it.isNotEmpty() && it.isNotBlank() },
        description = descHtml?.text(),
        descriptionHtml = rawHtml,
        url = link,
        sourceLanguages = listOfNotNull(sourceLanguage).toPersistentList(),
        source =
            UiTimelineV2.Feed.Source(
                name = sourceName,
                icon = sourceIcon,
            ),
        media = imageMedia(img, link),
        clickEvent =
            displayMode.toFeedClickEvent(
                url = link,
                descriptionHtml = rawHtml,
                title = title?.value?.decodeFeedTitle(),
            ),
        createdAt =
            (published ?: updated)
                ?.let { input -> parseRssDateToInstant(input) }
                ?.toUi() ?: Instant.fromEpochMilliseconds(0L).toUi(),
        accountType = AccountType.Guest,
    )
}

internal fun Feed.Rss20.Item.render(
    sourceName: String,
    sourceIcon: String?,
    displayMode: RssDisplayMode,
    sourceLanguage: String? = null,
): UiTimelineV2 {
    val url = resolvedLink().orEmpty()
    val rawHtml = contentEncoded?.takeIf { it.hasRssContent() } ?: description
    val descHtml = rawHtml?.let { Ksoup.parse(it, url) }
    val img =
        firstImageSrc(descHtml, url)
            ?: (
                mediaContent.firstOrNull()?.url
                    ?: enclosure?.takeIf { it.type?.startsWith("image/") == true }?.url
            )?.let { resolveFeedUrl(url, it) }
    val itemTitle = title?.decodeFeedTitle()
    return UiTimelineV2.Feed(
        title = itemTitle,
        description = descHtml?.text(),
        descriptionHtml = rawHtml,
        url = url,
        sourceLanguages = listOfNotNull(sourceLanguage).toPersistentList(),
        source =
            UiTimelineV2.Feed.Source(
                name = sourceName,
                icon = sourceIcon,
            ),
        media = imageMedia(img, url),
        clickEvent =
            displayMode.toFeedClickEvent(
                url = url,
                descriptionHtml = rawHtml,
                title = itemTitle,
            ),
        createdAt =
            (pubDate ?: dcDate)
                ?.let { input -> parseRssDateToInstant(input) }
                ?.toUi() ?: Instant.fromEpochMilliseconds(0L).toUi(),
        accountType = AccountType.Guest,
    )
}

internal fun Feed.RDF.Item.render(
    sourceName: String,
    sourceIcon: String?,
    displayMode: RssDisplayMode,
    sourceLanguage: String? = null,
): UiTimelineV2 {
    val url = resolvedLink().orEmpty()
    val rawHtml = contentEncoded?.takeIf { it.hasRssContent() } ?: description
    val descHtml = rawHtml?.let { Ksoup.parse(it, url) }
    val img = firstImageSrc(descHtml, url)
    val itemTitle = title?.decodeFeedTitle()
    return UiTimelineV2.Feed(
        title = itemTitle,
        description = descHtml?.text(),
        descriptionHtml = rawHtml,
        url = url,
        sourceLanguages = listOfNotNull(sourceLanguage).toPersistentList(),
        source =
            UiTimelineV2.Feed.Source(
                name = sourceName,
                icon = sourceIcon,
            ),
        media = imageMedia(img, url),
        clickEvent =
            displayMode.toFeedClickEvent(
                url = url,
                descriptionHtml = rawHtml,
                title = itemTitle,
            ),
        createdAt =
            date
                ?.let { input -> parseRssDateToInstant(input) }
                ?.toUi() ?: Instant.fromEpochMilliseconds(0L).toUi(),
        accountType = AccountType.Guest,
    )
}

private fun RssDisplayMode.toFeedClickEvent(
    url: String,
    descriptionHtml: String?,
    title: String?,
): ClickEvent =
    ClickEvent.Deeplink(
        when (this) {
            RssDisplayMode.OPEN_IN_BROWSER -> {
                DeeplinkRoute.OpenLinkDirectly(url)
            }

            RssDisplayMode.FULL_CONTENT -> {
                DeeplinkRoute.Rss.Detail(url = url, title = title, fallbackHtml = descriptionHtml)
            }

            RssDisplayMode.DESCRIPTION_ONLY -> {
                if (descriptionHtml.isThinFeedText()) {
                    // Link feeds like Hacker News carry no article, so read the page instead.
                    DeeplinkRoute.Rss.Detail(url = url, title = title, fallbackHtml = descriptionHtml)
                } else {
                    DeeplinkRoute.Rss.Detail(url, descriptionHtml, title)
                }
            }
        },
    )

/** Feed titles often arrive with HTML entities such as `&#8217;` still encoded. */
internal fun String.decodeFeedTitle(): String = Ksoup.parse(this).text()

/** True when the feed item has no text of its own, only links such as "Comments". */
internal fun String?.isThinFeedText(): Boolean {
    if (this == null) return true
    val body = Ksoup.parse(this).body()
    if (body.select("img[src], video[src]").isNotEmpty()) return false
    body.select("a, script, style").remove()
    return body.text().isBlank()
}
